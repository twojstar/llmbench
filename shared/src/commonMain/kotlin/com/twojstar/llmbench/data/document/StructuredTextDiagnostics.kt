package com.twojstar.llmbench.data.document

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException

/** Structured text syntaxes currently validated by the portable document core. */
enum class StructuredTextFormat {
    JSON,
    YAML
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
    private val documentYaml = Yaml(
        configuration = YamlConfiguration(
            anchorsAndAliases = AnchorsAndAliases.Permitted(maxAliasCount = MAX_YAML_ALIAS_COUNT)
        )
    )

    fun validate(text: String, format: StructuredTextFormat): StructuredTextValidationResult =
        when (format) {
            StructuredTextFormat.JSON -> validateJson(text)
            StructuredTextFormat.YAML -> validateYaml(text)
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

        val formatted = JsonLexicalFormatter(text).format()
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

    private fun validateYaml(text: String): StructuredTextValidationResult = try {
        documentYaml.parseToYamlNode(text)
        StructuredTextValidationResult(StructuredTextFormat.YAML)
    } catch (error: YamlException) {
        StructuredTextValidationResult(
            format = StructuredTextFormat.YAML,
            errorMessage = error.message
        )
    }

    private const val MAX_JSON_NESTING = 128
    private val MAX_YAML_ALIAS_COUNT = 100u

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
        private val output = StringBuilder(source.length + source.length / 4)
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
                ':' -> output.append(": ")
                else -> output.append(char)
            }
        }

        private fun startString() {
            inString = true
            output.append('"')
        }

        private fun appendStringCharacter(char: Char) {
            output.append(char)
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        }

        private fun appendOpening(index: Int, char: Char) {
            output.append(char)
            val closing = if (char == '{') '}' else ']'
            val nonEmpty = nextNonWhitespace(index + 1) != closing
            nonEmptyScopes.add(nonEmpty)
            if (!nonEmpty) return
            indent++
            output.append('\n')
            appendIndent()
        }

        private fun appendClosing(char: Char) {
            val nonEmpty = nonEmptyScopes.removeLast()
            if (nonEmpty) {
                indent--
                output.append('\n')
                appendIndent()
            }
            output.append(char)
        }

        private fun appendComma() {
            output.append(',').append('\n')
            appendIndent()
        }

        private fun appendIndent() {
            repeat(indent) { output.append("  ") }
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

    private val JSON_WHITESPACE = setOf(' ', '\t', '\r', '\n')

    private fun isJsonHexDigit(char: Char): Boolean =
        char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}
