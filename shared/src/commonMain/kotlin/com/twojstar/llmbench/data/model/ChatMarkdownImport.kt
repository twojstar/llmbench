package com.twojstar.llmbench.data.model

/** One user-visible turn recovered from a canonical LlmBench chat Markdown export. */
data class ImportedChatMarkdownTurn(
    val role: String,
    val displayHeading: String,
    val text: String
) {
    /** Imported chat text is user content and must not leak through incidental stringification. */
    override fun toString(): String =
        "ImportedChatMarkdownTurn(role=$role, displayHeading=<redacted>, text=<redacted>)"
}

/** Parsed canonical chat export. Provider/model heading text remains display metadata, not trusted identity. */
class ImportedChatMarkdown internal constructor(
    val version: Int,
    turns: List<ImportedChatMarkdownTurn>
) {
    private val turnSnapshot = turns.toList()

    val turns: List<ImportedChatMarkdownTurn>
        get() = turnSnapshot.toList()
}

data class ChatMarkdownImportResult(
    val chat: ImportedChatMarkdown?,
    val error: String?
) {
    val isValid: Boolean
        get() = chat != null && error == null
}

/**
 * Parses only the versioned, byte-framed Markdown emitted by [renderChatMarkdown].
 *
 * Legacy/unframed Markdown is deliberately not guessed into turns because raw message bodies may
 * themselves contain headings that look exactly like exported chat boundaries. The declared UTF-8
 * body length is consumed before the parser looks for the fixed end marker, so marker-like text
 * inside a body is preserved as content instead of being interpreted structurally.
 */
fun parseChatMarkdown(source: String): ChatMarkdownImportResult {
    val documentPrefix = "# $CHAT_MARKDOWN_TITLE\n\n$CHAT_MARKDOWN_VERSION_MARKER\n\n"
    if (!source.startsWith(documentPrefix)) {
        return invalidChatMarkdown("Not a canonical LlmBench chat v$CHAT_MARKDOWN_VERSION export.")
    }

    val turns = mutableListOf<ImportedChatMarkdownTurn>()
    var index = documentPrefix.length
    val metadataSuffix = "$CHAT_MARKDOWN_MESSAGE_META_SUFFIX\n"
    val frameSuffix = "\n$CHAT_MARKDOWN_MESSAGE_END\n\n"

    while (index < source.length) {
        if (!source.startsWith(CHAT_MARKDOWN_MESSAGE_PREFIX, index)) {
            return invalidChatMarkdown("Expected a framed chat message.")
        }
        index += CHAT_MARKDOWN_MESSAGE_PREFIX.length

        val roleEnd = source.indexOf(CHAT_MARKDOWN_MESSAGE_BYTES, startIndex = index)
        if (roleEnd < index) return invalidChatMarkdown("Malformed chat message metadata.")
        val role = source.substring(index, roleEnd)
        if (role != CHAT_ROLE_USER && role != CHAT_ROLE_ASSISTANT) {
            return invalidChatMarkdown("Unsupported chat message role.")
        }
        index = roleEnd + CHAT_MARKDOWN_MESSAGE_BYTES.length

        val metadataEnd = source.indexOf(metadataSuffix, startIndex = index)
        if (metadataEnd < index) return invalidChatMarkdown("Malformed chat message length metadata.")
        val bodyUtf8Bytes = source.substring(index, metadataEnd).toIntOrNull()
            ?.takeIf { it >= 0 }
            ?: return invalidChatMarkdown("Invalid chat message body length.")
        index = metadataEnd + metadataSuffix.length

        if (!source.startsWith("## ", index)) {
            return invalidChatMarkdown("Expected the message display heading.")
        }
        val headingEnd = source.indexOf('\n', startIndex = index)
        if (headingEnd < index) return invalidChatMarkdown("Unterminated message display heading.")
        val displayHeading = source.substring(index + 3, headingEnd)
        if (displayHeading.isBlank()) return invalidChatMarkdown("Message display heading is blank.")
        index = headingEnd + 1
        if (source.getOrNull(index) != '\n') {
            return invalidChatMarkdown("Expected a blank line before the message body.")
        }
        index++

        val bodyEnd = source.endIndexAfterUtf8Bytes(index, bodyUtf8Bytes)
            ?: return invalidChatMarkdown("Chat message body does not match its declared UTF-8 length.")
        val body = source.substring(index, bodyEnd)
        index = bodyEnd

        if (!source.startsWith(frameSuffix, index)) {
            return invalidChatMarkdown("Chat message framing does not match its declared body length.")
        }
        index += frameSuffix.length
        turns += ImportedChatMarkdownTurn(
            role = role,
            displayHeading = displayHeading,
            text = body
        )
    }

    if (turns.isEmpty()) return invalidChatMarkdown("Chat export contains no messages.")
    if (turns.first().role != CHAT_ROLE_USER) {
        return invalidChatMarkdown("Chat export must start with a user turn.")
    }

    return ChatMarkdownImportResult(
        chat = ImportedChatMarkdown(CHAT_MARKDOWN_VERSION, turns),
        error = null
    )
}

private fun String.endIndexAfterUtf8Bytes(startIndex: Int, byteCount: Int): Int? {
    var index = startIndex
    var remaining = byteCount
    while (remaining > 0) {
        if (index >= length) return null
        val codeUnit = this[index].code
        val codeUnitCount: Int
        val utf8Bytes: Int
        when {
            codeUnit <= 0x7F -> {
                codeUnitCount = 1
                utf8Bytes = 1
            }
            codeUnit <= 0x7FF -> {
                codeUnitCount = 1
                utf8Bytes = 2
            }
            codeUnit in 0xD800..0xDBFF &&
                index + 1 < length &&
                this[index + 1].code in 0xDC00..0xDFFF -> {
                codeUnitCount = 2
                utf8Bytes = 4
            }
            else -> {
                codeUnitCount = 1
                utf8Bytes = 3
            }
        }
        if (utf8Bytes > remaining) return null
        remaining -= utf8Bytes
        index += codeUnitCount
    }
    return index
}

private fun invalidChatMarkdown(message: String): ChatMarkdownImportResult = ChatMarkdownImportResult(
    chat = null,
    error = message
)
