---
name: add-screen
description: >-
  Add a presentation-only slice — Screen + ViewModel + tests + golden tree + spec + nav route —
  for an EXISTING entity/repository in this Compose Multiplatform app, cloned deterministically
  from the `home` exemplar's presentation layer. Requires the entity's data layer (model,
  repository, use case, fake) to already exist. Use this when the user wants to "add a screen",
  "add a screen for an existing entity", "add a screen backed by a repository I already have",
  "put a UI on this data", or names an existing domain noun they want a screen for (e.g. "add a
  screen for Tag", "I need a Bookmarks screen — the repository's already there"). Works with NO
  create-cmp plugin installed — the stamper (`qa/scaffold-feature.mjs --preset screen`) and this
  skill both ship inside the generated project.
---

# add-screen — stamp a presentation slice over an existing entity

> Deterministic-stamp, gate-proven. The script (`qa/scaffold-feature.mjs --preset screen`) does
> the mechanical work — copy the `home` exemplar's presentation files, whole-word identifier
> rename, DI injection for the ViewModel only, nav route + import. You (the AI) only refine spec
> wording and adapt the screen to the entity's real shape. You are not done until
> `node qa/verify.mjs` PASSes and the receipt is committed — see this project's `CLAUDE.md`.

This is the `screen` subset of `add-feature` — same stamper, same rename mechanic, filtered to
the 6 presentation+tests+spec files, **composed on top of an entity that already exists** (via
`add-repository` or a prior `add-feature` run). If the entity doesn't exist yet, this will refuse
to stamp anything — see step 2.

## Why a stamper and not hand-written files

Every hand-written file is a drift chance in this project's architecture. `qa/scaffold-feature.mjs
--preset screen` produces a **conforming skeleton by construction**: a Screen composable with a
testTagged root, a ViewModel (+ test), a Compose UI test, a golden-tree test, and a seven-clause
spec — wired into Koin and the nav graph. Your job is to make it *behave* like the real screen,
not to make it *structurally correct*.

## The flow

### 1. Name the feature and confirm the entity

Ask the human for the feature/screen name (PascalCase, plural-ish noun — e.g. `Tags`,
`Bookmarks`) and the entity it's backed by (e.g. `Tag`, `Bookmark`). If they don't give an
entity, the script defaults it by stripping a trailing `s`/`ies` from the feature name — naive
de-pluralization is unreliable for irregular nouns, so **always show the proposed entity and let
the human confirm or override it** before proceeding (`--entity <EntityName>`).

The entity must already exist — its use case, fake, and model must be present
(`domain/usecase/Get<Entity>sUseCase.kt`, `testing/fakes/Fake<Entity>Repository.kt`,
`domain/model/<Entity>.kt`). If it doesn't, the stamper will refuse before writing anything and
tell you to run `add-repository` first (or use plain `add-feature` instead, which generates both
layers together).

### 2. Dry-run

```
node qa/scaffold-feature.mjs <FeatureName> --entity <EntityName> --preset screen --dry-run
```

Show the human the file plan (Screen, ViewModel, ViewModel test, Compose UI test, golden-tree
test, spec) and the injection diffs (ViewModel DI binding, nav route + import — no repository or
use case wiring, since those already exist). Confirm before stamping for real — this is the last
chance to catch a wrong entity name.

If the entity doesn't exist, this step (and the real stamp) exits non-zero with an actionable
message rather than half-stamping a screen that won't compile.

### 3. Stamp

```
node qa/scaffold-feature.mjs <FeatureName> --entity <EntityName> --preset screen
```

This writes the Screen/ViewModel/tests/golden-tree-test, wires the ViewModel into
`di/AppModule.kt` and a route into `presentation/navigation/Screen.kt` +
`presentation/navigation/AppNavHost.kt` at their `// cmp:anchor` markers, and writes
`specs/<feature>.spec.md` with a default seven-clause set (`<FEATURE>-01..07`) cited by the three
generated test files.

If it exits non-zero, read the message — it is actionable. Do not hand-edit around a stamper
failure.

### 4. Refine the spec, then the behavior

Rewrite the clause prose in `specs/<feature>.spec.md` for the entity's real behavior — the six
clause **ids stay fixed** (specCoverage binds tests to ids, not prose). Propose the rewritten
clauses to the human; get them confirmed before moving on.

Then adapt the generated code to match the entity's actual shape (which may differ from the
`home` exemplar's `{id, title, subtitle}` list): update the screen's rendering in
`presentation/<feature>/<Feature>Screen.kt` and the copied tests
(`<Feature>ViewModelTest.kt`, `<Feature>ScreenTest.kt`) together, consistent with whatever
`<Entity>.kt` already looks like. The stamped screen already **composes the registry
vocabulary** (`ScreenColumn`/`AppHeader`/`ContentStateContainer`/`ListItemCard`,
`presentation/components/*.kt`) — adapt the content shape inside `ContentStateContainer`'s
trailing slot rather than hand-rolling a new header/loading state/list row. The gate (step 6)
will name exactly what you missed.

### 5. Capture the golden tree

The golden baseline is **not** copied (a copied one would silently mismatch the adapted screen).
Generate it fresh once the screen renders the real behavior:

```
UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*<Feature>GoldenTree*"
```

Review `qa/golden/<feature>.json` briefly — it should reflect the structure you intended. Commit
it alongside the feature.

### 6. Gate

```
node qa/verify.mjs
```

This must PASS. It proves: the spec's six clauses are all bound to a citing test
(specCoverage — `<FEATURE>-01..07` newly bound), the build compiles, unit tests pass, architecture
conformance holds (the screen is automation-reachable via a literal `testTag` or `screenTag =`
wiring into a registry component, and references no `CircularProgressIndicator`/
`LinearProgressIndicator` directly), the golden tree matches, and accessibility holds. **Not done
until this is PASS and the evidence receipt (`qa/evidence/latest.json`) is committed with your
change** — this project's standing definition of done (see `CLAUDE.md`).

If it fails: read the failing step's reason, fix the actual behavior or spec/test binding, and
re-run. Do not delete or weaken a test to reach green.

## Guardrails

- Works identically with or without the create-cmp Claude Code plugin installed — everything it
  needs (`qa/scaffold-feature.mjs`, this file) ships inside the generated project.
- Respect whichever toggles this project was stamped with (e.g. if Room or Appium/Maestro were
  disabled at scaffold time, don't reintroduce them for the new screen).
- `add-screen` generates a pushed-nav-route screen, not a bottom-nav tab, and does not generate a
  "tap → detail" destination — same MVP scope as `add-feature`.
- If the entity doesn't exist yet, don't try to work around the stamper's refusal by hand-writing
  the missing use case/fake/model — run `add-repository` first (or use plain `add-feature` for
  both layers together), then retry.
