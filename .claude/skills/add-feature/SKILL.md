---
name: add-feature
description: >-
  Add a new conforming vertical-slice feature (Screen + ViewModel + UseCase + Repository + spec
  + tests + golden tree + nav route + DI wiring) to this Compose Multiplatform app, cloned
  deterministically from the `home` exemplar. Use this when the user wants to "add a feature",
  "add a screen with data", "scaffold a feature", "create a new screen backed by a repository",
  "add a list screen", or names a new domain noun they want a screen for (e.g. "add a Favorites
  feature", "I need a Bookmarks screen"). Works with NO create-cmp plugin installed — the
  stamper (`qa/scaffold-feature.mjs`) and this skill both ship inside the generated project.
---

# add-feature — stamp a conforming vertical slice

> Spec-first, deterministic-stamp, gate-proven. The script (`qa/scaffold-feature.mjs`) does the
> mechanical work — copy the `home` exemplar file set, whole-word identifier rename, anchor
> injection into the three shared files. You (the AI) only refine spec wording and adapt the
> feature to its real shape. You are not done until `node qa/verify.mjs` PASSes and the receipt
> is committed — see this project's `CLAUDE.md`.

## Why a stamper and not hand-written files

This project's whole thesis is that determinism beats freehand generation for anything
architecturally load-bearing (see `docs/adr/0001-*.md` if present, or just: every hand-written
file is a drift chance). `qa/scaffold-feature.mjs` produces a **conforming skeleton by
construction** — it passes the architecture conformance gates before you write a line of
feature-specific logic. Your job is to make it *behave* like the real feature, not to make it
*structurally correct* — that part is already done.

## The flow

### 1. Interview

Ask the human for the feature name (PascalCase, plural-ish noun — e.g. `Favorites`,
`Bookmarks`). Propose a singular entity name by stripping a trailing `s`/`ies` (`Favorites` →
`Favorite`, `Categories` → `Category`). Naive de-pluralization is unreliable for irregular nouns
— **always show your proposed entity name and let the human confirm or override it** before
proceeding (`--entity <EntityName>`).

### 2. Dry-run

Run:

```
node qa/scaffold-feature.mjs <FeatureName> --entity <EntityName> --dry-run
```

Show the human the file plan and the anchor-injection diffs it prints. Confirm before stamping
for real — this is the last chance to catch a wrong entity name or a naming collision.

### 3. Stamp

Run the same command without `--dry-run`:

```
node qa/scaffold-feature.mjs <FeatureName> --entity <EntityName>
```

This writes the new Screen/ViewModel/UseCase/Repository(+impl)/tests/fake, wires them into
`di/AppModule.kt`, `presentation/navigation/Screen.kt`, and `presentation/navigation/AppNavHost.kt`
at their `// cmp:anchor` markers, and writes `specs/<feature>.spec.md` with a default six-clause
set (`<FEATURE>-01..06`: loading, success, error, reload-after-failure, tap-navigates, golden
tree) — copied verbatim from the `home` exemplar's shape.

If it exits non-zero, read the message — it is actionable (name already taken, an anchor marker
is missing, or a name isn't a valid Kotlin identifier). Do not hand-edit around a stamper
failure; if an anchor is genuinely missing from a shared file, that is a template defect worth
flagging, not something to route around by hand-splicing.

### 4. Refine the spec, then the behavior

The default spec clauses are placeholders shaped like the `home` exemplar (a plain list of
title/subtitle rows). **Rewrite the clause prose** in `specs/<feature>.spec.md` to describe the
feature's real behavior — the six clause **ids stay fixed** (`specCoverage` binds tests to ids,
not prose), only the wording changes. Propose the rewritten clauses to the human; get them
confirmed before moving on — this project's contract is spec-first.

Then adapt the generated code to match:

- If the feature isn't shaped like "a list of `{id, title, subtitle}`", update the entity's
  fields in `domain/model/<Entity>.kt`, the sample data in `<Entity>RepositoryImpl.kt`, and the
  screen's rendering in `presentation/<feature>/<Feature>Screen.kt` together — keep them
  consistent with each other and with the tests.
- Update the copied tests (`<Feature>ViewModelTest.kt`, `<Feature>ScreenTest.kt`) to match
  whatever you changed. The gate (step 6) will tell you exactly what you missed — a compile
  error names the mismatch; a spec-coverage failure names an orphaned clause or tag.
- Leave the DI wiring, nav route, and screen scaffold as stamped unless the feature genuinely
  needs a different shape (e.g. no navigation-on-tap — then remove the `-05` clause's
  citing test and strike the clause through, don't leave it dangling).
- The new screen is reachable via `Screen.<Feature>` but is **not** wired into a bottom-nav tab
  by this generator (MVP scope — pushed-route only). Promoting it to a tab is a manual edit to
  `appTabs()` + `AppShell` call sites; a future `--tab` flag may automate this.

### 5. Capture the golden tree

The golden baseline is **not** copied by the stamper (a copied one would silently mismatch the
adapted screen). Generate it fresh once the screen renders the real behavior:

```
UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*<Feature>GoldenTree*"
```

Review `qa/golden/<feature>.json` briefly — it should reflect the structure you intended, not a
copy-paste artifact. Commit it alongside the feature.

### 6. Gate

```
node qa/verify.mjs
```

This must PASS. It proves: the spec's six clauses are all bound to a citing test
(`specCoverage`), the build compiles, unit tests pass (ViewModel + UseCase + Repository +
fakes), architecture conformance holds (`presentation` doesn't import `data`, the new
`*Screen.kt` carries a `testTag`, the new `*ViewModel.kt` has a matching test), the golden tree
matches what you just captured, and accessibility holds. **Not done until this is PASS and the
evidence receipt (`qa/evidence/latest.json`) is committed with your change** — this is this
project's standing definition of done (see `CLAUDE.md`).

If it fails: read the failing step's reason (it is worded for exactly this), fix the actual
behavior or spec/test binding, and re-run. Do not delete or weaken a test to reach green.

## Guardrails

- This flow works identically with or without the create-cmp Claude Code plugin installed —
  everything it needs (`qa/scaffold-feature.mjs`, this file) ships inside the generated project.
- Respect whichever toggles this project was stamped with (e.g. if Room or Appium/Maestro were
  disabled at scaffold time, don't reintroduce them for the new feature).
- `add-feature` generates a pushed-nav-route screen, not a bottom-nav tab, and does not generate
  a "tap → detail" destination (the exemplar's `DetailScreen.kt` is intentionally not copied —
  MVP scope). Both are documented future extensions, not omissions to silently work around.
