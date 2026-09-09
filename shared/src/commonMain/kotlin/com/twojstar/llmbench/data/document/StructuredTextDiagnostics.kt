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
    private val strictJson = Json {
        isLenient = false
        allowTrailingComma = false
        allowComments = false
    }

    private val prettyJson = Json(strictJson) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun validate(text: String, format: StructuredTextFormat): StructuredTextValidationResult =
        when (format) {
            StructuredTextFormat.JSON -> validateJson(text)
            StructuredTextFormat.YAML -> validateYaml(text)
        }

    /** Pretty-prints valid strict JSON and returns the original source unchanged on parse failure. */
    fun formatJson(text: String): StructuredTextFormatResult {
        val element = try {
            strictJson.parseToJsonElement(text)
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

    private fun validateJson(text: String): StructuredTextValidationResult = try {
        strictJson.parseToJsonElement(text)
        StructuredTextValidationResult(StructuredTextFormat.JSON)
    } catch (error: SerializationException) {
        StructuredTextValidationResult(
            format = StructuredTextFormat.JSON,
            errorMessage = error.message ?: "Invalid JSON."
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
