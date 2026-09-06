package com.twojstar.llmbench.ui.screens

import android.view.View
import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.data.model.WebChatActivityStatus
import com.twojstar.llmbench.data.model.WebChatGenerationObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewLruTest {
    @Test
    fun failedExternalNavigationLaunchReportsFailure() {
        var failures = 0

        reportExternalNavigationLaunch(launched = true) { failures++ }
        assertEquals(0, failures)

        reportExternalNavigationLaunch(launched = false) { failures++ }
        assertEquals(1, failures)
    }

    @Test
    fun keepsSelectedProviderFirstAndCapsPoolAtTwo() {
        val firstSwitch = nextWebViewLru(
            current = listOf(WebAiService.CLAUDE),
            selected = WebAiService.CHATGPT
        )
        assertEquals(listOf(WebAiService.CHATGPT, WebAiService.CLAUDE), firstSwitch)

        val eviction = nextWebViewLru(firstSwitch, WebAiService.GEMINI)
        assertEquals(listOf(WebAiService.GEMINI, WebAiService.CHATGPT), eviction)

        val revisit = nextWebViewLru(eviction, WebAiService.CHATGPT)
        assertEquals(listOf(WebAiService.CHATGPT, WebAiService.GEMINI), revisit)
    }
    @Test
    fun keepsGeneratingProviderAheadOfOrdinaryRecentProvider() {
        val next = nextWebViewLru(
            current = listOf(WebAiService.CHATGPT, WebAiService.CLAUDE),
            selected = WebAiService.GEMINI,
            protectedServices = setOf(WebAiService.CLAUDE)
        )
        assertEquals(listOf(WebAiService.GEMINI, WebAiService.CLAUDE), next)
    }

    @Test
    fun freshGenerationProbeProtectsResponseBeforeNativePollCatchesUp() {
        val protected = protectedWebServicesForLru(
            knownGenerating = emptySet(),
            freshObservations = mapOf(
                WebAiService.CLAUDE to WebChatGenerationObservation.GENERATING,
                WebAiService.CHATGPT to WebChatGenerationObservation.IDLE
            )
        )
        val next = nextWebViewLru(
            current = listOf(WebAiService.CHATGPT, WebAiService.CLAUDE),
            selected = WebAiService.GEMINI,
            protectedServices = protected
        )
        assertEquals(listOf(WebAiService.GEMINI, WebAiService.CLAUDE), next)
    }

    @Test
    fun freshIdleProbeClearsStaleGeneratingProtection() {
        val protected = protectedWebServicesForLru(
            knownGenerating = setOf(WebAiService.CLAUDE),
            freshObservations = mapOf(
                WebAiService.CLAUDE to WebChatGenerationObservation.IDLE
            )
        )
        assertEquals(emptySet<WebAiService>(), protected)
    }

    @Test
    fun freshCompletionBecomesUnreadBeforeEviction() {
        assertEquals(
            WebChatActivityStatus.UNREAD,
            webChatActivityStatusAfterFreshLruProbe(
                previous = WebChatActivityStatus.GENERATING,
                observation = WebChatGenerationObservation.COMPLETED,
                isSelected = false
            )
        )
    }

    @Test
    fun hidesInactiveProviderWebViewsAtTheViewLevel() {
        assertEquals(View.VISIBLE, providerWebViewVisibility(isCurrentService = true))
        assertEquals(View.GONE, providerWebViewVisibility(isCurrentService = false))
    }
}
