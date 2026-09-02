package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.WebChatActivityStatus
import com.twojstar.llmbench.data.model.WebChatGenerationObservation
import org.junit.Assert.assertEquals
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
    fun selectedCompletionSurvivesEvictionWithoutBecomingUnread() {
        assertTrue(
            shouldApplyWebChatObservation(
                observation = WebChatGenerationObservation.COMPLETED_WHILE_SELECTED,
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
    fun lateGeneratingObservationSurvivesEvictionAsPending() {
        assertTrue(
            shouldApplyWebChatObservation(
                observation = WebChatGenerationObservation.GENERATING,
                isLiveService = false,
                isSameWebView = false,
                hasCurrentWebView = false
            )
        )
        assertEquals(
            WebChatActivityStatus.PENDING,
            nextObservedWebChatActivityStatus(
                previous = WebChatActivityStatus.IDLE,
                observation = WebChatGenerationObservation.GENERATING,
                isSelected = false,
                isLiveService = false
            )
        )
    }

    @Test
    fun idleObservationFromEvictedWebViewIsRejected() {
        assertFalse(
            shouldApplyWebChatObservation(
                observation = WebChatGenerationObservation.IDLE,
                isLiveService = false,
                isSameWebView = false,
                hasCurrentWebView = false
            )
        )
    }

    @Test
    fun generatingObservationFromReplacedWebViewIsRejected() {
        assertFalse(
            shouldApplyWebChatObservation(
                observation = WebChatGenerationObservation.GENERATING,
                isLiveService = true,
                isSameWebView = false,
                hasCurrentWebView = true
            )
        )
    }

    @Test
    fun stableExternalPageCanApplyPendingDisplayMode() {
        assertTrue(
            shouldApplyPendingDesktopMode(
                observation = WebChatGenerationObservation.UNKNOWN,
                trackingSupported = true,
                isStableOffProviderPage = true
            )
        )
    }

    @Test
    fun unknownProviderPageKeepsPendingDisplayModeDeferred() {
        assertFalse(
            shouldApplyPendingDesktopMode(
                observation = WebChatGenerationObservation.UNKNOWN,
                trackingSupported = true,
                isStableOffProviderPage = false
            )
        )
    }

    @Test
    fun generatingPageAlwaysKeepsPendingDisplayModeDeferred() {
        assertFalse(
            shouldApplyPendingDesktopMode(
                observation = WebChatGenerationObservation.GENERATING,
                trackingSupported = false,
                isStableOffProviderPage = true
            )
        )
    }
}
