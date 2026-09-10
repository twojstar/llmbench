package com.twojstar.llmbench.data.model

internal const val CHAT_MARKDOWN_TITLE = "LlmBench chat"
internal const val CHAT_MARKDOWN_VERSION = 1
internal const val CHAT_MARKDOWN_VERSION_MARKER = "<!-- llmbench-chat:v1 -->"
internal const val CHAT_MARKDOWN_MESSAGE_PREFIX = "<!-- llmbench-message:v1 role="
internal const val CHAT_MARKDOWN_MESSAGE_BYTES = " bytes="
internal const val CHAT_MARKDOWN_MESSAGE_META_SUFFIX = " -->"
internal const val CHAT_MARKDOWN_MESSAGE_END = "<!-- llmbench-message-end -->"
private const val MAX_HEADING_METADATA_CHARS = 160

/**
 * Renders the user-visible native chat as portable, round-trip-safe Markdown.
 *
 * Export starts at the first user turn so app welcome content is omitted. Only user and assistant
 * messages are included by construction; internal/system roles, profile notes, latency and other
 * diagnostics are intentionally excluded from the export boundary. Invisible HTML comments frame
 * each message with its role and exact UTF-8 body length so canonical exports can later be imported
 * without mistaking Markdown headings or marker-like text inside a message for new turns.
 *
 * When [maxUtf8Bytes] is set, output is bounded incrementally before message bodies are appended, so
 * oversized chats do not require building the complete export first.
 */
fun renderChatMarkdown(
    messages: List<ModelChatMessage>,
    maxUtf8Bytes: Int = Int.MAX_VALUE
): String? {
    if (maxUtf8Bytes < 0) return null
    val firstUserIndex = messages.indexOfFirst { it.sender == CHAT_ROLE_USER }
    if (firstUserIndex < 0) return null

    val output = BoundedUtf8StringBuilder(maxUtf8Bytes)
    if (!output.append("# $CHAT_MARKDOWN_TITLE\n\n$CHAT_MARKDOWN_VERSION_MARKER\n\n")) return null

    for (index in firstUserIndex..messages.lastIndex) {
        val message = messages[index]
        if (message.sender != CHAT_ROLE_USER && message.sender != CHAT_ROLE_ASSISTANT) continue

        if (!output.append(message.markdownFrameStart())) return null
        if (!output.append("## ${message.markdownHeading()}\n\n")) return null
        if (!output.append(message.text)) return null
        if (!output.append("\n$CHAT_MARKDOWN_MESSAGE_END\n\n")) return null
    }

    return output.toString()
}

private fun ModelChatMessage.markdownFrameStart(): String {
    val bodyUtf8Bytes = requireNotNull(utf8ByteCountAtMost(text, Int.MAX_VALUE))
    return "$CHAT_MARKDOWN_MESSAGE_PREFIX$sender$CHAT_MARKDOWN_MESSAGE_BYTES$bodyUtf8Bytes" +
        "$CHAT_MARKDOWN_MESSAGE_META_SUFFIX\n"
}

private fun ModelChatMessage.markdownHeading(): String = when (sender) {
    CHAT_ROLE_USER -> "You"
    CHAT_ROLE_ASSISTANT -> buildList {
        val providerName = provider?.shortName
            ?.safeHeadingMetadata()
            ?.takeIf { it.isNotBlank() }
            ?: "Assistant"
        add(providerName)
        modelName?.safeHeadingMetadata()?.takeIf { it.isNotBlank() }?.let(::add)
        if (isError) add("error")
        if (isSimulated) add("simulated")
        if (isPartial) add("partial")
    }.joinToString(" · ")
    else -> "Message"
}

private fun String.safeHeadingMetadata(): String =
    replace('\r', ' ')
        .replace('\n', ' ')
        .trim()
        .take(MAX_HEADING_METADATA_CHARS)

private class BoundedUtf8StringBuilder(maxUtf8Bytes: Int) {
    private val builder = StringBuilder()
    private var remainingUtf8Bytes = maxUtf8Bytes

    fun append(value: String): Boolean {
        val byteCount = utf8ByteCountAtMost(value, remainingUtf8Bytes) ?: return false
        builder.append(value)
        remainingUtf8Bytes -= byteCount
        return true
    }

    override fun toString(): String = builder.toString()
}

internal fun utf8ByteCountAtMost(value: String, limit: Int): Int? {
    var bytes = 0
    var index = 0
    while (index < value.length) {
        val codeUnit = value[index].code
        val byteCount = when {
            codeUnit <= 0x7F -> 1
            codeUnit <= 0x7FF -> 2
            codeUnit in 0xD800..0xDBFF &&
                index + 1 < value.length &&
                value[index + 1].code in 0xDC00..0xDFFF -> {
                index++
                4
            }
            else -> 3
        }
        if (bytes > limit - byteCount) return null
        bytes += byteCount
        index++
    }
    return bytes
}
