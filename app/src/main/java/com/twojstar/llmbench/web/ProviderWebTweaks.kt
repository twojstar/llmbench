package com.twojstar.llmbench.web

import android.webkit.WebView
import com.twojstar.llmbench.data.model.WebAiService
import org.json.JSONObject
import java.net.URI

internal data class ProviderWebTweak(
    val id: String,
    val css: String
)

internal object ProviderWebTweakRegistry {
    private val mobileBaseline = ProviderWebTweak(
        id = "mobile-baseline",
        css = """
            html {
                scroll-behavior: auto !important;
            }
            @media (prefers-reduced-motion: reduce) {
                *, *::before, *::after {
                    animation-duration: 0.01ms !important;
                    animation-iteration-count: 1 !important;
                    transition-duration: 0.01ms !important;
                }
            }
        """.trimIndent()
    )

    // Canonical hosts come from WebAiService.url. Add only verified provider-owned aliases here.
    private val ownedHostAliases = mapOf(
        WebAiService.KIMI to setOf("kimi.com")
    )

    private val providerTweaks = WebAiService.entries.associateWith { listOf(mobileBaseline) }

    // Keep activity probes conservative: exact generation controls only, never broad "contains stop" matches.
    private val generationSelectors = mapOf(
        WebAiService.CHATGPT to listOf(
            "[data-testid=\"stop-button\"]"
        ),
        WebAiService.CLAUDE to listOf(
            "button[aria-label=\"Stop Response\" i]"
        ),
        WebAiService.GEMINI to listOf(
            "button[aria-label=\"Stop response\" i]"
        ),
        WebAiService.DEEPSEEK to listOf(
            "button[aria-label=\"Stop generating\" i]",
            "[role=\"button\"][aria-label=\"Stop generating\" i]"
        ),
        WebAiService.KIMI to listOf(
            "button[aria-label=\"Stop generating\" i]",
            "[role=\"button\"][aria-label=\"Stop generating\" i]"
        )
    )

    fun ownedHosts(service: WebAiService): Set<String> = buildSet {
        URI(service.url).host?.lowercase()?.let(::add)
        addAll(ownedHostAliases[service].orEmpty())
    }

    fun forProvider(service: WebAiService): List<ProviderWebTweak> =
        providerTweaks[service].orEmpty()

    fun generationSelectors(service: WebAiService): List<String> =
        generationSelectors[service].orEmpty()
}

internal fun providerHostMatches(service: WebAiService, host: String?): Boolean {
    val normalizedHost = host?.trimEnd('.')?.lowercase() ?: return false
    return ProviderWebTweakRegistry.ownedHosts(service).any { ownedHost ->
        normalizedHost == ownedHost || normalizedHost.endsWith(".$ownedHost")
    }
}

internal fun providerUrlMatches(service: WebAiService, url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) && providerHostMatches(service, uri.host)
}

internal fun applyProviderWebTweaks(
    webView: WebView,
    service: WebAiService,
    pageUrl: String
) {
    if (!providerUrlMatches(service, pageUrl)) return

    val providerId = JSONObject.quote(service.id)
    val allowedHosts = ProviderWebTweakRegistry.ownedHosts(service)
        .joinToString(prefix = "[", postfix = "]") { JSONObject.quote(it) }

    ProviderWebTweakRegistry.forProvider(service).forEach { tweak ->
        val styleId = JSONObject.quote("llmbench-${service.id}-${tweak.id}")
        val css = JSONObject.quote(tweak.css)
        val script = """
            (() => {
                const allowedHosts = $allowedHosts;
                let currentHost = location.hostname.toLowerCase();
                if (currentHost.endsWith('.')) currentHost = currentHost.slice(0, -1);
                const owned = allowedHosts.some(host =>
                    currentHost === host || currentHost.endsWith('.' + host)
                );
                if (location.protocol !== 'https:' || !owned) return;

                const root = document.documentElement;
                if (!root) return;
                root.dataset.llmbenchProvider = $providerId;
                let style = document.getElementById($styleId);
                if (!style) {
                    style = document.createElement('style');
                    style.id = $styleId;
                    style.dataset.llmbench = 'provider-tweak';
                    (document.head || root).appendChild(style);
                }
                style.textContent = $css;
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
}
