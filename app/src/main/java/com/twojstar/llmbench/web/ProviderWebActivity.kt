package com.twojstar.llmbench.web

import android.webkit.WebView
import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.data.model.WebChatGenerationObservation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private fun generationActivityProbe(selectors: List<String>): String {
    val encodedSelectors = Json.encodeToString(selectors)
    return """
        (() => {
            const selectors = $encodedSelectors;
            const isVisible = element => {
                const style = getComputedStyle(element);
                return !element.disabled &&
                    style.display !== 'none' &&
                    style.visibility !== 'hidden' &&
                    element.getClientRects().length > 0;
            };
            return selectors.some(selector =>
                Array.from(document.querySelectorAll(selector)).some(isVisible)
            );
        })();
    """.trimIndent()
}

/**
 * Reports only whether a provider-owned page exposes a provider-scoped generation control.
 * It intentionally does not read message text, prompts, credentials, or conversation content.
 */
internal fun probeProviderGenerationActivity(
    webView: WebView,
    service: WebAiService,
    onResult: (WebChatGenerationObservation) -> Unit
) {
    val pageUrl = webView.url
    if (pageUrl == null || !providerUrlMatches(service, pageUrl)) {
        onResult(WebChatGenerationObservation.UNKNOWN)
        return
    }

    val selectors = ProviderWebTweakRegistry.generationSelectors(service)
    if (selectors.isEmpty()) {
        onResult(WebChatGenerationObservation.UNKNOWN)
        return
    }

    webView.evaluateJavascript(generationActivityProbe(selectors)) { rawResult ->
        onResult(
            when (rawResult?.trim()) {
                "true" -> WebChatGenerationObservation.GENERATING
                "false" -> WebChatGenerationObservation.IDLE
                else -> WebChatGenerationObservation.UNKNOWN
            }
        )
    }
}
