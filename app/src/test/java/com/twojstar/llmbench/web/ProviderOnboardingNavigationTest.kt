package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.data.model.onboardingCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOnboardingNavigationTest {
    @Test
    fun androidNavigationVerificationRemainsProviderScoped() {
        val verified = setOf(WebAiService.QWEN, WebAiService.COPILOT, WebAiService.ZAI)

        WebAiService.entries.forEach { service ->
            assertEquals(
                service in verified,
                ProviderWebTweakRegistry.hasVerifiedTopLevelNavigationPolicy(service)
            )
        }
    }

    @Test
    fun identityMetadataAloneCannotProduceAndroidAuthHosts() {
        assertTrue(WebAiService.QWEN.onboardingCapabilities().hasIdentityAssistedPath)
        assertTrue(
            ProviderWebTweakRegistry.identityAuthHostsForNavigation(
                service = WebAiService.QWEN,
                navigationVerified = false
            ).isEmpty()
        )
    }

    @Test
    fun verifiedIdentityMethodsResolveToExpectedAndroidAuthHosts() {
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
