// The evidence ladder — the receipt's COARSE grade, derived, never declared.
//
// Receipts already grade themselves in fine print ("PASS (desktop-only)",
// "PASS (on-device: e2eSmoke+androidChecks)"). This module names the rungs so
// every surface that shows a receipt can say the same thing in one word:
//
//   L0 "scaffold" — the scaffold profile's checks passed (stamp-time green
//                   build: build + unit tests + the pure-Node gates).
//   L1 "desktop"  — full static + JVM evidence: everything L0 proves PLUS
//                   conformance, golden trees, a11y, and the release COMPILE
//                   (releaseBuild) — a green lane with no on-device step run.
//   L2 "device"   — L1 plus at least one on-device EXECUTION step PASSed
//                   (e2eSmoke, androidChecks, or the live tokenDrift tier).
//   L3 "release"  — L2 plus releaseSmoke PASSed (the release APK installed
//                   and driven on a device).
//
// HONESTY RULES — the rung must be honest to a fault, it is the vocabulary
// evidence is sold in:
//   - A rung is DERIVED from which steps actually ran and PASSED. It is never
//     declared: the `profile` argument is deliberately NOT part of the
//     derivation — a requested profile can never buy a rung its steps did not
//     earn (it is accepted so callers state what was asked for vs. earned).
//   - A SKIP never upgrades. A SKIPped device step does not count toward L2;
//     a SKIPped releaseSmoke (e.g. unsigned keystore) is NOT L3. The label
//     can never overclaim.
//   - A FAILED lane has no rung: the rung is only computed for a PASS
//     verdict; the receipt of a FAIL records evidenceLevel null.
//   - A FAST-MODE lane has no rung either — not even L0. `verify --fast` is
//     the inner loop, a signal rather than evidence, so a fast receipt must
//     never be silently reused as if it were a full-lane result: pass the
//     run's mode and "fast" derives null, always.
//   - The rung is COARSE by design. The per-step list (and the existing
//     strength string) stays the fine print alongside it — steps that may
//     SKIP for honest configuration absence (approvals unreviewed, no
//     exported schemas) are visible there; only the always-run steps gate
//     the desktop rungs, and only executed PASSes gate the device rungs.

/** The scaffold profile's step set (verify.mjs stepsForProfile.scaffold). */
const SCAFFOLD_CORE = [
  "specCoverage",
  "approvals",
  "componentStories",
  "reachability",
  "archDoc",
  "schemaHistory",
  "build",
  "unitTests",
];

/** Steps every PASS must carry to claim even L0 — they run in every profile and never SKIP. */
const L0_REQUIRED = ["build", "unitTests"];

/**
 * The steps that distinguish full desktop evidence (L1) from the scaffold
 * checks. None of these can SKIP — they PASS or FAIL — so "PASSed" is exactly
 * "ran green".
 */
const L1_REQUIRED = ["releaseBuild", "conformance", "goldenTrees", "a11y"];

/** On-device EXECUTION steps — the only steps that can earn L2. */
const DEVICE_EXECUTION = ["e2eSmoke", "tokenDrift", "androidChecks"];

/** The one step that can lift L2 to L3. */
const RELEASE_EXECUTION = "releaseSmoke";

const RUNG_NAMES = { L0: "scaffold", L1: "desktop", L2: "device", L3: "release" };

/**
 * The Compose Multiplatform pack's ladder — the step names above, as one
 * object a pack hands to the spine. THE LADDER IS THE PACK'S, NOT THE SPINE'S
 * (2026-09-03): vendored into a Kotlin backend, these names graded its
 * strongest run — detekt, Konsist, mutation, gitleaks — as L0 "scaffold" and
 * made L1 unreachable by construction. A fixed-amount understatement is not
 * conservative, it is wrong, and receipts are where labels get quoted. So a
 * pack declares its ladder (`evidenceLadder` on createXSteps' return); a pack
 * that declares none earns no rung at all, which is the honest grade for a
 * ladder nobody has calibrated. Every field is a list of step names except
 * `release`, one name. `names` maps rung → label.
 */
export const CMP_LADDER = Object.freeze({
  scaffoldCore: Object.freeze(SCAFFOLD_CORE),
  l0Required: Object.freeze(L0_REQUIRED),
  l1Required: Object.freeze(L1_REQUIRED),
  deviceExecution: Object.freeze(DEVICE_EXECUTION),
  release: RELEASE_EXECUTION,
  names: Object.freeze(RUNG_NAMES),
});

/**
 * Derive the receipt's evidence rung from the lane's step results.
 *
 * @param {Array<{name: string, verdict: string}>} stepResults the lane's steps
 *   as recorded on the receipt (verdict PASS | FAIL | SKIP per step)
 * @param {string} [profile] the profile that was REQUESTED — recorded context
 *   only, never part of the derivation (see honesty rules above)
 * @param {{mode?: string}} [opts] the run's mode ("full" | "fast"). "fast"
 *   derives null unconditionally — the inner loop earns no rung (see honesty
 *   rules above). Absent/other values mean full.
 * @returns {{rung: "L0"|"L1"|"L2"|"L3", name: string, satisfiedBy: string[]}|null}
 *   null when any step FAILed (a failed lane has no rung), when the run was
 *   fast-mode (the inner loop is never evidence), or when even the L0 floor
 *   was not earned. `satisfiedBy` lists the PASSed steps the rung counts as
 *   its evidence, in lane order.
 */
export function evidenceLevel(stepResults, profile, { mode, ladder } = {}) { // eslint-disable-line no-unused-vars
  if (mode === "fast") return null; // the inner loop derives no rung — ever
  // `ladder` absent → the Compose ladder (every caller before packs declared
  // one). `ladder: null` → the pack declares none: no rung, by decision.
  if (ladder === null) return null;
  const L = ladder ?? CMP_LADDER;
  const SCAFFOLD_CORE = L.scaffoldCore ?? [];
  const L0_REQUIRED = L.l0Required ?? [];
  const L1_REQUIRED = L.l1Required ?? [];
  const DEVICE_EXECUTION = L.deviceExecution ?? [];
  const RELEASE_EXECUTION = L.release ?? null;
  const RUNG_NAMES = L.names ?? CMP_LADDER.names;
  const steps = Array.isArray(stepResults) ? stepResults.filter((s) => s && typeof s.name === "string") : [];
  // A failed lane has no rung — and a lane with a step that could not run
  // (ERROR) has none either: a rung is evidence, and "could not check" is not.
  if (steps.some((s) => s.verdict === "FAIL" || s.verdict === "ERROR")) return null;
  const passed = new Set(steps.filter((s) => s.verdict === "PASS").map((s) => s.name));

  if (!L0_REQUIRED.every((name) => passed.has(name))) return null; // not even a stamp-time green build

  const inLaneOrder = (names) => steps.filter((s) => names.has(s.name) && passed.has(s.name)).map((s) => s.name);

  let rung = "L0";
  const counted = new Set(SCAFFOLD_CORE);

  if (L1_REQUIRED.every((name) => passed.has(name))) {
    rung = "L1";
    for (const name of L1_REQUIRED) counted.add(name);

    // Only an EXECUTED (PASSed) device step lifts to L2 — a SKIP never does.
    const deviceRan = DEVICE_EXECUTION.some((name) => passed.has(name));
    if (deviceRan) {
      rung = "L2";
      for (const name of DEVICE_EXECUTION) counted.add(name);

      // Only a PASSed releaseSmoke lifts to L3 — a SKIP (unsigned keystore,
      // no device) never does.
      if (RELEASE_EXECUTION && passed.has(RELEASE_EXECUTION)) {
        rung = "L3";
        counted.add(RELEASE_EXECUTION);
      }
    }
  }

  return { rung, name: RUNG_NAMES[rung], satisfiedBy: inLaneOrder(counted) };
}
