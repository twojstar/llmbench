# LlmBench

Android workspace for using multiple AI services from one app without turning every provider into a separate installed client.

> Status: early prototype. The Android code was bootstrapped in Gemini AI Studio and is now normalized under the LlmBench project identity.

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
- Gemini AI Studio, if its authentication and editor remain usable inside Android WebView

### Native / free-provider layer

Planned adapters include OpenRouter-compatible services and other free endpoints such as AIHubMix where their terms and APIs allow it. Provider-specific details belong behind adapters rather than being spread through UI code.

## WebView approach

Each web provider gets a persistent WebView instance so switching tabs does not constantly destroy sessions or drafts. Provider tweaks can later live in a small, auditable userscript-style adapter layer for things such as mobile layout fixes, hiding redundant chrome, or provider-specific navigation.

LlmBench must not scrape passwords, session cookies, OAuth tokens or other login credentials. Authentication remains between the embedded provider page and that provider.

## Relationship to `.ai`

[`trvny/.ai`](https://github.com/trvny/.ai) remains the canonical portable AI configuration core. This repository is the Android client.

```text
trvny/.ai             reusable profiles / instructions / skills
      │
      └── optional consumption
              │
              ▼
twojstar/llmbench      LlmBench Android application
```

Do not vendor a second copy of `.ai` here. If runtime integration becomes useful, consume a pinned/exported representation with an explicit boundary.

## Architecture direction

```text
Compose UI
├── Web workspace
│   ├── provider registry
│   ├── persistent WebViews
│   └── provider tweaks
├── Native compare hub
│   ├── provider adapters
│   └── shared chat models
└── Profile tools
    └── optional .ai-compatible import/rendering
```

Backends are optional, not the default. If a feature truly needs one, prefer a tiny stateless service and evaluate Cloudflare, Google Cloud, AWS or Oracle free tiers based on the actual requirement rather than choosing infrastructure first.

## Near-term roadmap

- [ ] remove generated/build-machine files from version control
- [x] normalize app name, namespace and application ID to LlmBench
- [ ] harden WebView security while preserving provider login compatibility
- [ ] add Gemini AI Studio as a provider target and test its WebView behavior
- [ ] add AIHubMix and OpenRouter-compatible free-provider adapters
- [ ] create a provider-tweak/userscript interface instead of hard-coded WebView hacks
- [ ] add CI build/lint checks
- [ ] document which providers work fully, partially, or block embedded login

## Development

The project currently targets Android API 35 with minSdk 26 and uses Kotlin, Jetpack Compose, OkHttp and kotlinx.serialization.

The repository is still an early AI Studio export, so expect cleanup before treating the build layout or package names as stable.

## License

ISC, see [LICENSE](LICENSE).
