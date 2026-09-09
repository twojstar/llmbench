package com.twojstar.llmbench.data.document

import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.events.AliasEvent
import it.krzeminski.snakeyaml.engine.kmp.events.Event
import it.krzeminski.snakeyaml.engine.kmp.events.NodeEvent
import it.krzeminski.snakeyaml.engine.kmp.exceptions.YamlEngineException
import it.krzeminski.snakeyaml.engine.kmp.parser.ParserImpl
import it.krzeminski.snakeyaml.engine.kmp.scanner.StreamReader
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.xmlStreaming

/** Structured text syntaxes currently validated by the portable document core. */
enum class StructuredTextFormat {
    JSON,
    YAML,
    XML
}

data class StructuredTextValidationResult(
    val format: StructuredTextFormat,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = errorMessage == null
}

data class StructuredTextFormatResult(
    val text: String,
    val changed: Boolean,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = errorMessage == null
}

/**
 * Syntax-aware validation for portable document tooling.
 *
 * This deliberately stays separate from BOM/EOL/NUL diagnostics. Invalid source is never repaired
 * heuristically, and formatting is exposed only where the current shared parser can round-trip the
 * syntax without knowingly weakening fidelity.
 */
object StructuredTextDiagnostics {
    private const val MAX_JSON_NESTING = 128
    private const val MAX_JSON_FORMATTED_CHARS = 8 * 1024 * 1024
    private const val MAX_YAML_CODE_POINTS = 3 * 1024 * 1024
    private const val MAX_XML_CHARS = 3 * 1024 * 1024
    private const val MAX_XML_NESTING = 128

    fun validate(text: String, format: StructuredTextFormat): StructuredTextValidationResult =
        when (format) {
            StructuredTextFormat.JSON -> validateJson(text)
            StructuredTextFormat.YAML -> validateYaml(text)
            StructuredTextFormat.XML -> validateXml(text)
        }

    /**
     * Pretty-prints strict JSON only after a fidelity gate. Duplicate object keys are valid syntax
     * but are not formatted until a caller explicitly resolves their ambiguous semantics.
     */
    fun formatJson(text: String): StructuredTextFormatResult {
        val inspection = StrictJsonParser(text).inspect()
        inspection.errorMessage?.let { error ->
            return StructuredTextFormatResult(
                text = text,
                changed = false,
                errorMessage = error
            )
        }
        if (inspection.hasDuplicateObjectKeys) {
            return StructuredTextFormatResult(
                text = text,
                changed = false,
                errorMessage = "Formatting blocked: duplicate JSON object keys must remain byte-visible."
            )
        }

        val formatted = try {
            JsonLexicalFormatter(text).format()
        } catch (_: JsonFormattingLimitExceededException) {
            return StructuredTextFormatResult(
                text = text,
                changed = false,
                errorMessage = "Formatting blocked: formatted JSON would exceed the 8 MiB safety limit."
            )
        }
        return StructuredTextFormatResult(
            text = formatted,
            changed = formatted != text
        )
    }

    private fun validateJson(text: String): StructuredTextValidationResult {
        val inspection = StrictJsonParser(text).inspect()
        return StructuredTextValidationResult(
            format = StructuredTextFormat.JSON,
            errorMessage = inspection.errorMessage
        )
    }

    /**
     * Parses YAML events only. This validates syntax without forcing documents into Kotaml's
     * narrower YamlNode model or constructing application objects. Anchors are tracked per document
     * so aliases still have to refer to a previously defined anchor. StreamReader enforces the
     * bounded code-point limit before imported input can consume unbounded parser memory.
     */
    private fun validateYaml(text: String): StructuredTextValidationResult {
        return try {
            val settings = LoadSettings(
                label = "LlmBench document",
                codePointLimit = MAX_YAML_CODE_POINTS
            )
            val parser = ParserImpl(settings, StreamReader(settings, text))
            val anchors = mutableSetOf<Any>()
            while (parser.hasNext()) {
                val event = parser.next()
                when {
                    event.eventId == Event.ID.DocumentStart -> anchors.clear()
                    event is AliasEvent -> {
                        val anchor = event.anchor
                        if (anchor == null || anchor !in anchors) {
                            return StructuredTextValidationResult(
                                format = StructuredTextFormat.YAML,
                                errorMessage = "YAML alias references an undefined anchor."
                            )
                        }
                    }
                    event is NodeEvent -> event.anchor?.let(anchors::add)
                }
            }
            StructuredTextValidationResult(StructuredTextFormat.YAML)
        } catch (error: YamlEngineException) {
            StructuredTextValidationResult(
                format = StructuredTextFormat.YAML,
                errorMessage = error.message ?: "Invalid YAML."
            )
        }
    }

    /**
     * Validates XML well-formedness through the portable pull parser without building a document tree.
     * Entity expansion stays disabled and DTD/DOCTYPE declarations are rejected so validation never
     * becomes a network/file-resolution surface. XML Schema or DTD validation is intentionally out of
     * scope; this gate checks only the portable syntax boundary.
     */
    private fun validateXml(text: String): StructuredTextValidationResult {
        if (text.length > MAX_XML_CHARS) {
            return invalidXml("XML input exceeds the supported 3 Mi character limit.")
        }
        if (text.isBlank()) return invalidXml("XML input is empty.")

        return try {
            val reader = xmlStreaming.newReader(text, expandEntities = false)
            var depth = 0
            var rootElements = 0
            try {
                while (reader.hasNext()) {
                    when (reader.next()) {
                        EventType.START_ELEMENT -> {
                            if (depth == 0) rootElements++
                            depth++
                            if (depth > MAX_XML_NESTING) {
                                return invalidXml(
                                    "XML nesting exceeds the supported limit of $MAX_XML_NESTING."
                                )
                            }
                        }
                        EventType.END_ELEMENT -> depth--
                        EventType.DOCDECL -> return invalidXml(
                            "XML DOCTYPE/DTD declarations are not supported by portable validation."
                        )
                        else -> Unit
                    }
                }
            } finally {
                reader.close()
            }

            when {
                depth != 0 -> invalidXml("XML element nesting is unbalanced.")
                rootElements != 1 -> invalidXml("XML must contain exactly one root element.")
                else -> StructuredTextValidationResult(StructuredTextFormat.XML)
            }
        } catch (error: XmlException) {
            invalidXml(error.message ?: "Invalid XML.")
        }
    }

    private fun invalidXml(message: String): StructuredTextValidationResult =
        StructuredTextValidationResult(
            format = StructuredTextFormat.XML,
            errorMessage = message
        )

    private data class StrictJsonInspection(
        val errorMessage: String? = null,
        val hasDuplicateObjectKeys: Boolean = false
    )

    /** RFC-style syntax gate that preserves lexical source instead of normalizing while parsing. */
    private class StrictJsonParser(private val source: String) {
        private var index = 0
        private var hasDuplicateObjectKeys = false

        fun inspect(): StrictJsonInspection {
            skipWhitespace()
            if (index == source.length) return failureAt("JSON input is empty")
            parseValue(depth = 0)?.let { error ->
                return StrictJsonInspection(errorMessage = error)
            }
            skipWhitespace()
            if (index != source.length) return failureAt("Unexpected trailing JSON content")
            return StrictJsonInspection(hasDuplicateObjectKeys = hasDuplicateObjectKeys)
        }

        private fun parseValue(depth: Int): String? {
            if (depth > MAX_JSON_NESTING) {
                return errorAt("JSON nesting exceeds the supported limit of $MAX_JSON_NESTING")
            }
            if (index >= source.length) return errorAt("Expected a JSON value")
            return when (source[index]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString().errorMessage
                't' -> parseLiteral("true")
                'f' -> parseLiteral("false")
                'n' -> parseLiteral("null")
                '-', in '0'..'9' -> parseNumber()
                else -> errorAt("Invalid JSON value")
            }
        }

        private fun parseObject(depth: Int): String? {
            index++
            skipWhitespace()
            if (consume('}')) return null
            val keys = mutableSetOf<String>()

            while (true) {
                if (index >= source.length || source[index] != '"') {
                    return errorAt("Expected a quoted JSON object key")
                }
                val key = parseString()
                key.errorMessage?.let { return it }
                if (!keys.add(requireNotNull(key.value))) hasDuplicateObjectKeys = true

                skipWhitespace()
                if (!consume(':')) return errorAt("Expected ':' after JSON object key")
                skipWhitespace()
                parseValue(depth + 1)?.let { return it }
                skipWhitespace()

                when {
                    consume('}') -> return null
                    consume(',') -> {
                        skipWhitespace()
                        if (index < source.length && source[index] == '}') {
                            return errorAt("Trailing commas are not valid JSON")
                        }
                    }
                    else -> return errorAt("Expected ',' or '}' in JSON object")
                }
            }
        }

        private fun parseArray(depth: Int): String? {
            index++
            skipWhitespace()
            if (consume(']')) return null

            while (true) {
                parseValue(depth + 1)?.let { return it }
                skipWhitespace()
                when {
                    consume(']') -> return null
                    consume(',') -> {
                        skipWhitespace()
                        if (index < source.length && source[index] == ']') {
                            return errorAt("Trailing commas are not valid JSON")
                        }
                    }
                    else -> return errorAt("Expected ',' or ']' in JSON array")
                }
            }
        }

        private fun parseLiteral(expected: String): String? {
            if (!source.regionMatches(index, expected, 0, expected.length)) {
                return errorAt("Invalid JSON literal")
            }
            index += expected.length
            return null
        }

        private fun parseNumber(): String? {
            consume('-')
            parseIntegerPart()?.let { return it }
            parseFractionPart()?.let { return it }
            return parseExponentPart()
        }

        private fun parseIntegerPart(): String? {
            if (index >= source.length) return errorAt("Incomplete JSON number")
            return when (source[index]) {
                '0' -> {
                    index++
                    if (index < source.length && source[index] in '0'..'9') {
                        errorAt("Leading zeroes are not valid JSON numbers")
                    } else {
                        null
                    }
                }
                in '1'..'9' -> {
                    consumeAsciiDigits()
                    null
                }
                else -> errorAt("Invalid JSON number")
            }
        }

        private fun parseFractionPart(): String? {
            if (!consume('.')) return null
            if (index >= source.length || source[index] !in '0'..'9') {
                return errorAt("JSON fraction requires at least one digit")
            }
            consumeAsciiDigits()
            return null
        }

        private fun parseExponentPart(): String? {
            if (index >= source.length || source[index] !in setOf('e', 'E')) return null
            index++
            if (index < source.length && source[index] in setOf('+', '-')) index++
            if (index >= source.length || source[index] !in '0'..'9') {
                return errorAt("JSON exponent requires at least one digit")
            }
            consumeAsciiDigits()
            return null
        }

        private fun consumeAsciiDigits() {
            while (index < source.length && source[index] in '0'..'9') index++
        }

        private fun parseString(): ParsedJsonString {
            index++
            val decoded = StringBuilder()
            while (index < source.length) {
                val char = source[index++]
                when {
                    char == '"' -> return ParsedJsonString(decoded.toString())
                    char == '\\' -> parseEscape(decoded)?.let { return stringFailure(it) }
                    char.code < 0x20 -> return stringFailure(
                        "Unescaped control character in JSON string"
                    )
                    else -> decoded.append(char)
                }
            }
            return stringFailure("Unterminated JSON string")
        }

        private fun parseEscape(decoded: StringBuilder): String? {
            if (index >= source.length) return "Incomplete JSON escape"
            return when (val escaped = source[index++]) {
                '"', '\\', '/' -> appendEscape(decoded, escaped)
                'b' -> appendEscape(decoded, '\b')
                'f' -> appendEscape(decoded, '\u000C')
                'n' -> appendEscape(decoded, '\n')
                'r' -> appendEscape(decoded, '\r')
                't' -> appendEscape(decoded, '\t')
                'u' -> parseUnicodeEscape(decoded)
                else -> "Invalid JSON escape"
            }
        }

        private fun appendEscape(decoded: StringBuilder, char: Char): String? {
            decoded.append(char)
            return null
        }

        private fun parseUnicodeEscape(decoded: StringBuilder): String? {
            if (index + 4 > source.length) return "Incomplete JSON Unicode escape"
            val digits = source.substring(index, index + 4)
            if (!digits.all(::isJsonHexDigit)) return "Invalid JSON Unicode escape"
            decoded.append(digits.toInt(16).toChar())
            index += 4
            return null
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index] in JSON_WHITESPACE) index++
        }

        private fun consume(expected: Char): Boolean {
            if (index >= source.length || source[index] != expected) return false
            index++
            return true
        }

        private fun errorAt(message: String): String = "$message at offset $index."

        private fun failureAt(message: String): StrictJsonInspection =
            StrictJsonInspection(errorMessage = errorAt(message))

        private fun stringFailure(message: String): ParsedJsonString =
            ParsedJsonString(errorMessage = errorAt(message))
    }

    /** Formats structural whitespace only, preserving string, number and literal lexemes verbatim. */
    private class JsonLexicalFormatter(private val source: String) {
        private val output = StringBuilder(minOf(source.length, MAX_JSON_FORMATTED_CHARS))
        private val nonEmptyScopes = mutableListOf<Boolean>()
        private var indent = 0
        private var inString = false
        private var escaped = false

        fun format(): String {
            source.forEachIndexed(::appendCharacter)
            return output.toString()
        }

        private fun appendCharacter(index: Int, char: Char) {
            if (inString) {
                appendStringCharacter(char)
                return
            }
            when (char) {
                '"' -> startString()
                in JSON_WHITESPACE -> Unit
                '{', '[' -> appendOpening(index, char)
                '}', ']' -> appendClosing(char)
                ',' -> appendComma()
                ':' -> appendChecked(": ")
                else -> appendChecked(char)
            }
        }

        private fun startString() {
            inString = true
            appendChecked('"')
        }

        private fun appendStringCharacter(char: Char) {
            appendChecked(char)
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        }

        private fun appendOpening(index: Int, char: Char) {
            appendChecked(char)
            val closing = if (char == '{') '}' else ']'
            val nonEmpty = nextNonWhitespace(index + 1) != closing
            nonEmptyScopes.add(nonEmpty)
            if (!nonEmpty) return
            indent++
            appendChecked('\n')
            appendIndent()
        }

        private fun appendClosing(char: Char) {
            val nonEmpty = nonEmptyScopes.removeLast()
            if (nonEmpty) {
                indent--
                appendChecked('\n')
                appendIndent()
            }
            appendChecked(char)
        }

        private fun appendComma() {
            appendChecked(',')
            appendChecked('\n')
            appendIndent()
        }

        private fun appendIndent() {
            val spaces = indent * 2
            ensureOutputCapacity(spaces)
            repeat(spaces) { output.append(' ') }
        }

        private fun appendChecked(char: Char) {
            ensureOutputCapacity(1)
            output.append(char)
        }

        private fun appendChecked(text: String) {
            ensureOutputCapacity(text.length)
            output.append(text)
        }

        private fun ensureOutputCapacity(additionalChars: Int) {
            if (additionalChars > MAX_JSON_FORMATTED_CHARS - output.length) {
                throw JsonFormattingLimitExceededException()
            }
        }

        private fun nextNonWhitespace(start: Int): Char? {
            var cursor = start
            while (cursor < source.length && source[cursor] in JSON_WHITESPACE) cursor++
            return source.getOrNull(cursor)
        }
    }

    private data class ParsedJsonString(
        val value: String? = null,
        val errorMessage: String? = null
    )

    private class JsonFormattingLimitExceededException : RuntimeException()

    private val JSON_WHITESPACE = setOf(' ', '\t', '\r', '\n')

    private fun isJsonHexDigit(char: Char): Boolean =
        char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}
