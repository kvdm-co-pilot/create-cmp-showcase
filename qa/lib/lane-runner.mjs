// lane-runner.mjs — the lane's step loop, as a function (evidence-economics S8a).
//
// The SPINE, separated from the STEPS. Everything a lane does around its steps —
// stamp the in-flight marker with the step's own narration, set the step's
// deadline from its measured history, keep a pulse alive while a synchronous
// step blocks, turn a throw or a timeout into one ERROR row and keep going,
// print the mark, derive the verdict — is the same for a Compose app and for
// a Kotlin backend. Only the steps differ. payment-blueprint re-implemented
// all of this by hand (2,769 lines) because it lived inside verify.mjs next
// to Gradle calls it could not use; this file is what it should have been
// able to import.
//
// PURE OF PROJECT KNOWLEDGE. No ROOT, no Gradle, no composeApp path: every
// project-specific fact arrives through `ctx`. The runner never reads argv.

import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

import { stepDeadlineMs, stepErrorResult, resolveStepDeadlines } from "./step-outcomes.mjs";

/**
 * Per-step expected durations from the journal's LAST FULL run — the source
 * of the marker's "usually ~Ns" and of each step's deadline. Measured, never
 * estimated; empty until one new-format full run exists.
 * @param {object[]} entries parsed flight-journal entries, oldest first
 * @returns {{byName: Map<string, number>, laneMs: (number|null)}}
 */
export function expectedDurations(entries) {
  const list = Array.isArray(entries) ? entries : [];
  for (let i = list.length - 1; i >= 0; i--) {
    const e = list[i];
    if (!e || e.mode === "fast" || !Array.isArray(e.steps)) continue;
    const byName = new Map();
    for (const s of e.steps) {
      if (s && typeof s.name === "string" && typeof s.durationMs === "number" && s.durationMs > 0) byName.set(s.name, s.durationMs);
    }
    return { byName, laneMs: typeof e.durationMs === "number" && e.durationMs > 0 ? e.durationMs : null };
  }
  return { byName: new Map(), laneMs: null };
}

/**
 * "stepUnitTests" → "unitTests", "stepSpecCoverageMemo" → "specCoverage" — the
 * name the result will carry. An anonymous step narrates as null rather than
 * guessing. Exported so a step pack can name its steps the same way.
 * @param {Function} fn
 * @returns {string|null}
 */
export function stepDisplayName(fn) {
  const raw = typeof fn?.name === "string" ? fn.name.replace(/^step/, "").replace(/Memo$/, "") : "";
  return raw === "" ? null : raw.charAt(0).toLowerCase() + raw.slice(1);
}

/** The lane's verdict over its rows: FAIL on any FAIL or ERROR, else PASS. CACHED counts as PASS (it IS a prior PASS). */
export function laneVerdict(steps) {
  return steps.some((s) => s && (s.verdict === "FAIL" || s.verdict === "ERROR")) ? "FAIL" : "PASS";
}

/** The mark a row wears on the console: ✓ PASS · ⚡ CACHED · → SKIP · ⊘ ERROR (could not run) · ✗ FAIL. */
export function verdictMark(verdict) {
  return verdict === "PASS" ? "✓" : verdict === "CACHED" ? "⚡" : verdict === "SKIP" ? "→" : verdict === "ERROR" ? "⊘" : "✗";
}

// ── The compile short-circuit ───────────────────────────────────────────────
// Once the thing that turns source into a runnable artifact has FAILED, every
// verdict behind it is meaningless: the tests are testing the last successful
// artifact or nothing at all, and the lane would spend minutes producing rows
// nobody can read. Stopping there is right.
//
// WHICH STEP THAT IS, IS THE PACK'S TO SAY. This predicate was written
// `r.name === "build"`, which is Gradle's word twice over — the directory and
// the task. Keyed to the literal it was wrong in both directions on any other
// stack, and wrong SILENTLY, with a plausible-looking lane to show for it:
//
//   a pack that compiles in `py_build` never short-circuited, so its whole slow
//   tier ran against a tree that does not compile
//   a pack that compiles elsewhere but has SOME step named `build` — a
//   packaging step, a container image, a docs build; the word is not reserved —
//   had its lane truncated there, dropping real verdicts on the floor
//
// So the name arrives on `ctx.compileStepName`, declared by the step pack
// (test/fixtures/profiles/py-alien/index.mjs declares "py_build";
// profiles/cmp/steps-cmp.mjs declares "build"). Three cases, and the
// distinction is the KEY's presence, not its value:
//
//   key absent      a caller written before the declaration existed. It keeps
//                   the historical behaviour rather than silently losing its
//                   short-circuit — a compatibility fallback, and the only
//                   place this file still spells one stack's word.
//   key present,    the pack has no compile phase (a lint-only lane, an
//   no name         interpreted stack). Short-circuit on NOTHING: every step
//                   runs and every verdict is taken. Never inherit someone
//                   else's step name — that is how a lane loses rows.
//   key present,    stop after that step FAILs, and only that step.
//   a name
//
// FAIL and not ERROR, deliberately and unchanged: ERROR means "I could not
// check this", which is not the same claim as "this does not compile", and
// widening it here would change the shipped cmp lane's behaviour.
export const LEGACY_COMPILE_STEP_NAME = "build";

function compileShortCircuit(ctx) {
  if (!Object.hasOwn(ctx, "compileStepName")) {
    return (r) => r.name === LEGACY_COMPILE_STEP_NAME && r.verdict === "FAIL";
  }
  const declared = ctx.compileStepName;
  if (typeof declared !== "string" || declared === "") return () => false;
  return (r) => r.name === declared && r.verdict === "FAIL";
}

/**
 * Run the steps, in order, under the lane's own discipline.
 *
 * @param {object} ctx
 * @param {Function[]} ctx.steps the step functions, each returning a result row
 * @param {string} ctx.markerPath the in-flight marker (.cmp-lane-in-progress) — stamped
 *   before every step with {pid, at, step, index, total, stepStartedAt, expectedStepMs,
 *   expectedLaneMs}, removed when the loop ends however it ends
 * @param {{byName: Map<string, number>, laneMs: (number|null)}} [ctx.expected] from expectedDurations
 * @param {(ms: number) => void} [ctx.setDeadline] receives each step's deadline before it
 *   runs — the project's subprocess helper reads it (verify.mjs's sh())
 * @param {(line: string) => void|null} [ctx.print] one line per finished step; null = silent
 *   (--json). Also gates the narrator: no print, no pulse.
 * @param {{entry: string, root: string}|null} [ctx.narrator] the pulse process to spawn
 *   beside the loop (lane-narrator.mjs) — a separate process because the steps are
 *   synchronous and no timer in this process can fire while one runs
 * @param {string|null} [ctx.compileStepName] the PACK's name for the step whose failure
 *   makes every later verdict meaningless (see compileShortCircuit above). Omit the key
 *   entirely and the historical `"build"` short-circuit is kept; pass null/undefined and
 *   the lane short-circuits on nothing
 * @param {(result: object) => boolean} [ctx.stopAfter] short-circuit predicate — the
 *   caller's own word, outranking ctx.compileStepName; default: compileShortCircuit(ctx)
 * @param {() => void} [ctx.onFinally] runs in the finally (the project releases its device lease here)
 * @param {number} [ctx.startedAt] the lane's start, for the marker's `at`
 * @returns {{steps: object[], verdict: "PASS"|"FAIL", durationMs: number}}
 */
export function runLane(ctx) {
  const {
    steps: stepFns,
    markerPath,
    expected = { byName: new Map(), laneMs: null },
    setDeadline = () => {},
    // HOW LONG a step may take before it is wedged is the pack's to say, the
    // same way `compileStepName` and `timeoutHint` are. Absent, the spine's
    // fallback applies AND the ERROR row says so, so an adopter whose cold
    // toolchain was cut short learns the knob exists.
    stepDeadlines = undefined,
    print = null,
    // A STRUCTURED report of each step as it finishes, beside `print`'s human
    // row. They are deliberately different channels: `print` writes prose for a
    // person watching a terminal, and is null on a machine run because a
    // narrator during one is a lane doing something unasked. `onStep` is the
    // machine's version of the same instant — the result object, unformatted —
    // so a console can append a row while the lane is still running instead of
    // learning the whole story from a receipt after it ends.
    //
    // It must never be able to fail the lane: a reporter that throws would turn
    // a passing run red for a reason that has nothing to do with the code under
    // test, which is the exact false red this project exists to remove.
    onStep = null,
    narrator = null,
    stopAfter = compileShortCircuit(ctx),
    onFinally = () => {},
    startedAt = Date.now(),
  } = ctx;

  // Resolved once per lane, not once per step: the bounds cannot change mid-run
  // and `isDefault` must be the same answer in the deadline and in the ERROR row
  // that explains it.
  const DEADLINES = resolveStepDeadlines(stepDeadlines);

  const stamp = (stepFn, index, total) => {
    try {
      const name = stepFn ? stepDisplayName(stepFn) : null;
      const narration = {
        pid: process.pid,
        at: new Date(startedAt).toISOString(),
        step: name,
        index,
        total,
        stepStartedAt: new Date().toISOString(),
        expectedStepMs: name !== null ? (expected.byName.get(name) ?? null) : null,
        expectedLaneMs: expected.laneMs,
      };
      fs.writeFileSync(markerPath, `${JSON.stringify(narration)}\n`);
    } catch {
      /* the narration must never break the lane it narrates */
    }
  };

  fs.mkdirSync(path.dirname(markerPath), { recursive: true });
  stamp(null, 0, stepFns.length);

  let pulse = null;
  if (print && narrator) {
    try {
      pulse = spawn(process.execPath, [narrator.entry, narrator.root], { stdio: ["ignore", "ignore", "inherit"] });
      pulse.on("error", () => {});
    } catch {
      /* a missing pulse is a quieter lane, never a failed one */
    }
  }

  const results = [];
  try {
    for (const [i, step] of stepFns.entries()) {
      stamp(step, i + 1, stepFns.length);
      const name = stepDisplayName(step) ?? `step${i + 1}`;
      // S4: every step under a deadline from its own history (×3, floor 5 min,
      // ceiling 30). A deadline or a throw is ONE ERROR row — the lane keeps
      // going, because the other verdicts are still worth having.
      setDeadline(stepDeadlineMs(expected.byName.get(name), DEADLINES));
      const stepStarted = Date.now();
      let result;
      try {
        result = step();
      } catch (err) {
        // `step.timeoutHint` is the pack's where-to-look sentence, marked on the
        // step function like `step.layer`. The spine carries it; it never writes one.
        result = stepErrorResult(name, err, Date.now() - stepStarted, {
          hint: typeof step.timeoutHint === "string" ? step.timeoutHint : undefined,
          deadlineWasDefault: DEADLINES.isDefault,
        });
      }
      // Layer tag: a pack may mark a step function with the layer of the
      // stack it proves (`fn.layer = "backend"`). The runner stamps it onto
      // the row so the receipt carries it and the console can group by it —
      // a step that set its own `layer` in the result keeps its word.
      if (result && typeof result === "object" && typeof step.layer === "string" && step.layer && typeof result.layer !== "string") {
        result.layer = step.layer;
      }
      results.push(result);
      if (onStep) {
        try {
          onStep(result, { index: results.length - 1, total: stepFns.length });
        } catch {
          /* a reporter may not fail the lane — see the option's note */
        }
      }
      if (print) {
        print(
          `${verdictMark(result.verdict)} ${result.name}: ${result.verdict}${result.note ? ` (${result.note})` : ""}${result.reason ? ` — ${String(result.reason).split("\n")[0]}` : ""}`,
        );
      }
      if (stopAfter(result)) break;
    }
  } finally {
    if (pulse) {
      try {
        pulse.kill();
      } catch {
        /* the narrator holds nothing; a failed kill must not fail the lane */
      }
    }
    fs.rmSync(markerPath, { force: true });
    try {
      onFinally();
    } catch {
      /* a finalizer that throws must not hide the rows already earned */
    }
  }

  return { steps: results, verdict: laneVerdict(results), durationMs: Date.now() - startedAt };
}
