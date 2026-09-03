# LlmBench

Kotlin Multiplatform workspace for using multiple AI services from one client without turning every provider into a separate installed app.

> Status: early prototype. Android is the first client; shared domain/provider logic is moving to Kotlin Multiplatform so desktop and iOS clients can reuse the same core.

## What it is

LlmBench is intended to combine three layers:

1. **Web accounts** — persistent WebView tabs for services where the user signs in with their normal account.
2. **Free/native providers** — a shared chat/compare surface for API-compatible free providers such as OpenRouter-style endpoints.
3. **Optional AI tooling** — reusable profiles/instructions from [`trvny/.ai`](https://github.com/trvny/.ai) without copying that repository into this one.

The first prototype already contains Compose UI, persistent per-provider WebViews, a native comparison chat, profile/instruction rendering, YAML editing and a small skills/docs browser.

## Initial provider targets

### Account-backed WebViews

- ChatGPT
- Claude
- Gemini
- DeepSeek
- Kimi
- Mistral Vibe (formerly Le Chat)
- Qwen
- Microsoft Copilot
- Z.ai
- Grok
- Character.AI
- Venice
- Meta AI
- Gemini AI Studio via a browser-backed platform flow rather than embedded OAuth

AI Studio intentionally opens in the system browser because Google OAuth policy disallows authorization inside embedded user-agents such as Android WebView.

### Native / free-provider layer

The native/free-provider layer supports OpenRouter Free and AIHubMix through the shared OpenAI-compatible gateway adapter. Direct Gemini, OpenAI and Claude chats preserve bounded provider-scoped history with native message roles and stream text incrementally with cancellable requests; interrupted partial replies are never replayed. Their model pickers refresh from each gateway's live catalog and keep only zero-cost text models, with bundled models as an offline fallback. Both gateways stay outside the default All Models comparison to avoid duplicate aggregator routing. All Models dispatches only to direct providers with configured API keys and reports API failures without substituting simulated answers. Provider-specific details belong behind adapters rather than being spread through UI code.

## Provider support matrix

Full means the LlmBench-side integration is implemented; provider-side login or page changes can still affect an embedded web client. Partial calls out a known limitation rather than hiding it.

| Provider / surface | Status | Authentication | Uploads | Activity tracking | Notes |
| --- | --- | --- | --- | --- | --- |
| ChatGPT Web | Full | Provider page in WebView | Yes | Generating + unread | Persistent session, provider-scoped mobile/desktop mode |
| Claude Web | Full | Provider page in WebView | Yes | Generating + unread | Persistent session and provider-scoped tweaks |
| Gemini Chat Web | Partial | Google sign-in may be blocked in embedded user-agents | Yes | Generating + unread | The chat surface is integrated, but fresh Google OAuth inside WebView is not a supported flow |
| DeepSeek Web | Full | Provider page in WebView | Yes | Generating + unread | Persistent session |
| Kimi Web | Full | Provider page in WebView | Yes | Generating + unread | Persistent session |
| Mistral Vibe Web | Full | Provider page in WebView | Yes | Generating + unread | Tracks the locale-independent square stop control in the composer |
| Qwen Web | Partial | Qwen-owned email/password + Google/GitHub sign-in surface; embedded not verified | Provider-documented image upload; embedded not verified | Not yet | Qwen documents image upload in Qwen Chat; embedded sign-in/upload flow and provider-specific activity tracking still need verification |
| Microsoft Copilot Web | Partial | Microsoft sign-in is provider-owned | Provider-documented; embedded not verified | Not yet | Official Copilot entry aliases are recognized; embedded sign-in/upload flow and provider-specific activity tracking still need verification |
| Z.ai Web | Partial | Provider page in WebView | Page-driven | Not yet | Persistent session, file chooser and mobile/desktop mode are integrated; provider-specific activity probe still needs verification |
| Grok Web | Partial | Provider page in WebView | Provider-documented; embedded not verified | Not yet | xAI documents multi-file upload on the web; embedded upload flow and provider-specific activity tracking still need verification |
| Character.AI Web | Partial | Provider page in WebView | Provider-documented image attachments; embedded not verified | Not yet | Character.AI documents image attachments in chats; embedded sign-in/upload flow and activity probe still need verification |
| Venice Web | Partial | Provider page in WebView | Provider-documented; embedded not verified | Not yet | Venice documents file uploads in the chat input; embedded sign-in/upload flow and provider-specific activity tracking still need verification |
| Meta AI Web | Partial | Meta login surface is provider-owned | Not verified | Not yet | `alpha.meta.ai` is a verified provider-owned login alias; embedded sign-in, chat uploads and activity tracking still need verification |
| Gemini AI Studio | Browser-only | System browser | Browser-owned | Browser-owned | Kept out of WebView because Google OAuth forbids authorization in embedded user-agents |
| OpenRouter Free | Native gateway | API key | N/A | Native request state | Uses openrouter/free; excluded from default All Models compare |
| AIHubMix Free | Native gateway | API key | N/A | Native request state | Uses explicit -free models; excluded from default All Models compare |

Verification references for the newer web providers: [Microsoft Copilot entry points](https://learn.microsoft.com/microsoft-365/copilot/microsoft-365-copilot-overview), [Microsoft Copilot file upload](https://support.microsoft.com/en-us/microsoft-copilot/file-upload-in-microsoft-copilot), [Grok files FAQ](https://docs.x.ai/grok/faq), [Venice upload changelog](https://featurebase.venice.ai/changelog/veniceai-change-log-march-1st-3rd-2025), [Character.AI image attachments](https://support.character.ai/hc/en-us/articles/35409588582683-Community-Update-March-2025), [Qwen VLo image upload in Qwen Chat](https://qwen.ai/blog?id=qwen-vlo), the provider-owned [Qwen sign-in surface](https://coder.qwen.ai/auth?action=signin&from=coder), and the provider-owned [Meta AI login surface](https://alpha.meta.ai/). These verify provider capabilities or owned hosts, not Android WebView login compatibility or stable generation DOM selectors.

Google documents the embedded-user-agent restriction in its [OAuth 2.0 policies](https://developers.google.com/identity/protocols/oauth2/policies).

## WebView approach

Account sessions persist, but LlmBench must not keep every heavy provider SPA alive forever. The Android host uses a small LRU pool, pauses inactive WebViews and evicts them under memory pressure while cookies/session state remain provider-owned. Provider tweaks live in a small, auditable in-app registry: scripts are static, scoped to the matching provider host and applied after page load; remote userscript code is never fetched.

LlmBench must not scrape passwords, session cookies, OAuth tokens or other login credentials. Authentication remains between the embedded provider page and that provider.

## Relationship to `.ai`

[`trvny/.ai`](https://github.com/trvny/.ai) remains the canonical portable AI configuration core. This repository contains the multiplatform LlmBench core plus platform clients; Android is the first shipping client.

```text
trvny/.ai             reusable profiles / instructions / skills
      │
      └── optional consumption
              │
              ▼
twojstar/llmbench      shared KMP core + platform clients
```

Do not vendor a second copy of `.ai` here. If runtime integration becomes useful, consume a pinned/exported representation with an explicit boundary.

## Architecture direction

```text
shared (Kotlin Multiplatform)
├── provider/model registry
├── profile + prompt tools
└── portable domain logic

platform clients
├── Android app
│   ├── WebView host + file chooser
│   ├── provider tweaks / mobile performance
│   └── Custom Tabs / intents / Keystore
├── Desktop app (planned)
└── iOS app (planned)
```

Backends are optional, not the default. If a feature truly needs one, prefer a tiny stateless service and evaluate Cloudflare, Google Cloud, AWS or Oracle free tiers based on the actual requirement rather than choosing infrastructure first.

## Near-term roadmap

- [x] remove generated/build-machine files from version control
- [x] normalize app name, namespace and application ID to LlmBench
- [x] harden WebView security while preserving provider login compatibility
- [x] migrate portable domain/provider logic to KMP `shared`
- [x] add mobile WebView LRU/memory-pressure handling for long chats
- [x] implement reliable provider file uploads through the platform file picker
- [x] add Gemini AI Studio through a browser-backed platform flow
- [x] add AIHubMix and OpenRouter-compatible free-provider gateways
- [x] create a provider-tweak/userscript interface instead of hard-coded WebView hacks
- [x] show generating and unread response status on ChatGPT, Claude, Gemini, DeepSeek, Kimi and Vibe web tabs
- [x] add Qwen, Microsoft Copilot, Z.ai, Grok, Character.AI, Venice and Meta AI account-backed WebView entries
- [ ] verify embedded sign-in, embedded upload flows and provider-specific generation activity probes for Qwen, Copilot, Z.ai, Grok, Character.AI, Venice and Meta AI
- [x] add CI build/lint checks
- [x] document which providers work fully, partially, or block embedded login

## Development

Android targets API 35 with minSdk 26. The shared core uses Kotlin Multiplatform; platform UI remains Compose-first and moves into Compose Multiplatform only where it does not weaken native WebView, upload, authentication or secure-storage behavior.

Mobile UX is a product constraint: long chats must stay responsive, file upload must work, and provider tweaks should reduce wasted chrome/animation without breaking provider pages. Native API keys are encrypted at rest with an Android Keystore-backed AES-GCM key; credentials are never logged or exported.

## License

ISC, see [LICENSE](LICENSE).
