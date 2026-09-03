#!/usr/bin/env node
// lane-narrator.mjs — the lane's pulse while a step is running.
//
//   node qa/lib/lane-narrator.mjs <projectRoot>
//
// WHY THIS IS A SEPARATE PROCESS, and not a setInterval in verify.mjs. The lane's
// steps are SYNCHRONOUS: each one blocks on execSync/spawnSync while Gradle works.
// A timer inside that process cannot fire — the event loop is not running — so the
// lane could only ever print when a step FINISHED. Observed live: fourteen minutes
// without a single byte while the release build ran, with no way to tell a grinding
// step from a wedged one except checking the Gradle daemon's CPU by hand. A step
// that can take minutes must emit a heartbeat, or the operator's only signal is
// silence, and silence is exactly what a crash looks like.
//
// The marker verify.mjs already rewrites at each step start (.cmp-lane-in-progress,
// JSON: step, index, total, stepStartedAt, expectedStepMs, expectedLaneMs) carries
// everything a pulse needs, so this narrator INVENTS NOTHING — it reads what the
// lane declared about itself and says it out loud on a timer the lane cannot run.
//
// It writes to STDERR and never to stdout: --json consumers parse stdout, and a
// narrator that corrupted machine output would be worse than the silence it fixes.
// It is spawned only for human runs, and killed with the step loop.

import fs from "node:fs";
import path from "node:path";

const ROOT = process.argv[2];
const MARKER = path.join(ROOT ?? ".", "composeApp", "build", ".cmp-lane-in-progress");

// A step under this is not a wait — saying anything about it is noise.
const FIRST_AFTER_MS = 20_000;
const EVERY_MS = 30_000;
const POLL_MS = 1_000;

/** "42s" / "4m12s" — short enough to sit inside one line without wrapping. */
export function shortDuration(ms) {
  if (!(ms > 0)) return "0s";
  const s = Math.round(ms / 1000);
  return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m${String(s % 60).padStart(2, "0")}s`;
}

/**
 * The line for one poll, or null when there is nothing worth saying yet.
 * Pure, so the cadence and the wording are testable without a clock or a lane.
 *
 * @param {object|null} marker parsed marker content (null when absent/legacy)
 * @param {number} elapsedMs how long the CURRENT step has been running
 * @param {number|null} lastSaidAtMs elapsed value at the previous line, or null
 * @returns {string|null}
 */
export function pulseLine(marker, elapsedMs, lastSaidAtMs) {
  if (!marker || typeof marker.step !== "string") return null; // legacy marker: nothing to narrate
  if (elapsedMs < FIRST_AFTER_MS) return null;
  if (lastSaidAtMs !== null && elapsedMs - lastSaidAtMs < EVERY_MS) return null;
  const where = marker.index && marker.total ? ` (${marker.index}/${marker.total})` : "";
  // The expectation is quoted from the flight journal's last full run — measured,
  // never estimated (walk-legibility L4). Absent until one such run exists.
  const usually = marker.expectedStepMs > 0 ? `, usually ~${shortDuration(marker.expectedStepMs)}` : "";
  // Past its usual time is the fact an operator actually wants: it is the
  // difference between "grinding" and "possibly wedged", and it is derived, not
  // guessed — so it is stated plainly rather than dressed up as a warning.
  const over = marker.expectedStepMs > 0 && elapsedMs > marker.expectedStepMs * 1.5 ? " — longer than usual" : "";
  return `⋯ ${marker.step}${where} — ${shortDuration(elapsedMs)} elapsed${usually}${over}`;
}

function readMarker() {
  try {
    return JSON.parse(fs.readFileSync(MARKER, "utf8"));
  } catch {
    return null;
  }
}

if (ROOT) {
  let currentStep = null;
  let lastSaidAtMs = null;
  const timer = setInterval(() => {
    const m = readMarker();
    if (!m) return; // between steps, or the lane has finished and cleared it
    if (m.step !== currentStep) {
      currentStep = m.step;
      lastSaidAtMs = null; // each step narrates on its own clock
    }
    const started = Date.parse(m.stepStartedAt ?? "");
    if (Number.isNaN(started)) return;
    const elapsed = Date.now() - started;
    const line = pulseLine(m, elapsed, lastSaidAtMs);
    if (line) {
      lastSaidAtMs = elapsed;
      process.stderr.write(`${line}\n`);
    }
  }, POLL_MS);
  timer.unref?.();
  for (const sig of ["SIGINT", "SIGTERM", "SIGHUP"]) process.on(sig, () => process.exit(0));
  // Hold the process open against the unref'd timer: the parent kills us.
  setInterval(() => {}, 1 << 30);
}
