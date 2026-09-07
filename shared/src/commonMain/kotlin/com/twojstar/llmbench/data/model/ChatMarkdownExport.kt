package com.twojstar.llmbench.data.model

private const val CHAT_MARKDOWN_TITLE = "LlmBench chat"
private const val MAX_HEADING_METADATA_CHARS = 160

/**
 * Renders the user-visible native chat as portable Markdown.
 *
 * Export starts at the first user turn so app welcome content is omitted. Only user and assistant
 * messages are included by construction; internal/system roles, profile notes, latency and other
 * diagnostics are intentionally excluded from the export boundary.
 */
fun renderChatMarkdown(messages: List<ModelChatMessage>): String? {
    val firstUserIndex = messages.indexOfFirst { it.sender == CHAT_ROLE_USER }
    if (firstUserIndex < 0) return null

    val exportable = messages
        .drop(firstUserIndex)
        .filter { it.sender == CHAT_ROLE_USER || it.sender == CHAT_ROLE_ASSISTANT }

    return buildString {
        append("# ")
        append(CHAT_MARKDOWN_TITLE)
        append("\n\n")
        exportable.forEachIndexed { index, message ->
            append("## ")
            append(message.markdownHeading())
            append("\n\n")
            append(message.text)
            if (!message.text.endsWith('\n')) append('\n')
            if (index != exportable.lastIndex) append('\n')
        }
    }
}

private fun ModelChatMessage.markdownHeading(): String = when (sender) {
    CHAT_ROLE_USER -> "You"
    CHAT_ROLE_ASSISTANT -> buildList {
        add(provider?.shortName?.safeHeadingMetadata().takeUnless(String?::isNullOrBlank) ?: "Assistant")
        modelName?.safeHeadingMetadata()?.takeIf(String::isNotBlank)?.let(::add)
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
