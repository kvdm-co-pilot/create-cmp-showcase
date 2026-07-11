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

## Format

One file per feature: `specs/<feature>.spec.md`. Clauses are Given/When/Then with **stable
ids** (`<FEATURE>-<NN>`) — ids are never renumbered or reused; a withdrawn clause is struck
through and kept. `app-base.spec.md` covers the base architecture and the app shell the
scaffold itself ships.

> **Why no Cucumber?** We adopt the Given/When/Then *grammar* but reject the Cucumber
> *runtime*: step-definition glue is a third artifact that drifts from both the spec and the
> tests. Spec and test are bound by **clause id** instead (`// SPEC:` tag + `[CLAUSE-ID]` in
> failure messages) — greppable, machine-checkable, no glue to maintain.
