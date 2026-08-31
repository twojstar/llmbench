# Use `.ai` as a Git submodule

This repository is designed to be consumed as a public core while your own repository keeps only local or private differences.

## 1. Add the public core

From the root of your repository:

```bash
git submodule add https://github.com/trvny/.ai.git .ai/core
mkdir -p .ai/generated
cp .ai/core/examples/profile.overlay.yaml .ai/profile.yaml
python -m pip install pyyaml jsonschema
```

Commit both `.gitmodules` and the `.ai/core` gitlink. The parent repository records an exact `.ai` commit, so clones are reproducible.

Recommended layout:

```text
your-repo/
└── .ai/
    ├── core/          public submodule -> trvny/.ai
    ├── profile.yaml   your overlay
    ├── private/       optional project/private material
    └── generated/     optional generated output
```

Do not edit files inside `.ai/core` to customize a consumer repository. Put local choices in the overlay or contribute reusable changes upstream.

## 2. Clone a repository that already uses it

Either clone recursively:

```bash
git clone --recurse-submodules https://github.com/OWNER/REPO.git
```

or initialize the submodule after a normal clone:

```bash
git submodule update --init --recursive
```

If `.ai/core` looks empty, this is usually the missing step.

## 3. Compose your profile

Later layers win:

```bash
python .ai/core/tools/merge_profile.py \
  .ai/core/profiles/default.yaml \
  .ai/profile.yaml \
  --schema .ai/core/schema/style-profile.schema.json \
  --output .ai/generated/profile.yaml
```

A small overlay can contain only the values you want to change:

```yaml
id: my-project
locale: en-US
personality:
  modifiers:
    concise: 2
    warm: 2
```

The schema validates the final composed profile, not each partial layer by itself.

## 4. Render instructions

```bash
python .ai/core/tools/render_profile.py \
  .ai/core/profiles/default.yaml \
  .ai/profile.yaml \
  --schema .ai/core/schema/style-profile.schema.json \
  --output .ai/generated/instructions.txt
```

Use `--language en` or `--language pl` to override the language inferred from the composed locale.

## 5. Update the pinned core

```bash
git -C .ai/core fetch origin
git -C .ai/core checkout main
git -C .ai/core pull --ff-only
git add .ai/core
git commit -m "chore: update AI core"
```

Updating a submodule is intentionally explicit. Your repository moves to a new core commit only when you commit the changed gitlink.

## CI

Most checkout actions do not fetch submodules unless asked. For GitHub Actions:

```yaml
- uses: actions/checkout@v4
  with:
    submodules: true
```

Only enable this in jobs that actually need files from `.ai/core`.

## What belongs where

- reusable profiles, schemas, tools, templates, provider defaults, instructions and public skills -> `trvny/.ai`
- repository-specific behavior, identities, private workflow rules and private skills -> the consumer repository
- credentials and tokens -> environment variables or secret storage, never either repository

The point is one-way composition, not synchronization gymnastics: update the public core upstream, keep local differences downstream.