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

import { stepDeadlineMs, stepErrorResult } from "./step-outcomes.mjs";

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
 * @param {(result: object) => boolean} [ctx.stopAfter] short-circuit predicate; default:
 *   stop after a FAILed "build" — nothing downstream is meaningful
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
    print = null,
    narrator = null,
    stopAfter = (r) => r.name === "build" && r.verdict === "FAIL",
    onFinally = () => {},
    startedAt = Date.now(),
  } = ctx;

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
      setDeadline(stepDeadlineMs(expected.byName.get(name)));
      const stepStarted = Date.now();
      let result;
      try {
        result = step();
      } catch (err) {
        result = stepErrorResult(name, err, Date.now() - stepStarted);
      }
      // Layer tag: a pack may mark a step function with the layer of the
      // stack it proves (`fn.layer = "backend"`). The runner stamps it onto
      // the row so the receipt carries it and the console can group by it —
      // a step that set its own `layer` in the result keeps its word.
      if (result && typeof result === "object" && typeof step.layer === "string" && step.layer && typeof result.layer !== "string") {
        result.layer = step.layer;
      }
      results.push(result);
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
