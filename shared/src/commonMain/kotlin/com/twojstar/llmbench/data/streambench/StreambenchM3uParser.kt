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
                        parseArtworkUrlCandidate(attributes["tvg-logo"].orEmpty()).orEmpty()
                    } else {
                        ""
                    },
                    country = attributes["tvg-country"].orEmpty(),
                    language = attributes["tvg-language"].orEmpty(),
                    quality = (
                        attributes["tvg-quality"]
                            ?: attributes["quality"]
                            ?: attributes["resolution"]
                    ).orEmpty(),
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

    private fun parseArtworkUrlCandidate(value: String): String? {
        val url = parseHttpUrlCandidate(value) ?: return null
        if (url.scheme != "https" || !isPublicArtworkHostname(url.host)) return null
        return url.raw
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
            if (!isIpv6Literal(host)) return null
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
        return HttpUrlCandidate(raw = candidate, scheme = scheme, host = host)
    }

    private fun isIpv6Literal(host: String): Boolean {
        if (host.isEmpty() || '%' in host) return false
        val compression = host.indexOf("::")
        if (compression >= 0) {
            if (host.indexOf("::", compression + 2) >= 0) return false
            val leftUnits = ipv6SectionUnits(host.substring(0, compression), allowIpv4Tail = false)
                ?: return false
            val rightUnits = ipv6SectionUnits(host.substring(compression + 2), allowIpv4Tail = true)
                ?: return false
            return leftUnits + rightUnits < 8
        }
        return ipv6SectionUnits(host, allowIpv4Tail = true) == 8
    }

    private fun ipv6SectionUnits(section: String, allowIpv4Tail: Boolean): Int? {
        if (section.isEmpty()) return 0
        val groups = section.split(':')
        var units = 0
        groups.forEachIndexed { index, group ->
            if (group.isEmpty()) return null
            if ('.' in group) {
                if (!allowIpv4Tail || index != groups.lastIndex || !isIpv4Literal(group)) return null
                units += 2
            } else {
                if (group.length !in 1..4 || group.any { !it.isAsciiHexDigit() }) return null
                units += 1
            }
        }
        return units
    }

    /**
     * Artwork metadata is stricter than stream URLs: accept only HTTPS DNS hostnames and leave DNS/IP
     * revalidation to the eventual network loader immediately before a request.
     */
    private fun isPublicArtworkHostname(host: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        if (normalized.isEmpty() || normalized.length > 253 || '.' !in normalized || ':' in normalized) return false
        if (
            normalized == "localhost" ||
            normalized.endsWith(".localhost") ||
            normalized.endsWith(".local") ||
            normalized.endsWith(".lan") ||
            normalized.endsWith(".internal")
        ) return false
        if (isIpv4Literal(normalized) || normalized.all { it.isDigit() || it == '.' }) return false

        return normalized.split('.').all { label ->
            label.length in 1..63 &&
                label.first().isAsciiLetterOrDigit() &&
                label.last().isAsciiLetterOrDigit() &&
                label.all { it.isAsciiLetterOrDigit() || it == '-' }
        }
    }

    private fun isIpv4Literal(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() &&
                part.length <= 3 &&
                part.all { it in '0'..'9' } &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun Char.isAsciiHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private fun isForbiddenUrlCharacter(character: Char): Boolean =
        character <= ' ' || character == '\u007F' || character == '\\'

    private inline fun forEachLine(source: String, block: (String) -> Unit) {
        var start = if (source.firstOrNull() == '\uFEFF') 1 else 0
        while (start <= source.length) {
            val newline = source.indexOfAny(charArrayOf('\r', '\n'), start)
            val end = if (newline >= 0) newline else source.length
            block(source.substring(start, end).trim())
            if (newline < 0) break
            start = newline + 1
            if (source[newline] == '\r' && source.getOrNull(start) == '\n') start += 1
        }
    }

    private data class HttpUrlCandidate(
        val raw: String,
        val scheme: String,
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
