package com.twojstar.llmbench.data.document

/**
 * Detects a supported structured-text syntax from portable document metadata.
 *
 * Detection deliberately does not sniff document contents. File names and MIME types are treated as
 * independent hints; conflicting specific hints return null instead of selecting the wrong parser.
 * Generic, malformed or unknown MIME types do not override a recognized file extension.
 */
fun detectStructuredTextFormat(
    displayName: String?,
    mimeType: String? = null
): StructuredTextFormat? {
    val fromName = structuredTextFormatFromDisplayName(displayName)
    val fromMime = structuredTextFormatFromMimeType(mimeType)
    return when {
        fromName != null && fromMime != null && fromName != fromMime -> null
        fromName != null -> fromName
        else -> fromMime
    }
}

/** Validates a document only when its metadata identifies a supported structured-text syntax. */
fun validateStructuredTextDocument(
    text: String,
    displayName: String?,
    mimeType: String? = null
): StructuredTextValidationResult? =
    detectStructuredTextFormat(displayName, mimeType)
        ?.let { format -> StructuredTextDiagnostics.validate(text, format) }

private fun structuredTextFormatFromDisplayName(displayName: String?): StructuredTextFormat? {
    val normalized = displayName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
    return when (extension) {
        "json" -> StructuredTextFormat.JSON
        "yaml", "yml" -> StructuredTextFormat.YAML
        "xml" -> StructuredTextFormat.XML
        else -> null
    }
}

private fun structuredTextFormatFromMimeType(mimeType: String?): StructuredTextFormat? {
    val mime = parseMimeType(mimeType) ?: return null
    val normalized = "${mime.type}/${mime.subtype}"
    return when {
        normalized == "application/json" ||
            normalized == "text/json" ||
            mime.subtype.hasStructuredSuffix("json") -> StructuredTextFormat.JSON
        normalized in YAML_MIME_TYPES -> StructuredTextFormat.YAML
        normalized == "application/xml" ||
            normalized == "text/xml" ||
            mime.subtype.hasStructuredSuffix("xml") -> StructuredTextFormat.XML
        else -> null
    }
}

private fun parseMimeType(mimeType: String?): ParsedMimeType? {
    val normalized = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.count { char -> char == '/' } == 1 }
        ?: return null
    val type = normalized.substringBefore('/')
    val subtype = normalized.substringAfter('/')
    if (!type.isMimeToken() || !subtype.isMimeToken()) return null
    return ParsedMimeType(type, subtype)
}

private fun String.hasStructuredSuffix(suffix: String): Boolean {
    val marker = "+$suffix"
    return endsWith(marker) && length > marker.length
}

private fun String.isMimeToken(): Boolean =
    isNotEmpty() && all { char ->
        char in 'a'..'z' ||
            char in '0'..'9' ||
            char in MIME_TOKEN_PUNCTUATION
    }

private data class ParsedMimeType(
    val type: String,
    val subtype: String
)

private const val MIME_TOKEN_PUNCTUATION = "!#$%&'*+-.^_`|~"

private val YAML_MIME_TYPES = setOf(
    "application/yaml",
    "text/yaml",
    "application/x-yaml",
    "text/x-yaml"
)
