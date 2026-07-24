# Spec: app base — architecture & shell

> Clauses the scaffold itself guarantees. ARCH clauses are enforced by the conformance gates
> (dependency-free source scans); SHELL clauses by UI tests and the E2E smoke flow.

## Architecture invariants

- **ARCH-01** — Given any file in `presentation`, When its imports **and fully-qualified
  inline references** are inspected, Then none resolve into the `data` layer (presentation
  depends on domain only).
- **ARCH-02** — Given any file in `domain`, When its imports **and fully-qualified inline
  references** are inspected, Then none resolve into `presentation`, `data`, or `di`, and
  none reference Compose, Koin, or platform types (domain is pure Kotlin).
- **ARCH-03** — Given any ViewModel class, When the test sources are inspected, Then a
  corresponding `*ViewModelTest` exists (no untested presentation state).
- **ARCH-04** — Given any file in a `presentation` feature package that contains a
  `@Composable` function, When its source is inspected, Then it declares at least one
  literal `testTag` **or** passes a `screenTag =` argument to a component imported from
  `presentation.components` (scoped by content, not `*Screen.kt` filename — split
  `Content.kt` UI files are covered, ViewModel-only files are exempt). Component-derived
  tags count as tag provenance: a screen built entirely from `ScreenColumn`/`AppHeader`/
  `ContentStateContainer`/etc. is automation-reachable through the tags those components
  emit (`<screenTag>_screen`, `<screenTag>_title`, `<screenTag>_loading`, …) even without a
  literal `testTag` of its own.
- **ARCH-05** — Given any file outside `presentation/theme`, When its source is inspected,
  Then it constructs no literal `Color(0x…)` values (design colors come from the token
  catalog).
- **ARCH-06** — Given any repository interface in `domain/repository`, When its one-shot
  operations (`suspend fun`s) are inspected, Then each declares an `AppResult<…>` return
  type — raw exceptions never cross the data → domain boundary; failures travel as typed
  `DomainError` values assigned inside the data implementation.
- **ARCH-07** — Given any ViewModel in `presentation`, When its source is inspected, Then it
  contains no `try`/`catch`/`runCatching` — ViewModels fold over `AppResult` and map
  `DomainError` kinds to user-facing copy; a raw exception message is never shown to a user.
- **ARCH-08** — Given any file in the `data` layer, When its source is inspected, Then the
  only exception-catching mechanism is the shared `suspendRunCatching` helper
  (`data/AppResultCatching.kt`), and the helper always rethrows `CancellationException` —
  cancellation is never swallowed into a failure state.
- **ARCH-09** — Given any file in `data`, When its imports and fully-qualified inline
  references are inspected, Then none resolve into `presentation` or `di` (data serves
  domain contracts; it never reaches upward).
- **ARCH-10** — Given any file in `core`, When its imports and fully-qualified inline
  references are inspected, Then none resolve into `presentation`, `data`, or `di` (core
  is leaf utility code; `domain` at most).
- **ARCH-11** — Given any file in a presentation feature package (`components/` excluded),
  When its source is inspected, Then it references neither `CircularProgressIndicator` nor
  `LinearProgressIndicator` directly — loading is presented through the components
  registry (`ContentStateContainer`/`ContentStateDefaults`), never hand-rolled per screen.
- **ARCH-12** — Given a `sample*` preview fixture declared in a `commonMain` presentation
  file, When any OTHER `commonMain` file references it, Then the conformance gate fails —
  a sample is the UI-first preview seam (the stateless screen's own default parameter,
  plus the preview registry/stories and tests), never production wiring. Fake data
  resolving a nav route or seeding a repository is exactly the drift this stops.

## App shell

- **SHELL-01** — Given a fresh install, When the app launches, Then the first tab's screen
  renders inside the shell with the bottom navigation visible (`app_bottom_nav`).
- **SHELL-02** — Given the shell is visible, When the user taps another tab in the bottom
  nav, Then that tab's screen renders and the bottom nav stays visible.
- **SHELL-03** — Given any tab screen, When it renders, Then its content is laid out inside
  the safe-area insets owned by `BaseScreen` (edge-to-edge without overlap).
- **SHELL-04** — Given the app renders any screen, When interactive elements are present,
  Then each is perceivable by automation: it exposes a testTag, text, or content description.
- **SHELL-05** — Given any screen registered directly on the NavHost (not a shell tab), When
  it renders, Then its content is composed inside `BaseScreen`.

## Component vocabulary

> Component *contracts* — the shared state/a11y behavior every screen inherits from
> `presentation/components/*.kt` (the governed `components` artifact). Feature clauses
> (e.g. `HOME-NN`) keep citing feature behavior; these clauses are covered once, here.

- **COMP-01** — Given any screen with a data-backed state, When it renders, Then
  loading/error/empty are presented by `ContentStateContainer` with tags
  `<screen>_loading` / `<screen>_error` / `<screen>_empty`.
- **COMP-02** — Given a recoverable load failure and a retry handler, When the error state
  renders, Then a `<screen>_retry` control of at least 48 dp is present.
- **COMP-03** — Given any interactive registry component, When it renders, Then its
  pointer target is at least 48×48 dp.
