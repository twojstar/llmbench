package com.twojstar.llmbench.data.model

enum class WebChatActivityStatus {
    IDLE,
    GENERATING,
    UNREAD
}

enum class WebChatGenerationObservation {
    IDLE,
    GENERATING,
    COMPLETED,
    UNKNOWN
}

fun nextWebChatActivityStatus(
    previous: WebChatActivityStatus,
    observation: WebChatGenerationObservation,
    isSelected: Boolean
): WebChatActivityStatus = when (observation) {
    WebChatGenerationObservation.GENERATING -> WebChatActivityStatus.GENERATING
    WebChatGenerationObservation.COMPLETED ->
        if (isSelected) WebChatActivityStatus.IDLE else WebChatActivityStatus.UNREAD
    WebChatGenerationObservation.IDLE -> when {
        previous == WebChatActivityStatus.GENERATING && !isSelected -> WebChatActivityStatus.UNREAD
        previous == WebChatActivityStatus.UNREAD && !isSelected -> WebChatActivityStatus.UNREAD
        else -> WebChatActivityStatus.IDLE
    }
    WebChatGenerationObservation.UNKNOWN -> when {
        previous == WebChatActivityStatus.UNREAD && !isSelected -> WebChatActivityStatus.UNREAD
        else -> WebChatActivityStatus.IDLE
    }
}

fun markWebChatActivityRead(status: WebChatActivityStatus): WebChatActivityStatus =
    if (status == WebChatActivityStatus.UNREAD) WebChatActivityStatus.IDLE else status

fun webChatActivityStatusAfterEviction(status: WebChatActivityStatus): WebChatActivityStatus =
    if (status == WebChatActivityStatus.UNREAD) WebChatActivityStatus.UNREAD else WebChatActivityStatus.IDLE
