# ADR-0003: The JVM desktop target is harness infrastructure

- **Status:** accepted
- **Date:** (scaffold date)

## Context

This project's `jvm("desktop")` Gradle target (`composeApp/build.gradle.kts`) hosts the fast,
device-free verification tier — unit tests, the conformance gates this document's clauses are
backed by (`docs/ARCHITECTURE.md` §5/§7), Compose UI Tests, and golden-tree renders, all run
via `./gradlew :composeApp:desktopTest`. It sits alongside an optional interactive dev-client
feature (a hot-reload window, gated by feature flags at scaffold time) that happens to reuse
the same JVM target for its window and Compose Hot Reload wiring.

## Decision

The `jvm("desktop")` target is unconditional harness infrastructure in this project, present
regardless of which optional features were scaffolded, decoupled from the interactive
dev-client experience that merely reuses it. Only the window/hot-reload/foojay pieces specific
to interactive desktop development are feature-gated; the `kspDesktop` and `desktopTest`
dependencies that back the verification tier are unconditional.

## Consequences

- Disabling the dev-client feature never removes verification capability — only the
  interactive desktop window and hot reload go away; `node qa/verify.mjs` and
  `:composeApp:desktopTest` keep working exactly the same.
- The dev-client feature's footprint in the build file is limited to what it truly owns
  (the window, hot-reload wiring); the harness's own test tier is never at risk of being
  pruned by an unrelated feature toggle.
- Anyone editing the `jvm("desktop")` block in `composeApp/build.gradle.kts` should read the
  surrounding comment first — it exists specifically to prevent this target being mistaken
  for optional dev-client scaffolding and deleted along with it.

## Related

- `composeApp/build.gradle.kts`, the `jvm("desktop")` target comment — the in-repo record of
  this decision.
- `docs/ARCHITECTURE.md` §4 ("Platform & deployment view") — desktop's documented role.
