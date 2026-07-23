# ADR-0002: Maestro over Appium for E2E

- **Status:** accepted
- **Date:** (scaffold date)

## Context

Device E2E is the least AI-load-bearing layer in this project's testing pyramid — the AI
collaborator's real verification instrument is the structural inspector (the preview loop
described in `CLAUDE.md`'s "UI feedback loop"), not black-box UI driving. That reframes what
the E2E layer should optimize for: least brittleness and cross-platform reach, not language
uniformity with the rest of the stack (which is Kotlin end to end everywhere else).

## Decision

This project's sole E2E layer is Maestro YAML flows. Flows live in `qa/e2e/*.yaml`; the verify
lane's `e2eSmoke` step runs `maestro test` against them. Maestro is Apache-2.0, free to run
locally and in CI on both Android and iOS simulators — only the hosted Maestro Cloud device
farm is paid, and this project brings its own emulator/simulator, so it's never needed.
Selectors reference testTags exclusively (surfaced as resource-ids on Android and
accessibility identifiers on iOS via `TestTagAutomation`), never display text, keeping flows
l10n-stable.

## Consequences

- Auto-waits eliminate most flake; one YAML flow drives both platforms with no per-platform
  driver code to maintain.
- Trade-off accepted: Maestro flows are not compile-checked. This project compensates by
  keeping E2E thin (boot + a few critical journeys, see `qa/e2e/smoke.yaml`) and putting
  compile-checked, interaction-level assertions in Compose UI Test instead
  (`composeApp/src/desktopTest`).
- No Appium runner exists in this project — don't add one for a "just this one flow" need;
  extend the Maestro flows or add a Compose UI Test instead.

## Related

- `docs/TESTING.md` — the test pyramid this project uses, Maestro's place in it.
- `CLAUDE.md` — "UI feedback loop", the primary AI verification instrument E2E is deliberately
  thin relative to.
