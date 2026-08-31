# AGENTS.md

LlmBench is a Kotlin Multiplatform project with Android as the first shipping client.

## Repository boundaries

- If LlmBench consumes `.ai`, use an explicit adapter/import boundary and keep `.ai` canonical upstream.
- Prefer extending the existing module structure over creating parallel implementations.

## Multiplatform conventions

- Put portable provider/model/profile/domain logic in `shared`; keep platform APIs in platform modules/source sets.
- Prefer Compose Multiplatform for reusable UI, but do not abstract away native WebView, file picker, authentication, secure storage or lifecycle behavior.
- Keep provider-specific behavior behind provider adapters/registries rather than scattering hostname checks through UI code.
- Keep WebView tweaks small, reviewable and provider-scoped.
- Treat long-chat mobile performance as a core requirement: bound live WebViews, pause inactive views and evict under memory pressure without clearing provider sessions.
- File upload is a core provider capability: preserve accept types, multiple selection, cancellation and platform picker lifecycle.
- Never intercept, persist, export or log provider passwords, session cookies, OAuth tokens or equivalent account credentials.
- Treat embedded account pages as untrusted web content and minimize WebView privileges needed for compatibility.

## Workflow

- Inspect current main, open PRs and recent changes before overlapping work.
- Keep one logical change per pull request.
- Use `LlmBench` as the project name and `com.twojstar.llmbench` as the canonical Android application ID; shared modules use distinct namespaces under that root.
- Keep documentation short and update it when provider support or security assumptions change.
