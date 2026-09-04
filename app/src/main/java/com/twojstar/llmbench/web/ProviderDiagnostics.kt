package com.twojstar.llmbench.web

import android.webkit.WebView
import com.twojstar.llmbench.data.model.WebAiService
import java.net.URI

internal data class ProviderDomCapabilities(
    val textareas: Int,
    val contentEditables: Int,
    val fileInputs: Int,
    val activeEditorKind: String?
)

internal sealed interface ProviderDiagnosticsProbeResult {
    data class Ready(val capabilities: ProviderDomCapabilities) : ProviderDiagnosticsProbeResult
    data object OffProvider : ProviderDiagnosticsProbeResult
    data object Failed : ProviderDiagnosticsProbeResult
}

internal data class ProviderDiagnosticsSnapshot(
    val providerName: String,
    val host: String,
    val providerOwned: Boolean,
    val webViewPackage: String,
    val siteMode: String,
    val activityTracking: String,
    val activityState: String,
    val fileChooserRequests: Int,
    val fileChooserMode: String,
    val fileChooserHost: String,
    val fileChooserAcceptTypes: String,
    val fileChooserOutcome: String,
    val domProbeSummary: String
) {
    fun safeReport(): String = buildString {
        appendLine("LlmBench provider diagnostics")
        appendLine("Provider: $providerName")
        appendLine("Host: $host")
        appendLine("Provider-owned page: ${if (providerOwned) "yes" else "no"}")
        appendLine("WebView: $webViewPackage")
        appendLine("Site mode: $siteMode")
        appendLine("Activity tracking: $activityTracking")
        appendLine("Activity state: $activityState")
        appendLine("File chooser requests: $fileChooserRequests")
        appendLine("File chooser mode: $fileChooserMode")
        appendLine("Last file chooser host: $fileChooserHost")
        appendLine("Accept types: $fileChooserAcceptTypes")
        appendLine("Last file chooser outcome: $fileChooserOutcome")
        append("DOM capability probe: $domProbeSummary")
    }
}

internal fun providerDiagnosticsProbeSummary(result: ProviderDiagnosticsProbeResult?): String = when (result) {
    null -> "Running..."
    ProviderDiagnosticsProbeResult.OffProvider -> "Off provider page"
    ProviderDiagnosticsProbeResult.Failed -> "Unavailable"
    is ProviderDiagnosticsProbeResult.Ready -> with(result.capabilities) {
        "textarea=$textareas, contenteditable=$contentEditables, file=$fileInputs, active=${activeEditorKind ?: "none"}"
    }
}

internal fun providerDiagnosticsHost(url: String?): String? = url
    ?.let { runCatching { URI(it).host }.getOrNull() }
    ?.trimEnd('.')
    ?.lowercase()

private val SAFE_ACCEPT_TYPE = Regex("^[A-Za-z0-9.+*_-]+/[A-Za-z0-9.+*_-]+$")

internal fun sanitizeProviderAcceptTypes(values: Array<out String>?): String = values
    .orEmpty()
    .asSequence()
    .flatMap { it.split(',').asSequence() }
    .map(String::trim)
    .filter { it.length <= 80 && SAFE_ACCEPT_TYPE.matches(it) }
    .distinct()
    .take(6)
    .joinToString(", ")
    .ifBlank { "*/*" }

internal fun providerDiagnosticsProbeScript(
    service: WebAiService,
    pageUrl: String
): String? {
    if (!providerUrlMatches(service, pageUrl)) return null
    return """
        (() => {
            ${providerRuntimeGuardScript(service, "'off-provider'")}
            ${documentRuntimeGuardScript(pageUrl, "'off-provider'")}

            function llmbenchVisible(node) {
                if (!node || node.hidden === true) return false;
                if (typeof node.getAttribute === 'function' &&
                    String(node.getAttribute('aria-hidden') || '').toLowerCase() === 'true') return false;
                if (typeof node.getClientRects === 'function' && node.getClientRects().length === 0) return false;
                return true;
            }

            var textareas = Array.prototype.filter.call(
                document.querySelectorAll('textarea'),
                llmbenchVisible
            ).length;
            var contentEditables = Array.prototype.filter.call(
                document.querySelectorAll('[contenteditable]'),
                function(node) {
                    return node.isContentEditable === true && llmbenchVisible(node) &&
                        !(node.parentElement && node.parentElement.isContentEditable === true);
                }
            ).length;
            var fileInputs = document.querySelectorAll('input[type="file"]').length;

            var active = document.activeElement;
            var activeKind = 'none';
            if (active) {
                var tag = String(active.tagName || '').toLowerCase();
                if (tag === 'textarea') activeKind = 'textarea';
                else if (active.isContentEditable === true) activeKind = 'contenteditable';
                else if (tag === 'input') activeKind = 'input';
            }
            return [textareas, contentEditables, fileInputs, activeKind].join('|');
        })();
    """.trimIndent()
}

internal fun parseProviderDiagnosticsProbeResult(rawResult: String?): ProviderDiagnosticsProbeResult {
    val token = rawResult?.trim()?.removeSurrounding("\"") ?: return ProviderDiagnosticsProbeResult.Failed
    if (token == "off-provider") return ProviderDiagnosticsProbeResult.OffProvider
    val parts = token.split('|')
    if (parts.size != 4) return ProviderDiagnosticsProbeResult.Failed
    val textareas = parts[0].toIntOrNull() ?: return ProviderDiagnosticsProbeResult.Failed
    val contentEditables = parts[1].toIntOrNull() ?: return ProviderDiagnosticsProbeResult.Failed
    val fileInputs = parts[2].toIntOrNull() ?: return ProviderDiagnosticsProbeResult.Failed
    if (textareas < 0 || contentEditables < 0 || fileInputs < 0) return ProviderDiagnosticsProbeResult.Failed
    val activeKind = parts[3].takeIf { it in setOf("textarea", "contenteditable", "input") }
    return ProviderDiagnosticsProbeResult.Ready(
        ProviderDomCapabilities(
            textareas = textareas,
            contentEditables = contentEditables,
            fileInputs = fileInputs,
            activeEditorKind = activeKind
        )
    )
}

internal fun probeProviderDiagnostics(
    webView: WebView,
    service: WebAiService,
    onResult: (ProviderDiagnosticsProbeResult) -> Unit
) {
    val pageUrl = webView.url
    if (pageUrl == null) {
        onResult(ProviderDiagnosticsProbeResult.OffProvider)
        return
    }
    val script = providerDiagnosticsProbeScript(service, pageUrl)
    if (script == null) {
        onResult(ProviderDiagnosticsProbeResult.OffProvider)
        return
    }
    webView.evaluateJavascript(script) { raw ->
        onResult(parseProviderDiagnosticsProbeResult(raw))
    }
}
