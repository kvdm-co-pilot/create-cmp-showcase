# Agent instructions

This repository is agent-first, with a verification harness. The working contract — the
definition of done (`node qa/verify.mjs` must PASS, receipt committed), the architecture
gates, and the device-free **UI feedback loop** — lives in [CLAUDE.md](./CLAUDE.md).

Read CLAUDE.md before making changes. It applies to every coding agent, not only Claude.

## Stuck? Symptom → command

Every command below runs from the repo root with **nothing to install**: Gradle is
wrapped, the scripts ship inside this project, and `npx` fetches on demand. The
create-cmp Claude Code plugin layers better ergonomics over the same capabilities
(skills, the `cmp-inspector` MCP's structured tools) — an accelerator, never a
prerequisite.

| Symptom | Run |
|---|---|
| "Did my edit break anything?" | `node qa/verify.mjs --fast` — the inner loop: seconds of JVM-tier signal; never the done-gate |
| Want that answer on every save | `node qa/watch.mjs` — resident watcher; re-runs the fast tier on save, debounced |
| Can't see the UI (no device attached) | `./gradlew :composeApp:renderScreens && node qa/preview-gallery.mjs` — every real screen headless: `tree.json` for you to assert on, one gallery page for the human |
| Need the RUNNING app's real state | `adb forward tcp:9500 tcp:9500`, then `http://127.0.0.1:9500/inspect/remote` — debug builds serve the live semantics tree on loopback |
| Adding a feature / screen / repository | `node qa/scaffold-feature.mjs <Name>` — clones the tested exemplar through every layer; never freehand the pattern (skills: `add-feature`, `add-screen`, `add-repository`) |
| A gate failed and looks arbitrary | `node qa/refusal-demo.mjs` — stages canonical violations so each gate names the clause it protects |
| Where are we / whose turn is it | `node qa/walk-status.mjs` — every open walk's stage card (Decide·Design·Contract·Build·Prove·Sign-off), whose turn, what arrived unplanned; `--statusline` is the one-liner |
| Build broken, toolchain suspect | `npx create-cmp-cli doctor --fix` — diagnoses machine AND project (kotlin↔ksp lockstep, catalog drift); asks before any repair |
| Dependency versions stale or mismatched | `npx create-cmp-cli upgrade --dry-run` — diff against the next proven-green set before touching anything |
| Ready to claim done | `node qa/verify.mjs` — the full lane, once, deliberately; commit the receipt it writes |

Famous build failures (kotlin↔KSP mismatch, the KSP2/iOS catch-22, `SDK location not
found`, `No space left on device`): `doctor` diagnoses all of them offline; the worked
write-ups live upstream at
<https://github.com/kvdm-co-pilot/create-cmp/tree/main/docs/errors>.

One rule before you edit anything: the `.mjs` files directly under `qa/` and `qa/lib/`
are **machine-owned** harness code — byte-identical in every create-cmp app and
hash-locked by `qa/harness.lock.json`. Editing them fails the lane's first step. If the
lane is wrong, the fix is upstream — see "The lane is not yours to edit" in CLAUDE.md.
