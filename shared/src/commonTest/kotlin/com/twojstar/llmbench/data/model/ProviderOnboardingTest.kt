package com.twojstar.llmbench.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderOnboardingTest {
    @Test
    fun exposesOnlyVerifiedPreferredIdentityMethods() {
        assertEquals(
            listOf(ProviderIdentityMethod.GOOGLE, ProviderIdentityMethod.GITHUB),
            WebAiService.QWEN.onboardingCapabilities().preferredIdentityMethods
        )
        assertEquals(
            listOf(ProviderIdentityMethod.MICROSOFT),
            WebAiService.COPILOT.onboardingCapabilities().preferredIdentityMethods
        )
        assertEquals(
            listOf(ProviderIdentityMethod.GOOGLE, ProviderIdentityMethod.GITHUB),
            WebAiService.ZAI.onboardingCapabilities().preferredIdentityMethods
        )
    }

    @Test
    fun doesNotInventIdentityPathsForUnsupportedProviders() {
        val verified = setOf(WebAiService.QWEN, WebAiService.COPILOT, WebAiService.ZAI)

        WebAiService.entries.filterNot { it in verified }.forEach { service ->
            val capabilities = service.onboardingCapabilities()
            assertFalse(capabilities.hasIdentityAssistedPath, service.name)
            assertEquals(
                EmbeddedSessionHandoff.UNSUPPORTED,
                capabilities.embeddedSessionHandoff,
                service.name
            )
        }
    }

    @Test
    fun identityMethodsDoNotClaimEmbeddedSessionHandoff() {
        listOf(WebAiService.QWEN, WebAiService.COPILOT, WebAiService.ZAI).forEach { service ->
            val capabilities = service.onboardingCapabilities()
            assertTrue(capabilities.hasIdentityAssistedPath, service.name)
            assertEquals(
                EmbeddedSessionHandoff.UNVERIFIED,
                capabilities.embeddedSessionHandoff,
                service.name
            )
        }
    }
}
