package com.twojstar.llmbench.web

import android.webkit.WebView
import com.twojstar.llmbench.data.model.WebAiService
import java.net.URI

internal data class ProviderWebTweak(
    val id: String,
    val css: String,
    val script: String = ""
)

internal object ProviderWebTweakRegistry {
    private const val STOP_BUTTON_TEST_ID_SELECTOR = "[data-testid=\"stop-button\"]"
    private const val VIBE_STOP_ICON_SELECTOR = "button[type=\"submit\"] svg rect"
    private const val VIBE_SEND_ICON_SELECTOR =
        "button[type=\"submit\"] svg path[d^=\"M12 18v4h4v-4h-4ZM16 14v4h4v-4h-4\"]"
    private const val CLAUDE_OVERLAY_SELECTOR =
        "[role=\"dialog\"],[role=\"menu\"],[role=\"tooltip\"],[role=\"listbox\"]," +
            "[data-radix-popper-content-wrapper]"

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

    // Mobile WebView subset of the proven Claude-Smooth userscript. Keep it static and
    // provider-scoped: no remote script loading, credential access, or page-text collection.
    private val claudeSmooth = ProviderWebTweak(
        id = "claude-smooth",
        css = """
            *, *::before, *::after {
                animation-duration: 0.001s !important;
                animation-iteration-count: 1 !important;
                transition-duration: 0.001s !important;
                scroll-behavior: auto !important;
            }
            [class*="animate-spin"], [class*="animate-pulse"] {
                animation-duration: 1s !important;
                animation-iteration-count: infinite !important;
            }
            [class*="blur"], [class*="backdrop"] {
                backdrop-filter: none !important;
                -webkit-backdrop-filter: none !important;
            }
            [class*="shadow"] {
                box-shadow: none !important;
            }
            $CLAUDE_OVERLAY_SELECTOR {
                box-shadow: 0 4px 16px rgba(0, 0, 0, 0.28) !important;
            }
            *:not($CLAUDE_OVERLAY_SELECTOR) {
                will-change: auto !important;
            }
            .llmbench-cv-turn {
                content-visibility: auto;
                contain-intrinsic-size: auto 600px;
            }
            .llmbench-cv-code {
                content-visibility: auto;
                contain-intrinsic-size: auto 300px;
            }
            .llmbench-cv-side {
                content-visibility: auto;
                contain-intrinsic-size: auto 44px;
            }
        """.trimIndent(),
        script = """
            (() => {
                const STATE_KEY = '__llmbenchClaudeSmoothV1';
                const existing = window[STATE_KEY];
                if (existing) {
                    existing.tag();
                    return;
                }

                const OVERLAY_SELECTOR = '$CLAUDE_OVERLAY_SELECTOR';
                const TURN_SELECTOR = '[data-testid^="conversation-turn"],[data-test-render-count],' +
                    'div.font-claude-message,div.font-claude-response,[data-testid="user-message"]';
                const SIDE_SELECTOR = 'nav a[href^="/chat/"],nav li,aside a[href^="/chat/"]';
                const TAIL = 2;
                const isDesktop = () => window.matchMedia('(pointer: fine)').matches && window.innerWidth >= 900;

                const tag = () => {
                    const turns = Array.from(document.querySelectorAll(TURN_SELECTOR));
                    turns.forEach((turn, index) => {
                        turn.classList.toggle('llmbench-cv-turn', index < turns.length - TAIL);
                    });

                    document.querySelectorAll('pre').forEach((code) => {
                        if (!code.closest(OVERLAY_SELECTOR)) code.classList.add('llmbench-cv-code');
                    });

                    document.querySelectorAll(SIDE_SELECTOR).forEach((item) => {
                        item.classList.toggle('llmbench-cv-side', !isDesktop());
                    });

                    document.querySelectorAll('img:not([data-llmbench-lazy])').forEach((image) => {
                        image.loading = 'lazy';
                        image.decoding = 'async';
                        image.dataset.llmbenchLazy = '1';
                    });
                };

                const idle = window.requestIdleCallback
                    ? (callback) => window.requestIdleCallback(callback, { timeout: 500 })
                    : (callback) => window.setTimeout(callback, 100);
                let queued = false;
                const schedule = () => {
                    if (queued) return;
                    queued = true;
                    idle(() => {
                        queued = false;
                        tag();
                    });
                };

                const processNode = (node) => {
                    if (!(node instanceof Element)) return;
                    const candidates = [node, ...node.querySelectorAll('pre, img, nav a[href^="/chat/"], nav li, aside a[href^="/chat/"]')];
                    candidates.forEach((candidate) => {
                        if (candidate.matches?.('pre') && !candidate.closest(OVERLAY_SELECTOR)) candidate.classList.add('llmbench-cv-code');
                        if (candidate.matches?.('img')) {
                            candidate.loading = 'lazy';
                            candidate.decoding = 'async';
                            candidate.dataset.llmbenchLazy = '1';
                        }
                        if (candidate.matches?.(SIDE_SELECTOR)) candidate.classList.toggle('llmbench-cv-side', !isDesktop());
                    });
                };

                const observer = new MutationObserver((mutations) => {
                    let turnBoundaryMayHaveChanged = false;
                    mutations.forEach((mutation) => {
                        mutation.addedNodes.forEach((node) => {
                            processNode(node);
                            if (node instanceof Element && (node.matches?.(TURN_SELECTOR) || node.querySelector?.(TURN_SELECTOR))) {
                                turnBoundaryMayHaveChanged = true;
                            }
                        });
                        mutation.removedNodes.forEach((node) => {
                            if (node instanceof Element && (node.matches?.(TURN_SELECTOR) || node.querySelector?.(TURN_SELECTOR))) {
                                turnBoundaryMayHaveChanged = true;
                            }
                        });
                    });
                    if (turnBoundaryMayHaveChanged) schedule();
                });
                const start = () => {
                    tag();
                    observer.observe(document.body, { childList: true, subtree: true });
                    window.addEventListener('resize', schedule, { passive: true });
                };
                window[STATE_KEY] = { tag, observer };

                if (document.body) {
                    start();
                } else {
                    const boot = new MutationObserver(() => {
                        if (!document.body) return;
                        boot.disconnect();
                        start();
                    });
                    boot.observe(document.documentElement, { childList: true });
                }
            })();
        """.trimIndent()
    )

    // Canonical hosts come from WebAiService.url. Add only verified provider-owned aliases here.
    private val ownedHostAliases = mapOf(
        WebAiService.KIMI to setOf("kimi.com"),
        WebAiService.QWEN to setOf("qwen.ai"),
        WebAiService.COPILOT to setOf(
            "copilot.com",
            "copilot.ai",
            "copilot.cloud.microsoft",
            "m365.cloud.microsoft",
            "m365copilot.com"
        ),
        WebAiService.META_AI to setOf("alpha.meta.ai")
    )

    private val topLevelNavigationAuthHosts = mapOf(
        WebAiService.QWEN to setOf("accounts.google.com", "github.com"),
        WebAiService.COPILOT to setOf("login.live.com", "login.microsoftonline.com")
    )

    private val providerTweaks = WebAiService.entries.associateWith { service ->
        when (service) {
            WebAiService.CLAUDE -> listOf(mobileBaseline, claudeSmooth)
            else -> listOf(mobileBaseline)
        }
    }

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
            "div[role=\"button\"] svg[viewBox^=\"0 0 16\"] " +
                "path[d^=\"M2 4.88C2 3.68009 2 3.08013 2.30557 2.65954\"]"
        ),
        WebAiService.KIMI to listOf(
            "div.send-button-container.stop"
        ),
        // Vibe documents its running-task control as a black square. The composer keeps a
        // submit button while its SVG switches to a rect-based stop icon, avoiding localized labels.
        WebAiService.VIBE to listOf(
            VIBE_STOP_ICON_SELECTOR
        )
    )

    fun ownedHosts(service: WebAiService): Set<String> = buildSet {
        URI(service.url).host?.lowercase()?.let(::add)
        addAll(ownedHostAliases[service].orEmpty())
    }

    fun hasVerifiedTopLevelNavigationPolicy(service: WebAiService): Boolean =
        service in topLevelNavigationAuthHosts

    fun topLevelNavigationAuthHosts(service: WebAiService): Set<String> =
        topLevelNavigationAuthHosts[service].orEmpty()

    fun forProvider(service: WebAiService): List<ProviderWebTweak> =
        providerTweaks[service].orEmpty()

    private val generationIdleSelectors = mapOf(
        WebAiService.VIBE to listOf(VIBE_SEND_ICON_SELECTOR)
    )

    fun generationSelectors(service: WebAiService): List<String> =
        generationSelectors[service].orEmpty()

    fun generationIdleSelectors(service: WebAiService): List<String> =
        generationIdleSelectors[service].orEmpty()
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

internal fun providerNavigationUrlMatches(service: WebAiService, url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (providerHostMatches(service, uri.host)) return true

    val normalizedHost = uri.host?.trimEnd('.')?.lowercase() ?: return false
    return normalizedHost in ProviderWebTweakRegistry.topLevelNavigationAuthHosts(service)
}

internal fun shouldLoadHttpsInProviderWebView(
    service: WebAiService,
    url: String
): Boolean {
    if (!ProviderWebTweakRegistry.hasVerifiedTopLevelNavigationPolicy(service)) return true
    return providerNavigationUrlMatches(service, url)
}

internal fun applyProviderWebTweaks(
    webView: WebView,
    service: WebAiService,
    pageUrl: String
) {
    if (!providerUrlMatches(service, pageUrl)) return

    val providerId = javascriptStringLiteral(service.id)
    ProviderWebTweakRegistry.forProvider(service).forEach { tweak ->
        val styleId = javascriptStringLiteral("llmbench-${service.id}-${tweak.id}")
        val css = javascriptStringLiteral(tweak.css)
        val script = """
            (() => {
                ${providerRuntimeGuardScript(service, "undefined")}

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
                ${tweak.script}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
}
