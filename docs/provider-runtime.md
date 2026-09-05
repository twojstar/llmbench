# Provider runtime notes

This document tracks the native/API chat runtime, provider-specific constraints and API-level TODOs. The WebView provider matrix stays in the main README.

## Current shape

Portable provider/model/profile data lives in `shared`. Android currently owns HTTP transport in `AiChatService`, while `StudioViewModel` coordinates chat state and rendered profile instructions.

| Provider | API shape | System instructions | Streaming | Conversation state today |
| --- | --- | --- | --- | --- |
| Gemini | `generateContent` REST | `systemInstruction` | SSE | bounded visible text replay |
| OpenAI | Responses API | `instructions` | Responses SSE | bounded visible text replay, `store=false` |
| Claude | Messages API | top-level `system` | SSE | bounded visible text replay |
| DeepSeek | OpenAI-compatible chat completions | `system` message | SSE | bounded visible text replay |
| Kimi | OpenAI-compatible chat completions | `system` message | SSE | bounded visible text replay |
| OpenRouter | OpenAI-compatible chat completions | `system` message | SSE | bounded visible text replay |
| AIHubMix | OpenAI-compatible chat completions | `system` message | SSE | bounded visible text replay |

All live request paths are coroutine-cancellable. Cancelling generation cancels the underlying OkHttp call. SSE parsing is shared, accepts multi-line `data:` events, propagates provider error events, and stops at provider completion or `[DONE]` where applicable.

## History invariants

`buildBoundedProviderTextTurns` is the source of truth for replaying text history. Keep these properties intact:

- append the current user prompt exactly once;
- replay only successful, complete, non-simulated assistant responses from the selected provider;
- replay a prior user prompt only when that provider produced a replayable response to that turn;
- bound history by both turn count and approximate character budget;
- reserve history budget for the current prompt and system instruction.

This isolation matters in compare/switch-provider flows: a provider should not receive user turns it never answered unless that behavior is explicitly redesigned.

## Prompt Studio boundary

Rendered Studio instructions can be attached to native chat as the provider's native system/developer instruction. They are not duplicated into normal user history.

For account-backed WebViews, `StudioPromptBridge` applies the rendered prompt only to a recently focused, empty eligible composer. It refuses non-empty editors and off-provider pages. Clipboard fallback remains a UI escape hatch rather than a second injection implementation.

Do not add one universal set of sampling/reasoning parameters just because the UI can expose sliders. Provider defaults and model families differ. Controls should be capability-driven and omitted when the provider recommends its default.

Example: Gemini 3 documentation recommends keeping temperature at the default `1.0`; lowering it can degrade reasoning behavior. A global temperature override would therefore be actively harmful for some current models.

## Reasoning/state gaps

### Gemini

The REST path currently reconstructs history from visible text only. Gemini thinking models can return `thoughtSignature` metadata that should be passed back unchanged in subsequent stateless turns. Dropping it can reduce reasoning continuity, and function-calling flows may reject requests without required signatures.

Potential directions:

- keep `generateContent` stateless but persist opaque response parts/signatures alongside visible text; or
- evaluate Gemini Interactions stateful mode, with an explicit privacy/storage decision before switching transports.

Do not expose or rewrite the signature content. Treat it as opaque provider state.

### OpenAI

The Responses API is currently called with `store=false`, while LlmBench replays only visible user/assistant text. Current OpenAI APIs support stateless reasoning continuity by requesting `reasoning.encrypted_content` and replaying the returned output items. A stateful alternative is `previous_response_id`.

If stateful Responses are evaluated later, remember that `instructions` are not automatically inherited through `previous_response_id`; the active Studio instruction still needs to be supplied deliberately.

### Claude

The current Claude path does not enable or retain thinking blocks. If adaptive/extended thinking controls are added, preserve provider-returned `thinking` and `redacted_thinking` blocks exactly where the API requires them, especially around tool-use turns. Do not flatten them into visible text history.

Thinking configuration is model-family-specific. Prefer capability metadata over model-name conditionals scattered through UI code.

### Gateways

OpenRouter and other gateways may expose metadata that identifies the model actually used. LlmBench currently labels responses with the requested model/route. Capture a returned model identifier when available so router aliases such as `openrouter/free` do not hide the actual responder.

## API/runtime TODO

- [ ] Add a provider capability model for reasoning controls, response metadata and state strategy.
- [ ] Preserve Gemini thought signatures or migrate that path to a stateful API with explicit storage semantics.
- [ ] Preserve OpenAI stateless reasoning items while keeping `store=false`, or document a deliberate move to stateful Responses.
- [ ] Add Claude thinking/effort only through model-aware capabilities; preserve opaque thinking blocks when enabled.
- [ ] Record and display the actual routed model returned by OpenRouter when available.
- [ ] Replace hard-coded output limits such as Claude's current `max_tokens=2048` with model/provider-aware limits.
- [ ] Add a provider-aware Prompt Studio preview showing effective instruction placement, history size and enabled controls without exposing API keys or hidden reasoning state.
- [ ] Parse provider usage/token metadata so comparisons can include latency and token/cost information when the API returns it.
- [ ] Keep transport/SSE/history tests JVM-testable; device testing should be required only for Android/WebView behavior.

## References

- Google Gemini thought signatures: https://ai.google.dev/gemini-api/docs/generate-content/thought-signatures
- Google Gemini 3 guidance: https://ai.google.dev/gemini-api/docs/generate-content/gemini-3
- OpenAI Responses API: https://developers.openai.com/api/reference/resources/responses/methods/create
- OpenAI model guidance: https://developers.openai.com/api/docs/guides/latest-model
- Anthropic prompting/thinking guidance: https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/prompt-templates-and-variables
- OpenRouter routing/fallbacks: https://openrouter.ai/docs/guides/routing/model-fallbacks

Keep this file focused on provider/runtime behavior. Product-facing provider status belongs in `README.md`; implementation details should live next to code when they become stable enough to stop being TODOs.