package com.twojstar.llmbench.data.security

import kotlin.io.encoding.Base64

enum class TextFindingSeverity {
    HIGH,
    MEDIUM,
    LOW
}

data class TextSafetyFinding(
    val severity: TextFindingSeverity,
    val kind: String,
    val label: String,
    val detail: String,
    val offset: Int,
    val length: Int,
    val line: Int,
    val column: Int
)

data class TextInspectionResult(
    val findings: List<TextSafetyFinding>,
    val detectedCount: Int = findings.size,
    val truncated: Boolean = detectedCount > findings.size
) {
    val hasFindings: Boolean get() = detectedCount > 0
    val highCount: Int get() = findings.count { it.severity == TextFindingSeverity.HIGH }
    val mediumCount: Int get() = findings.count { it.severity == TextFindingSeverity.MEDIUM }
    val lowCount: Int get() = findings.count { it.severity == TextFindingSeverity.LOW }
}

object TextInspector {
    private const val MAX_FINDINGS = 500
    private const val MIN_BASE64_CANDIDATE_CHARS = 20
    private const val MAX_BASE64_DECODE_CHARS = 65_536
    private const val MAX_VARIATION_PREVIEW_BYTES = 512
    private const val MAX_TAG_PREVIEW_CHARS = 512
    private const val MIXED_SCRIPT_PREVIEW_CHARS = 144
    private const val KIND_MARKER_CARRIER = "marker-carrier"
    private const val BIDI_ORDER_DETAIL = "Bidi control can make source render in a misleading order."
    private const val BIDI_ISOLATION_DETAIL = "Bidi isolation control can conceal source ordering."
    private const val INVISIBLE_FORMATTING_DETAIL = "Invisible formatting character."

    private data class RawFinding(
        val severity: TextFindingSeverity,
        val kind: String,
        val label: String,
        val detail: String,
        val offset: Int,
        val length: Int
    )

    private class FindingCollector {
        private val high = mutableListOf<RawFinding>()
        private val medium = mutableListOf<RawFinding>()
        private val low = mutableListOf<RawFinding>()
        var detectedCount: Int = 0
            private set

        fun add(finding: RawFinding) {
            detectedCount += 1
            val bucket = when (finding.severity) {
                TextFindingSeverity.HIGH -> high
                TextFindingSeverity.MEDIUM -> medium
                TextFindingSeverity.LOW -> low
            }
            if (bucket.size < MAX_FINDINGS) bucket += finding
        }

        fun retained(): List<RawFinding> = buildList(MAX_FINDINGS) {
            addAll(high.take(MAX_FINDINGS))
            if (size < MAX_FINDINGS) addAll(medium.take(MAX_FINDINGS - size))
            if (size < MAX_FINDINGS) addAll(low.take(MAX_FINDINGS - size))
        }
    }

    private data class SpecialCharacter(
        val severity: TextFindingSeverity,
        val label: String,
        val detail: String
    )

    private data class MixedScriptTokenState(
        var start: Int = -1,
        var end: Int = -1,
        var latin: Boolean = false,
        var cyrillic: Boolean = false,
        var greek: Boolean = false
    ) {
        fun reset() {
            start = -1
            end = -1
            latin = false
            cyrillic = false
            greek = false
        }
    }

    private val specialCharacters = buildMap {
        put(0x00AD, SpecialCharacter(TextFindingSeverity.MEDIUM, "Soft hyphen", "Invisible discretionary hyphen."))
        put(0x034F, SpecialCharacter(TextFindingSeverity.MEDIUM, "Combining grapheme joiner", "Invisible combining control."))
        put(0x061C, SpecialCharacter(TextFindingSeverity.MEDIUM, "Arabic letter mark", "Invisible bidi-affecting mark."))
        put(0x200B, SpecialCharacter(TextFindingSeverity.MEDIUM, "Zero-width space", "Invisible separator often used for Unicode smuggling."))
        put(0x200C, SpecialCharacter(TextFindingSeverity.LOW, "Zero-width non-joiner", "Can be legitimate in some writing systems; review unexpected use."))
        put(0x200D, SpecialCharacter(TextFindingSeverity.LOW, "Zero-width joiner", "Used legitimately in scripts and emoji; review unexpected use."))
        put(0x200E, SpecialCharacter(TextFindingSeverity.MEDIUM, "Left-to-right mark", "Invisible bidirectional text mark."))
        put(0x200F, SpecialCharacter(TextFindingSeverity.MEDIUM, "Right-to-left mark", "Invisible bidirectional text mark."))
        put(0x202A, SpecialCharacter(TextFindingSeverity.HIGH, "Left-to-right embedding", BIDI_ORDER_DETAIL))
        put(0x202B, SpecialCharacter(TextFindingSeverity.HIGH, "Right-to-left embedding", BIDI_ORDER_DETAIL))
        put(0x202C, SpecialCharacter(TextFindingSeverity.HIGH, "Pop directional formatting", "Bidi control terminator."))
        put(0x202D, SpecialCharacter(TextFindingSeverity.HIGH, "Left-to-right override", BIDI_ORDER_DETAIL))
        put(0x202E, SpecialCharacter(TextFindingSeverity.HIGH, "Right-to-left override", BIDI_ORDER_DETAIL))
        put(0x2060, SpecialCharacter(TextFindingSeverity.MEDIUM, "Word joiner", INVISIBLE_FORMATTING_DETAIL))
        put(0x2061, SpecialCharacter(TextFindingSeverity.MEDIUM, "Function application", INVISIBLE_FORMATTING_DETAIL))
        put(0x2062, SpecialCharacter(TextFindingSeverity.MEDIUM, "Invisible times", INVISIBLE_FORMATTING_DETAIL))
        put(0x2063, SpecialCharacter(TextFindingSeverity.MEDIUM, "Invisible separator", INVISIBLE_FORMATTING_DETAIL))
        put(0x2064, SpecialCharacter(TextFindingSeverity.MEDIUM, "Invisible plus", INVISIBLE_FORMATTING_DETAIL))
        put(0x2066, SpecialCharacter(TextFindingSeverity.HIGH, "Left-to-right isolate", BIDI_ISOLATION_DETAIL))
        put(0x2067, SpecialCharacter(TextFindingSeverity.HIGH, "Right-to-left isolate", BIDI_ISOLATION_DETAIL))
        put(0x2068, SpecialCharacter(TextFindingSeverity.HIGH, "First-strong isolate", BIDI_ISOLATION_DETAIL))
        put(0x2069, SpecialCharacter(TextFindingSeverity.HIGH, "Pop directional isolate", BIDI_ISOLATION_DETAIL))
        for (codePoint in 0x206A..0x206F) {
            put(codePoint, SpecialCharacter(TextFindingSeverity.HIGH, "Deprecated bidi control", "Deprecated invisible directional control."))
        }
        put(0xFEFF, SpecialCharacter(TextFindingSeverity.MEDIUM, "Zero-width no-break space / BOM", "Unexpected inside document text."))
        put(0xFFFD, SpecialCharacter(TextFindingSeverity.MEDIUM, "Replacement character", "May indicate earlier decoding or copy/paste data loss."))
        val unusualSpaces = listOf(
            0x00A0, 0x1680,
            0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
            0x202F, 0x205F, 0x3000
        )
        unusualSpaces.forEach { codePoint ->
            put(codePoint, SpecialCharacter(TextFindingSeverity.LOW, "Unusual Unicode space", "Whitespace that can be hard to distinguish from an ordinary space."))
        }
    }

    private val injectionPatterns = listOf(
        Regex("\\b(?:ignore|disregard|forget)\\b.{0,90}\\b(?:previous|prior|above|system|developer)\\b.{0,60}\\b(?:instruction|instructions|prompt|message|messages)\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("\\b(?:reveal|print|show|output|expose|dump)\\b.{0,60}\\b(?:system|developer)\\s+(?:prompt|message|instructions?)\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("\\b(?:do not|don't)\\s+(?:tell|show|inform)\\s+(?:the\\s+)?user\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("(?:^|[^\\p{L}])(?:zignoruj|ignoruj|pomiń|zapomnij)(?:$|[^\\p{L}]).{0,90}\\b(?:poprzednie|wcześniejsze|powyższe|systemowe|deweloperskie)\\b.{0,60}\\b(?:instrukcje|polecenia|prompt|wiadomości)\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    )

    fun inspect(text: String): TextInspectionResult {
        if (text.isEmpty()) return TextInspectionResult(emptyList())
        val collector = FindingCollector()
        scanCharacters(text, collector::add)
        scanUnicodeTags(text, collector::add)
        scanMixedScripts(text, collector::add)
        scanPromptInjection(text, collector::add)
        scanEncodedPrompts(text, collector::add)

        val retainedByOffset = collector.retained()
            .sortedWith(compareBy<RawFinding> { it.offset }.thenBy(::severityRank))
        val lineStarts = makeLineStarts(text)
        var cursorLine = -1
        var cursorOffset = 0
        var cursorColumn = 1
        val located = retainedByOffset.map { finding ->
            val lineIndex = lineIndexForOffset(lineStarts, finding.offset)
            if (lineIndex != cursorLine || finding.offset < cursorOffset) {
                cursorLine = lineIndex
                cursorOffset = lineStarts[lineIndex]
                cursorColumn = 1
            }
            while (cursorOffset < finding.offset) {
                cursorOffset += codePointAt(text, cursorOffset).second
                cursorColumn += 1
            }
            TextSafetyFinding(
                severity = finding.severity,
                kind = finding.kind,
                label = finding.label,
                detail = finding.detail,
                offset = finding.offset,
                length = finding.length,
                line = lineIndex + 1,
                column = cursorColumn
            )
        }.sortedWith(compareBy<TextSafetyFinding> { it.severity.ordinal }.thenBy { it.offset })
        return TextInspectionResult(
            findings = located,
            detectedCount = collector.detectedCount,
            truncated = collector.detectedCount > located.size
        )
    }

    private fun severityRank(finding: RawFinding): Int = when (finding.severity) {
        TextFindingSeverity.HIGH -> 0
        TextFindingSeverity.MEDIUM -> 1
        TextFindingSeverity.LOW -> 2
    }

    private fun scanCharacters(text: String, add: (RawFinding) -> Unit) {
        var offset = 0
        var variationStart = -1
        var variationCount = 0
        val variationBytes = mutableListOf<Int>()

        fun flushVariation(end: Int) {
            if (variationCount >= 4) {
                val preview = decodeUtf8(variationBytes.take(MAX_VARIATION_PREVIEW_BYTES))
                    ?.takeIf(::hasVisibleText)
                    ?.let(::quotedPreview)
                    ?: variationBytes.take(48).joinToString(" ") { it.toString(16).padStart(2, '0') }
                add(
                    RawFinding(
                        TextFindingSeverity.MEDIUM,
                        KIND_MARKER_CARRIER,
                        "Variation-selector sequence",
                        "$variationCount consecutive variation selectors. Decoded payload: $preview",
                        variationStart,
                        end - variationStart
                    )
                )
            }
            variationStart = -1
            variationCount = 0
            variationBytes.clear()
        }

        while (offset < text.length) {
            val (codePoint, width) = codePointAt(text, offset)
            val variationByte = variationSelectorByte(codePoint)
            if (variationByte != null) {
                if (variationStart < 0) variationStart = offset
                variationCount += 1
                if (variationBytes.size < MAX_VARIATION_PREVIEW_BYTES) variationBytes += variationByte
            } else {
                flushVariation(offset)
            }

            if (!isTagCharacter(codePoint)) {
                val special = specialCharacters[codePoint]
                when {
                    special != null -> add(
                        RawFinding(
                            special.severity,
                            "invisible",
                            special.label,
                            "${special.detail} ${codePointLabel(codePoint)}",
                            offset,
                            width
                        )
                    )
                    isRawControl(codePoint) -> add(
                        RawFinding(
                            TextFindingSeverity.HIGH,
                            "control",
                            "Control character",
                            "Unexpected non-printing control ${codePointLabel(codePoint)}.",
                            offset,
                            width
                        )
                    )
                    isNoncharacter(codePoint) -> add(
                        RawFinding(
                            TextFindingSeverity.HIGH,
                            "invalid-unicode",
                            "Unicode noncharacter",
                            "${codePointLabel(codePoint)} is reserved as a noncharacter.",
                            offset,
                            width
                        )
                    )
                    isPrivateUse(codePoint) -> add(
                        RawFinding(
                            TextFindingSeverity.LOW,
                            KIND_MARKER_CARRIER,
                            "Private-use character",
                            "${codePointLabel(codePoint)} has no standardized meaning and can carry application-specific metadata.",
                            offset,
                            width
                        )
                    )
                }
            }
            offset += width
        }
        flushVariation(text.length)
    }

    private fun scanUnicodeTags(text: String, add: (RawFinding) -> Unit) {
        var offset = 0
        while (offset < text.length) {
            val (codePoint, width) = codePointAt(text, offset)
            if (!isTagCharacter(codePoint)) {
                offset += width
                continue
            }
            val start = offset
            val payload = StringBuilder()
            var count = 0
            var previewTruncated = false
            while (offset < text.length) {
                val (tagCodePoint, tagWidth) = codePointAt(text, offset)
                if (!isTagCharacter(tagCodePoint)) break
                val ascii = tagCodePoint - 0xE0000
                if (ascii in 0x20..0x7E) {
                    if (payload.length < MAX_TAG_PREVIEW_CHARS) payload.append(ascii.toChar()) else previewTruncated = true
                }
                count += 1
                offset += tagWidth
            }
            val detail = if (payload.isEmpty()) {
                "$count invisible Unicode tag characters."
            } else {
                "Hidden tag payload: ${quotedPreview(payload.toString())}${if (previewTruncated) " (preview truncated)" else ""}"
            }
            add(
                RawFinding(
                    TextFindingSeverity.HIGH,
                    KIND_MARKER_CARRIER,
                    "Unicode tag sequence",
                    detail,
                    start,
                    offset - start
                )
            )
        }
    }

    private fun scanMixedScripts(text: String, add: (RawFinding) -> Unit) {
        val state = MixedScriptTokenState()
        var offset = 0
        while (offset < text.length) {
            val (codePoint, width) = codePointAt(text, offset)
            if (isMixedTokenCharacter(codePoint)) {
                updateMixedScriptToken(state, codePoint, offset, width)
            } else {
                flushMixedScriptToken(text, state, add)
            }
            offset += width
        }
        flushMixedScriptToken(text, state, add)
    }

    private fun isMixedTokenCharacter(codePoint: Int): Boolean =
        isLatin(codePoint) || isCyrillic(codePoint) || isGreek(codePoint) ||
            codePoint in '0'.code..'9'.code || codePoint == '_'.code || codePoint == '-'.code

    private fun updateMixedScriptToken(
        state: MixedScriptTokenState,
        codePoint: Int,
        offset: Int,
        width: Int
    ) {
        if (state.start < 0) state.start = offset
        state.end = offset + width
        state.latin = state.latin || isLatin(codePoint)
        state.cyrillic = state.cyrillic || isCyrillic(codePoint)
        state.greek = state.greek || isGreek(codePoint)
    }

    private fun flushMixedScriptToken(
        text: String,
        state: MixedScriptTokenState,
        add: (RawFinding) -> Unit
    ) {
        val isSuspicious = state.start >= 0 && state.latin && (state.cyrillic || state.greek)
        if (isSuspicious) {
            val previewEnd = state.start + minOf(MIXED_SCRIPT_PREVIEW_CHARS, state.end - state.start)
            val preview = text.substring(state.start, previewEnd)
            add(
                RawFinding(
                    TextFindingSeverity.MEDIUM,
                    "confusable",
                    "Mixed-script token",
                    "Latin mixed with Cyrillic or Greek can create look-alike identifiers or links: ${quotedPreview(preview)}.",
                    state.start,
                    state.end - state.start
                )
            )
        }
        state.reset()
    }

    private fun scanPromptInjection(text: String, add: (RawFinding) -> Unit) {
        injectionPatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                add(
                    RawFinding(
                        TextFindingSeverity.MEDIUM,
                        "prompt-injection",
                        "Prompt-injection-like instruction",
                        "Matched instruction: ${quotedPreview(match.value)}. Heuristic match only.",
                        match.range.first,
                        match.value.length
                    )
                )
            }
        }
    }

    private fun scanEncodedPrompts(text: String, add: (RawFinding) -> Unit) {
        var cursor = 0
        while (cursor < text.length) {
            while (cursor < text.length && !isBase64Alphabet(text[cursor])) cursor += 1
            val start = cursor
            while (cursor < text.length && isBase64Alphabet(text[cursor])) cursor += 1
            if (cursor - start < MIN_BASE64_CANDIDATE_CHARS) continue
            var end = cursor
            var padding = 0
            while (end < text.length && text[end] == '=' && padding < 2) {
                end += 1
                padding += 1
            }
            val length = end - start
            val encoded = text.substring(start, end)
            if (length <= MAX_BASE64_DECODE_CHARS) {
                val decoded = decodeBase64(encoded)
                val match = decoded?.let(::firstInjectionMatch)
                if (decoded != null && match != null) {
                    add(
                        RawFinding(
                            TextFindingSeverity.HIGH,
                            "prompt-injection",
                            "Encoded prompt-like instruction",
                            "Base64 decoded payload: ${quotedPreviewAround(decoded, match.range.first, match.value.length)}",
                            start,
                            length
                        )
                    )
                }
            } else {
                add(
                    RawFinding(
                        TextFindingSeverity.MEDIUM,
                        KIND_MARKER_CARRIER,
                        "Large Base64 carrier",
                        "Encoded run is $length characters; hidden content may exist inside.",
                        start,
                        length
                    )
                )
            }
            cursor = end
        }
    }

    private fun firstInjectionMatch(value: String): MatchResult? =
        injectionPatterns.firstNotNullOfOrNull { pattern -> pattern.find(value) }

    private fun decodeBase64(value: String): String? = runCatching {
        val normalized = value.replace('-', '+').replace('_', '/')
        val remainder = normalized.length % 4
        if (remainder == 1) return null
        val padded = normalized + "=".repeat((4 - remainder) % 4)
        Base64.Default.decode(padded).decodeToString(throwOnInvalidSequence = true)
    }.getOrNull()

    private fun decodeUtf8(bytes: List<Int>): String? = runCatching {
        bytes.map(Int::toByte).toByteArray().decodeToString(throwOnInvalidSequence = true)
    }.getOrNull()

    private fun codePointAt(text: String, offset: Int): Pair<Int, Int> {
        val first = text[offset].code
        if (first in 0xD800..0xDBFF && offset + 1 < text.length) {
            val second = text[offset + 1].code
            if (second in 0xDC00..0xDFFF) {
                val codePoint = 0x10000 + ((first - 0xD800) shl 10) + (second - 0xDC00)
                return codePoint to 2
            }
        }
        return first to 1
    }

    private fun variationSelectorByte(codePoint: Int): Int? = when (codePoint) {
        in 0xFE00..0xFE0F -> codePoint - 0xFE00
        in 0xE0100..0xE01EF -> codePoint - 0xE0100 + 16
        else -> null
    }

    private fun isTagCharacter(codePoint: Int): Boolean = codePoint in 0xE0000..0xE007F

    private fun isRawControl(codePoint: Int): Boolean =
        (codePoint < 0x20 && codePoint !in setOf(0x09, 0x0A, 0x0D)) || codePoint in 0x7F..0x9F

    private fun isNoncharacter(codePoint: Int): Boolean =
        codePoint in 0xFDD0..0xFDEF || (codePoint and 0xFFFF) == 0xFFFE || (codePoint and 0xFFFF) == 0xFFFF

    private fun isPrivateUse(codePoint: Int): Boolean =
        codePoint in 0xE000..0xF8FF || codePoint in 0xF0000..0xFFFFD || codePoint in 0x100000..0x10FFFD

    private fun isLatin(codePoint: Int): Boolean =
        codePoint in 0x0041..0x005A || codePoint in 0x0061..0x007A || codePoint in 0x00C0..0x024F || codePoint in 0x1E00..0x1EFF

    private fun isCyrillic(codePoint: Int): Boolean =
        codePoint in 0x0400..0x052F || codePoint in 0x2DE0..0x2DFF || codePoint in 0xA640..0xA69F || codePoint in 0x1C80..0x1C8F

    private fun isGreek(codePoint: Int): Boolean = codePoint in 0x0370..0x03FF || codePoint in 0x1F00..0x1FFF

    private fun isBase64Alphabet(char: Char): Boolean =
        char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '+' || char == '/' || char == '_' || char == '-'

    private fun codePointLabel(codePoint: Int): String =
        "U+${codePoint.toString(16).uppercase().padStart(if (codePoint <= 0xFFFF) 4 else 6, '0')}"

    private fun makeLineStarts(text: String): IntArray {
        val starts = mutableListOf(0)
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    val isCrLf = index + 1 < text.length && text[index + 1] == '\n'
                    starts += index + if (isCrLf) 2 else 1
                    index += if (isCrLf) 2 else 1
                }
                '\n', '\u2028', '\u2029' -> {
                    starts += index + 1
                    index += 1
                }
                else -> index += 1
            }
        }
        return starts.toIntArray()
    }

    private fun lineIndexForOffset(starts: IntArray, offset: Int): Int {
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= offset) low = mid + 1 else high = mid - 1
        }
        return high.coerceAtLeast(0)
    }

    private fun hasVisibleText(value: String): Boolean = value.any { it.code > 0x1F && it.code != 0x7F }

    private fun quotedPreview(value: String, limit: Int = 180): String {
        val escaped = buildString {
            value.forEach { char ->
                when {
                    char == '\n' -> append("\\n")
                    char == '\r' -> append("\\r")
                    char == '\t' -> append("\\t")
                    char.code < 0x20 || char.code == 0x7F -> append("\\u${char.code.toString(16).padStart(4, '0')}")
                    else -> append(char)
                }
            }
        }
        val clipped = if (escaped.length > limit) escaped.take(limit) + "…" else escaped
        return "\"$clipped\""
    }

    private fun quotedPreviewAround(value: String, matchStart: Int, matchLength: Int, limit: Int = 180): String {
        if (value.length <= limit) return quotedPreview(value, limit)
        val matchEnd = matchStart + matchLength
        val context = (limit - matchLength.coerceAtMost(limit)).coerceAtLeast(0)
        val before = matchStart.coerceAtMost(context / 2)
        val after = (value.length - matchEnd).coerceAtMost(context - before)
        val start = matchStart - before
        val end = matchEnd + after
        val preview = buildString {
            if (start > 0) append('…')
            append(value.substring(start, end))
            if (end < value.length) append('…')
        }
        return quotedPreview(preview, limit + 2)
    }
}
