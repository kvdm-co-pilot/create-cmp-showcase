# Agent instructions

This repository is agent-first. The full working contract — the definition of done
(`node qa/verify.mjs` must PASS), the architecture gates, the testing pyramid, and the
device-free **UI feedback loop** (render every real screen headlessly and see exactly
what your edit changed) — lives in [CLAUDE.md](./CLAUDE.md).

Read CLAUDE.md before making changes. It applies to every coding agent, not only Claude.

One rule worth knowing before you touch anything: the `.mjs` files directly under `qa/`
and `qa/lib/` are **machine-owned harness code**, byte-identical in every create-cmp app
and hash-locked by `qa/harness.lock.json`. Editing them fails the lane's first step. Fix
the engine upstream instead — see "The lane is not yours to edit" in CLAUDE.md.
