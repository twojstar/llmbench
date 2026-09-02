package com.twojstar.llmbench.web

import android.webkit.WebView
import com.twojstar.llmbench.data.model.WebAiService
import com.twojstar.llmbench.data.model.WebChatGenerationObservation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val GENERATION_TRACKER_KEY = "__llmbenchGenerationTracker"

private fun generationActivityScript(
    selectors: List<String>,
    consumeCompletion: Boolean,
    selected: Boolean? = null
): String {
    val encodedSelectors = Json.encodeToString(selectors)
    val selectedState = selected?.toString() ?: "null"
    return """
        (() => {
            const selectors = $encodedSelectors;
            const trackerKey = "$GENERATION_TRACKER_KEY";
            const signature = JSON.stringify(selectors);
            const selectedState = $selectedState;
            const isVisible = element => {
                const style = getComputedStyle(element);
                return !element.disabled &&
                    style.display !== 'none' &&
                    style.visibility !== 'hidden' &&
                    element.getClientRects().length > 0;
            };
            const generationControls = () => selectors.flatMap(selector =>
                Array.from(document.querySelectorAll(selector)).filter(isVisible)
            );
            const isGenerating = () => generationControls().length > 0;

            let tracker = window[trackerKey];
            if (!tracker || tracker.signature !== signature) {
                if (tracker && tracker.observer) tracker.observer.disconnect();
                tracker = {
                    signature,
                    active: isGenerating(),
                    completed: false,
                    completedWhileSelected: false,
                    selected: selectedState === null ? false : selectedState,
                    controls: generationControls(),
                    observer: null
                };
                window[trackerKey] = tracker;
            }
            if (selectedState !== null) tracker.selected = selectedState;

            const refreshActivity = () => {
                const controls = generationControls();
                const nextActive = controls.length > 0;
                if (tracker.active && !nextActive) {
                    tracker.completedWhileSelected = tracker.completed
                        ? tracker.completedWhileSelected && tracker.selected
                        : tracker.selected;
                    tracker.completed = true;
                }
                tracker.active = nextActive;
                tracker.controls = controls;
            };

            if (!tracker.observer && document.documentElement) {
                const nodeTouchesGenerationControl = node => {
                    if (!(node instanceof Element)) return false;
                    return selectors.some(selector =>
                        node.matches(selector) ||
                        node.querySelector(selector) !== null ||
                        node.closest(selector) !== null
                    );
                };
                const mutationTouchesGenerationControl = mutation =>
                    (mutation.type === 'attributes' && tracker.controls.includes(mutation.target)) ||
                    nodeTouchesGenerationControl(mutation.target) ||
                    Array.from(mutation.addedNodes).some(nodeTouchesGenerationControl) ||
                    Array.from(mutation.removedNodes).some(nodeTouchesGenerationControl);
                const update = mutations => {
                    if (!mutations.some(mutationTouchesGenerationControl)) return;
                    refreshActivity();
                };
                tracker.observer = new MutationObserver(update);
                tracker.observer.observe(document.documentElement, {
                    subtree: true,
                    childList: true,
                    attributes: true,
                    attributeFilter: [
                        'class', 'disabled', 'aria-disabled', 'aria-label',
                        'data-testid', 'data-test-id', 'hidden', 'style', 'd'
                    ]
                });
            }

            refreshActivity();
            if (tracker.active) return 1;
            if (tracker.completed) {
                const completionResult = tracker.completedWhileSelected ? 3 : 2;
                if ($consumeCompletion) {
                    tracker.completed = false;
                    tracker.completedWhileSelected = false;
                }
                return completionResult;
            }
            return 0;
        })();
    """.trimIndent()
}

/** Installs an in-page observer so short generation cycles cannot disappear between native polls. */
internal fun installProviderGenerationTracker(
    webView: WebView,
    service: WebAiService,
    isSelected: Boolean
) {
    val pageUrl = webView.url ?: return
    if (!providerUrlMatches(service, pageUrl)) return
    val selectors = ProviderWebTweakRegistry.generationSelectors(service)
    if (selectors.isEmpty()) return
    webView.evaluateJavascript(
        generationActivityScript(selectors, consumeCompletion = false, selected = isSelected),
        null
    )
}

/** Keeps the in-page completion latch aligned with the native provider selection. */
internal fun setProviderGenerationTrackerSelected(
    webView: WebView,
    service: WebAiService,
    isSelected: Boolean
) {
    val pageUrl = webView.url ?: return
    if (!providerUrlMatches(service, pageUrl)) return
    webView.evaluateJavascript(
        """
            (() => {
                const tracker = window["$GENERATION_TRACKER_KEY"];
                if (tracker) {
                    tracker.selected = $isSelected;
                    if ($isSelected) {
                        tracker.completed = false;
                        tracker.completedWhileSelected = false;
                    }
                }
            })();
        """.trimIndent(),
        null
    )
}

/**
 * Reports provider activity without reading message text, prompts, credentials, or conversation content.
 * A provider-scoped MutationObserver remembers completed generation between native polling intervals.
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

    webView.evaluateJavascript(generationActivityScript(selectors, consumeCompletion = true)) { rawResult ->
        onResult(
            when (rawResult?.trim()) {
                "1" -> WebChatGenerationObservation.GENERATING
                "2" -> WebChatGenerationObservation.COMPLETED
                "3" -> WebChatGenerationObservation.COMPLETED_WHILE_SELECTED
                "0" -> WebChatGenerationObservation.IDLE
                else -> WebChatGenerationObservation.UNKNOWN
            }
        )
    }
}
