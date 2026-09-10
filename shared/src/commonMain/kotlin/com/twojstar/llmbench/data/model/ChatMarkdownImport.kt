package com.twojstar.llmbench.data.model

/** One user-visible turn recovered from a canonical LlmBench chat Markdown export. */
data class ImportedChatMarkdownTurn(
    val role: String,
    val displayHeading: String,
    val text: String
)

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
 * Parses only the versioned, length-framed Markdown emitted by [renderChatMarkdown].
 *
 * Legacy/unframed Markdown is deliberately not guessed into turns because raw message bodies may
 * themselves contain headings that look exactly like exported chat boundaries. The length field is
 * consumed before the parser looks for the fixed end marker, so marker-like text inside a body is
 * preserved as content instead of being interpreted structurally.
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

        val roleEnd = source.indexOf(CHAT_MARKDOWN_MESSAGE_CHARS, startIndex = index)
        if (roleEnd < index) return invalidChatMarkdown("Malformed chat message metadata.")
        val role = source.substring(index, roleEnd)
        if (role != CHAT_ROLE_USER && role != CHAT_ROLE_ASSISTANT) {
            return invalidChatMarkdown("Unsupported chat message role.")
        }
        index = roleEnd + CHAT_MARKDOWN_MESSAGE_CHARS.length

        val metadataEnd = source.indexOf(metadataSuffix, startIndex = index)
        if (metadataEnd < index) return invalidChatMarkdown("Malformed chat message length metadata.")
        val bodyChars = source.substring(index, metadataEnd).toIntOrNull()
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

        if (bodyChars > source.length - index) {
            return invalidChatMarkdown("Chat message body is shorter than its declared length.")
        }
        val body = source.substring(index, index + bodyChars)
        index += bodyChars

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

private fun invalidChatMarkdown(message: String): ChatMarkdownImportResult = ChatMarkdownImportResult(
    chat = null,
    error = message
)
