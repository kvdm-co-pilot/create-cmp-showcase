// step-outcomes.mjs — a step's VERDICT, separated from its INVOCATION.
//
// A step that ran zero tests knows nothing about behaviour and must not speak
// as though it does. Observed 2026-09-02 (create-cmp-showcase): a concurrent
// adb session collided with androidChecks, Gradle exited non-zero having
// executed no tests, and the step reported "an on-device behavior claim is
// broken. Fix the behavior, not the test." The identical task passed 8 tests
// moments later. Believed, that sends the reader hunting a defect that does not
// exist; disbelieved once, it teaches them to discount every future red from
// the step — a gate that misattributes its own failures corrodes the gates that
// are right.
//
// Pure, so the wording and the rule are testable without a build tool or a device.
// (docs/proposals/evidence-economics.md C3, S4.)
//
// FOUR VERDICTS. PASS / FAIL / SKIP had no way to say "I could not run": a
// step whose infrastructure broke reported a behaviour failure. ERROR is that
// fourth word — zero tests executed, a deadline passed, a tool vanished, a
// step threw. An ERROR never accuses the change, never counts as evidence
// (evidence-level derives no rung over it; the plausibility check does not
// count it as executed), is visibly distinct from FAIL (⊘, not ✗), and is
// never silently retried. It still makes the lane FAIL — "could not check" is
// not green. This is JUnit's error-vs-failure, Bazel's FAILED_TO_BUILD /
// TIMEOUT vs FAILED, pytest's error vs failed — the distinction every mature
// runner makes and this one did not.

/** Thrown by the lane's subprocess helper when a step's deadline passes. */
export class StepTimeout extends Error {
  constructor(cmd, deadlineMs) {
    super(`deadline of ${Math.round(deadlineMs / 60000)} min passed: ${cmd}`);
    this.name = "StepTimeout";
    this.cmd = cmd;
    this.deadlineMs = deadlineMs;
  }
}

/**
 * Did a spawnSync result hit its deadline? Node reports ETIMEDOUT on
 * `error.code` and the kill signal on `signal`; either alone is enough — an
 * older Node sets only one of them.
 * @param {{error?: {code?: string}, signal?: string|null}} res
 * @returns {boolean}
 */
export function spawnTimedOut(res) {
  if (!res) return false;
  if (res.error && res.error.code === "ETIMEDOUT") return true;
  return res.signal === "SIGTERM" && (res.status === null || res.status === undefined);
}

/**
 * The bounds a pack gets when it declares none.
 *
 * These were the rule. "Never under five minutes, never over thirty" is a
 * judgement about how long a build can honestly take before it is wedged, and
 * it was made once, for one toolchain: a cold `xcodebuild`, a cold `cargo test`
 * against an empty target dir, or a Gradle daemon starting on a cold CI runner
 * can each exceed thirty minutes honestly — and the lane killed the step and
 * wrote an ERROR row, which is a wrong verdict about a healthy build.
 *
 * `stepDeadlineMs` always accepted overrides. Nothing supplied them: there was
 * no channel from the profile to the call, so the numbers were unreachable from
 * the only place that knows the stack. That was the defect, not the constants —
 * which is why they survive here as a fallback rather than being replaced by a
 * different guess.
 */
export const DEFAULT_STEP_DEADLINES = Object.freeze({ floorMs: 5 * 60_000, ceilingMs: 30 * 60_000 });

/** A positive, finite number of milliseconds, or null. */
function msOrNull(v) {
  return typeof v === "number" && Number.isFinite(v) && v > 0 ? v : null;
}

/**
 * Resolve a pack's declared deadline bounds against the fallback.
 *
 * A malformed declaration is refused into the fallback rather than trusted: a
 * deadline is the one number where believing a bad value either kills a healthy
 * build or lets a wedged one run forever. This mirrors how the spine treats
 * every other pack field — duck-typed and degrading — except that here the
 * degradation is TOLD to the adopter, because `isDefault` reaches the ERROR row.
 *
 * `isDefault` is true only when NOTHING usable was declared. A pack that named
 * one bound has made a choice and is not lectured about the other.
 *
 * @param {{floorMs?: number, ceilingMs?: number}|null|undefined} declared
 * @returns {{floorMs: number, ceilingMs: number, isDefault: boolean}}
 */
export function resolveStepDeadlines(declared) {
  const floor = declared && typeof declared === "object" ? msOrNull(declared.floorMs) : null;
  const ceiling = declared && typeof declared === "object" ? msOrNull(declared.ceilingMs) : null;
  return {
    floorMs: floor ?? DEFAULT_STEP_DEADLINES.floorMs,
    ceilingMs: ceiling ?? DEFAULT_STEP_DEADLINES.ceilingMs,
    isDefault: floor === null && ceiling === null,
  };
}

/**
 * A step's own deadline, from the journal's last measured duration for it:
 * three times what it usually takes, never under the pack's floor (a cold build
 * daemon is slow, not wedged), never over its ceiling (past that it IS wedged).
 * Unknown steps get the ceiling — a first run is never cut short.
 *
 * A floor above the ceiling is incoherent and the CEILING wins, because `min`
 * is the outer operation: cutting a healthy step short is the failure this
 * mechanism exists to avoid, so an incoherent declaration errs toward waiting.
 *
 * @param {number|null|undefined} expectedMs
 * @param {{floorMs?: number, ceilingMs?: number}} [bounds] the pack's, via resolveStepDeadlines
 * @returns {number}
 */
export function stepDeadlineMs(expectedMs, { floorMs = DEFAULT_STEP_DEADLINES.floorMs, ceilingMs = DEFAULT_STEP_DEADLINES.ceilingMs } = {}) {
  if (!(expectedMs > 0)) return ceilingMs;
  return Math.min(ceilingMs, Math.max(floorMs, Math.round(expectedMs * 3)));
}

/**
 * The step result for a step that could not run — a deadline, or any throw
 * out of the step's own body (which used to crash the whole lane; now it is
 * one ERROR row and the lane keeps going, because the other steps' verdicts
 * are still worth having).
 * WHERE TO LOOK is the pack's to say, never the spine's: this used to end with
 * "check `./gradlew --status` and `adb devices`", which is confident wrong
 * advice in a repo that has neither. A pack marks a step with `fn.timeoutHint`
 * (the same mechanism as `fn.layer`) and the runner passes it through; with no
 * hint the message says what it honestly knows and stops.
 *
 * @param {string} name the step's display name
 * @param {unknown} err
 * @param {number} durationMs
 * HOW LONG TO WAIT is the pack's too, and when it has not said, the row says
 * so. A killed step used to give an adopter no way to learn the bound existed —
 * let alone that it was another stack's number — so a cold `xcodebuild` looked
 * like a wedged build rather than a knob nobody had turned.
 *
 * @param {{hint?: string, deadlineWasDefault?: boolean}} [opts] `hint` — the pack's own
 *        where-to-look sentence; `deadlineWasDefault` — the bound was inherited, so name the knob
 * @returns {{name: string, verdict: "ERROR", reason: string, durationMs: number, details: {executed: false, kind: string}}}
 */
export function stepErrorResult(name, err, durationMs, { hint, deadlineWasDefault = false } = {}) {
  const timeout = err instanceof StepTimeout;
  const where = typeof hint === "string" && hint.trim() ? ` ${hint.trim()}` : "";
  const knob = deadlineWasDefault
    ? ` This deadline is the spine's fallback, not a number this stack chose — if the step was healthy and simply slow, declare \`stepDeadlines: { ceilingMs }\` on the step pack.`
    : "";
  const reason = timeout
    ? `DID NOT COMPLETE — no result within its deadline (${Math.round(err.deadlineMs / 60000)} min). This step has observed nothing about your change and is not accusing it.${where}${knob} ` +
      `Re-run the step alone before suspecting the code.
  ${err.cmd}`
    : `DID NOT RUN — the step threw before producing a verdict: ${err && err.message ? err.message : String(err)}. ` +
      `Nothing here is a claim about your change.`;
  return { name, verdict: "ERROR", reason, durationMs, details: { executed: false, kind: timeout ? "deadline" : "threw" } };
}

// The Maestro per-flow verdict and the device-log sweep moved to
// qa/lib/profiles/cmp/maestro.mjs (Stage 0 PR 3); androidChecksOutcome moved to
// qa/lib/profiles/cmp/android-checks.mjs (Stage 0 PR 6b.2). Facts about an
// Android device, a Gradle task and an APK are a mobile profile's, not the
// spine's. What is left here is neutral: deadlines, timeouts, and turning a
// throw into one ERROR row.
