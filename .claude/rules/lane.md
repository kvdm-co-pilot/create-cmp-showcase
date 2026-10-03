---
paths:
  - "qa/*.mjs"
  - "qa/lib/**/*.mjs"
  - "qa/harness-manifest.json"
  - "qa/verified-surface.json"
---

## The lane is not yours to edit

Every `.mjs` file directly under `qa/` and `qa/lib/`, every `.mjs` under
`qa/lib/profiles/**` (this app's stack profile — its steps, tiers, ladder and device
glue), and the declarations the lane reads (`qa/harness-manifest.json`,
`qa/verified-surface.json`) are **machine-owned**: harness code that is byte-identical in
every create-cmp app and carries no app content at all. It
belongs to `prooflane-harness` (the name in your `qa/harness.lock.json`), versioned independently of the engine that stamped this
app's shape, and `qa/harness.lock.json` records a sha256 of every one of those files.

`node qa/verify.mjs` checks that lock first, on every run. Editing lane code fails the
`harnessIntegrity` step and names the file — because a lane that has been modified cannot
honestly vouch for itself. Without that check the receipt was unfalsifiable in one
specific way: force every step to PASS in `qa/verify.mjs` and the receipt still validated,
since the edited file was simply part of the hashed input surface.

**So: do not edit `qa/*.mjs`, `qa/lib/*.mjs`, `qa/lib/profiles/cmp/**`, `qa/verified-surface.json`, or
`qa/harness-manifest.json`.** (A profile you wrote yourself under `qa/lib/profiles/<id>/` is yours; after
editing it, `npx create-cmp-cli harness relock` re-takes the lock.) If the lane is wrong, the fix is
upstream in the engine, not here. If you genuinely must fork it, know that
`npx create-cmp-cli upgrade --harness` will replace the region and preserve your edits as
`qa/harness-local.patch` for you to re-apply or upstream — nothing is lost, but the fork
stops being invisible.

Everything else under `qa/` **is** yours: `approvals.json`, `comments.json`, `golden/`,
`evidence/`, and `e2e/*.yaml` (seeded once at stamp time, app-owned forever after). So is
`specs/`, and so is every line under `composeApp/src/`.

Upgrading the lane is safe to do unattended — it touches no app content and no signed
artifact:

```bash
npx create-cmp-cli upgrade --harness --yes   # without --yes it dry-runs and exits 0 — a silent no-op from a tool
```
