package com.twojstar.llmbench.data.model

internal const val CHAT_MARKDOWN_TITLE = "LlmBench chat"
internal const val CHAT_MARKDOWN_VERSION = 1
internal const val CHAT_MARKDOWN_VERSION_MARKER = "<!-- llmbench-chat:v1 -->"
internal const val CHAT_MARKDOWN_MESSAGE_PREFIX = "<!-- llmbench-message:v1 role="
internal const val CHAT_MARKDOWN_MESSAGE_BYTES = " bytes="
internal const val CHAT_MARKDOWN_MESSAGE_META_SUFFIX = " -->"
internal const val CHAT_MARKDOWN_MESSAGE_END = "<!-- llmbench-message-end -->"
private const val MAX_HEADING_METADATA_CHARS = 160
private const val UTF8_SPAN_BYTE_MASK = 0x0F
private const val UTF8_SPAN_CODE_UNIT_SHIFT = 4

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
 * oversized chats do not require building or scanning the complete export first.
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

        val frameStart = message.markdownFrameStart(output.remainingUtf8Bytes) ?: return null
        if (!output.append(frameStart)) return null
        if (!output.append("## ${message.markdownHeading()}\n\n")) return null
        if (!output.append(message.text)) return null
        if (!output.append("\n$CHAT_MARKDOWN_MESSAGE_END\n\n")) return null
    }

    return output.toString()
}

private fun ModelChatMessage.markdownFrameStart(maxBodyUtf8Bytes: Int): String? {
    val bodyUtf8Bytes = utf8ByteCountAtMost(text, maxBodyUtf8Bytes) ?: return null
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
    var remainingUtf8Bytes: Int = maxUtf8Bytes
        private set

    fun append(value: String): Boolean {
        val byteCount = utf8ByteCountAtMost(value, remainingUtf8Bytes) ?: return false
        builder.append(value)
        remainingUtf8Bytes -= byteCount
        return true
    }

    override fun toString(): String = builder.toString()
}

/** Packed as `(UTF-16 code units << 4) | UTF-8 bytes`; zero means no code unit at [index]. */
internal fun String.packedUtf8SpanAt(index: Int): Int {
    val codeUnit = getOrNull(index)?.code ?: return 0
    val nextCodeUnit = getOrNull(index + 1)?.code
    return when {
        codeUnit <= 0x7F -> packUtf8Span(codeUnitCount = 1, byteCount = 1)
        codeUnit <= 0x7FF -> packUtf8Span(codeUnitCount = 1, byteCount = 2)
        codeUnit in 0xD800..0xDBFF && nextCodeUnit?.let { it in 0xDC00..0xDFFF } == true ->
            packUtf8Span(codeUnitCount = 2, byteCount = 4)
        else -> packUtf8Span(codeUnitCount = 1, byteCount = 3)
    }
}

internal fun packedUtf8SpanCodeUnitCount(span: Int): Int = span ushr UTF8_SPAN_CODE_UNIT_SHIFT

internal fun packedUtf8SpanByteCount(span: Int): Int = span and UTF8_SPAN_BYTE_MASK

private fun packUtf8Span(codeUnitCount: Int, byteCount: Int): Int =
    (codeUnitCount shl UTF8_SPAN_CODE_UNIT_SHIFT) or byteCount

internal fun utf8ByteCountAtMost(value: String, limit: Int): Int? {
    var bytes = 0
    var index = 0
    while (index < value.length) {
        val span = value.packedUtf8SpanAt(index)
        if (span == 0) return null
        val byteCount = packedUtf8SpanByteCount(span)
        if (bytes > limit - byteCount) return null
        bytes += byteCount
        index += packedUtf8SpanCodeUnitCount(span)
    }
    return bytes
}
