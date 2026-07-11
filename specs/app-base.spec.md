# Spec: app base — architecture & shell

> Clauses the scaffold itself guarantees. ARCH clauses are enforced by the conformance gates
> (dependency-free source scans); SHELL clauses by UI tests and the E2E smoke flow.

## Architecture invariants

- **ARCH-01** — Given any file in `presentation`, When its imports are inspected, Then none
  resolve into the `data` layer (presentation depends on domain only).
- **ARCH-02** — Given any file in `domain`, When its imports are inspected, Then none resolve
  into `presentation`, `data`, or `di`, and none import Compose, Koin, or platform types
  (domain is pure Kotlin).
- **ARCH-03** — Given any ViewModel class, When the test sources are inspected, Then a
  corresponding `*ViewModelTest` exists (no untested presentation state).
- **ARCH-04** — Given any file containing a `*Screen` composable, When its source is
  inspected, Then it declares at least one `testTag` (every screen is automation-reachable).
- **ARCH-05** — Given any file outside `presentation/theme`, When its source is inspected,
  Then it constructs no literal `Color(0x…)` values (design colors come from the token
  catalog).

## App shell

- **SHELL-01** — Given a fresh install, When the app launches, Then the first tab's screen
  renders inside the shell with the bottom navigation visible (`app_bottom_nav`).
- **SHELL-02** — Given the shell is visible, When the user taps another tab in the bottom
  nav, Then that tab's screen renders and the bottom nav stays visible.
- **SHELL-03** — Given any tab screen, When it renders, Then its content is laid out inside
  the safe-area insets owned by `BaseScreen` (edge-to-edge without overlap).
- **SHELL-04** — Given the app renders any screen, When interactive elements are present,
  Then each is perceivable by automation: it exposes a testTag, text, or content description.
