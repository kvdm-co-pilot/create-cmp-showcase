# ADR-0004: Fakes, not mocks, for unit tests

- **Status:** accepted
- **Date:** (scaffold date)

## Context

Every repository/source interface in this project's domain layer (`domain/repository/*.kt`)
needs a test double for ViewModel and use-case unit tests — see `FakeItemRepository`
(`composeApp/src/commonTest/.../testing/fakes/`), used by `HomeViewModelTest` and
`GetItemsUseCaseTest`. Mocking frameworks (MockK, Mockito) are the conventional default for
this in JVM-only Android projects, but this project is Kotlin Multiplatform: `commonTest` runs
on Android, iOS (native), and desktop (JVM) targets from one source set, and the bytecode-proxy
mechanism mocking frameworks rely on doesn't exist on Kotlin/Native. A mock-based test suite
would either be JVM-only (silently untested on iOS) or need a second, divergent native-only
test strategy.

## Decision

This project uses **hand-written fakes**, never mocking frameworks, for every domain-layer
interface under test. A fake lives in `testing/fakes/` (commonTest), implements the real
interface, and is configurable in the shape the exemplar establishes: a typed
`failure: DomainError?` to force the error path, seeded data for the success path, and
call-recording where a test needs to assert an interaction happened. A fake returns
`AppResult.Failure` when configured to fail — it never throws, because the domain contract it
implements (`domain/repository/*.kt`, ARCH-06) doesn't either.

## Consequences

- One test double works identically on every target `commonTest` runs on — no JVM-only
  behavior gap between what Android/desktop CI proves and what iOS actually ships.
- Fakes are slightly more code up front than a one-line `every { } returns` mock stub, but
  they read as real (if simplified) implementations, which makes failure-path tests
  (`failure = DomainError.Network`) as readable as success-path tests instead of relying on
  stubbing syntax a reviewer has to decode.
- No mocking-framework dependency to keep on the frozen version set (`gradle/libs.versions.toml`)
  or track for Kotlin/Native compatibility on every version bump.
- The cost: a new repository interface needs its fake hand-written before its consumers can be
  tested — there is no "just mock it inline" escape hatch. This is intentional: an interface
  too awkward to fake by hand is usually an interface with the wrong shape.

## Related

- `CLAUDE.md` — "Testing" section: "**Hand-written fakes** in `testing/fakes/` — never mocking
  frameworks", the contract this ADR records the reasoning for.
- `docs/TESTING.md` — "Fakes, never mocks" under Unit conventions.
- `composeApp/src/commonTest/.../testing/fakes/FakeItemRepository.kt` — the exemplar fake this
  ADR's shape is drawn from.
