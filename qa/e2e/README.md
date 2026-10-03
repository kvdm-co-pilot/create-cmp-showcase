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
# The lane runs EVERY flow in this directory (e2eSmoke), on the DEBUG build, and
# boots a headless emulator itself when nothing is attached:
node qa/verify.mjs

# By hand, one flow, against whatever is installed:
./gradlew :composeApp:installDebug
maestro test qa/e2e/smoke.yaml
```

- **Every top-level `*.yaml` here runs**, in one Maestro session; the receipt's e2eSmoke row
  lists each flow's result (`details.results`). A flow in a subfolder does not run and does
  not count as coverage — the executed list and the coverage scan read the same list.
- **The device**: an attached device is used as-is. With none attached the lane boots an
  emulator headless — `CMP_AVD`, else the doctor's `cmp_pixel`, else the only AVD — waits
  (bounded, 4 min) and shuts it down when the lane exits (`CMP_KEEP_DEVICE=1` keeps it up).
  A device that cannot be provisioned is an ERROR row and the lane FAILs. `CMP_DEVICE=none`
  is the one explicit opt-out; a receipt carrying it is refused as done-evidence.
- **Per feature**: `qa/scaffold-feature.mjs` stamps `qa/e2e/<feature>.yaml` — a passing
  skeleton (launch + shell) naming the screen id and the clauses to prove. It cites nothing
  until you make it the journey. Mark clauses only a device can observe `[tier: e2e]` in
  the spec; specCoverage then fails by name until a flow cites them.
- **The gate**: `e2eCoverage` (pure Node, every profile). A feature with a screen and a spec
  must have at least one live clause cited from a flow here — so a stamped skeleton FAILs the
  lane by name until it is the journey. A screen with no spec is a placeholder (reported);
  a screen declared `{ "unrouted": true }` in its brief is exempt. A `screens: true` brief is
  likewise not done until one of its clauses is proven by a flow.

## Conventions

- **Selectors by testTag** (`id:` — TestTagAutomation surfaces tags as resource-ids on
  Android and accessibility ids on iOS); visible text only for content assertions.
- Every flow cites the spec clauses it verifies (`# SPEC: SHELL-01`).
- One flow per journey; deterministic start (`clearState: true`).
