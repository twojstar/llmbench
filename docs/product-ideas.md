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
