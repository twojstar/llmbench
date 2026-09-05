package com.twojstar.llmbench.ui.screens

import android.view.View
import com.twojstar.llmbench.data.model.WebAiService
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
    fun hidesInactiveProviderWebViewsAtTheViewLevel() {
        assertEquals(View.VISIBLE, providerWebViewVisibility(isCurrentService = true))
        assertEquals(View.GONE, providerWebViewVisibility(isCurrentService = false))
    }
}
