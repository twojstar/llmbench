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
    fun claudeIncludesScopedSmoothPerformanceProfile() {
        val smooth = ProviderWebTweakRegistry.forProvider(WebAiService.CLAUDE)
            .firstOrNull { it.id == "claude-smooth" }
            ?: error("Claude smooth tweak missing")

        assertTrue(smooth.css.contains("content-visibility: auto"))
        assertTrue(smooth.css.contains("animate-spin"))
        assertTrue(smooth.script.contains("const TAIL = 2"))
        assertTrue(smooth.script.contains("MutationObserver"))
        assertTrue(smooth.script.contains("image.loading = 'lazy'"))
        assertTrue(smooth.script.contains("window.setTimeout(callback, 100)"))
        assertTrue(smooth.script.contains("window.addEventListener('resize', schedule"))
        assertTrue(smooth.css.contains("animation-iteration-count: 1"))
        assertFalse(
            ProviderWebTweakRegistry.forProvider(WebAiService.CHATGPT)
                .any { it.id == "claude-smooth" }
        )
    }

    @Test
    fun acceptsVerifiedKimiAliasWithoutAcceptingSuffixSpoofs() {
        assertTrue(providerHostMatches(WebAiService.KIMI, "kimi.com"))
        assertTrue(providerHostMatches(WebAiService.KIMI, "www.kimi.com"))
        assertFalse(providerHostMatches(WebAiService.KIMI, "notkimi.com"))
        assertFalse(providerHostMatches(WebAiService.KIMI, "kimi.com.evil.example"))
    }

    @Test
    fun acceptsVerifiedQwenAliasWithoutAcceptingSuffixSpoofs() {
        assertTrue(providerHostMatches(WebAiService.QWEN, "qwen.ai"))
        assertTrue(providerHostMatches(WebAiService.QWEN, "www.qwen.ai"))
        assertTrue(providerHostMatches(WebAiService.QWEN, "chat.qwen.ai"))
        assertFalse(providerHostMatches(WebAiService.QWEN, "notqwen.ai"))
        assertFalse(providerHostMatches(WebAiService.QWEN, "qwen.ai.evil.example"))
    }

    @Test
    fun qwenTopLevelNavigationUsesVerifiedBoundaryAndAuthHosts() {
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.QWEN, "https://chat.qwen.ai/"))
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.QWEN, "https://www.qwen.ai/"))
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.QWEN, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.QWEN, "https://github.com/login/oauth/authorize"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.QWEN, "https://example.com/"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.QWEN, "https://github.com.evil.example/"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.QWEN, "http://github.com/login/oauth/authorize"))

        // Roll the stricter boundary out provider-by-provider after auth redirects are verified.
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.CHATGPT, "https://accounts.google.com/"))
    }

    @Test
    fun acceptsVerifiedCopilotAliasesWithoutAcceptingSuffixSpoofs() {
        listOf(
            "copilot.com",
            "copilot.ai",
            "copilot.cloud.microsoft",
            "m365.cloud.microsoft",
            "m365copilot.com"
        ).forEach { host ->
            assertTrue(providerHostMatches(WebAiService.COPILOT, host))
            assertTrue(providerHostMatches(WebAiService.COPILOT, "www.$host"))
            assertFalse(providerHostMatches(WebAiService.COPILOT, "not-$host"))
            assertFalse(providerHostMatches(WebAiService.COPILOT, "$host.evil.example"))
        }
    }

    @Test
    fun copilotTopLevelNavigationUsesVerifiedBoundaryAndAuthHosts() {
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.COPILOT, "https://copilot.microsoft.com/"))
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.COPILOT, "https://m365.cloud.microsoft/chat"))
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.COPILOT, "https://login.live.com/"))
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.COPILOT, "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.COPILOT, "https://example.com/"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.COPILOT, "https://login.live.com.evil.example/"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.COPILOT, "http://login.live.com/"))
    }

    @Test
    fun zaiTopLevelNavigationUsesVerifiedBoundaryAndAuthHosts() {
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.ZAI, "https://chat.z.ai/"))
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.ZAI, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(shouldLoadHttpsInProviderWebView(WebAiService.ZAI, "https://github.com/login/oauth/authorize"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.ZAI, "https://example.com/"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.ZAI, "https://github.com.evil.example/"))
        assertFalse(shouldLoadHttpsInProviderWebView(WebAiService.ZAI, "http://accounts.google.com/"))
    }

    @Test
    fun acceptsVerifiedMetaLoginAliasWithoutAcceptingSuffixSpoofs() {
        assertTrue(providerHostMatches(WebAiService.META_AI, "alpha.meta.ai"))
        assertTrue(providerHostMatches(WebAiService.META_AI, "www.alpha.meta.ai"))
        assertFalse(providerHostMatches(WebAiService.META_AI, "notalpha.meta.ai"))
        assertFalse(providerHostMatches(WebAiService.META_AI, "alpha.meta.ai.evil.example"))
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
