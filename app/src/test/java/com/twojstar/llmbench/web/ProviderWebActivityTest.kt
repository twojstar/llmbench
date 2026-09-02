package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWebActivityTest {
    @Test
    fun everyProviderWithVerifiedStableMarkupHasAnAuditableGenerationProbe() {
        WebAiService.entries.forEach { service ->
            assertTrue(
                "Missing generation probe for ${service.id}",
                ProviderWebTweakRegistry.generationSelectors(service).isNotEmpty()
            )
        }
    }

    @Test
    fun probesUsePortableExactGenerationControls() {
        WebAiService.entries
            .flatMap { service ->
                ProviderWebTweakRegistry.generationSelectors(service) +
                    ProviderWebTweakRegistry.generationIdleSelectors(service)
            }
            .forEach { selector ->
                assertFalse("Broad stop selector: $selector", selector.contains("*="))
                assertFalse("Unsupported :has selector: $selector", selector.contains(":has("))
            }
    }

    @Test
    fun chatGptUsesItsStableStopButtonTestId() {
        assertTrue(
            ProviderWebTweakRegistry.generationSelectors(WebAiService.CHATGPT)
                .contains("[data-testid=\"stop-button\"]")
        )
    }

    @Test
    fun localeIndependentGenerationControlsArePreferredWhereVerified() {
        assertTrue(
            ProviderWebTweakRegistry.generationSelectors(WebAiService.CLAUDE)
                .first().contains("data-testid")
        )
        assertTrue(
            ProviderWebTweakRegistry.generationSelectors(WebAiService.GEMINI)
                .first().contains("data-test-id")
        )
        assertFalse(
            ProviderWebTweakRegistry.generationSelectors(WebAiService.DEEPSEEK)
                .first().contains("aria-label")
        )
        assertFalse(
            ProviderWebTweakRegistry.generationSelectors(WebAiService.KIMI)
                .first().contains("aria-label")
        )
    }

    @Test
    fun vibeTrackerRejectsIdleFingerprintOnTheSameSubmitControl() {
        val script = requireNotNull(
            providerGenerationActivityScript(
                service = WebAiService.VIBE,
                consumeCompletion = false
            )
        )

        assertTrue(script.contains("button[type=\\\"submit\\\"] svg rect"))
        assertTrue(script.contains("M12 18v4h4v-4h-4ZM16 14v4h4v-4h-4"))
        assertTrue(
            script.contains(
                "idleControl.closest('button[type=\"submit\"]') === activeSubmit"
            )
        )
    }

    @Test
    fun vibeUsesLocaleIndependentSubmitStopIcon() {
        val activeSelector = ProviderWebTweakRegistry.generationSelectors(WebAiService.VIBE).single()
        val idleSelector = ProviderWebTweakRegistry.generationIdleSelectors(WebAiService.VIBE).single()
        assertTrue(activeSelector.contains("button[type=\"submit\"]"))
        assertTrue(activeSelector.endsWith("svg rect"))
        assertTrue(idleSelector.contains("path[d^=\"M12 18v4h4v-4h-4ZM16 14v4h4v-4h-4\"]"))
        assertFalse(activeSelector.contains("aria-label"))
        assertFalse(idleSelector.contains("aria-label"))
    }
}
