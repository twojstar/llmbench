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
- Gemini AI Studio via a browser-backed platform flow rather than embedded OAuth

### Native / free-provider layer

Planned adapters include OpenRouter-compatible services and other free endpoints such as AIHubMix where their terms and APIs allow it. Provider-specific details belong behind adapters rather than being spread through UI code.

## WebView approach

Account sessions persist, but LlmBench must not keep every heavy provider SPA alive forever. The Android host will use a small LRU pool, pause inactive WebViews and evict them under memory pressure while cookies/session state remain provider-owned. Provider tweaks live in a small, auditable userscript/CSS adapter layer for mobile layout fixes, reduced animation/chrome and provider-specific navigation.

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
- [ ] add Gemini AI Studio through a browser-backed platform flow
- [ ] add AIHubMix and OpenRouter-compatible free-provider adapters
- [ ] create a provider-tweak/userscript interface instead of hard-coded WebView hacks
- [x] add CI build/lint checks
- [ ] document which providers work fully, partially, or block embedded login

## Development

Android targets API 35 with minSdk 26. The shared core uses Kotlin Multiplatform; platform UI remains Compose-first and moves into Compose Multiplatform only where it does not weaken native WebView, upload, authentication or secure-storage behavior.

Mobile UX is a product constraint: long chats must stay responsive, file upload must work, and provider tweaks should reduce wasted chrome/animation without breaking provider pages.

## License

ISC, see [LICENSE](LICENSE).
