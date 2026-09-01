package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ProviderWebTweaksTest {
    @Test
    fun acceptsOnlyProviderOwnedHosts() {
        WebAiService.entries.forEach { service ->
            val canonicalHost = URI(service.url).host
            assertTrue(providerHostMatches(service, canonicalHost))
            assertTrue(providerHostMatches(service, canonicalHost.uppercase()))
            assertTrue(providerHostMatches(service, "$canonicalHost."))
            assertTrue(providerHostMatches(service, "mobile.$canonicalHost"))
            assertFalse(providerHostMatches(service, "accounts.google.com"))
            assertFalse(providerHostMatches(service, "not-$canonicalHost.example"))
            assertFalse(providerHostMatches(service, "$canonicalHost.evil.example"))
        }
    }

    @Test
    fun acceptsVerifiedKimiAliasWithoutAcceptingSuffixSpoofs() {
        assertTrue(providerHostMatches(WebAiService.KIMI, "kimi.com"))
        assertTrue(providerHostMatches(WebAiService.KIMI, "www.kimi.com"))
        assertFalse(providerHostMatches(WebAiService.KIMI, "notkimi.com"))
        assertFalse(providerHostMatches(WebAiService.KIMI, "kimi.com.evil.example"))
    }

    @Test
    fun requiresHttpsForProviderPages() {
        assertTrue(providerUrlMatches(WebAiService.CHATGPT, "https://chatgpt.com/"))
        assertFalse(providerUrlMatches(WebAiService.CHATGPT, "http://chatgpt.com/"))
        assertFalse(providerUrlMatches(WebAiService.CHATGPT, "https://chatgpt.com.evil.example/"))
        assertFalse(providerUrlMatches(WebAiService.CHATGPT, "not a url"))
    }

    @Test
    fun everyProviderHasAuditableTweaksAndOwnedHosts() {
        WebAiService.entries.forEach { service ->
            assertTrue(ProviderWebTweakRegistry.ownedHosts(service).isNotEmpty())
            assertTrue(ProviderWebTweakRegistry.forProvider(service).isNotEmpty())
        }
    }
}
