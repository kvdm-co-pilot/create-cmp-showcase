# E2E flows — Maestro

Thin device-level smoke: does the real app boot and do the critical journeys work. Behavior
lives in unit tests; screen behavior in Compose UI Tests; structure in golden trees — keep
this layer small.

## Setup (one-time)

```bash
curl -fsSL "https://get.maestro.mobile.dev" | bash   # Apache-2.0, free CLI
```

## Run

```bash
# Android: emulator/device attached, debug build installed
./gradlew :composeApp:installDebug
maestro test qa/e2e/smoke.yaml

# The verify lane runs this automatically when maestro + a device are present:
node qa/verify.mjs
```

## Conventions

- **Selectors by testTag** (`id:` — TestTagAutomation surfaces tags as resource-ids on
  Android and accessibility ids on iOS); visible text only for content assertions.
- Every flow cites the spec clauses it verifies (`# SPEC: SHELL-01`).
- One flow per journey; deterministic start (`clearState: true`).
