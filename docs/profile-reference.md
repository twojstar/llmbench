# Profile reference

`profile.yaml` describes communication and collaboration preferences. It does not grant tools, permissions, credentials, network access, or authority to change external state.

The JSON Schema is the machine-readable source of truth for accepted fields and values. This document explains their intended meaning and renderer behavior.

## Layering

Profiles are composed from left to right; later layers win.

- omit a field to inherit it from an earlier layer
- use an explicit value to replace the earlier value
- `null` is an explicit value, not shorthand for "inherit"

Mappings merge recursively. Scalars and lists replace earlier values.

## Personality

### Base voice

`personality.base` selects the overall voice:

- `default` - neutral and content-first
- `professional` - precise and low on ceremony
- `friendly` - warm and collaborative
- `honest` - candid and direct; avoids unnecessary social softening or withholding useful criticism
- `whimsical` - light imagery or humor when useful
- `concise` - result-first with minimal framing
- `cynical` - dry skepticism toward claims and needless complexity, never toward the user

`personality.intensity` is deliberately broad. It controls how strongly the selected base voice colors the whole response rather than changing one specific trait.

| intensity | meaning |
|---|---|
| `0` | restrained; keep the base voice mostly in the background |
| `1` | normal baseline |
| `2` | pronounced; make the base voice clearly visible |
| `3` | strong; let the base voice strongly shape tone and phrasing |
| `null` | no explicit intensity; render as the normal baseline |

The base voice is always present. Intensity `0` does not disable it.

The portable default profile uses `base: friendly` with `intensity: 1`. `base: default` uses the same intensity scale; higher values reinforce its neutral, content-first character rather than adding another personality.

### Modifiers

Modifiers are focused controls for individual traits:

`honest`, `warm`, `enthusiastic`, `concise`, `technical`, `educational`, `critical`, `headingsAndLists`, `emoji`, `quickReplies`, `whimsical`, and `cynical`.

Each modifier accepts `0..3` or `null`:

| value | meaning |
|---|---|
| omitted | inherit the earlier layer |
| `null` | explicitly clear the inherited modifier value |
| `0` | no extra emphasis from this modifier |
| `1` | light accent |
| `2` | normal, clearly active preference |
| `3` | strong and recurring preference |

Levels `1`, `2`, and `3` render different instructions for each modifier. They are not merely priority numbers.

`honest` controls candor and directness, not factual accuracy. Higher levels reduce unnecessary social softening, euphemism, and withholding useful criticism or disagreement merely because it may feel awkward or impolite. It should remain respectful rather than abrasive.

A disabled modifier is not a negative instruction. For example, `honest: 0` means "do not add extra candor emphasis", not "be dishonest". Basic factual integrity is rendered independently of the `honest` modifier, so turning the modifier off never permits invented facts, sources, files, tool output, checks, or completed actions.

Base voice and modifiers are additive. For example:

```yaml
personality:
  base: cynical
  intensity: 3
  modifiers:
    cynical: 3
```

The base sets a strongly cynical overall voice, while the modifier specifically reinforces scrutiny of hype, inflated claims, and needless complexity.

## Adaptation

All adaptation fields are booleans:

- `followUserRegister`
- `preserveRequestedArtifactStyle`
- `reduceHumorInSeriousContexts`
- `mirrorLanguage`
- `allowCasualProfanity`

Only `true` adds the corresponding instruction to rendered output.

## Collaboration

### `preamble`

- `off` - do not announce work before answering
- `multiStepOnly` - preamble only for multi-step or state-changing work
- `always` - briefly state the plan before acting

### `initiative`

- `conservative` - stay within the requested scope unless another step is necessary
- `balanced` - take obvious useful steps independently without needless scope expansion
- `proactive` - actively surface related problems and useful improvements while respecting scope

### `verification`

- `light` - basic consistency and visible-error checks
- `normal` - verify important claims and results in proportion to risk
- `strict` - require strong evidence and thorough validation before firm conclusions

### `questionPolicy`

- `blockingOnly` - ask only when missing information blocks useful or safe progress
- `materialAmbiguity` - ask when ambiguity could materially change the result
- `earlyAlignment` - for larger tasks, align early on goal, scope, and success criteria

### `assumptionPolicy`

- `cautious` - avoid outcome-changing assumptions; label and confirm them
- `balanced` - make reasonable reversible assumptions and state material ones
- `decisive` - make reasonable decisions independently unless risk is material

The remaining collaboration options are booleans:

- `answerFirst`
- `plainChatIsDefault`
- `respectExplicitTurnInstructions`
- `avoidRoutinePraise`
- `avoidRoutineFollowUpOffer`
- `announceOnlyMaterialActions`
- `reportPartialFailures`
- `preferResultOverProcess`

Only `true` adds the corresponding instruction to rendered output.

## Examples

A neutral base with selected accents:

```yaml
personality:
  base: default
  intensity: 1
  modifiers:
    concise: 2
    technical: 2
    warm: 1
```

An overlay that changes only initiative while inheriting everything else:

```yaml
collaboration:
  initiative: proactive
```

To remove an inherited modifier, use `0` when the intent is explicitly "off". Use `null` when an overlay intentionally clears a previous value.
