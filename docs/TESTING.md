# Testing

The pyramid this project uses, bottom-up. The `home` feature's tests are the exemplar —
copy their shape.

| Layer | Where | Run |
|---|---|---|
| Unit (majority) | `composeApp/src/commonTest` | `./gradlew :composeApp:desktopTest` |
| Conformance gates (ARCH clauses) | `composeApp/src/desktopTest/…/conformance` | same task |
| Screen behavior — Compose UI Test (spec-cited) | `composeApp/src/desktopTest/…/presentation` | same task |
| Golden trees (structure) | `qa/golden/` + `HomeGoldenTreeTest` | same task |
| Instrumented behavior (platform facts) | `composeApp/src/androidInstrumentedTest` | `./gradlew :composeApp:connectedDebugAndroidTest` (device attached) |
| E2E smoke (few) | `qa/e2e/*.yaml` (Maestro) | `maestro test qa/e2e/smoke.yaml` |
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

## The instrumented tier — platform behavior

This seam exists because platform behavior escapes every desktop tier. Alarms,
notifications, lock-screen/full-screen intents, notification channels, PendingIntent
identity, audio routing, process death, and runtime permissions are OS facts:
`desktopTest` runs on a JVM, golden trees pin structure, the conformance suite is static,
and the E2E smoke taps UI without asserting anything about the shade or the alarm table.
A feature whose whole point is "the phone alerts" can ship fully green from every other
tier and never alert — that class of defect escaped to production repeatedly before this
tier existed. When your feature touches alarms, notifications, or locks, its behavior
test lives here.

**What belongs here:** claims only the OS can witness — a notification actually reached
the shade, a channel holds the importance the feature needs, N logical alarms occupy N
PendingIntent slots, the alert path plays with `USAGE_ALARM` on a silenced ringer, the
app survives process death, a permission-gated path degrades correctly.

**What does not:** logic (→ `commonTest`), rendered structure (→ golden trees),
architecture rules (→ conformance), UI journeys (→ E2E flows). If a JVM test can prove
it, a JVM test is where it goes — this tier is the most expensive seat in the house.

**Cost model:** needs a device/emulator, so it runs at checkpoint cadence — the lane's
`androidChecks` step (`connectedDebugAndroidTest`) SKIPs honestly when no device is
attached and runs in `local`/`ci` when one is, with the `release` profile adding the
release-APK smoke on top. It is not an inner-loop tier; don't reach for it per-edit.

**The helpers** (`androidInstrumentedTest/…/testing/`), small and composable — see
`PlatformBehaviorSeamTest` for the exemplar shape:

- `NotificationAsserts` — bounded-poll wait for a posted notification (id/tag or
  predicate), channel-exists with an importance floor, full-screen-intent capability
  (API-aware).
- `AlarmAsserts` — the OS alarm table (`dumpsys alarm`) parsed per-package;
  alarm-registered and N-distinct-alarms assertions (the PendingIntent-identity
  collision, caught mechanically).
- `SystemState` — snapshot/restore of ringer mode and DND so audio-routing claims are
  testable; its header is honest about what instrumentation cannot control (OEM sound
  policy, "a human heard it") — those stay a documented manual tier.

### Runtime state control — put the system into the state the claim is about

The observation helpers above answer "what did the OS do?"; this organ family answers
the prior question — "can the test even reach the state the claim is about?" Most
unprovable mobile claims are unprovable exactly because the state is hard to reach: you
would have to wait hours for Doze, ship to a user who denies the permission, drop the
network by hand, or hope the OS reclaims your process while you watch. Each organ
reaches one such state on demand — emulator-only (every entry point SKIPs on a real
device), root-free on stock user-build images (every shell command verified from the
shell uid), and bracket-shaped (`withX { }` — snapshot, act, restore in `finally`) so
organs compose:

- `TimeWarp` — the device clock and time zone ("the 08:00 alarm actually arrives", DST).
- `DozeControl` — forced light/deep idle; composed with TimeWarp it proves
  `setExactAndAllowWhileIdle` does what its name promises (the flagship exemplar in
  `RuntimeStateSeamTest`).
- `PermissionControl` — the denied state (the fresh-install default) as a test input;
  one-way grants. Its header documents the trap that shapes it: revoking a held runtime
  permission kills the holding process — this seam's own process.
- `ProcessControl` — real OS-driven activity destruction and saved-state rebuild
  (don't-keep-activities — flipped live via the same binder call the Developer-options
  toggle uses, because `settings put global always_finish_activities` alone is read only
  at boot; verified on API 35); honest about why in-process "kill my own process" is
  structurally impossible under instrumentation.
- `NetworkControl` — offline (airplane mode) and per-transport wifi/data brackets; the
  path to proving `core/connectivity/NetworkMonitor` tracks the world.
- `ConfigControl` — dark mode, font scale, per-app locale (API 33+); configuration
  change as the other state-loss lever.

What device-state control cannot reproduce, honestly: the path INTO a state (real Doze's
descent ladder and maintenance windows — forced idle teleports and holds), OEM policy
(battery managers, sound routing, skinned lock screens), app-standby buckets, real-network
character (bandwidth, captive portals, flaky RSSI — emulator transports are the host's
connection wearing costumes), true cold-process start under instrumentation, and anything
whose proof is a human's senses. Each organ's header states its own gaps; a green test is
evidence for exactly the state the organ produced, nothing wider.

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

## The verify lane

`node qa/verify.mjs` is the definition of done: spec coverage → build → unit tests →
(conformance, golden trees, token drift, a11y — as they ship) → E2E smoke when a device is
attached. It writes the evidence receipt to `qa/evidence/latest.json`; **commit the receipt
with your change.** SKIPped steps are recorded honestly — green-with-gaps is visible, never
silent.

**`--fast` is the lane's inner loop.** `node qa/verify.mjs --fast` runs the resolved
profile minus the device/release tier (`releaseBuild`, `tokenDrift`, `e2eSmoke`,
`androidChecks`, `releaseSmoke`) — unconditionally, device attached or not. It exists
because device/release evidence is the scarce, slow tier (R8 compile, emulator, Maestro,
instrumented runner): batch it at the checkpoint, don't pay for it on every small edit.
The rest of the profile runs cheaply, three ways:

- **Unchanged pure-Node steps are reused, not re-run.** `specCoverage`, `approvals`,
  `componentStories`, `reachability`, and `archDoc` are pure functions of files on disk;
  a fast run reuses each one's last PASS when a content hash of its declared inputs is
  unchanged, shown as `⚡ name: CACHED (unchanged since …)`. Only a PASS is ever reused —
  a FAIL or SKIP always re-runs — and the cache
  (`composeApp/build/.cmp-step-cache.json`) is a gitignored cache, never evidence: the
  full lane never reads it (it always executes every step; it only writes entries so the
  next fast run benefits).
- **Gradle's up-to-date checks stand.** The full lane forces test execution with
  `--rerun` (evidence integrity — a receipt must attest tests that ran). A fast receipt
  is already declared non-evidence, so fast mode omits the flag and unchanged test tasks
  cost nothing.
- **Unit tests are scoped to the change.** Changed `.kt` files (git diff + untracked)
  map to `--tests "*<feature>*"` filters, reported honestly in the step line and the
  receipt. Broad-impact changes — build files, DI, theme/tokens, shared components,
  `qa/` itself, anything outside `composeApp/src` — disable filtering and run the whole
  suite, as does any uncertain case (no git, unmappable change): fail open, never fail
  silent. A filtered fast run can miss a cross-feature regression by design; the full,
  unfiltered suite at the checkpoint is what decides done.

A fast run is mechanically unable to claim done — its receipt records `"mode": "fast"`,
derives **no** evidence rung, and `qa/receipt-check.mjs` (the Stop hook, CI, pre-push)
refuses it by name. Iterate on `--fast`; run the full lane once, deliberately, when the
change is done.

**Watch mode makes the inner loop resident.** `node qa/watch.mjs` watches
`composeApp/src`, `specs/`, and `qa/` and re-runs `node qa/verify.mjs --fast` (as a
subprocess — it inherits every fast-mode economy above for free) on every save, debounced
so a multi-file save storm triggers one run and changes landing mid-run coalesce into
exactly one follow-up. Each run re-prints the step table with any failing step's reason
verbatim — the errors-on-save loop an IDE gives a human, for the agent. What it is **not**:
evidence. It runs the fast tier only, so nothing it produces can satisfy the done-gate,
and every run says so in a standing footer; the checkpoint remains one deliberate full
`node qa/verify.mjs` run. Coordination: it never launches while a verify lane
(`.cmp-lane-in-progress`) or a preview-daemon render (`.cmp-render-in-progress`) holds the
project — it waits and coalesces, so watch mode, the preview daemon, and the lane can all
be resident on one project without two Gradle invocations ever colliding. `--once` runs a
single coordinated pass for scripting; `--json` emits one JSON object per run for
programmatic consumption.

## One device, one driver

The machine typically has ONE Android device/emulator, and it is the scarcest, slowest,
most fragile resource the harness touches — two concurrent drivers produce wedged adbd,
`device offline` while `adb devices` looks fine, crossed app state, and false reds that
have nothing to do with the app. So device evidence is **batched, never an inner loop**:
the lane sequences its device steps (`tokenDrift` live tier, `e2eSmoke`, `androidChecks`,
`releaseSmoke`) once, last — don't hand-run `connectedDebugAndroidTest`, `maestro test`,
or `adb install` mid-task to "check something".

Mechanically, the first device step takes a **machine-global lease** on the device's adb
serial (`<os tmpdir>/create-cmp/device-leases/<serial>.json` — `qa/lib/device-lease.mjs`
documents the contract), held until the lane exits. It is machine-global on purpose: two
different projects (a scratch app in /tmp, the real one) share the same emulator, and
per-project markers cannot see each other.

**Contention is a SKIP, never a FAIL** — nothing is broken; another run legitimately holds
the device, and the reason names it: `held by "verify lane e2eSmoke" (pid 4711,
/tmp/scratch-x, 2m ago)`. A SKIPped device step does not buy its rung, so contention
visibly *lowers* the evidence level (L2 falls back to L1) instead of corrupting the run —
re-run when the holder finishes to earn the full rung. To see who holds a device:
`cat "${TMPDIR:-/tmp}/create-cmp/device-leases/<serial>.json"`. A crashed holder never
wedges the machine: a lease whose pid is dead, or older than 30 minutes, is silently
reclaimed by the next run. The live inspector tier (`connect_live`,
`navigate_and_inspect`) checks the same lease and refuses to drive a leased device by
naming the holder instead of failing with a mysterious transport error.

## The evidence ladder

Every PASS receipt names its rung (`evidenceLevel` in the receipt; derived by
`qa/lib/evidence-level.mjs` from which steps actually ran and PASSed — never declared, and
a SKIPped step never buys a rung: an unsigned-keystore `releaseSmoke` SKIP is not L3). The
rung is the coarse grade; the per-step list stays the fine print. A FAILed lane has no rung.

| Rung | What it proves | What it does NOT prove |
|---|---|---|
| **L0 scaffold** | Stamp-time green: the build compiles and the unit tests pass. | Nothing about conformance, rendered structure, a11y, or the release variant — and nothing on a device. |
| **L1 desktop** | Full static + JVM evidence: build, unit tests, conformance, golden trees, a11y, release COMPILE, and the pure-Node gates. | That the app runs on a device at all — no APK was installed or driven; platform behavior (alarms, notifications) is invisible from this rung. |
| **L2 device** | L1 plus executed on-device evidence: the debug APK installed and driven (`e2eSmoke`), instrumented platform assertions (`androidChecks`), and/or live token drift. | That the release variant behaves (R8 differs from debug — that is L3's job), nor that alarms/notifications actually land unless an instrumented behavior test asserts them. |
| **L3 release** | L2 plus `releaseSmoke` PASSed: the signed release APK installed and driven on a device. | Real-backend behavior (the emulator/dev backend is a documented tier boundary — see the instrumented-tier section) and store-review compliance. |
