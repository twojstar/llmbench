# Product ideas

This is a durable backlog of product directions worth exploring in LlmBench.
The ideas are inspired by observed workflows in other AI/document apps and by tools already maintained in `twojstar/twojstar`; they are not implementation copies.

## Markdown workspace / prompt vault

- Treat local `.md` files as editable source-of-truth assets, not one-shot attachments.
- Create, open, edit, save and Save As Markdown from inside LlmBench.
- Keep recent/search/favorite or pinned prompt files for fast reuse.
- Let one Markdown file act as a prompt, system instructions, reusable context or a skill definition.
- Support Android document-picker access without silently replacing the selected file with a private app copy.
- Add file preview plus open externally / share flows where useful.
- Accept Android text-sharing / `PROCESS_TEXT` style entry points for quickly turning selected text into a prompt or note.

## Local-first ownership and optional cloud

- LlmBench must have no account wall. First launch and normal local use must not require creating an LlmBench identity, accepting cloud storage or enabling sync.
- Require authentication only where an external provider itself requires it. Signing into ChatGPT/Claude/etc. or adding an API key must not silently enroll the user in a separate LlmBench account.
- Keep locally owned chats, projects, prompts, files, skills, built-in tools, settings and history usable without LlmBench-hosted infrastructure.
- Local export, backup and restore are first-class features, not fallback paths. Prefer ordinary portable files/archives with a documented manifest/version so users can keep copies wherever they choose.
- Do not silently upload app data for safekeeping. Cloud sync, remote backup, cross-device restore and hosted integrations are explicit opt-ins with clear data scope and a way to turn them off again.
- Do not degrade, delay or nag-gate local features because cloud backup is disabled. If a user chooses local-only storage and loses the device without making a backup, that is an accepted consequence of the choice rather than a reason to force account creation.
- Any future cloud feature should synchronize local sources of truth rather than replace them. Signing out or discontinuing the service must leave the user's local data usable and exportable.
- Avoid cloud-only proprietary formats. A user should be able to leave with their data without asking LlmBench for permission.

## Chat import / export

- Export a conversation to readable `.md`.
- Import `.md` into a new chat, composer, project context or Prompt Studio.
- Save useful chat output directly as a Markdown asset.
- Keep reusable files in a library so the same local asset can be used across chats without repeated manual recreation.
- Never export API keys, hidden reasoning, private diagnostics or other secrets as part of a chat Markdown export.

## Skills

- Use a portable `SKILL.md` convention with YAML frontmatter and optional `assets/` / `scripts/` siblings.
- Create and manually edit skills in LlmBench instead of hiding their instructions behind a form.
- Import a local skill directory or a skill URL; allow export/download as normal files.
- Provide built-in and custom skill lists with search, enable/disable, delete, rename and sample prompts.
- Add AI-assisted create/edit and document-to-skill flows as optional conveniences, while keeping the Markdown source directly editable.
- Preview and validate imported skills before enabling them.
- Require explicit capabilities/permissions for active behavior; importing Markdown alone must never execute scripts.
- Keep secrets outside `SKILL.md` and show a trust warning for third-party skills.

## Projects and libraries

- Group related chats, files and instructions into projects.
- Give projects a small local library rather than forcing every file through the current chat composer.
- Allow adding either a local file or pasted text as reusable project context.
- Make generated/imported artifacts easy to pin, reopen, edit, export or move into a project.
- Keep the simple chat flow intact; projects/library features should be additive rather than mandatory ceremony.

## Prompt/file tooling borrowed from Docbench

These are Docbench-style capabilities to bring into LlmBench, not changes to Docbench itself.

- Validate the construction/structure of prompt and Markdown formats.
- Offer safe repair/normalization when the structure is malformed.
- Detect and normalize EOL conventions instead of letting mixed line endings quietly accumulate.
- Port the proven Docbench token counter into the editing/prompt workflow: use a real bundled tokenizer locally (currently `js-tiktoken` with `o200k_base`), not a character-count estimate; keep lazy loading, encoder reuse and debounced recounting.
- Keep those tools useful for manually edited prompts, imported `.md`, skills and chat exports.

## Built-in Bench tools / plugins

- Treat useful capabilities from `twojstar/twojstar` Benches as first-party LlmBench tools instead of requiring an external MCP or another service for capabilities LlmBench should provide locally.
- Keep a small capability registry so each built-in tool declares its inputs, outputs, permissions, local/network behavior and which chat/provider transports can use it.
- Expose tools selectively per provider. Native/API chats can receive real tool calls where supported; account-backed WebViews should get only reliable, explicit user-approved bridges or one-tap insert/share flows rather than brittle page scraping.
- Keep one source of truth for Bench logic. Prefer extracting/reusing portable cores or a narrow typed bridge over copying implementations into LlmBench and letting them diverge.
- **Docbench tool:** document/Markdown/JSON/YAML/XML validation, repair and formatting; EOL/BOM handling; the real local tokenizer; safe previews; and selected PDF operations where they fit a chat workflow.
- **Docbench Text Inspector:** reuse the existing inspector as the pre-flight view for imported/selected text before it is trusted by a model or tool. Surface exact line/column, severity and safely escaped/decoded payloads for zero-width and bidi controls, Unicode tags, variation-selector carriers, mixed-script confusables, prompt-injection-like instructions, Base64-encoded instructions and oversized encoded carriers. Detection warns and reveals; it does not silently execute, rewrite or discard the source.
- **Codebench tool:** local QR/barcode generation and scanning/decoding, including making a code from selected/chat text and returning decoded content to the composer when useful.
- **Streambench companion:** a persistent compact radio/media player that can keep playing while chatting, with station search/favorites/recents and now-playing metadata. Treat playback primarily as app UI, not as a fake model tool; optional chat actions can be layered on later.
- Let users enable/disable built-in tools globally and, where useful, per chat/provider, with clear capability/permission indicators.

## Token Arena / prompt efficiency lab

Treat Token Arena as the overlap between Bench tooling and a small experimental Lab: a place to compare how the same intent is represented, tokenized, priced and answered across model/provider families.

- Keep the bundled local `o200k_base` counter as the always-available reference baseline. Label it by encoding and never present it as a universal token count for unrelated model families.
- Add model/provider-specific counters only when they provide useful signal through an official count API or a lightweight, trustworthy tokenizer. Do not bundle a tokenizer zoo merely to make the comparison table look complete.
- Distinguish measurement modes clearly: provider-exact count, local exact-for-encoding count, and reference/fallback estimate. Never blend them into one unlabeled number.
- Compare token count and percentage delta alongside provider-reported input/output/cached/reasoning usage where available, plus cost, latency, response length and LlmBench quality scores.
- Derive efficiency views such as quality per 1k input tokens, quality per cost unit and whether extra prompt structure reduces output length, retries or failure rate.
- Add a **Prompt Tournament** mode that keeps the intent fixed while testing representations such as concise vs verbose, plain text vs Markdown/JSON/YAML, or different natural languages across selected models.
- Optimize for task success and clarity, not minimum token count alone. A slightly larger structured prompt may be the winner if it improves quality, lowers output cost or avoids another round trip.
- Make experiments reproducible by recording the model/provider identity, counter backend/encoding, prompt variant and relevant pricing snapshot instead of comparing anonymous numbers that may drift over time.
- Keep the local baseline fully usable offline and without an LlmBench account. Network-backed provider counting is optional and must not silently upload text merely to obtain a more exact number.
- Use accumulated Arena results to reveal practical family/model tendencies without claiming that tokenization alone explains model reasoning or internal processing.

## UI/UX architecture and smoothness

- Before the UI refactor, align the currently pinned Compose BOM/Material 3 dependencies with the then-current stable releases. Use stable Material 3 + Material 3 Adaptive as the baseline instead of inventing parallel breakpoint/navigation systems; keep experimental/alpha-only Expressive APIs optional and isolated until they are needed.
- Adapt the main shell by width: compact screens keep bottom navigation; medium/expanded screens should prefer a navigation rail or drawer through `NavigationSuiteScaffold` rather than stretching phone chrome.
- Use a list-detail pattern for locally owned conversations on larger screens: conversation list on the leading pane, active chat in the main pane, and an optional supporting pane for tools/files/provider controls. Preserve pane and scroll state when resizing or rotating.
- Keep account-backed Web chats immersive on phones, but allow their modal provider drawer to become a persistent rail/drawer on wider layouts.
- Use Material 3 Expressive selectively for discovery, prominent actions and transitions. Keep repeated chat/message interactions calmer and faster with standard motion instead of animating every surface.
- The native chat list already uses stable message IDs. Add `contentType` for user/assistant/error/status rows so Lazy layouts can reuse compatible compositions efficiently.
- Auto-scroll only while the user is already near the latest message. If they scroll upward, never yank them back during streaming; show a compact jump-to-latest/unread control instead.
- Coalesce streaming text updates to a UI-friendly cadence and keep message rows fed by stable/immutable state so token-by-token updates do not recompose unrelated chrome or old messages.
- Avoid composition-driven infinite animation for incidental effects when a draw/graphics phase update can do the same job. Keep typing/generating indicators cheap.
- Replace fixed narrow message widths on expanded screens with adaptive readable widths: do not stretch text edge-to-edge, but do not keep the current phone-sized bubble cap on tablets either.
- Prefer Material typography over hard-coded tiny essential labels; keep touch targets and Android font-scaling/accessibility behavior intact.
- Keep edge-to-edge, IME handling and predictive back coherent across chats, sheets, drawers and list-detail panes.
- Add Macrobenchmark journeys and Baseline Profiles for cold/warm start, opening a chat, provider switching, long-message-list scrolling, returning from a detail pane and active streaming. Judge smoothness from release builds and frame timing, not debug feel.
- Re-check the current Android guidance at implementation time: Material 3, Material 3 Adaptive, Compose lazy-list performance and Baseline Profile/Macrobenchmark docs are the source of truth rather than version numbers frozen in this backlog.

### UI construction shortlist from the inspected APK batch

- **Google AI Edge Gallery:** strongest structural reference for native-feeling tool/skill management, import flows and capability surfaces.
- **ChatGPT / Claude:** strongest reference for keeping the main chat surface focused, with secondary capabilities discoverable without permanently crowding the composer.
- **Kimi:** strongest reference for fitting projects, files, skills and workspace actions into a feature-dense product without turning every action into a top-level tab.
- **Perplexity:** useful separation of chats/projects/library/artifacts and quick reuse of generated work.
- **Obsidian:** strongest local-file/source-of-truth ergonomics for create/open/search/edit flows.
- Treat these as interaction references, not a runtime benchmark; the APK inspection does not justify claiming one app has better frame timing than another.
## Android widgets and conversation notifications

- Build first-party home-screen widgets with Jetpack Glance and responsive layouts; update them from local state changes rather than aggressive polling.
- Persist locally owned native/API conversations with stable conversation IDs before exposing message widgets or conversation notifications. The current native chat history lives in ViewModel state and is not durable enough to back a widget across process death/reboot.
- Offer three configurable widget modes: **Chats** (recent/favorite conversations or provider shortcuts), **Messages** (latest locally known messages across chats), and **Pinned chat** (latest messages/status for one chosen conversation with a direct deep-link back into it).
- Back collection widgets with `LazyColumn` and stable item IDs so list state survives updates where the platform supports it; resize by showing more or fewer rows rather than scaling text into mush.
- Treat WebView account providers honestly: if LlmBench does not own their conversation history, the widget may expose provider/chat shortcuts and locally tracked status, but must not periodically scrape remote pages just to manufacture a message list.
- Make widget rows deep-link directly to the corresponding local conversation/provider. Do not use background activity-launch trampolines.
- Add privacy controls for widget/notification previews: allow hiding message bodies, model/provider details or all sensitive text while keeping a useful title/status.
- For locally owned native/API chats, publish proper conversation notifications with `MessagingStyle`, `Person` metadata and long-lived conversation shortcuts so Android can surface them consistently in conversation UI and system widgets.
- Add `RemoteInput` Direct Reply per conversation with a unique reply `PendingIntent`. Feed the reply through the same native send pipeline, reflect sending/failure state, then update the same notification instead of canceling it so repeated replies remain possible.
- For account-backed WebView providers, do not pretend background Direct Reply is reliable. Until a provider has a safe supported transport, capture the reply as a staged draft and deep-link into that exact provider/chat for explicit send rather than automating a hidden WebView.
- Keep a provider capability matrix for notifications/widgets (`messageHistory`, `completionNotification`, `directReply`, `draftReply`, `deepLink`) so UI only promises actions that actually work.
- Free-form typing does not belong inside a Glance/RemoteViews widget. A pinned-chat widget should open the composer; true inline text entry belongs to notification Direct Reply where Android provides `RemoteInput`.
- Re-check current Glance, conversation-notification and Direct Reply guidance at implementation time; these platform surfaces evolve independently from ordinary Compose UI.

## Identity-assisted provider onboarding

- Do not introduce a mandatory LlmBench account just to reduce provider login friction. Treat this as provider onboarding, not as a new identity silo.
- Let the user choose a preferred sign-in method such as Google, GitHub or Microsoft, then select which compatible providers to connect. Store the preference locally; do not require LlmBench to own the upstream identity.
- Extend the provider capability registry with supported social sign-in methods and an authentication surface/handoff mode so the UI only offers combinations verified for that provider.
- Launch third-party identity-provider steps in Android Auth Tab / Custom Tabs where supported. These use the user's browser-backed session, so an already signed-in Google/GitHub/Microsoft account can often turn repeated credential entry into a short provider-specific confirmation flow.
- Keep every provider authorization independent. One Google/GitHub/Microsoft login is not a universal token for unrelated relying parties, and LlmBench must never claim otherwise.
- Never copy browser cookies into WebView, extract OAuth tokens from provider pages, inject credentials, or automate hidden sign-in. Provider and identity-provider sessions remain owned by their respective origins.
- Track session handoff explicitly per provider: embedded session supported, browser-backed only, or manual/unsupported. If a provider cannot safely return an authenticated session to the integrated WebView, keep it browser-backed or require manual sign-in instead of bridging cookie stores.
- A browser-backed provider may lose WebView-only tweaks, activity probes or composer bridges; surface that tradeoff in the capability UI rather than silently degrading features.
- Better Auth is only a future option if LlmBench later needs its own optional account or wants to link several identities to LlmBench-owned cloud/sync features. It is not required for this provider-login flow and cannot mint sessions for unrelated AI providers.
- Re-check provider login surfaces and Android authentication guidance at implementation time; both provider OAuth behavior and browser/WebView constraints can change independently of the app.
## Security and privacy architecture

- Treat security as a release requirement for every feature that touches accounts, prompts, messages, files, tools, widgets or notifications; do threat modeling before wiring new cross-boundary data flows.
- Minimize sensitive state. Keep data local when practical, collect only what a feature needs, and make provider-owned login/session material stay provider-owned rather than copying cookies, OAuth tokens or passwords into LlmBench storage.
- Keep native API secrets behind the existing Android Keystore-backed AES-GCM store. Never write raw keys, credentials, auth headers, prompts or message bodies to logs, analytics, crash breadcrumbs, exports or diagnostics.
- Define explicit backup/transfer rules before durable chat storage ships. The current manifest allows backup; secrets, WebView/session state, private conversations and sensitive attachments must be excluded by default, with only deliberately safe settings opted into backup or device transfer.
- Classify local data by sensitivity and use separate stores for public preferences, private conversation content, imported files and secrets so retention, backup and deletion rules can be enforced independently.
- Give users clear delete controls for individual chats/projects/files and a secure "clear local data/sign out providers" path that removes LlmBench-owned sensitive state without pretending it can revoke provider-side data.
- Keep WebViews least-privileged: HTTPS-only provider boundaries, no mixed content, file/content access disabled unless a scoped user action requires it, no arbitrary remote userscripts, and no JavaScript-to-native interface for untrusted provider pages. Preserve the existing provider/document guards around injected static scripts.
- Treat every imported `SKILL.md`, document, generated artifact and decoded QR/barcode as untrusted data. Parsing or previewing it must never execute scripts or silently grant file/network/secret access.
- Run the Docbench Text Inspector as a reusable security pre-flight wherever untrusted text can cross into model context, skill instructions, tool input or a share/import flow. Make hidden carriers visible to the user before trust decisions instead of relying only on prompt-injection heuristics.
- Built-in Bench tools need explicit capability declarations and least privilege. Tool output is untrusted input to the model/app; network/file capabilities, destructive actions and secret access require narrow scopes and user-visible consent where appropriate.
- Direct Reply and background work must carry only the minimum conversation identifier and reply payload required for that action. Use immutable, unique `PendingIntent`s and reject stale/mismatched provider or conversation targets.
- Notifications and widgets default to privacy-safe previews, with configurable redaction. Never surface hidden/system instructions, API keys, auth state or file contents on the lock screen simply because the foreground chat can see them.
- Mark copied sensitive content with Android's sensitive-clipboard flag and avoid copying secrets automatically. Review ordinary prompt/message copying separately from API-key or credential handling.
- Add optional local privacy controls for users who need them: biometric/device-credential app lock, recents/screenshot protection for sensitive screens, and a quick privacy mode that redacts notification/widget content.
- Exports are explicit data-release boundaries: preview what will leave the app, exclude secrets/internal diagnostics by construction, avoid hidden metadata, and never silently include unrelated conversation/project context.
- Keep TLS validation strict and never add trust-all certificate handling for provider compatibility. Release logging must not include request/response bodies or authorization headers.
- Add security regression tests alongside feature tests: provider host/navigation boundaries, intent/deep-link validation, backup exclusions, secret/log redaction, tool capability gating, imported-skill non-execution, notification reply target isolation and export sanitization.
- Re-check current Android security, WebView, backup, clipboard, notification and storage guidance at implementation time; security-sensitive platform behavior changes independently from ordinary UI APIs.
## UX patterns worth keeping in mind

- File/library layer: reusable assets should outlive one attachment action.
- Markdown preview: generated or imported Markdown should be viewable before reuse/export.
- Quick actions: new prompt/note, open recent asset, search library and use in chat should stay close at hand.
- Skill management: create, edit, try, import, export and enable/disable from one obvious place.
- Project context: chats + files + instructions belong together when the user chooses to group them.
- Local-first editing: portable files remain understandable and editable outside LlmBench.

## Research notes from the APK batch

- Obsidian: local Markdown as source of truth, with fast new/open/search workflows and a real editor rather than attachment-only handling.
- ChatGPT: reusable file library, search, per-chat file views and dedicated previews for text/code/document formats.
- Claude: broad document/text intake plus Android text-processing/share entry points.
- Gemini: multi-file share/import flows are useful reference points for Android intake behavior.
- AI Edge Gallery: `SKILL.md` + optional `assets/` / `scripts/`, local/URL skill import, built-in/custom lists, sample prompts and explicit skill management.
- Kimi: project libraries, project instructions, reusable skills, skill create/edit/download, document-to-skill workflows and direct Save as Markdown export.
- Perplexity: library/projects/artifacts separation, pinning and Markdown open-externally behavior.
- Grok: editable files, agent instructions and export-oriented artifact workflows.
- DeepSeek: useful attachment validation/error UX, but attachment-only storage is not the target architecture for LlmBench.
- Meta AI: artifact/library/preset concepts reinforce keeping generated assets reusable outside one chat.