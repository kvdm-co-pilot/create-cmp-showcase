---
paths:
  - "qa/approvals.json"
  - "specs/**"
  - "docs/ARCHITECTURE.md"
  - "composeApp/src/commonMain/kotlin/**/presentation/theme/**"
  - "composeApp/src/commonMain/kotlin/**/presentation/components/**"
  - "composeApp/src/commonMain/kotlin/**/presentation/**/*Screen.kt"
---

## Approvals — governed artifacts need a human's sign-off

Some artifacts are **governed**: a human approves them, and the approval is bound to the
artifact's content by hash (`qa/approvals.json`) — the evidence-receipt idea, applied to a
human decision. The ordered walk is a **definition order**, not just an approval order:
each artifact is the vocabulary the next is written in, so on a fresh app each step is a
conversation that ends in an approval — the genesis walk:

The genesis order — intent → first feature brief → architecture + structure → exemplar spec →
exemplar feature → design system → components → per-feature specs — is driven by the `cmp-new`
interview and is over before CLAUDE.md is your working contract; `node qa/approve.mjs --status`
prints every artifact and its state. Two disciplines survive it: **behavior is spec-first** (clauses
confirmed before the slice is built) and **visuals are UI-first** (the design system and component
vocabulary are distilled from real screens, so they lock after the exemplar — a provisional palette
carries the build until then; if the lock changes the exemplar's look, reopen → re-approve it — that
loop is the design, not a failure). Once approved, the component registry is law: adding or changing
a common component invalidates the approval until a human re-approves.

### Configurable exemplar — the DNA features are cloned from

`qa/approvals.json` carries a top-level `"exemplarFeature"` key (absent means `"home"`, so
older ledgers keep meaning what they meant). It names the feature whose 11-file set is the
governed **exemplar-feature** artifact and the clone source `qa/scaffold-feature.mjs`
stamps new features from. The genesis walk's endgame is pointing it at *your* first real
feature: stamp it (`add-feature`), shape it, then set `exemplarFeature` — from then on the
stamper clones your pattern in your domain language, and `home` demotes to an ordinary
feature spec. If the configured exemplar has grown files beyond the canonical 11-file
shape, the stamper clones the canonical set and warns, listing exactly what it skipped.
