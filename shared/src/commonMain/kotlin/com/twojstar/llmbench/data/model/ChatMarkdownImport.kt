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
    val prefix = "# $CHAT_MARKDOWN_TITLE\n\n$CHAT_MARKDOWN_VERSION_MARKER\n\n"
    if (!source.startsWith(prefix)) {
        return invalidChatMarkdown("Not a canonical LlmBench chat v$CHAT_MARKDOWN_VERSION export.")
    }
    return ChatMarkdownParser(source, prefix.length).parse()
}

private class ChatMarkdownParser(
    private val source: String,
    startIndex: Int
) {
    private var index = startIndex
    private var error: String? = null

    fun parse(): ChatMarkdownImportResult {
        val turns = mutableListOf<ImportedChatMarkdownTurn>()
        while (index < source.length) {
            val turn = parseTurn() ?: return invalidChatMarkdown(requireNotNull(error))
            turns += turn
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

    private fun parseTurn(): ImportedChatMarkdownTurn? {
        val metadata = parseMetadata() ?: return null
        val heading = parseHeading() ?: return null
        val body = parseBody(metadata.bodyUtf8Bytes) ?: return null
        if (!consume("\n$CHAT_MARKDOWN_MESSAGE_END\n\n")) {
            return fail("Chat message framing does not match its declared body length.")
        }
        return ImportedChatMarkdownTurn(
            role = metadata.role,
            displayHeading = heading,
            text = body
        )
    }

    private fun parseMetadata(): ChatFrameMetadata? {
        if (!consume(CHAT_MARKDOWN_MESSAGE_PREFIX)) {
            return fail("Expected a framed chat message.")
        }
        val role = readUntil(CHAT_MARKDOWN_MESSAGE_BYTES)
            ?: return fail("Malformed chat message metadata.")
        if (role != CHAT_ROLE_USER && role != CHAT_ROLE_ASSISTANT) {
            return fail("Unsupported chat message role.")
        }
        val byteText = readUntil("$CHAT_MARKDOWN_MESSAGE_META_SUFFIX\n")
            ?: return fail("Malformed chat message length metadata.")
        val bodyUtf8Bytes = byteText.toIntOrNull()?.takeIf { it >= 0 }
            ?: return fail("Invalid chat message body length.")
        return ChatFrameMetadata(role, bodyUtf8Bytes)
    }

    private fun parseHeading(): String? {
        if (!consume("## ")) return fail("Expected the message display heading.")
        val heading = readUntil("\n")
            ?: return fail("Unterminated message display heading.")
        if (heading.isBlank()) return fail("Message display heading is blank.")
        if (!consume("\n")) return fail("Expected a blank line before the message body.")
        return heading
    }

    private fun parseBody(bodyUtf8Bytes: Int): String? {
        val bodyEnd = source.endIndexAfterUtf8Bytes(index, bodyUtf8Bytes)
            ?: return fail("Chat message body does not match its declared UTF-8 length.")
        return source.substring(index, bodyEnd).also { index = bodyEnd }
    }

    private fun consume(expected: String): Boolean {
        if (!source.startsWith(expected, index)) return false
        index += expected.length
        return true
    }

    private fun readUntil(delimiter: String): String? {
        val end = source.indexOf(delimiter, startIndex = index)
        if (end < index) return null
        return source.substring(index, end).also { index = end + delimiter.length }
    }

    private fun <T> fail(message: String): T? {
        error = message
        return null
    }
}

private data class ChatFrameMetadata(
    val role: String,
    val bodyUtf8Bytes: Int
)

private fun String.endIndexAfterUtf8Bytes(startIndex: Int, byteCount: Int): Int? {
    var index = startIndex
    var remaining = byteCount
    while (remaining > 0) {
        if (index >= length) return null
        val span = utf8SpanAt(index) ?: return null
        if (span.byteCount > remaining) return null
        remaining -= span.byteCount
        index += span.codeUnitCount
    }
    return index
}

private fun String.utf8SpanAt(index: Int): Utf8Span? {
    val codeUnit = getOrNull(index)?.code ?: return null
    return when {
        codeUnit <= 0x7F -> Utf8Span(codeUnitCount = 1, byteCount = 1)
        codeUnit <= 0x7FF -> Utf8Span(codeUnitCount = 1, byteCount = 2)
        codeUnit in 0xD800..0xDBFF &&
            getOrNull(index + 1)?.code in 0xDC00..0xDFFF -> Utf8Span(codeUnitCount = 2, byteCount = 4)
        else -> Utf8Span(codeUnitCount = 1, byteCount = 3)
    }
}

private data class Utf8Span(
    val codeUnitCount: Int,
    val byteCount: Int
)

private fun invalidChatMarkdown(message: String): ChatMarkdownImportResult = ChatMarkdownImportResult(
    chat = null,
    error = message
)
