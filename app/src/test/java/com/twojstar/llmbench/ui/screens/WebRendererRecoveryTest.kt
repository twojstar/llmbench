package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRendererRecoveryTest {
    @Test
    fun onlyInactiveIdleRendererWaivesPriorityWhenNotVisible() {
        assertFalse(rendererPriorityWaivedWhenNotVisible(isSelected = true, isGenerating = false))
        assertFalse(rendererPriorityWaivedWhenNotVisible(isSelected = false, isGenerating = true))
        assertTrue(rendererPriorityWaivedWhenNotVisible(isSelected = false, isGenerating = false))
    }

    @Test
    fun lowMemoryRendererTerminationRecreatesLastUrl() {
        assertEquals(
            WebRendererRecoveryAction.RECREATE_LAST_URL,
            webRendererRecoveryAction(didCrash = false, isSelected = true)
        )
    }

    @Test
    fun inactiveLowMemoryTerminationStaysEvictedUntilSelected() {
        assertEquals(
            WebRendererRecoveryAction.EVICT_UNTIL_SELECTED,
            webRendererRecoveryAction(didCrash = false, isSelected = false)
        )
    }

    @Test
    fun crashedInactiveProviderDoesNotKeepLivePoolSlot() {
        assertEquals(
            listOf(WebAiService.GEMINI),
            webServicesForActivation(
                current = listOf(WebAiService.CLAUDE, WebAiService.GEMINI),
                activationTarget = WebAiService.GEMINI,
                crashedServices = setOf(WebAiService.CLAUDE)
            )
        )
        assertEquals(
            listOf(WebAiService.CLAUDE, WebAiService.GEMINI),
            webServicesForActivation(
                current = listOf(WebAiService.CLAUDE, WebAiService.GEMINI),
                activationTarget = WebAiService.CLAUDE,
                crashedServices = setOf(WebAiService.CLAUDE)
            )
        )
    }

    @Test
    fun rendererCrashRequiresExplicitRetry() {
        assertEquals(
            WebRendererRecoveryAction.REQUIRE_USER_RETRY,
            webRendererRecoveryAction(didCrash = true, isSelected = true)
        )
    }
}
