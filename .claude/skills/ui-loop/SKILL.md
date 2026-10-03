---
name: ui-loop
description: >-
  See what you build without a device: render this app's real screens headlessly, get told which
  screens an edit changed, and prove the change — the preview loop (cmp-inspector `preview`,
  `preview_status`, `preview_diff`), its degraded path (`./gradlew :composeApp:renderScreens` +
  `node qa/preview-gallery.mjs`), and the live tier on a running app (`connect_live`,
  `navigate_and_inspect`, `db_query`, `runtime_crashes`). Use this while building or changing
  any screen or common component, when registering a screen in the PreviewRegistry or a story in
  ComponentStories, or when the human wants to watch or drive the running app.
---

## UI feedback loop — see what you build, without a device

While building or changing any screen, use the preview loop instead of an emulator. It
renders this app's real screens (real DI, real theme, seeded data) headlessly in seconds
and tells you what your edit changed.

**With the create-cmp plugin (cmp-inspector MCP tools):**

1. `preview { projectDir }` — once per session. Returns a live gallery URL for the human
   (it re-renders on every save) and per-screen structural summaries for you. Sources are
   watched; you never run Gradle by hand.
2. After each edit: `preview_status { waitForRender: true }` blocks until the outcome.
   `changedLastRender` names the screens your edit touched (empty = the edit reached no
   screen); `lastErrorSource: "compile"` means the edit did not build — the compiler's `e:`
   lines are in `lastError`.
3. `preview_diff { screen }` proves the change in one call: `proven-clean` /
   `changed-with-regressions` / `no-change`. No snapshot bookkeeping.

**If the tools are missing:** capability absence is a fault to diagnose and report — never
a silent fallback. If ToolSearch finds no `cmp-inspector` tools, STOP and tell the human
which it is: the plugin is disabled (`enabledPlugins` in `~/.claude/settings.json` or the
project settings); the session predates the plugin's enablement (MCP servers attach at
session start — restart the session; no in-session retry will surface them); or the plugin
copy is stale/broken (run cmp-doctor's inspector-MCP check group). Report before degrading.

**Degraded path** — for environments where the plugin is genuinely unavailable (CI, other
agents), and only after the fault is reported: `./gradlew :composeApp:renderScreens` renders
every screen to `composeApp/build/previews/<id>/{screen.png, tree.json}` (`-Pscreen=<id>`
for one); `node qa/preview-gallery.mjs` builds a self-contained gallery page from the
output. What this loses: on-save re-render, changed-screen attribution, compile errors
in-band, and the `preview_diff` change proof — structured feedback replaced by pixels.

**Live tier — the human's live device view (standing step).** Whenever `connect_live`
succeeds, OFFER the `remoteUrl` it returns (`http://127.0.0.1:9500/inspect/remote`) to the
human — every time, not as a maybe. It is a self-contained browser page that mirrors the
running app (~700ms refresh) with click-to-tap driving the real device: they watch and drive
the actual app while you assert on the tree (`navigate_and_inspect` — its before/after delta
is the change proof live — and `inspect_tree`). It is also the right way for a human to
*watch* an e2e run.

Asserting persisted state: `db_query` reads bounded rows from the running app's database;
use it when a flow's proof is a row existing (or not) after an action, instead of shelling
into sqlite or trusting the UI.

When the app crashes or misbehaves on device: `runtime_crashes` returns persisted crashes
with cause attribution and `runtime_logs` bounded structured logcat for the app's pid; use
these before hand-grepping `adb logcat`.

Screens come from `inspector/PreviewRegistry.kt` (desktopMain). The `add-feature` and
`add-screen` stampers auto-register stamped screens at the `// cmp:anchor preview-registry`
marker; when you add a screen by hand, register it there — a forced-state variant is just
another entry (`"home@empty"`). Every common component also carries a story entry
(`"component.<kebab-name>"` in `inspector/ComponentStories.kt`); when you add a component,
add its story — the lane's `componentStories` step fails naming the missing id otherwise.
Assert on `tree.json` structure; never read PNG bytes. Pixels are for humans.

A human comment left from the preview console follows `.claude/rules/comments.md` — read it before acting on or resolving one.
