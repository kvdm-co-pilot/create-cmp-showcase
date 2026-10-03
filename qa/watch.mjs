#!/usr/bin/env node
// qa/watch.mjs — resident watch mode: the inner verification loop, made free.
//
//   node qa/watch.mjs [--once] [--json] [--help]
//
// A human developer's inner loop costs nothing because the IDE is always
// running — errors appear on save. An agent has no IDE, so it reaches for the
// heaviest thing labelled "done" (the full verify lane) after small edits.
// This process is the missing IDE: it watches the sources, and on every save
// runs the SANCTIONED fast tier — `node qa/verify.mjs --fast` — as a
// subprocess, then re-prints a compact status block. Same idea as the preview
// daemon (the single most-adopted surface in this harness), extended from
// rendering to verification.
//
// DELIBERATELY DECOUPLED: watch mode shells out to verify.mjs rather than
// importing its internals. The fast lane's step economics (what runs, what is
// memoized, what is scoped) evolve in verify.mjs; this file inherits every
// improvement for free and can never fork the step list.
//
// NOT A GATE — BY CONSTRUCTION. It runs `--fast` only, so every receipt it
// causes records `"mode": "fast"`, earns no evidence rung, and is refused by
// qa/receipt-check.mjs. This process never prints a completion claim; every
// run ends with the standing footer naming the real gate. Watch green is a
// signal; the done-gate is one deliberate full `node qa/verify.mjs` run.
//
// COORDINATION (non-negotiable): two concurrent Gradle invocations against one
// project corrupt each other's output (KSP cache collisions, half-written
// classes dirs — a real 20+ bogus-failure cascade). The lane and the preview
// daemon already coordinate via marker files:
//   qa/.lane-in-progress     — stamped by verify.mjs for a run's duration (core
//                              state; qa/lib/lane-markers.mjs)
//   .cmp-render-in-progress  — stamped by the preview daemon while its Gradle
//                              build is in flight
// Watch mode participates as a third citizen: it never launches a run while a
// FOREIGN lane or a render is fresh — it waits, coalescing every change that
// arrives into the one run that fires when the project frees up. Its own runs
// need no extra marker: the verify.mjs child stamps the lane marker itself, so
// a second watch instance (or a hand-run lane) sees this one's run and defers.
// Both mtime-staleness bounds mirror the existing consumers so a crashed
// process never wedges this watcher: the lane marker goes stale after 30
// minutes (preview-service.mjs's bound), the render marker after 5 minutes
// (verify.mjs's bound).
//
// OUTPUT is for BOTH audiences: plain, line-oriented, greppable — no cursor
// control, no spinners — so a human sees green/red at a glance and an agent
// can read the same stdout through a pipe. On failure the failing step's
// reason is surfaced VERBATIM (that is the thing the agent needs). `--json`
// switches to one JSON object per line (NDJSON) for programmatic consumption.

import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { LANE_MARKER_REL, LANE_MARKER_STALE_MS, RENDER_MARKER_FRESH_MS, laneMarkerPath, renderMarkerPath } from "./lib/lane-markers.mjs";
import { resolveSpecModel } from "./lib/spec-model.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

// ── CLI ─────────────────────────────────────────────────────────────────────

export const USAGE = `node qa/watch.mjs [--once] [--json] [--help]

Resident watch mode — the inner verification loop. Watches the project's
source roots, specs/, and qa/ and runs \`node qa/verify.mjs --fast\` on every save (debounced;
a save storm triggers ONE run, changes during a run coalesce into one
follow-up). It defers while a verify lane or a preview-daemon render holds the
project (qa/.lane-in-progress and the eyes' own render marker), so two build
invocations never collide.

THIS IS NOT A GATE. It runs the fast tier only: every receipt records
"mode": "fast", earns no evidence rung, and qa/receipt-check.mjs refuses it.
The done-gate is one deliberate full \`node qa/verify.mjs\` run.

Flags:
  --once      run a single coordinated fast pass and exit with its exit code
              (0 = PASS, 1 = FAIL) — no watchers, useful for scripting
  --json      one JSON object per line (NDJSON) instead of the human block:
              events "start", "deferred", "run", "watch-fallback", "shutdown"
  --help, -h  print this usage and exit 0 without watching anything

Ctrl-C / SIGTERM stop it cleanly (watchers closed, any in-flight verify child
terminated and its marker cleaned up) and exit 0.
`;

// Strict parsing, same refusal-over-fabrication stance as verify.mjs: an
// unknown argument is refused by name, never swallowed into "watch anyway".
export function parseWatchArgs(rawArgs) {
  const opts = { help: false, once: false, json: false };
  for (const arg of rawArgs) {
    if (arg === "--help" || arg === "-h") opts.help = true;
    else if (arg === "--once") opts.once = true;
    else if (arg === "--json") opts.json = true;
    else return { error: `unknown argument "${arg}" — run node qa/watch.mjs --help` };
  }
  return opts;
}

// ── The watch set ───────────────────────────────────────────────────────────
// The profile's source roots (the app), its specs directory (the contract),
// and qa/ (the harness itself — a golden-tree or flow edit should re-verify
// too). NOT watched: the build output directory the PROFILE declares, at any
// depth (see shouldIgnorePath); qa/evidence/ (verify.mjs writes latest.json
// there on every run — watching your own output is an infinite loop); and
// dotfiles (VCS internals, editor droppings, and the in-progress markers
// themselves).

/**
 * The trees this loop watches: the profile's own source roots and specs
 * directory, plus qa/ (the harness judging itself — always core).
 *
 * Stage 0 PR 6b: this was `["composeApp/src", "specs", "qa"]`, so the inner
 * loop in a repo whose code lives anywhere else watched two directories out of
 * three and never fired on a source edit — a watcher that looks idle and is.
 * A project with no usable manifest gets the core roots alone and the startup
 * banner says so, rather than guessing a source layout.
 * @param {string} root
 * @returns {{roots: string[], degraded: string|null}}
 */
export function watchRoots(root) {
  const model = resolveSpecModel(root);
  if (!model.ok) {
    return { roots: ["specs", "qa"], degraded: model.reason };
  }
  return { roots: [...new Set([...model.model.sourceRoots, model.model.specsDir, "qa"])], degraded: null };
}

// ── Build output: the profile's word, not one stack's ───────────────────────
// The ignore set's whole job is to keep the loop from watching its own output.
// It used to find that output by the single literal directory name `build`,
// which is Gradle's — Rust writes `target/`, .NET `bin/` and `obj/`, Python
// `__pycache__`, Xcode `DerivedData`. Both halves were wrong, and both
// silently, with a watcher that looked like it was working:
//
//   on those stacks the lane's own writes woke the watcher, which ran the lane
//   again — a feedback loop, in the one process whose entire purpose is to fire
//   on a human's edit
//   and on EVERY stack a real source directory named `build/` (a build script
//   package, a `build/` module, Python's `app/build/`) was never watched at
//   all — the "watcher that looks idle and is" that Stage 0 PR 6b already
//   fixed once for source ROOTS and left in place here
//
// The profile declares `layout.buildDir` (qa/lib/spec-model.mjs), so the name
// is available and was simply not asked for.
//
// THE DECLARED DIRECTORY'S NAME MATCHES AT ANY DEPTH, not just the one declared
// path. A profile declares its app module's output, but a multi-module tree has
// one such directory PER MODULE (Gradle writes one into every module; the
// harness's own qa/ subtrees get one too — see test/watch-loop.test.mjs's
// `qa/somewhere/build/out.json`). Ignoring only the declared path would re-open
// the feedback loop one directory over. Only that name matches, so a stack that
// calls its output `target` starts watching `build/` as the ordinary source it
// is there.
//
// That rule is deliberately BROADER than the declaration, and the price is
// named here rather than discovered: a profile declaring a nested `buildDir`
// gets its LEAF name ignored everywhere (`build/outputs` ignores every
// `outputs/` in the tree, not just that one). The startup banner prints the
// glob it settled on for exactly this reason — an ignore set a reader can see
// is one they can correct in `layout.buildDir`.

/**
 * The historical name, kept ONLY for a project whose profile could not be
 * resolved or which declares no `layout.buildDir`. It is a FALLBACK and not a
 * default in the approving sense — the startup banner names it, exactly as
 * spec-model.mjs's `DEFAULT_GRAMMAR.isDefault` makes the borrowed citation
 * grammar say so out loud. The two failures are not symmetric: watching your
 * own output loops forever, while missing a source directory named `build/`
 * costs one manual run. When we know nothing, we take the cheaper mistake.
 */
export const FALLBACK_BUILD_OUTPUT_NAME = "build";

/**
 * The directory NAMES this project's build output wears, from the profile's
 * declaration. `composeApp/build` → ["build"]; `target` → ["target"].
 * @param {{buildDir?: string|null}|null} [model] a SpecModel, or null when none resolved
 * @returns {string[]}
 */
export function buildOutputNames(model) {
  const declared = typeof model?.buildDir === "string" ? model.buildDir : "";
  const name = declared.replace(/\\/g, "/").split("/").filter(Boolean).pop();
  return name ? [name] : [FALLBACK_BUILD_OUTPUT_NAME];
}

/**
 * Ignore predicate over a ROOT-relative path (forward slashes or backslashes).
 * @param {string} rel
 * @param {{buildDir?: string|null}|null} [model] the project's SpecModel — supplies the
 *   build output name. Omitted/null falls back to FALLBACK_BUILD_OUTPUT_NAME.
 */
export function shouldIgnorePath(rel, model = null) {
  const norm = String(rel).replace(/\\/g, "/");
  if (!norm) return true;
  const parts = norm.split("/");
  // Any dotted segment: .git, .gradle, .DS_Store, .lane-in-progress, …
  if (parts.some((s) => s.startsWith("."))) return true;
  // This profile's build output, at any depth (see the note above).
  const buildNames = buildOutputNames(model);
  if (parts.some((s) => buildNames.includes(s))) return true;
  // The lane's own output — the one path that would make watch feed itself.
  if (norm === "qa/evidence" || norm.startsWith("qa/evidence/")) return true;
  return false;
}

// ── Debounce ────────────────────────────────────────────────────────────────
// 400ms, matching the preview daemon's proven DEBOUNCE_MS (inspector MCP
// preview-service) — the one number in this harness already field-tested
// against real editor save storms. One logical edit often lands as several
// fs events over a few hundred ms (multi-file agent turns, format-on-save,
// `git checkout`); below ~300ms those split into two runs, and anything above
// ~500ms only adds perceptible lag against a run that itself costs tens of
// seconds. Trailing debounce: the timer resets on every event, so the run
// fires once the storm goes quiet.
export const DEBOUNCE_MS = 400;
export const POLL_MS = 2000; // marker-wait poll AND the no-recursive-watch fallback scan cadence

// ── Marker coordination ─────────────────────────────────────────────────────
// Shapes and bounds mirror the existing participants (see the header):
// verify.mjs stamps LANE for its duration; the preview daemon stamps RENDER
// around its Gradle builds. Freshness is mtime-bounded so a crashed stamper
// never wedges us.

// Paths and bounds are qa/lib/lane-markers.mjs's — the lane marker is core state
// under qa/, the render marker is the profile's (under its buildDir, or none).
// Re-exported because markerDecision's bounds are part of this module's
// contract (qa/watch.mjs --json consumers and the engine suite read them).
export { LANE_MARKER_STALE_MS, RENDER_MARKER_FRESH_MS };

/**
 * The launch decision, pure: given the two markers' mtimes (null = absent) and
 * the clock, may a run start now? A fresh foreign marker means someone else's
 * Gradle is (or may be) in flight — wait and coalesce, never collide.
 */
export function markerDecision({ laneMtimeMs = null, renderMtimeMs = null, nowMs = Date.now() } = {}) {
  if (laneMtimeMs != null && nowMs - laneMtimeMs < LANE_MARKER_STALE_MS) {
    return { launch: false, reason: `a verify lane is in progress (${LANE_MARKER_REL} is fresh) — deferring; changes coalesce into one run when it finishes` };
  }
  if (renderMtimeMs != null && nowMs - renderMtimeMs < RENDER_MARKER_FRESH_MS) {
    return { launch: false, reason: "the preview daemon has a build in flight (.cmp-render-in-progress is fresh) — deferring; changes coalesce into one run when it finishes" };
  }
  return { launch: true };
}

/**
 * If `markerPath` exists and its first token is `pid`, remove it. A verify
 * child killed by a signal never runs its `finally`, so its lane marker would
 * outlive it and make every consumer defer for the full staleness window —
 * this is the cleanup for exactly (and only) the marker OUR child stamped.
 * @returns {boolean} true when a marker owned by `pid` was removed
 */
export function clearMarkerIfOwnedBy(markerPath, pid) {
  try {
    const first = fs.readFileSync(markerPath, "utf8").trim().split(/\s+/)[0];
    if (Number(first) !== pid) return false;
    fs.rmSync(markerPath, { force: true });
    return true;
  } catch {
    return false; // absent or unreadable — nothing to clean
  }
}

// ── The run loop (pure of fs/child_process — unit-testable) ─────────────────
// State machine: idle → (change, debounce) → launch-check → running → idle.
//  - A save storm resets the trailing debounce: ONE run per storm.
//  - Changes while a run is in flight accumulate and queue EXACTLY ONE
//    follow-up (coalesce, never stack).
//  - When canLaunch says wait (foreign lane/render), the loop re-polls on
//    pollMs, still coalescing; onDefer fires once per wait, not per poll.

export function createRunLoop({ debounceMs = DEBOUNCE_MS, pollMs = POLL_MS, canLaunch, runOnce, onDefer = () => {} }) {
  let timer = null;
  let running = false;
  let queued = false; // a follow-up run is owed (set by changes arriving mid-run)
  let stopped = false;
  let deferNoticed = false;
  const trigger = new Set(); // paths accumulated for the NEXT run

  function change(relPath) {
    if (stopped) return;
    trigger.add(relPath);
    if (running) {
      queued = true;
      return;
    }
    if (timer) clearTimeout(timer);
    timer = setTimeout(fire, debounceMs);
  }

  async function fire() {
    timer = null;
    if (stopped || running) return;
    const decision = canLaunch();
    if (!decision.launch) {
      if (!deferNoticed) {
        deferNoticed = true;
        onDefer(decision.reason);
      }
      timer = setTimeout(fire, pollMs);
      return;
    }
    deferNoticed = false;
    const paths = [...trigger];
    trigger.clear();
    queued = false;
    running = true;
    try {
      await runOnce(paths);
    } finally {
      running = false;
      if (!stopped && queued) {
        queued = false;
        timer = setTimeout(fire, debounceMs);
      }
    }
  }

  function stop() {
    stopped = true;
    if (timer) clearTimeout(timer);
    timer = null;
  }

  return {
    change,
    stop,
    get running() {
      return running;
    },
    get queued() {
      return queued;
    },
  };
}

// ── Output (pure formatters — unit-testable) ────────────────────────────────

// The standing contract line, printed after EVERY run and at startup. This
// process never claims completion; the real gate is named instead.
export const FOOTER =
  "watch mode is the inner loop — the done-gate is one deliberate full `node qa/verify.mjs` run (a fast receipt earns no rung and satisfies no gate)";

export function formatTrigger(paths, cap = 3) {
  if (!paths || paths.length === 0) return "(manual)";
  const shown = paths.slice(0, cap).join(", ");
  return paths.length > cap ? `${shown} (+${paths.length - cap} more)` : shown;
}

const secs = (ms) => `${(Math.max(0, ms) / 1000).toFixed(1)}s`;

/**
 * One run's status block: header (run number, wall-clock time, trigger), the
 * fast lane's step table, a one-line verdict, and the standing footer. Plain
 * lines only. FAILed steps get their reason VERBATIM, indented; SKIPs keep
 * their first reason line as the fine print.
 */
export function formatRunBlock({ n, startedAtIso, trigger, receipt, exitCode, durationMs, rawTail }) {
  const lines = [];
  lines.push(`── watch run #${n} · ${startedAtIso} · trigger: ${formatTrigger(trigger)}`);

  if (receipt && Array.isArray(receipt.steps)) {
    for (const step of receipt.steps) {
      const mark = step.verdict === "PASS" ? "✓" : step.verdict === "SKIP" ? "→" : "✗";
      const first = step.reason ? ` — ${String(step.reason).split("\n")[0]}` : "";
      lines.push(`  ${mark} ${step.name} ${step.verdict} (${secs(step.durationMs ?? 0)})${step.verdict === "FAIL" ? "" : first}`);
      if (step.verdict === "FAIL" && step.reason) {
        for (const rl of String(step.reason).split("\n")) lines.push(`      ${rl}`);
      }
    }
    const failed = receipt.steps.filter((s) => s.verdict === "FAIL").map((s) => s.name);
    const verdictNote =
      failed.length > 0 ? `${failed.length} step${failed.length === 1 ? "" : "s"} failed: ${failed.join(", ")}` : "fast tier green";
    lines.push(`── watch run #${n}: ${receipt.verdict} in ${secs(durationMs)} — ${verdictNote} (fast lane — a signal, not evidence)`);
  } else {
    // The child produced no parseable receipt (crashed, was killed, verify
    // refused the invocation). Surface its tail verbatim — that IS the reason.
    for (const rl of (rawTail ?? []).slice(-15)) lines.push(`      ${rl}`);
    lines.push(`── watch run #${n}: NO RECEIPT in ${secs(durationMs)} — verify exited ${exitCode ?? "by signal"} without a receipt (output above)`);
  }
  lines.push(FOOTER);
  return lines.join("\n");
}

// ── main (everything below touches fs / child_process / signals) ────────────

function markerMtime(absPath) {
  try {
    return fs.statSync(absPath).mtimeMs;
  } catch {
    return null;
  }
}

function launchDecisionNow() {
  return markerDecision({
    laneMtimeMs: markerMtime(laneMarkerPath(ROOT)),
    renderMtimeMs: markerMtime(renderMarkerPath(ROOT)),
  });
}

/** Best-effort receipt extraction from `verify --json`'s stdout. */
export function parseReceipt(stdoutText) {
  const t = String(stdoutText ?? "").trim();
  try {
    return JSON.parse(t);
  } catch {
    const i = t.indexOf("{");
    if (i > 0) {
      try {
        return JSON.parse(t.slice(i));
      } catch {
        return null;
      }
    }
    return null;
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function main() {
  const opts = parseWatchArgs(process.argv.slice(2));
  if (opts.error) {
    console.error(opts.error);
    process.exit(2);
  }
  if (opts.help) {
    console.log(USAGE);
    process.exit(0);
  }

  const asJson = opts.json;
  const emit = (obj) => {
    if (asJson) console.log(JSON.stringify(obj));
  };
  const say = (line) => {
    if (!asJson) console.log(line);
  };

  let runCounter = 0;
  let currentChild = null;

  /** Spawn one fast lane run and print/emit its outcome. Never rejects. */
  function runVerify(triggerPaths) {
    return new Promise((resolve) => {
      runCounter += 1;
      const n = runCounter;
      const startedAtIso = new Date().toISOString();
      const started = Date.now();
      say(`── watch run #${n} starting (node qa/verify.mjs --fast --no-journal) …`);
      const child = spawn(process.execPath, [path.join(ROOT, "qa", "verify.mjs"), "--fast", "--json", "--no-journal", "--events"], {
        cwd: ROOT,
        stdio: ["ignore", "pipe", "pipe"],
        // Its own process GROUP: verify spawns Gradle through a shell, and a
        // shutdown signal to the child alone would strand those grandchildren
        // (a live gradlew JVM survived exactly that in testing). With a group,
        // shutdown signals -pid and the whole tree gets it.
        detached: true,
      });
      currentChild = child;
      let stdout = "";
      let stderr = "";
      child.stdout.on("data", (d) => (stdout += d));
      // stderr carries two things now: verify's own diagnostics, and one NDJSON
      // line per finished step (--events). The step lines are RELAYED as they
      // arrive — that is the whole point, a console appending a row while the
      // lane runs rather than learning the story from a receipt after it ends.
      // Everything else is kept verbatim for the failure report, because a
      // watcher that swallowed a stack trace to look tidy would be hiding the
      // one thing a red run is for.
      let stderrPartial = "";
      child.stderr.on("data", (d) => {
        stderr += d;
        stderrPartial += d;
        const lines = stderrPartial.split("\n");
        stderrPartial = lines.pop() ?? "";
        for (const line of lines) {
          const t = line.trim();
          if (!t.startsWith("{")) continue;
          let obj;
          try {
            obj = JSON.parse(t);
          } catch {
            continue; // not ours — a diagnostic that merely starts with a brace
          }
          if (obj && obj.event === "step") emit({ ...obj, n });
        }
      });
      child.on("error", (err) => {
        currentChild = null;
        say(`── watch run #${n}: could not spawn verify — ${err.message}`);
        emit({ event: "run", n, startedAt: startedAtIso, trigger: triggerPaths, error: `spawn failed: ${err.message}` });
        resolve();
      });
      child.on("exit", (code, signal) => {
        currentChild = null;
        // A signal-killed child never ran verify's `finally` — clean the lane
        // marker it stamped so nothing defers on a ghost for 30 minutes.
        if (signal) clearMarkerIfOwnedBy(laneMarkerPath(ROOT), child.pid);
        const durationMs = Date.now() - started;
        const receipt = parseReceipt(stdout);
        const rawTail = `${stdout}\n${stderr}`.split("\n").filter(Boolean);
        say(
          formatRunBlock({
            n,
            startedAtIso,
            trigger: triggerPaths,
            receipt,
            exitCode: code,
            durationMs,
            rawTail,
          }),
        );
        emit({
          event: "run",
          n,
          startedAt: startedAtIso,
          trigger: triggerPaths,
          verdict: receipt ? receipt.verdict : null,
          mode: "fast",
          innerLoopOnly: true,
          exitCode: code,
          signal: signal ?? undefined,
          durationMs,
          steps: receipt
            ? receipt.steps.map(({ name, verdict, durationMs: d, reason }) => ({ name, verdict, durationMs: d, ...(reason ? { reason } : {}) }))
            : undefined,
          note: FOOTER,
        });
        resolve(code);
      });
    });
  }

  // ── --once: one coordinated pass, exit with the child's code ──────────────
  if (opts.once) {
    (async () => {
      let noticed = false;
      for (;;) {
        const d = launchDecisionNow();
        if (d.launch) break;
        if (!noticed) {
          noticed = true;
          say(`… ${d.reason}`);
          emit({ event: "deferred", reason: d.reason });
        }
        await sleep(POLL_MS);
      }
      const code = await runVerify([]);
      process.exit(typeof code === "number" ? code : 1);
    })();
    // Ctrl-C during --once still exits 0 cleanly.
    installSignalHandlers(() => {}, () => currentChild, emit, say);
    return;
  }

  // ── resident mode ─────────────────────────────────────────────────────────
  const loop = createRunLoop({
    canLaunch: launchDecisionNow,
    runOnce: runVerify,
    onDefer: (reason) => {
      say(`… ${reason}`);
      emit({ event: "deferred", reason });
    },
  });

  const watchers = [];
  const pollTimers = [];
  const { roots: declaredRoots, degraded: rootsDegraded } = watchRoots(ROOT);
  const watchedRoots = declaredRoots.filter((rel) => fs.existsSync(path.join(ROOT, rel)));
  // The ignore set is this profile's too: WHICH directory holds build output is
  // a stack fact (`layout.buildDir`), and a watcher that guesses it either
  // feeds itself or skips real source. resolveSpecModel shares the module cache
  // with watchRoots's own call, so this is a map lookup, not a second load.
  const specModel = (() => {
    const r = resolveSpecModel(ROOT);
    return r.ok ? r.model : null;
  })();
  const ignoredBuildNames = buildOutputNames(specModel);
  const ignorePath = (rel) => shouldIgnorePath(rel, specModel);

  // Poll fallback for platforms without recursive fs.watch: a full mtime scan
  // per tick, diffed against the previous one so changed paths still get
  // attributed. The trees here are small (sources + specs + qa scripts);
  // build dirs and evidence are excluded by the same predicate as the watcher.
  function scanTree(rootRel) {
    const out = new Map(); // rel path -> mtimeMs
    const walk = (dirAbs, dirRel) => {
      let entries;
      try {
        entries = fs.readdirSync(dirAbs, { withFileTypes: true });
      } catch {
        return;
      }
      for (const e of entries) {
        const rel = `${dirRel}/${e.name}`;
        if (ignorePath(rel)) continue;
        const abs = path.join(dirAbs, e.name);
        if (e.isDirectory()) walk(abs, rel);
        else {
          try {
            out.set(rel, fs.statSync(abs).mtimeMs);
          } catch {
            /* raced a delete */
          }
        }
      }
    };
    walk(path.join(ROOT, rootRel), rootRel);
    return out;
  }

  function startPolling(rootRel) {
    let prev = scanTree(rootRel);
    const t = setInterval(() => {
      const next = scanTree(rootRel);
      for (const [rel, mtime] of next) {
        if (!prev.has(rel) || prev.get(rel) !== mtime) loop.change(rel);
      }
      for (const rel of prev.keys()) {
        if (!next.has(rel)) loop.change(rel);
      }
      prev = next;
    }, POLL_MS);
    pollTimers.push(t);
  }

  function noteFallback(rootRel, why) {
    // Never silently watch nothing: the fallback is announced, per root.
    say(`recursive watch unavailable for ${rootRel} (${why}) — falling back to a ${POLL_MS}ms poll`);
    emit({ event: "watch-fallback", root: rootRel, reason: why, pollMs: POLL_MS });
  }

  for (const rootRel of watchedRoots) {
    try {
      const w = fs.watch(path.join(ROOT, rootRel), { recursive: true }, (_event, filename) => {
        const rel = filename ? `${rootRel}/${String(filename).replace(/\\/g, "/")}` : rootRel;
        if (ignorePath(rel)) return;
        loop.change(rel);
      });
      w.on("error", () => {
        // A watcher dying mid-flight (rare) degrades to polling, announced.
        try {
          w.close();
        } catch {}
        noteFallback(rootRel, "watcher error");
        startPolling(rootRel);
      });
      watchers.push(w);
    } catch (err) {
      // ENOSYS / ERR_FEATURE_UNAVAILABLE: recursive watch unsupported here.
      noteFallback(rootRel, err.code || err.message);
      startPolling(rootRel);
    }
  }

  // Startup: what is watched, what is respected, what this is NOT.
  // The ignore globs are PRINTED, never assumed: they name the directory this
  // profile declared, so a reader can see at a glance that the loop is ignoring
  // this stack's output and not some other stack's word for it.
  const ignoreGlobs = [...ignoredBuildNames.map((n) => `**/${n}/**`), "qa/evidence/**", "dotfiles"];
  say("qa/watch.mjs — resident inner loop: runs `node qa/verify.mjs --fast` on save");
  say(`watching: ${watchedRoots.join(", ")}  (ignoring ${ignoreGlobs.join(", ")})`);
  // Never watch a guessed layout silently: if the manifest could not be read,
  // the source roots are unknown and this loop is watching less than it looks.
  if (rootsDegraded) say(`NOTE: watching the core roots only — ${rootsDegraded}`);
  // And never ignore a guessed layout silently either. A profile that declares
  // no layout.buildDir leaves this loop on FALLBACK_BUILD_OUTPUT_NAME — one
  // stack's convention, applied to a stack that never claimed it — so it says
  // so, the way a borrowed citation grammar does (spec-model.mjs).
  else if (specModel && typeof specModel.buildDir !== "string") {
    say(`NOTE: this profile declares no layout.buildDir — ignoring **/${FALLBACK_BUILD_OUTPUT_NAME}/** as a fallback, which may be this project's real source`);
  }
  say(`coordination: defers while ${LANE_MARKER_REL} or the eyes' render marker is fresh — never two builds against this project`);
  say(`debounce: ${DEBOUNCE_MS}ms — a save storm triggers one run; changes during a run coalesce into one follow-up`);
  say(FOOTER);
  say("waiting for changes… (Ctrl-C to stop · --once for a single pass · --json for line-per-run output)");
  emit({
    event: "start",
    pid: process.pid,
    watching: watchedRoots,
    ignoring: ignoreGlobs,
    debounceMs: DEBOUNCE_MS,
    coordinates: [LANE_MARKER_REL, renderMarkerPath(ROOT) ? path.relative(ROOT, renderMarkerPath(ROOT)).split(path.sep).join("/") : null].filter(Boolean),
    runs: "node qa/verify.mjs --fast",
    note: FOOTER,
  });

  installSignalHandlers(
    () => {
      loop.stop();
      for (const w of watchers) {
        try {
          w.close();
        } catch {}
      }
      for (const t of pollTimers) clearInterval(t);
    },
    () => currentChild,
    emit,
    say,
  );
}

/**
 * Clean shutdown on SIGINT/SIGTERM: stop producing work, terminate any
 * in-flight verify child (TERM, then KILL after a short grace), clean up the
 * lane marker that child stamped (its `finally` never ran), and exit 0 —
 * Ctrl-C on a watcher is a normal end, not a failure.
 */
function installSignalHandlers(stopWork, getChild, emit, say) {
  let shuttingDown = false;
  const shutdown = async (sig) => {
    if (shuttingDown) return;
    shuttingDown = true;
    stopWork();
    const child = getChild();
    if (child && child.exitCode === null) {
      const exited = new Promise((r) => child.once("exit", r));
      // Signal the child's whole process GROUP (it was spawned detached as a
      // group leader): verify's Gradle grandchildren must get the signal too,
      // or a gradlew JVM outlives the shutdown. Fall back to the single pid
      // if the group is already gone.
      const signalTree = (sig) => {
        try {
          process.kill(-child.pid, sig);
        } catch {
          try {
            child.kill(sig);
          } catch {}
        }
      };
      signalTree("SIGTERM");
      await Promise.race([exited, sleep(2500)]);
      if (child.exitCode === null && child.signalCode === null) {
        signalTree("SIGKILL");
        await Promise.race([exited, sleep(1000)]);
      }
      clearMarkerIfOwnedBy(laneMarkerPath(ROOT), child.pid);
    }
    say(`watch mode stopped (${sig}) — no receipt was made valid by watching; the done-gate is still one full \`node qa/verify.mjs\` run`);
    emit({ event: "shutdown", reason: sig });
    process.exit(0);
  };
  process.on("SIGINT", () => void shutdown("SIGINT"));
  process.on("SIGTERM", () => void shutdown("SIGTERM"));
}

// Import-safe: tests import the pure pieces above without starting watchers.
// Realpath BOTH sides: the entry path may reach this file through a symlink
// (macOS's /var/folders → /private/var, npm links) and must still count.
const invokedDirectly = (() => {
  try {
    if (!process.argv[1]) return false;
    return fs.realpathSync(path.resolve(process.argv[1])) === fs.realpathSync(fileURLToPath(import.meta.url));
  } catch {
    return false;
  }
})();
if (invokedDirectly) main();
