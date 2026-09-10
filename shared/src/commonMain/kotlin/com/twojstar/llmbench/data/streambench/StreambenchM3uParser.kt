package com.twojstar.llmbench.data.streambench

/** Portable playlist metadata parsed from one M3U entry. User-controlled fields are redacted in logs. */
data class StreambenchPlaylistEntry(
    val id: String,
    val url: String,
    val title: String,
    val group: String,
    val logo: String,
    val country: String,
    val language: String,
    val quality: String,
    val radio: Boolean,
    val providerId: String,
    val providerLabel: String
) {
    override fun toString(): String =
        "StreambenchPlaylistEntry(radio=$radio, hasArtwork=${logo.isNotEmpty()}, metadata=<redacted>)"
}

/**
 * Local-first M3U parser derived from the canonical Streambench browser parser.
 *
 * This layer parses playlist structure and common metadata only. Playback/source classification stays
 * outside the parser so platform clients can validate a URL again immediately before opening media.
 */
object StreambenchM3uParser {
    const val MAX_SOURCE_UTF16_UNITS: Int = 8 * 1024 * 1024
    const val MAX_ENTRIES: Int = 10_000

    private val quotedAttribute = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(
        source: String,
        allowArtwork: Boolean = false,
        providerId: String = "local",
        providerLabel: String = "Local"
    ): List<StreambenchPlaylistEntry> {
        require(source.length <= MAX_SOURCE_UTF16_UNITS) {
            "Playlist exceeds the local parser size limit"
        }
        val items = mutableListOf<StreambenchPlaylistEntry>()
        var pending: PendingEntry? = null

        forEachLine(source) { line ->
            if (line.isEmpty()) return@forEachLine

            if (line.startsWith("#EXTINF:")) {
                val attributes = parseAttributes(line)
                pending = PendingEntry(
                    id = attributes["tvg-id"].orEmpty(),
                    title = extinfTitle(line).ifEmpty { attributes["tvg-name"].orEmpty() },
                    group = attributes["group-title"].orEmpty(),
                    logo = if (allowArtwork) {
                        parseHttpUrlCandidate(attributes["tvg-logo"].orEmpty())?.raw.orEmpty()
                    } else {
                        ""
                    },
                    country = attributes["tvg-country"].orEmpty(),
                    language = attributes["tvg-language"].orEmpty(),
                    quality = attributes["tvg-quality"]
                        ?: attributes["quality"]
                        ?: attributes["resolution"]
                        ?: "",
                    radio = attributes["radio"] == "true" || attributes["type"] == "radio"
                )
                return@forEachLine
            }

            if (line.startsWith("#")) return@forEachLine
            val url = parseHttpUrlCandidate(line)
            if (url == null) {
                pending = null
                return@forEachLine
            }
            require(items.size < MAX_ENTRIES) {
                "Playlist exceeds the local entry limit"
            }

            items += StreambenchPlaylistEntry(
                id = pending?.id.orEmpty(),
                url = url.raw,
                title = pending?.title?.takeIf { it.isNotEmpty() } ?: url.host,
                group = pending?.group.orEmpty(),
                logo = pending?.logo.orEmpty(),
                country = pending?.country.orEmpty(),
                language = pending?.language.orEmpty(),
                quality = pending?.quality.orEmpty(),
                radio = pending?.radio ?: false,
                providerId = providerId,
                providerLabel = providerLabel
            )
            pending = null
        }
        return items
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val separator = extinfSeparatorIndex(line)
        val metadata = if (separator >= 0) line.substring(0, separator) else line
        return buildMap {
            quotedAttribute.findAll(metadata).forEach { match ->
                put(match.groupValues[1].lowercase(), match.groupValues[2])
            }
        }
    }

    private fun extinfTitle(line: String): String {
        val separator = extinfSeparatorIndex(line)
        return if (separator >= 0) line.substring(separator + 1).trim() else ""
    }

    private fun extinfSeparatorIndex(line: String): Int {
        var quoted = false
        var index = "#EXTINF:".length
        while (index < line.length) {
            val character = line[index]
            if (character == '"' && line.getOrNull(index - 1) != '\\') quoted = !quoted
            if (character == ',' && !quoted) return index
            index += 1
        }
        return -1
    }

    private fun parseHttpUrlCandidate(value: String): HttpUrlCandidate? {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.any(::isForbiddenUrlCharacter)) return null
        val schemeEnd = candidate.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = candidate.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null

        val authorityStart = schemeEnd + 3
        val authorityEnd = candidate.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
            .takeIf { it >= 0 }
            ?: candidate.length
        if (authorityEnd <= authorityStart) return null
        val authority = candidate.substring(authorityStart, authorityEnd)
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isEmpty()) return null

        val host: String
        val port: String?
        if (hostPort.firstOrNull() == '[') {
            val closing = hostPort.indexOf(']')
            if (closing <= 1) return null
            host = hostPort.substring(1, closing)
            val remainder = hostPort.substring(closing + 1)
            port = when {
                remainder.isEmpty() -> null
                remainder.startsWith(":") && remainder.length > 1 -> remainder.substring(1)
                else -> return null
            }
        } else {
            if (hostPort.count { it == ':' } > 1) return null
            val lastColon = hostPort.lastIndexOf(':')
            if (lastColon >= 0) {
                host = hostPort.substring(0, lastColon)
                port = hostPort.substring(lastColon + 1)
            } else {
                host = hostPort
                port = null
            }
            if (host.isEmpty()) return null
        }
        if (port != null) {
            val portNumber = port.toIntOrNull() ?: return null
            if (portNumber !in 0..65_535) return null
        }
        return HttpUrlCandidate(raw = candidate, host = host)
    }

    private fun isForbiddenUrlCharacter(character: Char): Boolean =
        character <= ' ' || character == '\u007F' || character == '\\'

    private inline fun forEachLine(source: String, block: (String) -> Unit) {
        var start = if (source.firstOrNull() == '\uFEFF') 1 else 0
        while (start <= source.length) {
            val newline = source.indexOf('\n', start)
            val end = if (newline >= 0) newline else source.length
            val contentEnd = if (end > start && source[end - 1] == '\r') end - 1 else end
            block(source.substring(start, contentEnd).trim())
            if (newline < 0) break
            start = newline + 1
        }
    }

    private data class HttpUrlCandidate(
        val raw: String,
        val host: String
    )

    private data class PendingEntry(
        val id: String,
        val title: String,
        val group: String,
        val logo: String,
        val country: String,
        val language: String,
        val quality: String,
        val radio: Boolean
    )
}
