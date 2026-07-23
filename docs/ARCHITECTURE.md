# Architecture

> **Reading this document.** Every normative sentence below carries a tier tag.
> `[enforced: CLAUSE-ID]`: a named gate in `node qa/verify.mjs` fails the lane on violation.
> `[governed]`: the sentence lives inside a hash-bound human approval (`qa/approvals.json`);
> changing it without re-approval fails the `approvals` gate. `[advisory]`: a documented
> convention with no mechanical check yet. Every sentence is law, signed intent, or advice —
> and says which.

## 1. Purpose & quality goals

This app's purpose, audience, and shape are recorded in [`specs/intent.md`](../specs/intent.md)
— the root brief this document, the component registry, and the exemplar feature all trace
back to. The table below is the default quality-goal set a fresh scaffold ships with. The
genesis walk's architecture conversation is where a human promotes, demotes, or replaces
them for this app's actual priorities ("offline matters more than a11y for a field-work
app").

| Quality (ISO/IEC 25010) | Scenario | Backing |
|---|---|---|
| Maintainability | An AI session adds a feature; the lane names any layer violation as a clause, not a style nit | `[enforced: ARCH-01..05]` |
| Reliability | A source fails; the failure crosses layers as a typed `DomainError`, never a raw exception, and the screen shows a mapped error state | `[enforced: ARCH-06/07/08]` |
| Reliability (offline) | Network drops mid-session; cached Room data still renders, UI shows degraded state | `[advisory]` today — `NetworkMonitor` and Room ship as infrastructure (§4, §7) but no repository wires the cache-first fallback yet; clause candidate |
| Interaction capability (a11y) | Every interactive element is perceivable by assistive tech and automation | `[enforced: SHELL-04 + A11y gates]` |
| Security | The debug inspector HTTP server (§3) never ships in a release build | `[advisory]` today — true by source-set placement (`androidDebug`), not yet gated; clause candidate (`DEBUG-01`) |

## 2. Constraints

- **The version set is frozen and moves as one set.** Kotlin, KSP, Compose Multiplatform, Room,
  and AGP are pinned together in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml)
  (currently Kotlin `2.2.20` / KSP `2.2.20-2.0.4` / Compose Multiplatform `1.10.3` / Room
  `2.8.4` / AGP `8.7.3`; KSP **must** be `<kotlin>-<ksp>` or Room's KMP native compilation
  breaks). Bump the set together via `npx create-cmp-cli upgrade` (proven-green sets), never
  one library at a time — an isolated bump is how the native build gets `MainKt`/`Continuation`
  link errors. `[advisory — no version-drift gate ships yet]`
- **Platform commitment:** Kotlin Multiplatform + Compose Multiplatform, one shared UI/logic
  tree across Android and iOS (`composeApp/src/commonMain`). Android `minSdk 24` / `compileSdk
  35`; iOS deployment target `16.0` (`iosApp/Podfile`). A `jvm("desktop")` target exists too —
  see §4, it is harness infrastructure, not a shipped app target.
- **The harness conventions are the baseline** — Clean Architecture, hand-written fakes, a
  verify-lane definition of done — recorded in
  [`docs/adr/0001-adopt-the-create-cmp-harness-conventions.md`](./adr/0001-adopt-the-create-cmp-harness-conventions.md).
  Deviating from a convention gets its own ADR, not a silent drift.

## 3. System context

```
   User ──taps/reads──▶ This app (Android APK · iOS framework)
                              │           │            │
                        Firebase       Room       NetworkMonitor
                     (GitLive SDK:  (on-device    (platform connectivity,
                      auth/firestore/ SSOT —        StateFlow<Boolean>)
                      functions/    AppDatabase,
                      storage)      ItemDao)

   Development-time only — never in a release build or on a user's device:
      Debug inspector HTTP server            QA harness
      (androidDebug, loopback-only,          (Maestro E2E, desktop preview
       adb-forwarded)                         daemon) — drives the app
                                               from the outside
```

| Integration | What | Where in the tree | Notes |
|---|---|---|---|
| Firebase | Auth / Firestore / Functions / Storage via the GitLive KMP SDK | `data/remote/FirebaseConfig.kt`, wired at `initKoin()`/`AppApplication`/`KoinHelper` | Emulator-backed in debug builds (`configureFirebaseEmulators()`); `google-services.json` ships as a placeholder — wire the real project before shipping. |
| Room | On-device SSOT — `AppDatabase`, `ItemDao` | `data/local/*.kt` | Registered in DI on every platform (`single<AppDatabase>`); the exemplar's `ItemRepositoryImpl` does not yet read/write it — see §1's offline row. |
| NetworkMonitor | Platform connectivity as `StateFlow<Boolean>` | `core/connectivity/NetworkMonitor.kt` (expect) + one `actual` per platform | Registered in DI (`single { NetworkMonitor(...) }`); not yet consumed by a repository — available, not wired. |
| Debug inspector HTTP server | Loopback-only (`ServerSocket`, never the LAN) structural/crash/DB inspection endpoint for the AI verification loop | `composeApp/src/androidDebug/kotlin/.../inspector/*.kt` | **Never compiled into `androidRelease`** — a separate no-op twin ships there (source-set placement, not a runtime flag). |
| QA harness | Maestro E2E flows + the desktop preview daemon | `qa/e2e/*.yaml`, `composeApp/src/desktopMain/.../inspector/PreviewHarness.kt` | Development/CI-time actors, never shipped to a device. |

## 4. Platform & deployment view

**Source-set map** (`composeApp/src/`):

| Source set | Role |
|---|---|
| `commonMain` | Shared UI + logic — presentation, domain, data, di, core. The vast majority of the app lives here. |
| `commonTest` | Unit tests (kotlin-test + coroutines-test + Turbine), hand-written fakes (`testing/fakes/`). |
| `androidMain` | Android entry point (`AppApplication`), platform `actual`s. |
| `androidDebug` | Debug-only additions layered on `androidMain` — the inspector HTTP server, crash recorder, DB inspector. Never in `androidRelease`. |
| `androidRelease` | Release-only twins (currently a no-op inspector stub) that make the `androidDebug` additions compile out cleanly. |
| `iosMain` | iOS entry point (`MainViewController.kt`, `KoinHelper.kt`), platform `actual`s. |
| `desktopMain` | The JVM tier: dev-client window/hot-reload, the preview-render harness, and desktop DI — **harness infrastructure**, not a shipped app target (project ADR `0003`). |
| `desktopTest` | Conformance gates (this document's enforced clauses), Compose UI Tests, golden-tree structural baselines — the fast, device-free verification tier. |

**The expect/actual boundary.** Every shared declaration, with one `actual` per platform
that needs it. Each `actual` wraps that platform's own connectivity, persistence, or
automation API — open the file for the concrete mechanism. `AppDatabaseConstructor`'s
`actual` is generated by the Room KSP compiler plugin at build time, so no source file
appears for it:

<!-- cmp:generated expect-actual-table -->
| Declaration | commonMain (expect) | androidMain (actual) | iosMain (actual) | desktopMain (actual) |
|---|---|---|---|---|
| `AppDatabaseConstructor` | `data/local/AppDatabase.kt` | _(Room KSP-generated — no actual in source)_ | _(Room KSP-generated — no actual in source)_ | _(Room KSP-generated — no actual in source)_ |
| `Modifier.exposeTestTagsForAutomation()` | `presentation/components/TestTagAutomation.kt` | `presentation/components/TestTagAutomation.android.kt` | `presentation/components/TestTagAutomation.ios.kt` | `presentation/components/TestTagAutomation.desktop.kt` |
| `getDatabaseBuilder()` | `data/local/DatabaseBuilder.kt` | `data/local/DatabaseBuilder.android.kt` | `data/local/DatabaseBuilder.ios.kt` | `data/local/DatabaseBuilder.desktop.kt` |
| `NetworkMonitor` | `core/connectivity/NetworkMonitor.kt` | `core/connectivity/NetworkMonitor.kt` | `core/connectivity/NetworkMonitor.kt` | `core/connectivity/NetworkMonitor.desktop.kt` |
<!-- /cmp:generated -->

**iOS topology:** the Kotlin framework (`composeApp`) is consumed by an Xcode project generated
from `iosApp/project.yml` (XcodeGen) with dependencies via CocoaPods (`iosApp/Podfile`,
deployment target `16.0`). `MainViewController.kt` bridges into `ContentView.swift`;
`KoinHelper.kt` starts the shared Koin graph from Swift. Build by opening
`iosApp/iosApp.xcworkspace` — never the `.xcodeproj` directly, or CocoaPods dependencies won't
resolve.

**Desktop's role:** the `jvm("desktop")` target is unconditional harness infrastructure —
present regardless of feature flags — hosting `desktopTest` (this document's gates) and,
if the dev-client feature is enabled, an interactive hot-reload window. It is not a shipped
release target. See project ADR
[`0003-jvm-desktop-target-is-harness-infrastructure.md`](./adr/0003-jvm-desktop-target-is-harness-infrastructure.md).

## 5. Building blocks — the layer model

```
┌──────────────────────────────────────────────────────────────────┐
│ presentation   Screens (Compose) · ViewModels · the component     │
│                registry (presentation/components/)                │
│                └─ depends on domain only [enforced: ARCH-01]      │
├──────────────────────────────────────────────────────────────────┤
│ domain         models · repository INTERFACES · use cases ·       │
│                AppResult/DomainError — imports nothing             │
│                app-internal [enforced: ARCH-02]                    │
├──────────────────────────────────────────────────────────────────┤
│ data           local/  (Room: AppDatabase, ItemDao)                │
│                remote/ (repository implementations, FirebaseConfig)│
│                never reaches into presentation or di               │
│                [enforced: ARCH-09]                                 │
├──────────────────────────────────────────────────────────────────┤
│ core           leaf utility code (connectivity, format) —          │
│                importable by every layer above; imports domain     │
│                at most, never presentation/data/di                 │
│                [enforced: ARCH-10]                                 │
└──────────────────────────────────────────────────────────────────┘
           di/ wires data implementations to domain interfaces (Koin) —
           the one place allowed to import both.
```

Every arrow in that box is a cited rule, not a wish:

- `presentation → domain` only; ViewModels call **use cases**, never repositories directly
  `[enforced: ARCH-01]`.
- `domain` is pure Kotlin — no Compose, no Koin, no platform types
  `[enforced: ARCH-02]`.
- `data` implements domain's repository interfaces and never reaches upward into
  `presentation` or `di` `[enforced: ARCH-09]`.
- `core` is leaf utility code, importable by every other layer; it imports `domain` at most,
  never `presentation`/`data`/`di` `[enforced: ARCH-10]`.
- The typed error boundary between `data` and everything above it
  `[enforced: ARCH-06/07/08 — see §7]`.

<!-- cmp:generated layer-file-inventory -->
- `presentation/` — commonMain: `App.kt`, `brand/FuelledLogo.kt`, `components/AppBottomBar.kt`, `components/AppButton.kt`, `components/AppHeader.kt`, `components/BaseScreen.kt`, `components/ContentStateContainer.kt`, `components/ContentUiState.kt`, `components/EmptyState.kt`, `components/ErrorState.kt`, `components/ListItemCard.kt`, `components/PlaceholderScreen.kt`, `components/ScreenColumn.kt`, `components/Shimmer.kt`, `components/TestTagAutomation.kt`, `foods/FoodDetailScreen.kt`, `foods/FoodsScreen.kt`, `home/DetailScreen.kt`, `home/HomeScreen.kt`, `home/HomeViewModel.kt`, `navigation/AppNavHost.kt`, `navigation/AppShell.kt`, `navigation/AppTab.kt`, `navigation/NavInspectionHook.kt`, `navigation/Screen.kt`, `profile/ProfileScreen.kt`, `supplements/SupplementsScreen.kt`, `theme/DesignToken.kt`, `theme/Shape.kt`, `theme/Theme.kt`, `theme/Tokens.kt`, `theme/Typography.kt`, `today/TodayScreen.kt`; androidMain: `components/TestTagAutomation.android.kt`; iosMain: `components/TestTagAutomation.ios.kt`; desktopMain: `components/TestTagAutomation.desktop.kt`
- `domain/` — commonMain: `model/DomainError.kt`, `model/Item.kt`, `repository/ItemRepository.kt`, `result/AppResult.kt`, `usecase/GetItemsUseCase.kt`
- `data/` — commonMain: `AppResultCatching.kt`, `local/AppDatabase.kt`, `local/DatabaseBuilder.kt`, `local/ItemDao.kt`, `remote/FirebaseConfig.kt`, `remote/ItemRepositoryImpl.kt`; androidMain: `local/DatabaseBuilder.android.kt`; iosMain: `local/DatabaseBuilder.ios.kt`; desktopMain: `local/DatabaseBuilder.desktop.kt`
- `core/` — commonMain: `connectivity/NetworkMonitor.kt`, `format/Format.kt`; androidMain: `connectivity/NetworkMonitor.kt`; iosMain: `connectivity/NetworkMonitor.kt`; desktopMain: `connectivity/NetworkMonitor.desktop.kt`
- `di/` — commonMain: `AppModule.kt`; androidMain: `AndroidModule.kt`; desktopMain: `DesktopModule.kt`
<!-- /cmp:generated -->

## 6. Runtime view

**The UDF loop:** `Screen` collects `StateFlow<UiState>` from its ViewModel → user intent calls
a ViewModel function → the ViewModel invokes a use case → repository → sources → new immutable
`UiState` is emitted. No state lives in composables beyond UI-local concerns.

Three named scenarios ground that loop in what actually happens on this codebase:

1. **Cold start.** `AppApplication.onCreate()` (Android) / `KoinHelper.initKoin()` (iOS) starts
   the Koin graph — DI modules register, `NetworkMonitor` and `AppDatabase` come online — then
   the Compose entry point (`MainActivity`/`MainViewController`) composes `App()`, which themes
   and hosts `AppNavHost()`; the NavHost's shell destination renders `AppShell` (bottom nav) with
   the first tab's screen.
2. **Load with typed-failure handling.** A screen's `init { load() }` calls a use case, which
   calls the repository. The repository's I/O runs inside `suspendRunCatching`
   `[enforced: ARCH-08]`, which returns `AppResult.Success` or maps the failure to a typed
   `DomainError` — never throws. The ViewModel folds the result into `ContentUiState`
   (`Loading`/`Content`/`Empty`/`Error`) `[enforced: ARCH-07]` and the screen renders the
   matching arm via `ContentStateContainer`. **Not yet wired:** a cache-first branch through
   Room when `NetworkMonitor.isOnline` is false. Both pieces of infrastructure exist (§3, §7);
   no repository reads them today — hence the offline goal's `[advisory]` status in §1.
3. **Navigate + process death.** `AppNavHost` (single-Activity/single-`ComposeUIViewController`)
   owns the back stack; each screen's `ViewModel` (scoped via `viewModelOf`/Koin) survives
   configuration change but not process death — a killed-and-restored process re-runs cold
   start and reloads from the repository, the same path as scenario 1.

## 7. Crosscutting policies

### Error handling `[enforced: ARCH-06/07/08]`

Failures cross layer boundaries as **typed results, never exceptions**:

- **`AppResult<T>`** (`domain/result/AppResult.kt`) is the boundary type: `Success(value)` or
  `Failure(error: DomainError)`. One-shot repository operations return it; they never throw.
  (Deliberately not `kotlin.Result` — its untyped `Throwable` would put raw exceptions right
  back on the boundary.)
- **`DomainError`** (`domain/model/DomainError.kt`) is the typed failure vocabulary — KINDS
  only (`Network`, `NotFound`, `Unexpected(cause)`), no message strings. Extend it with the
  kinds your sources actually produce.
- **The repository implementation is the only translation point.** I/O runs inside
  `suspendRunCatching` (`data/AppResultCatching.kt`), which maps infrastructure exceptions to
  `DomainError` via its `mapError` classifier and **always rethrows `CancellationException`**
  — swallowing cancellation breaks structured concurrency (a closed screen would render an
  error instead of just stopping). `suspendRunCatching` is the data layer's *only* allowed
  catch mechanism (enforced: ARCH-08); ad-hoc `try`/`catch` in `data/` fails the gate.
- **ViewModels contain no `try`/`catch`** (enforced: ARCH-07). They `when` over the
  `AppResult` into a **sealed UiState** (`Loading` / `Content` / `Empty` / `Error`) —
  impossible states are unrepresentable. User-facing error copy is mapped in presentation
  from the `DomainError` kind (see the exemplar's `toUserMessage()`); a raw
  `Throwable.message` never reaches the UI.

The sealed-UiState shape and the `toUserMessage()` placement are `[advisory]` convention,
carried by the exemplar and its tests, not gated.

### Threading (main-safety policy) `[advisory — THREAD-01 staged, not shipped]`

**Repositories are main-safe by delegation, not by ceremony.** Every I/O path the scaffold
ships is main-safe under its own library's contract, so the template injects no dispatcher:

- **Room suspend DAO calls** (`data/local/ItemDao.kt` — all `suspend fun`s): Room executes
  suspending queries on its own background executor; calling them from `Dispatchers.Main` is
  safe by Room's documented contract.
- **GitLive Firebase suspend APIs** (when you wire them into `data/remote/`): suspending
  wrappers over the async native SDKs — main-safe by the SDK's contract.
- **The example source** (`data/remote/ItemRepositoryImpl.kt`): only `delay()` (a suspension,
  not a block) and list construction.

The rule when you add a source that does NOT carry such a guarantee (JDBC, direct file I/O,
heavy parsing, any blocking call): inject a `CoroutineDispatcher` into the repository via
Koin (default `Dispatchers.IO`) and wrap the blocking work in `withContext(dispatcher)` —
injected, not hardcoded, so tests can pass a test dispatcher. Do **not** add `withContext`
around calls that are already main-safe by contract; it's dead weight that hides where the
real guarantee lives.

### DI `[advisory]`

One Koin module per concern: `repositoryModule` / `useCaseModule` / `viewModelModule`
(`di/AppModule.kt`, common to every platform), plus one platform module for platform-only
bindings (`AndroidModule.kt`, `DesktopModule.kt`; iOS wires its platform singletons inline in
`KoinHelper.kt`). **Constructor injection only** — see `GetItemsUseCase(repository:
ItemRepository)` and `HomeViewModel(getItems: GetItemsUseCase)`; no field/property injection,
no service-locator lookups inside domain or presentation code. Platform modules provide
`actual`-backed singletons (`NetworkMonitor`, `AppDatabase`) that common modules then depend on
via the domain-facing interface, not the concrete platform type.

### Logging `[advisory — no shared logger ships today]`

The scaffold ships **no cross-platform logging library**. The only logging present is Koin's
`androidLogger()` (DI diagnostics, Android only) and `android.util.Log` calls confined to
the debug-only `inspector` package (§3) — both Android-specific, neither usable from
`commonMain`. If you need cross-platform application logging, add a KMP logging library
(e.g. Kermit) behind a thin interface in `core/`, inject it via Koin, and never call a
platform logger directly from `commonMain`. Nothing enforces this yet; it is a documented
gap.

### expect/actual `[advisory]`

Shared code that needs a platform capability declares an `expect` in `commonMain` and one
`actual` per consuming platform — see §4's table (`NetworkMonitor`, `getDatabaseBuilder()`,
`AppDatabaseConstructor`, `Modifier.exposeTestTagsForAutomation()`). Convention, not a gate:
put the `expect` next to the domain-shaped contract it serves (`core/` or `data/local/`, not a
grab-bag `platform/` package), and keep the `actual`'s signature a mechanical mirror — logic
differences belong behind the shared type, not in divergent call sites.

### Persistence `[advisory]`

Room (`data/local/AppDatabase.kt`, `ItemDao.kt`) is the on-device single source of truth,
built via the shared `buildDatabase()` (`data/local/DatabaseBuilder.kt`, using
`BundledSQLiteDriver()` for Room-on-Kotlin/Native) and registered in DI on every platform.
It is wired but **not yet consumed**: `ItemRepositoryImpl` returns in-memory sample data,
not a Room-backed cache (see §1, §6). When you wire a real persistence path, the repository
stays the only place that talks to `AppDatabase`/`ItemDao` — domain never sees Room types;
map `ItemEntity` → the domain `Item` inside `data/` — and schema changes get a `version`
bump. `fallbackToDestructiveMigration` is a scaffold-stage convenience; replace it before
shipping user data you can't afford to lose.

### Design tokens `[enforced: ARCH-05]`

`presentation/theme/` (`Tokens.kt`, `Theme.kt`, `Typography.kt`, `Shape.kt`) is the only source
of design values — no hardcoded `Color(0x…)` literals outside it. The registry's own
components own the token call sites (declared once per component, correct everywhere it's
used), so a screen almost never touches a token directly.

### Automation reachability `[enforced: ARCH-04, ARCH-11, SHELL-04]`

Every screen root and interactive element is testTag-addressable
(`presentation/components/TestTagAutomation.kt` exposes tags to both platforms' UI-automation
layers) — either a literal `testTag`, or `screenTag =` wiring into a registry component, whose
derived tags (`<screenTag>_screen`/`_title`/`_loading`/`_error`/`_retry`/`_empty`) count as the
same automation-reachability. Loading is never hand-rolled (`ARCH-11`) — no screen outside
`presentation/components/` references `CircularProgressIndicator`/`LinearProgressIndicator`
directly; bind the loading arm to `ContentStateContainer` instead.

### Insets `[enforced: SHELL-03, SHELL-05]`

Insets are owned by `BaseScreen`/`AppShell` — new screens compose inside `BaseScreen` and never
re-solve edge-to-edge padding with a direct inset API call.

## 8. Decisions & glossary

<!-- cmp:generated adr-index -->
| ADR | Title | Status |
|---|---|---|
| [0001](./adr/0001-adopt-the-create-cmp-harness-conventions.md) | Adopt the create-cmp harness conventions | accepted |
| [0002](./adr/0002-maestro-over-appium-for-e2e.md) | Maestro over Appium for E2E | accepted |
| [0003](./adr/0003-jvm-desktop-target-is-harness-infrastructure.md) | The JVM desktop target is harness infrastructure | accepted |
| [0004](./adr/0004-fakes-not-mocks-for-unit-tests.md) | Fakes, not mocks, for unit tests | accepted |
<!-- /cmp:generated -->

<!-- cmp:generated glossary -->
_Lifted verbatim from the `## Glossary` section of [`specs/intent.md`](../specs/intent.md) — edit it there, not here; this block is regenerated from it.

- **Food** — a catalog entry with nutritional values per serving (kcal, protein, carbs, fat).
- **Serving** — a portion of a Food with its own gram weight and scaled macros.
- **Entry** — a Food logged at a quantity, at a time, on a given day; entries sum into the
  day's totals.
- **Meal** — a grouping of entries within a day (Breakfast · Lunch · Dinner · Snack).
- **Macro** — a tracked macronutrient: protein, carbs, or fat.
- **Goal** — the user's daily targets: a calorie target and a protein goal.
- **Supplement** — a planned intake (e.g. creatine, whey) with a schedule and reminder.
<!-- /cmp:generated -->

## The exemplar feature (`home` by default, configurable)

The **configured exemplar** — `exemplarFeature` in `qa/approvals.json`, `home` on a fresh
scaffold — is the **reference implementation** of every pattern above, including its tests
(`commonTest`). The genesis walk typically promotes your own first feature to exemplar (see
`CLAUDE.md`'s genesis section); `qa/scaffold-feature.mjs` then clones from *it*, and `home`
demotes to a regular feature. To add a feature, mirror the exemplar exactly:

1. Domain: model + repository interface + use case (+ tests).
2. Data: repository implementation (+ test through the domain contract).
3. Presentation: `<Feature>Screen` composed from the component vocabulary
   (`presentation/components/*.kt` — `ScreenColumn` for the root, `AppHeader` for the
   title, `ContentStateContainer` for the loading/content/empty/error dispatch,
   `ListItemCard` for rows) + `<Feature>ViewModel` with a `StateFlow<ContentUiState<T>>`
   (no `try`/`catch`, fold over `AppResult`, `List<E>.toContentState()` for the
   empty/content split) (+ test using a fake from `testing/fakes/`).
4. DI: register in `di/AppModule.kt`.
5. Navigation: add the route in `presentation/navigation/`.
6. Run `node qa/verify.mjs` — done means PASS + committed receipt.

Significant decisions get an ADR in [`docs/adr/`](./adr/) — see the template there.
