package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewLruTest {
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
}
