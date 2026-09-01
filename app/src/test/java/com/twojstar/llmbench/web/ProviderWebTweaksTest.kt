package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWebTweaksTest {
    @Test
    fun acceptsOnlyProviderOwnedHosts() {
        WebAiService.entries.forEach { service ->
            val canonicalHost = java.net.URI(service.url).host
            assertTrue(providerHostMatches(service, canonicalHost))
            assertTrue(providerHostMatches(service, canonicalHost.uppercase()))
            assertTrue(providerHostMatches(service, "$canonicalHost."))
            assertTrue(providerHostMatches(service, "mobile.$canonicalHost"))
            assertFalse(providerHostMatches(service, "accounts.google.com"))
            assertFalse(providerHostMatches(service, "not$canonicalHost"))
            assertFalse(providerHostMatches(service, "$canonicalHost.evil.example"))
            assertFalse(providerHostMatches(service, "evil-$canonicalHost.example"))
        }
    }

    @Test
    fun everyProviderHasAuditableTweaks() {
        WebAiService.entries.forEach { service ->
            assertTrue(ProviderWebTweakRegistry.forProvider(service).isNotEmpty())
        }
    }
}
