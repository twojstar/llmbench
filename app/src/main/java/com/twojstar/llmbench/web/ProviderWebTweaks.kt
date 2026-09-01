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
    private const val STOP_BUTTON_TEST_ID_SELECTOR = "[data-testid=\"stop-button\"]"

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

    // Keep activity probes conservative: provider-scoped controls only, preferring locale-independent state.
    private val generationSelectors = mapOf(
        WebAiService.CHATGPT to listOf(
            STOP_BUTTON_TEST_ID_SELECTOR
        ),
        WebAiService.CLAUDE to listOf(
            STOP_BUTTON_TEST_ID_SELECTOR,
            "button[aria-label=\"Stop Response\" i]"
        ),
        WebAiService.GEMINI to listOf(
            "[data-test-id=\"send-button-container\"].stop"
        ),
        WebAiService.DEEPSEEK to listOf(
            "div[role=\"button\"]:has(svg[viewBox^=\"0 0 16\"] " +
                "path[d^=\"M2 4.88C2 3.68009 2 3.08013 2.30557 2.65954\"])"
        ),
        WebAiService.KIMI to listOf(
            "div.send-button-container.stop"
        ),
        WebAiService.VIBE to listOf(
            "button[aria-label=\"Stop generation\" i]",
            "button[aria-label=\"Stop generating\" i]"
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
