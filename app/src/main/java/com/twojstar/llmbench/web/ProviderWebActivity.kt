package com.twojstar.llmbench.web

import android.webkit.WebView
import com.twojstar.llmbench.data.model.WebAiService

internal enum class WebChatActivityStatus {
    IDLE,
    GENERATING,
    UNREAD
}

internal fun nextWebChatActivityStatus(
    previous: WebChatActivityStatus,
    isGenerating: Boolean,
    isSelected: Boolean
): WebChatActivityStatus = when {
    isGenerating -> WebChatActivityStatus.GENERATING
    previous == WebChatActivityStatus.GENERATING && !isSelected -> WebChatActivityStatus.UNREAD
    previous == WebChatActivityStatus.UNREAD && !isSelected -> WebChatActivityStatus.UNREAD
    else -> WebChatActivityStatus.IDLE
}

internal fun markWebChatActivityRead(status: WebChatActivityStatus): WebChatActivityStatus =
    if (status == WebChatActivityStatus.UNREAD) WebChatActivityStatus.IDLE else status

private val generationActivityProbe = """
    (() => {
        const selectors = [
            'button[data-testid*="stop" i]',
            '[role="button"][data-testid*="stop" i]',
            'button[aria-label*="stop" i]',
            '[role="button"][aria-label*="stop" i]',
            'button[title*="stop" i]',
            '[role="button"][title*="stop" i]'
        ];
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

/**
 * Reports only whether a provider-owned page exposes a visible stop-generation control.
 * It intentionally does not read message text, prompts, credentials, or conversation content.
 */
internal fun probeProviderGenerationActivity(
    webView: WebView,
    service: WebAiService,
    onResult: (Boolean) -> Unit
) {
    val pageUrl = webView.url ?: return
    if (!providerUrlMatches(service, pageUrl)) return

    webView.evaluateJavascript(generationActivityProbe) { rawResult ->
        when (rawResult?.trim()) {
            "true" -> onResult(true)
            "false" -> onResult(false)
        }
    }
}
