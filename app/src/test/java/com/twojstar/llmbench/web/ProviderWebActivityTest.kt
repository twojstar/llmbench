package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWebActivityTest {
    @Test
    fun everyProviderHasAnAuditableGenerationProbe() {
        WebAiService.entries.forEach { service ->
            assertTrue(
                "Missing generation probe for ${service.id}",
                ProviderWebTweakRegistry.generationSelectors(service).isNotEmpty()
            )
        }
    }

    @Test
    fun probesUseExactGenerationControlsInsteadOfBroadStopSubstrings() {
        WebAiService.entries
            .flatMap(ProviderWebTweakRegistry::generationSelectors)
            .forEach { selector ->
                assertFalse("Broad stop selector: $selector", selector.contains("*="))
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
    fun vibeUsesExactStopGenerationLabels() {
        val selectors = ProviderWebTweakRegistry.generationSelectors(WebAiService.VIBE)
        assertTrue(selectors.contains("button[aria-label=\"Stop generation\" i]"))
        assertTrue(selectors.contains("button[aria-label=\"Stop generating\" i]"))
    }
}
