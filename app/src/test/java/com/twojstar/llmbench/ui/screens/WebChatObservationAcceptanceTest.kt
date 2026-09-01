package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.WebChatGenerationObservation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebChatObservationAcceptanceTest {
    @Test
    fun completedObservationSurvivesEvictionWithoutReplacement() {
        assertTrue(
            shouldApplyWebChatObservation(
                observation = WebChatGenerationObservation.COMPLETED,
                isLiveService = false,
                isSameWebView = false,
                hasCurrentWebView = false
            )
        )
    }

    @Test
    fun completedObservationFromReplacedWebViewIsRejected() {
        assertFalse(
            shouldApplyWebChatObservation(
                observation = WebChatGenerationObservation.COMPLETED,
                isLiveService = true,
                isSameWebView = false,
                hasCurrentWebView = true
            )
        )
    }

    @Test
    fun nonTerminalObservationFromEvictedWebViewIsRejected() {
        assertFalse(
            shouldApplyWebChatObservation(
                observation = WebChatGenerationObservation.GENERATING,
                isLiveService = false,
                isSameWebView = false,
                hasCurrentWebView = false
            )
        )
    }
}
