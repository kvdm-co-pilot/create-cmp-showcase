# Behavior specifications

**The spec is the source of truth for intended behavior.** Durable tests (Compose UI Tests,
E2E flows) are its executable projection — every clause maps to at least one test carrying its
id, and every durable test cites the clause it verifies.

## The workflow

1. **New behavior begins as a spec clause.** Before writing code, add a clause to the feature's
   spec (AI proposes, human confirms). Changing behavior = changing the clause first.
2. Implement, mirroring the exemplar. Write/update the durable tests **tagged with the clause
   id** (`// SPEC: HOME-02`). The `// SPEC:` tag convention applies to **all durable tests** —
   not just Compose UI Tests and E2E flows, but `commonTest` unit tests too (ViewModel/UseCase/
   Repository tests carry the tag on the behavior they verify). Conformance tests carry both
   the `// SPEC:` tag comment above the `@Test` annotation **and** the clause id inside the
   failure message's `[CLAUSE-ID]` bracket (see `violation()` in
   `ArchitectureConformanceTest.kt`) — the tag makes the clause greppable from source, the
   bracketed id makes it visible in a failing test's output.
3. `node qa/verify.mjs` — the lane's `specCoverage` step fails orphan clauses (a live clause
   with no citing test) and orphan tags (a `// SPEC:`/`# SPEC:` tag with no matching clause, or
   one citing a withdrawn clause). Withdrawn clauses are exempt from coverage.
4. It also fails a clause whose declared `[tier: …]` no citing test can satisfy — see
   **Tier requirements** below.

## Format

One file per feature: `specs/<feature>.spec.md`. Clauses are Given/When/Then with **stable
ids** (`<FEATURE>-<NN>`) — ids are never renumbered or reused; a withdrawn clause is struck
through and kept. `app-base.spec.md` covers the base architecture and the app shell the
scaffold itself ships.

## Tier requirements — a citation must be able to SEE the clause

A citation proves a test *exists*. It cannot prove that test could ever *observe* the promise,
and those are different things. A clause promising an animation "plays once per process start"
was cited by a desktop Compose test — which has no process lifecycle at all, so the claim was
unobservable there by construction. Coverage was green; nothing had checked anything.

So a clause about device behavior declares the tier it needs, right on the clause line:

```markdown
- **MOTION-13** [tier: device] — Given a warm resume, When the app returns to foreground,
  Then the intro animation does not play again.
```

- `[tier: device]` — satisfied by `androidInstrumentedTest` or `qa/e2e`.
- `[tier: e2e]` — satisfied by `qa/e2e` only.
- No tag — unchanged: any citing test satisfies coverage.

Declare it whenever the claim rests on an OS fact a host JVM cannot see: app/process lifecycle,
alarms, notifications, permissions, audio routing, real navigation. `specCoverage` FAILS when a
declared tier has no citing test, so a device tier that SKIPPED for want of a device leaves the
clause visibly unproven instead of silently passing. That is deliberate: "I could not check
this" is a failure, not a quieter rung on the evidence ladder.

> **Why no Cucumber?** We adopt the Given/When/Then *grammar* but reject the Cucumber
> *runtime*: step-definition glue is a third artifact that drifts from both the spec and the
> tests. Spec and test are bound by **clause id** instead (`// SPEC:` tag + `[CLAUSE-ID]` in
> failure messages) — greppable, machine-checkable, no glue to maintain.
