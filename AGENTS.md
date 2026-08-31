# AGENTS.md

LlmBench is an Android application. Keep application work here and keep reusable cross-project AI configuration in `trvny/.ai`.

## Repository boundaries

- Do not copy or fork the `.ai` core into this repository.
- If LlmBench consumes `.ai`, use an explicit adapter/import boundary and keep `.ai` canonical upstream.
- Prefer extending the existing Android structure over creating parallel implementations.

## Android conventions

- Preserve the current Kotlin + Jetpack Compose stack unless a task explicitly changes it.
- Keep provider-specific behavior behind provider adapters/registries rather than scattering hostname checks through UI code.
- Keep WebView tweaks small, reviewable and provider-scoped.
- Never intercept, persist, export or log provider passwords, session cookies, OAuth tokens or equivalent account credentials.
- Treat embedded account pages as untrusted web content and minimize WebView privileges needed for compatibility.

## Workflow

- Inspect current main, open PRs and recent changes before overlapping work.
- Keep one logical change per pull request.
- Use `LlmBench` as the app/project name and `com.twojstar.llmbench` as the canonical Android namespace/application ID.
- Keep documentation short and update it when provider support or security assumptions change.
