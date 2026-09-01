package com.twojstar.llmbench.web

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderWebActivityTest {
    @Test
    fun backgroundCompletionBecomesUnread() {
        assertEquals(
            WebChatActivityStatus.UNREAD,
            nextWebChatActivityStatus(
                previous = WebChatActivityStatus.GENERATING,
                isGenerating = false,
                isSelected = false
            )
        )
    }

    @Test
    fun selectedCompletionBecomesIdle() {
        assertEquals(
            WebChatActivityStatus.IDLE,
            nextWebChatActivityStatus(
                previous = WebChatActivityStatus.GENERATING,
                isGenerating = false,
                isSelected = true
            )
        )
    }

    @Test
    fun unreadPersistsUntilTabIsOpened() {
        assertEquals(
            WebChatActivityStatus.UNREAD,
            nextWebChatActivityStatus(
                previous = WebChatActivityStatus.UNREAD,
                isGenerating = false,
                isSelected = false
            )
        )
        assertEquals(
            WebChatActivityStatus.IDLE,
            markWebChatActivityRead(WebChatActivityStatus.UNREAD)
        )
    }

    @Test
    fun visibleGenerationAlwaysWins() {
        WebChatActivityStatus.entries.forEach { previous ->
            assertEquals(
                WebChatActivityStatus.GENERATING,
                nextWebChatActivityStatus(
                    previous = previous,
                    isGenerating = true,
                    isSelected = false
                )
            )
        }
    }
}
