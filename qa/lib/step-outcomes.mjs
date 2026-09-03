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
// Pure, so the wording and the rule are testable without Gradle or a device.
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

/**
 * The androidChecks outcome from Gradle's exit and the JUnit summary.
 *
 * @param {{ok: boolean, out: string}} res the Gradle invocation
 * @param {{tests: number, failures: number, errors: number}|null} summary parsed JUnit
 *   results, or null when none were written
 * @param {{gradlew?: string}} [opts]
 * @returns {{verdict: "PASS"|"FAIL"|"ERROR", executed: boolean, reason?: string}}
 */
export function androidChecksOutcome(res, summary, { gradlew = "./gradlew" } = {}) {
  const executed = Boolean(summary && summary.tests > 0);
  if (res.ok) return { verdict: "PASS", executed };
  const tail = String(res.out ?? "")
    .split("\n")
    .filter((l) => /FAILED|error:|failed/i.test(l))
    .slice(0, 12)
    .join("\n");
  if (executed) {
    return {
      verdict: "FAIL",
      executed,
      reason:
        `connectedDebugAndroidTest failed (${summary.failures + summary.errors} of ${summary.tests} tests) — ` +
        `an on-device behavior claim is broken. Fix the behavior, not the test:\n${tail}`,
    };
  }
  // ERROR, not FAIL: the step could not execute. A device tier that could not
  // run is not evidence (the lane still FAILs), and going green would be the
  // worse lie — but "your behaviour is broken" is withdrawn, and the receipt
  // can tell a red that measured something from a red that measured nothing.
  return {
    verdict: "ERROR",
    executed,
    reason:
      "connectedDebugAndroidTest DID NOT EXECUTE — the run reported no tests at all, so this step has observed " +
      "nothing about your change and is not accusing it. Usual cause: another adb/Gradle session touching the same " +
      "device (a manual `adb` command, a second lane, a running preview), or an install that never landed. " +
      `Re-run this step alone with nothing else on the device before suspecting the code:\n  ${gradlew} :composeApp:connectedDebugAndroidTest --rerun\n${tail}`,
  };
}

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
 * A step's own deadline, from the journal's last measured duration for it:
 * three times what it usually takes, never under five minutes (a cold Gradle
 * daemon is slow, not wedged), never over thirty (past that it IS wedged).
 * Unknown steps get the ceiling — a first run is never cut short.
 * @param {number|null|undefined} expectedMs
 * @returns {number}
 */
export function stepDeadlineMs(expectedMs, { floorMs = 5 * 60_000, ceilingMs = 30 * 60_000 } = {}) {
  if (!(expectedMs > 0)) return ceilingMs;
  return Math.min(ceilingMs, Math.max(floorMs, Math.round(expectedMs * 3)));
}

/**
 * The step result for a step that could not run — a deadline, or any throw
 * out of the step's own body (which used to crash the whole lane; now it is
 * one ERROR row and the lane keeps going, because the other steps' verdicts
 * are still worth having).
 * @param {string} name the step's display name
 * @param {unknown} err
 * @param {number} durationMs
 * @returns {{name: string, verdict: "ERROR", reason: string, durationMs: number, details: {executed: false, kind: string}}}
 */
export function stepErrorResult(name, err, durationMs) {
  const timeout = err instanceof StepTimeout;
  const reason = timeout
    ? `DID NOT COMPLETE — no result within its deadline (${Math.round(err.deadlineMs / 60000)} min). This step has observed nothing about your change and is not accusing it. ` +
      `A wedged Gradle daemon or a device that stopped answering are the usual causes; check \`./gradlew --status\` and \`adb devices\`, then re-run the step alone.
  ${err.cmd}`
    : `DID NOT RUN — the step threw before producing a verdict: ${err && err.message ? err.message : String(err)}. ` +
      `Nothing here is a claim about your change.`;
  return { name, verdict: "ERROR", reason, durationMs, details: { executed: false, kind: timeout ? "deadline" : "threw" } };
}
