package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WebChatActivityTest {
    @Test
    fun backgroundCompletionBecomesUnread() {
        assertEquals(
            WebChatActivityStatus.UNREAD,
            nextWebChatActivityStatus(
                previous = WebChatActivityStatus.GENERATING,
                observation = WebChatGenerationObservation.IDLE,
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
                observation = WebChatGenerationObservation.IDLE,
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
                observation = WebChatGenerationObservation.IDLE,
                isSelected = false
            )
        )
        assertEquals(
            WebChatActivityStatus.IDLE,
            markWebChatActivityRead(WebChatActivityStatus.UNREAD)
        )
    }

    @Test
    fun unknownObservationClearsUnverifiedGeneratingWithoutCreatingUnread() {
        assertEquals(
            WebChatActivityStatus.IDLE,
            nextWebChatActivityStatus(
                previous = WebChatActivityStatus.GENERATING,
                observation = WebChatGenerationObservation.UNKNOWN,
                isSelected = false
            )
        )
    }

    @Test
    fun evictionClearsGeneratingButPreservesKnownUnread() {
        assertEquals(
            WebChatActivityStatus.IDLE,
            webChatActivityStatusAfterEviction(WebChatActivityStatus.GENERATING)
        )
        assertEquals(
            WebChatActivityStatus.UNREAD,
            webChatActivityStatusAfterEviction(WebChatActivityStatus.UNREAD)
        )
    }

    @Test
    fun visibleGenerationAlwaysWins() {
        WebChatActivityStatus.entries.forEach { previous ->
            assertEquals(
                WebChatActivityStatus.GENERATING,
                nextWebChatActivityStatus(
                    previous = previous,
                    observation = WebChatGenerationObservation.GENERATING,
                    isSelected = false
                )
            )
        }
    }
}
