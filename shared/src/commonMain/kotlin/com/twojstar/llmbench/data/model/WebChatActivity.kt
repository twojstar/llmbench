package com.twojstar.llmbench.data.model

enum class WebChatActivityStatus {
    IDLE,
    GENERATING,
    PENDING,
    UNREAD
}

enum class WebChatGenerationObservation {
    IDLE,
    GENERATING,
    COMPLETED,
    COMPLETED_WHILE_SELECTED,
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
    WebChatGenerationObservation.COMPLETED_WHILE_SELECTED -> WebChatActivityStatus.IDLE
    WebChatGenerationObservation.IDLE -> when {
        previous == WebChatActivityStatus.GENERATING && !isSelected -> WebChatActivityStatus.UNREAD
        previous == WebChatActivityStatus.UNREAD && !isSelected -> WebChatActivityStatus.UNREAD
        previous == WebChatActivityStatus.PENDING && !isSelected -> WebChatActivityStatus.PENDING
        else -> WebChatActivityStatus.IDLE
    }
    WebChatGenerationObservation.UNKNOWN -> when {
        previous == WebChatActivityStatus.UNREAD && !isSelected -> WebChatActivityStatus.UNREAD
        previous == WebChatActivityStatus.PENDING && !isSelected -> WebChatActivityStatus.PENDING
        else -> WebChatActivityStatus.IDLE
    }
}

fun markWebChatActivityRead(status: WebChatActivityStatus): WebChatActivityStatus =
    if (status == WebChatActivityStatus.UNREAD || status == WebChatActivityStatus.PENDING) {
        WebChatActivityStatus.IDLE
    } else {
        status
    }

fun webChatActivityStatusAfterEviction(status: WebChatActivityStatus): WebChatActivityStatus = when (status) {
    WebChatActivityStatus.GENERATING -> WebChatActivityStatus.PENDING
    WebChatActivityStatus.UNREAD -> WebChatActivityStatus.UNREAD
    WebChatActivityStatus.PENDING -> WebChatActivityStatus.PENDING
    WebChatActivityStatus.IDLE -> WebChatActivityStatus.IDLE
}
