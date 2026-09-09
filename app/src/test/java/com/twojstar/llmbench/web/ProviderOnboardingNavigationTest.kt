package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.data.model.onboardingCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderOnboardingNavigationTest {
    @Test
    fun navigationPolicyTracksSharedOnboardingRegistry() {
        WebAiService.entries.forEach { service ->
            assertEquals(
                service.onboardingCapabilities().hasIdentityAssistedPath,
                ProviderWebTweakRegistry.hasVerifiedTopLevelNavigationPolicy(service)
            )
        }
    }

    @Test
    fun identityMethodsResolveToExpectedAndroidAuthHosts() {
        assertEquals(
            setOf("accounts.google.com", "github.com"),
            ProviderWebTweakRegistry.topLevelNavigationAuthHosts(WebAiService.QWEN)
        )
        assertEquals(
            setOf("login.live.com", "login.microsoftonline.com"),
            ProviderWebTweakRegistry.topLevelNavigationAuthHosts(WebAiService.COPILOT)
        )
        assertEquals(
            setOf("accounts.google.com", "github.com"),
            ProviderWebTweakRegistry.topLevelNavigationAuthHosts(WebAiService.ZAI)
        )
    }
}
