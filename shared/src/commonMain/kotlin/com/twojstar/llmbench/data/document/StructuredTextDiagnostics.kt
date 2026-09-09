package com.twojstar.llmbench.data.document

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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
    private val prettyJson = Json { prettyPrint = true }

    fun validate(text: String, format: StructuredTextFormat): StructuredTextValidationResult =
        when (format) {
            StructuredTextFormat.JSON -> validateJson(text)
            StructuredTextFormat.YAML -> validateYaml(text)
        }

    /**
     * Pretty-prints strict JSON only after a fidelity gate. Duplicate object keys are valid syntax
     * but are not formatted because a tree/map representation would silently discard earlier values.
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

        val element = try {
            Json.parseToJsonElement(text)
        } catch (error: SerializationException) {
            return StructuredTextFormatResult(
                text = text,
                changed = false,
                errorMessage = error.message ?: "Invalid JSON."
            )
        }
        val formatted = prettyJson.encodeToString(JsonElement.serializer(), element)
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
        Yaml.default.parseToYamlNode(text)
        StructuredTextValidationResult(StructuredTextFormat.YAML)
    } catch (error: YamlException) {
        StructuredTextValidationResult(
            format = StructuredTextFormat.YAML,
            errorMessage = error.message ?: "Invalid YAML."
        )
    }
}

private data class StrictJsonInspection(
    val errorMessage: String? = null,
    val hasDuplicateObjectKeys: Boolean = false
)

/** Small RFC-style syntax gate that preserves lexical source instead of normalizing it while parsing. */
private class StrictJsonParser(private val source: String) {
    private var index = 0
    private var hasDuplicateObjectKeys = false

    fun inspect(): StrictJsonInspection {
        skipWhitespace()
        if (index == source.length) return failure("JSON input is empty")
        parseValue()?.let { return failure(it) }
        skipWhitespace()
        if (index != source.length) return failure("Unexpected trailing JSON content")
        return StrictJsonInspection(hasDuplicateObjectKeys = hasDuplicateObjectKeys)
    }

    private fun parseValue(): String? {
        if (index >= source.length) return errorAt("Expected a JSON value")
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString().errorMessage
            't' -> parseLiteral("true")
            'f' -> parseLiteral("false")
            'n' -> parseLiteral("null")
            '-', in '0'..'9' -> parseNumber()
            else -> errorAt("Invalid JSON value")
        }
    }

    private fun parseObject(): String? {
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
            parseValue()?.let { return it }
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

    private fun parseArray(): String? {
        index++
        skipWhitespace()
        if (consume(']')) return null

        while (true) {
            parseValue()?.let { return it }
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
        if (index >= source.length) return errorAt("Incomplete JSON number")

        when (source[index]) {
            '0' -> {
                index++
                if (index < source.length && source[index].isDigit()) {
                    return errorAt("Leading zeroes are not valid JSON numbers")
                }
            }
            in '1'..'9' -> while (index < source.length && source[index].isDigit()) index++
            else -> return errorAt("Invalid JSON number")
        }

        if (consume('.')) {
            if (index >= source.length || !source[index].isDigit()) {
                return errorAt("JSON fraction requires at least one digit")
            }
            while (index < source.length && source[index].isDigit()) index++
        }

        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            index++
            if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
            if (index >= source.length || !source[index].isDigit()) {
                return errorAt("JSON exponent requires at least one digit")
            }
            while (index < source.length && source[index].isDigit()) index++
        }
        return null
    }

    private fun parseString(): ParsedJsonString {
        index++
        val decoded = StringBuilder()
        while (index < source.length) {
            val char = source[index++]
            when {
                char == '"' -> return ParsedJsonString(decoded.toString())
                char == '\\' -> {
                    if (index >= source.length) return stringFailure("Incomplete JSON escape")
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> decoded.append(escaped)
                        'b' -> decoded.append('\b')
                        'f' -> decoded.append('\u000C')
                        'n' -> decoded.append('\n')
                        'r' -> decoded.append('\r')
                        't' -> decoded.append('\t')
                        'u' -> {
                            if (index + 4 > source.length) {
                                return stringFailure("Incomplete JSON Unicode escape")
                            }
                            val code = source.substring(index, index + 4).toIntOrNull(16)
                                ?: return stringFailure("Invalid JSON Unicode escape")
                            decoded.append(code.toChar())
                            index += 4
                        }
                        else -> return stringFailure("Invalid JSON escape")
                    }
                }
                char.code < 0x20 -> return stringFailure("Unescaped control character in JSON string")
                else -> decoded.append(char)
            }
        }
        return stringFailure("Unterminated JSON string")
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

    private fun failure(message: String): StrictJsonInspection =
        StrictJsonInspection(errorMessage = errorAt(message))

    private fun stringFailure(message: String): ParsedJsonString =
        ParsedJsonString(errorMessage = errorAt(message))

    private companion object {
        val JSON_WHITESPACE = setOf(' ', '\t', '\r', '\n')
    }
}

private data class ParsedJsonString(
    val value: String? = null,
    val errorMessage: String? = null
)
