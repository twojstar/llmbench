package com.twojstar.llmbench.web

import android.webkit.WebView
import com.twojstar.llmbench.data.model.WebAiService

internal enum class StudioPromptApplyResult {
    INSERTED,
    NO_EDITOR,
    NOT_EMPTY,
    REJECTED,
    OFF_PROVIDER,
    FAILED
}

private const val STUDIO_PROMPT_TRACKER_KEY = "__llmbenchStudioPromptTarget"
private const val TRACKED_EDITOR_MAX_AGE_MS = 15_000

internal fun javascriptStringLiteral(value: String): String = buildString(value.length + 2) {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> if (char.code < 0x20) {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
    append('"')
}

private fun providerRuntimeGuardScript(service: WebAiService, offProviderResult: String): String {
    val allowedHosts = ProviderWebTweakRegistry.ownedHosts(service)
        .joinToString(prefix = "[", postfix = "]") { javascriptStringLiteral(it) }
    return """
        var allowedHosts = $allowedHosts;
        var currentHost = String(location.hostname || '').toLowerCase();
        if (currentHost.slice(-1) === '.') currentHost = currentHost.slice(0, -1);
        var owned = allowedHosts.some(function(host) {
            return currentHost === host || currentHost.slice(-(host.length + 1)) === '.' + host;
        });
        if (String(location.protocol || '').toLowerCase() !== 'https:' || !owned) {
            return $offProviderResult;
        }
    """.trimIndent()
}

private fun documentRuntimeGuardScript(pageUrl: String, offProviderResult: String): String {
    val expectedUrl = javascriptStringLiteral(pageUrl.substringBefore('#'))
    return """
        function llmbenchDocumentUrl(value) {
            return String(value || '').split('#')[0];
        }
        if (llmbenchDocumentUrl(location.href) !== $expectedUrl) {
            return $offProviderResult;
        }
    """.trimIndent()
}

private fun editableFinderScript(): String = """
    function llmbenchIsEligibleEditor(node) {
        if (!node || node.hidden === true || node.disabled === true || node.readOnly === true) return false;
        if (typeof node.getAttribute === 'function') {
            if (String(node.getAttribute('aria-hidden') || '').toLowerCase() === 'true') return false;
            if (String(node.getAttribute('aria-disabled') || '').toLowerCase() === 'true') return false;
            if (String(node.getAttribute('aria-readonly') || '').toLowerCase() === 'true') return false;
            if (String(node.getAttribute('role') || '').toLowerCase() === 'searchbox') return false;
        }
        if (typeof node.getClientRects === 'function' && node.getClientRects().length === 0) return false;
        return true;
    }

    function llmbenchFindEditable(node) {
        while (node && node !== document.documentElement) {
            var tag = String(node.tagName || '').toLowerCase();
            if (tag === 'input') return null;
            if (tag === 'textarea') return llmbenchIsEligibleEditor(node) ? node : null;
            if (node.isContentEditable === true) {
                var root = node;
                while (root.parentElement && root.parentElement.isContentEditable === true) {
                    root = root.parentElement;
                }
                return llmbenchIsEligibleEditor(root) ? root : null;
            }
            node = node.parentElement;
        }
        return null;
    }
""".trimIndent()

internal fun studioPromptTargetTrackerScript(
    service: WebAiService,
    pageUrl: String
): String? {
    if (!providerUrlMatches(service, pageUrl)) return null
    val providerId = javascriptStringLiteral(service.id)
    return """
        (() => {
            ${providerRuntimeGuardScript(service, "false")}
            ${documentRuntimeGuardScript(pageUrl, "false")}
            ${editableFinderScript()}
            var key = ${javascriptStringLiteral(STUDIO_PROMPT_TRACKER_KEY)};
            var state = window[key] || { target: null, focusedAt: 0, installed: false, providerId: $providerId };
            window[key] = state;
            state.providerId = $providerId;
            function remember(node) {
                var target = llmbenchFindEditable(node);
                if (target) {
                    state.target = target;
                    state.focusedAt = Date.now();
                }
            }
            remember(document.activeElement);
            if (!state.installed) {
                document.addEventListener('focusin', function(event) {
                    remember(event.target);
                }, true);
                document.addEventListener('focusout', function(event) {
                    var target = llmbenchFindEditable(event.target);
                    if (target && target === state.target) state.focusedAt = Date.now();
                }, true);
                document.addEventListener('input', function(event) {
                    var target = llmbenchFindEditable(event.target);
                    if (target && target === state.target) state.focusedAt = Date.now();
                }, true);
                state.installed = true;
            }
            return true;
        })();
    """.trimIndent()
}

internal fun studioPromptApplyScript(
    service: WebAiService,
    pageUrl: String,
    prompt: String
): String? {
    if (!providerUrlMatches(service, pageUrl)) return null
    val text = javascriptStringLiteral(prompt)
    return """
        (() => {
            ${providerRuntimeGuardScript(service, "'off-provider'")}
            ${documentRuntimeGuardScript(pageUrl, "'off-provider'")}
            ${editableFinderScript()}
            var key = ${javascriptStringLiteral(STUDIO_PROMPT_TRACKER_KEY)};
            var state = window[key];
            var target = llmbenchFindEditable(document.activeElement);
            if (!target && state && state.target && state.target.isConnected !== false) {
                var age = Date.now() - Number(state.focusedAt || 0);
                if (age >= 0 && age <= $TRACKED_EDITOR_MAX_AGE_MS) {
                    target = llmbenchFindEditable(state.target);
                }
            }
            if (!target || target.isConnected === false) return 'no-editor';

            var tag = String(target.tagName || '').toLowerCase();
            var current = tag === 'input' || tag === 'textarea'
                ? String(target.value || '')
                : String(target.innerText || target.textContent || '');
            if (current.trim().length > 0) return 'not-empty';
            if (tag !== 'textarea' && typeof target.querySelector === 'function') {
                var nonTextContent = target.querySelector(
                    'img,video,audio,canvas,svg,iframe,object,embed,input,button,[contenteditable="false"]'
                );
                if (nonTextContent) return 'not-empty';
            }

            if (typeof target.focus === 'function') target.focus();
            var beforeInput = typeof InputEvent === 'function'
                ? new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: $text })
                : new Event('beforeinput', { bubbles: true, cancelable: true });
            if (typeof target.dispatchEvent === 'function' && !target.dispatchEvent(beforeInput)) {
                return 'rejected';
            }

            if (tag === 'input' || tag === 'textarea') {
                var prototype = Object.getPrototypeOf(target);
                var descriptor = prototype && Object.getOwnPropertyDescriptor(prototype, 'value');
                if (descriptor && typeof descriptor.set === 'function') {
                    descriptor.set.call(target, $text);
                } else {
                    target.value = $text;
                }
            } else {
                target.textContent = $text;
            }

            if (typeof target.dispatchEvent === 'function') {
                var inputEvent = typeof InputEvent === 'function'
                    ? new InputEvent('input', { bubbles: true, inputType: 'insertText', data: $text })
                    : new Event('input', { bubbles: true });
                target.dispatchEvent(inputEvent);
                target.dispatchEvent(new Event('change', { bubbles: true }));
            }
            return 'inserted';
        })();
    """.trimIndent()
}

internal fun installStudioPromptTargetTracker(
    webView: WebView,
    service: WebAiService,
    pageUrl: String
) {
    val script = studioPromptTargetTrackerScript(service, pageUrl) ?: return
    webView.evaluateJavascript(script, null)
}

internal fun applyStudioPromptToFocusedEditor(
    webView: WebView,
    service: WebAiService,
    prompt: String,
    onResult: (StudioPromptApplyResult) -> Unit
) {
    val pageUrl = webView.url
    if (pageUrl == null) {
        onResult(StudioPromptApplyResult.OFF_PROVIDER)
        return
    }
    val script = studioPromptApplyScript(service, pageUrl, prompt)
    if (script == null) {
        onResult(StudioPromptApplyResult.OFF_PROVIDER)
        return
    }
    webView.evaluateJavascript(script) { raw ->
        onResult(parseStudioPromptApplyResult(raw))
    }
}

internal fun parseStudioPromptApplyResult(rawResult: String?): StudioPromptApplyResult =
    when (rawResult?.trim()?.removeSurrounding("\"")) {
        "inserted" -> StudioPromptApplyResult.INSERTED
        "no-editor" -> StudioPromptApplyResult.NO_EDITOR
        "not-empty" -> StudioPromptApplyResult.NOT_EMPTY
        "rejected" -> StudioPromptApplyResult.REJECTED
        "off-provider" -> StudioPromptApplyResult.OFF_PROVIDER
        else -> StudioPromptApplyResult.FAILED
    }
