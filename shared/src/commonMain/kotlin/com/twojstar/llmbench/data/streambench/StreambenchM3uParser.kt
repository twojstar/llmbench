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
                    logo = if (allowArtwork) remoteHttpUrlOrNull(attributes["tvg-logo"].orEmpty()).orEmpty() else "",
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
            val url = remoteHttpUrlOrNull(line)
            if (url == null) {
                pending = null
                return@forEachLine
            }
            require(items.size < MAX_ENTRIES) {
                "Playlist exceeds the local entry limit"
            }

            items += StreambenchPlaylistEntry(
                id = pending?.id.orEmpty(),
                url = url,
                title = pending?.title?.takeIf { it.isNotEmpty() } ?: hostFromHttpUrl(url),
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

    private fun parseAttributes(line: String): Map<String, String> = buildMap {
        quotedAttribute.findAll(line).forEach { match ->
            put(match.groupValues[1].lowercase(), match.groupValues[2])
        }
    }

    private fun extinfTitle(line: String): String {
        var quoted = false
        var index = "#EXTINF:".length
        while (index < line.length) {
            val character = line[index]
            if (character == '"' && line.getOrNull(index - 1) != '\\') quoted = !quoted
            if (character == ',' && !quoted) return line.substring(index + 1).trim()
            index += 1
        }
        return ""
    }

    private fun remoteHttpUrlOrNull(value: String): String? {
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
        if (hostPort.firstOrNull() == '[') {
            if (hostPort.indexOf(']') <= 1) return null
        } else {
            val host = hostPort.substringBeforeLast(':', hostPort)
            if (host.isEmpty()) return null
        }
        return candidate
    }

    private fun hostFromHttpUrl(url: String): String {
        val authorityStart = url.indexOf("://") + 3
        val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
            .takeIf { it >= 0 }
            ?: url.length
        val hostPort = url.substring(authorityStart, authorityEnd).substringAfterLast('@')
        if (hostPort.firstOrNull() == '[') {
            val closing = hostPort.indexOf(']')
            if (closing > 1) return hostPort.substring(1, closing)
        }
        val lastColon = hostPort.lastIndexOf(':')
        return if (lastColon > 0 && hostPort.indexOf(':') == lastColon) {
            hostPort.substring(0, lastColon)
        } else {
            hostPort
        }
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
