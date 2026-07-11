# Architecture

Clean Architecture, three layers, one rule: **dependencies point inward.**

```
┌─────────────────────────────────────────────────────┐
│ presentation   Screens (Compose) · ViewModels        │
│                └─ depends on domain only             │
├─────────────────────────────────────────────────────┤
│ domain         models · repository INTERFACES ·      │
│                use cases — imports nothing app-internal │
├─────────────────────────────────────────────────────┤
│ data           repository implementations ·          │
│                remote/local sources                  │
└─────────────────────────────────────────────────────┘
           di/ wires implementations to interfaces (Koin)
```

- `presentation` never imports `data`. ViewModels call **use cases**, not repositories.
- `domain` is pure Kotlin — no Compose, no Koin, no platform types.
- `data` implements the domain's repository interfaces; sources stay behind them.

## Data flow (unidirectional)

`Screen` collects `StateFlow<UiState>` from its ViewModel → user intent calls a ViewModel
function → the ViewModel invokes a use case → repository → sources → new immutable `UiState`
is emitted. No state lives in composables beyond UI-local concerns.

## The exemplar: the `home` feature

`presentation/home` + `domain/{model,repository,usecase}` + `data/remote` is the **reference
implementation** of the pattern — including its tests (`commonTest`). To add a feature, mirror
it exactly:

1. Domain: model + repository interface + use case (+ tests).
2. Data: repository implementation (+ test through the domain contract).
3. Presentation: `<Feature>Screen` (testTag-rooted) + `<Feature>ViewModel` with
   `StateFlow<UiState>` (+ test using a fake from `testing/fakes/`).
4. DI: register in `di/AppModule.kt`.
5. Navigation: add the route in `presentation/navigation/`.
6. Run `node qa/verify.mjs` — done means PASS + committed receipt.

## Conventions

- **Theme tokens** (`presentation/theme/`) are the only source of design values — no hardcoded
  colors/spacing/radii in screens.
- **testTags** on every screen root and interactive element (`TestTagAutomation` exposes them
  to E2E tooling on both platforms).
- **Insets** are owned by `BaseScreen` — new screens compose inside it and never re-solve
  edge-to-edge padding.
- Significant decisions get an ADR in [`docs/adr/`](./adr/) — see the template there.
