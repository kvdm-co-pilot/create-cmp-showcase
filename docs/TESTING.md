# Testing

The pyramid this project uses, bottom-up. The `home` feature's tests are the exemplar —
copy their shape.

| Layer | Where | Run |
|---|---|---|
| Unit (majority) | `composeApp/src/commonTest` | `./gradlew :composeApp:desktopTest` |
| Conformance gates (ARCH clauses) | `composeApp/src/desktopTest/…/conformance` | same task |
| Screen behavior — Compose UI Test (spec-cited) | `composeApp/src/desktopTest/…/presentation` | same task |
| Golden trees (structure) | `qa/golden/` + `HomeGoldenTreeTest` | same task |
<!-- >>> cmp:feature e2e -->
| E2E smoke (few) | `qa/e2e/*.yaml` (Maestro) | `maestro test qa/e2e/smoke.yaml` |
<!-- <<< cmp:feature e2e -->
| The lane (all of it) | `qa/verify.mjs` | `node qa/verify.mjs` |

Every durable test cites the spec clause it verifies (`// SPEC: HOME-02` — see
[`specs/`](../specs/README.md)); **new behavior begins as a spec clause.** The lane's
`specCoverage` step enforces this: it fails on orphan clauses (no citing test) and orphan tags
(no matching clause, or one citing a withdrawn clause).

## Unit conventions

- **Frameworks:** `kotlin-test` assertions · `kotlinx-coroutines-test` (`runTest`,
  `StandardTestDispatcher`) · **Turbine** for Flow/StateFlow.
- **Fakes, never mocks.** Every repository/source interface gets a hand-written fake in
  `commonTest/…/testing/fakes/` — configurable (a typed `failure: DomainError?`, seeded data)
  and call-recording; it returns `AppResult.Failure`, it never throws (the domain contract
  doesn't). Mocking frameworks are banned: they're JVM-only in KMP and hide bad seams.
- **Style:** Arrange-Act-Assert; behavior-named backtick tests
  (`` `emits Content when the repository returns items` ``); one behavior per test; no shared
  mutable state between tests.
- **ViewModels:** install a `StandardTestDispatcher` as Main (`@BeforeTest setMain` /
  `@AfterTest resetMain`) because `viewModelScope` launches on Main; assert state with
  `state.test { … }` (Turbine).
- **Suspend/delay:** always under `runTest` — virtual time makes `delay` free.

## What every new piece of code brings

| You added | You also add |
|---|---|
| a ViewModel | a `*ViewModelTest` (sealed states: loading, content, empty, error, retry) |
| a use case | a `*UseCaseTest` (behavior + typed-failure passthrough) |
| a repository impl | a test through its DOMAIN interface |
| a screen | a testTag root (E2E reachable) |

Never delete, weaken, or `@Ignore` a failing test to get green. Fix the behavior — or if the
test is genuinely wrong, change it and say so explicitly in your PR/summary.

<!-- >>> cmp:feature e2e -->
## E2E

Maestro flows (`qa/e2e/*.yaml`) cover boot + bottom-nav — install the free CLI once
(`curl -fsSL "https://get.maestro.mobile.dev" | bash`). Selectors go by **testTag** (`id:` —
surfaced as resource-ids via `TestTagAutomation`), never by display text. One flow per
journey, spec-clause cited; keep the E2E tip small — behavior belongs in unit tests.

**Settle rule:** an assertion that follows an interaction triggering an async state change
(typing a search query, a toggle that persists, a load) must be an `extendedWaitUntil`, not
a bare `assertVisible` — the ViewModel round-trip passes through a brief Loading arm that a
lane-loaded emulator stretches, and asserting into that transition is a false red (a real
one: a search assert that passed standalone failed in-lane behind a 33s type gap). Bare
asserts are for static post-navigation elements only.
<!-- <<< cmp:feature e2e -->

## The verify lane

`node qa/verify.mjs` is the definition of done: spec coverage → build → unit tests →
(conformance, golden trees, token drift, a11y — as they ship) → E2E smoke when a device is
attached. It writes the evidence receipt to `qa/evidence/latest.json`; **commit the receipt
with your change.** SKIPped steps are recorded honestly — green-with-gaps is visible, never
silent.
