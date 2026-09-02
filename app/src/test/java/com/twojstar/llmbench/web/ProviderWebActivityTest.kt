package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWebActivityTest {
    @Test
    fun everyProviderWithVerifiedStableMarkupHasAnAuditableGenerationProbe() {
        val supportedProviders = WebAiService.entries.filterNot { it == WebAiService.VIBE }
        supportedProviders.forEach { service ->
            assertTrue(
                "Missing generation probe for ${service.id}",
                ProviderWebTweakRegistry.generationSelectors(service).isNotEmpty()
            )
        }
    }

    @Test
    fun probesUsePortableExactGenerationControls() {
        WebAiService.entries
            .flatMap(ProviderWebTweakRegistry::generationSelectors)
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
    fun vibeDoesNotClaimLocaleDependentActivitySupport() {
        assertTrue(ProviderWebTweakRegistry.generationSelectors(WebAiService.VIBE).isEmpty())
    }
}
