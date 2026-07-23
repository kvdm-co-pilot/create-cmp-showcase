---
name: add-repository
description: >-
  Add a data/domain slice ONLY — model, repository interface + impl, use case, and a
  hand-written fake — to this Compose Multiplatform app, cloned deterministically from the
  `home` exemplar's data layer. No screen, no ViewModel, no nav route, no spec clauses. Use this
  when the user wants to "add a repository", "add a data source", "model a new entity", "new
  entity with a repository", or names a domain noun they want backed by data before any UI exists
  (e.g. "add a Tag repository", "I need a data source for Bookmarks"). Works with NO create-cmp
  plugin installed — the stamper (`qa/scaffold-feature.mjs --preset repository`) and this skill
  both ship inside the generated project.
---

# add-repository — stamp a data/domain slice only

> Deterministic-stamp, gate-proven. The script (`qa/scaffold-feature.mjs --preset repository`)
> does the mechanical work — copy the `home` exemplar's data/domain files, whole-word identifier
> rename, DI injection for the repository + use case only. You (the AI) only customize the
> entity's fields and its sample data. You are not done until `node qa/verify.mjs` PASSes and the
> receipt is committed — see this project's `CLAUDE.md`.

This is the `repository` subset of `add-feature` — same stamper, same rename mechanic, filtered
to just the 5 data/domain files. Use it when you want the data layer to exist before any screen
consumes it (e.g. you're modeling several entities up front, or a screen for this entity will
come later via `add-screen`).

## Why a stamper and not hand-written files

Every hand-written file is a drift chance in this project's architecture. `qa/scaffold-feature.mjs
--preset repository` produces a **conforming skeleton by construction** for the data/domain
layer: a domain model, a domain repository interface, a use case, a data-layer impl, and a
hand-written fake for tests — wired into Koin. Your job is to make the entity's shape and sample
data real, not to make the wiring correct — that part is already done.

## The flow

### 1. Name the entity

Ask the human for the entity name — PascalCase, singular (e.g. `Tag`, `Bookmark`, `Category`).
Unlike `add-feature`/`add-screen`, there is no feature name and no `--entity` flag here: the
positional argument **is** the entity.

### 2. Dry-run

```
node qa/scaffold-feature.mjs <Entity> --preset repository --dry-run
```

Show the human the file plan (5 files: `domain/model/<Entity>.kt`,
`domain/repository/<Entity>Repository.kt`, `domain/usecase/Get<Entity>sUseCase.kt`,
`data/remote/<Entity>RepositoryImpl.kt`, `testing/fakes/Fake<Entity>Repository.kt`) and the DI
injection diffs (repository binding + use case factory only — no ViewModel, no nav). Confirm
before stamping for real.

### 3. Stamp

```
node qa/scaffold-feature.mjs <Entity> --preset repository
```

If it exits non-zero, read the message — it is actionable (entity name already taken, or not a
valid Kotlin identifier). Do not hand-edit around a stamper failure.

### 4. Customize the entity and its data

The generated shape mirrors `home`'s `Item` (an `{id, title, subtitle}` list entity). Adapt it to
the real entity:

- Update the fields in `domain/model/<Entity>.kt` to match the real shape.
- Update the sample/seed data in `<Entity>RepositoryImpl.kt` accordingly.
- Update `Fake<Entity>Repository.kt` (`testing/fakes/`) to match the model's new fields — it's
  the hand-written fake every future test against this entity will use.
- Leave the DI wiring (`di/AppModule.kt`) as stamped unless you're renaming something — it's
  already correct: `single<<Entity>Repository> { <Entity>RepositoryImpl() }` and
  `factory { Get<Entity>sUseCase(get()) }`.

**No spec clauses are added by this preset, and that's intentional** — a bare repository has no
observable behavior to specify yet (no screen, no user-facing flow). Clauses attach once a
screen consumes this entity; that's `add-screen`'s job (`FEATURE-01..07` bound to the repository
you just built).

### 5. Gate

```
node qa/verify.mjs
```

This must PASS. It proves: the build compiles, unit tests pass (use case + repository + fake),
and architecture conformance holds (`domain` imports nothing app-internal, `data` implements the
`domain` interface). specCoverage is untouched by this preset — expect the same clause/tag counts
as before you ran the stamper. **Not done until this is PASS and the evidence receipt
(`qa/evidence/latest.json`) is committed with your change.**

If it fails: read the failing step's reason and fix the actual behavior. Do not delete or weaken
a test to reach green.

## Guardrails

- Works identically with or without the create-cmp Claude Code plugin installed — everything it
  needs (`qa/scaffold-feature.mjs`, this file) ships inside the generated project.
- Respect whichever toggles this project was stamped with (e.g. Room disabled at scaffold time
  stays disabled — this preset never introduces Room coupling, matching the exemplar's
  dependency-light `RepositoryImpl` pattern).
- This preset stamps data/domain only. If the human actually wants a screen too, either run
  `add-feature` (the full slice) instead, or follow up with `add-screen --entity <Entity>` once
  this repository exists.
