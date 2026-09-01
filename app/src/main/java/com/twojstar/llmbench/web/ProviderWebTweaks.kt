package com.twojstar.llmbench.web

import android.net.Uri
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

    private val providerTweaks = mapOf(
        WebAiService.CLAUDE to listOf(mobileBaseline),
        WebAiService.CHATGPT to listOf(mobileBaseline),
        WebAiService.GEMINI to listOf(mobileBaseline),
        WebAiService.DEEPSEEK to listOf(mobileBaseline),
        WebAiService.KIMI to listOf(mobileBaseline)
    )

    fun forProvider(service: WebAiService): List<ProviderWebTweak> =
        providerTweaks[service].orEmpty()
}

internal fun providerHostMatches(service: WebAiService, host: String?): Boolean {
    val canonicalHost = URI(service.url).host?.lowercase() ?: return false
    val normalizedHost = host?.trimEnd('.')?.lowercase() ?: return false
    return normalizedHost == canonicalHost || normalizedHost.endsWith(".$canonicalHost")
}

internal fun applyProviderWebTweaks(
    webView: WebView,
    service: WebAiService,
    pageUrl: String
) {
    val host = Uri.parse(pageUrl).host
    if (!providerHostMatches(service, host)) return

    val providerId = JSONObject.quote(service.id)
    ProviderWebTweakRegistry.forProvider(service).forEach { tweak ->
        val styleId = JSONObject.quote("llmbench-${service.id}-${tweak.id}")
        val css = JSONObject.quote(tweak.css)
        val script = """
            (() => {
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
