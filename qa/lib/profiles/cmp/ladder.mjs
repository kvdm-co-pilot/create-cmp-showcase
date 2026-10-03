// profiles/cmp/ladder.mjs — the Compose Multiplatform profile's evidence
// ladder: which of ITS steps earn which rung. Mobile's, by definition.
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
// THE LADDER IS THE PROFILE'S, NOT THE SPINE'S (2026-09-03): vendored into a
// Kotlin backend, these names graded its strongest run — detekt, Konsist,
// gitleaks — as L0 "scaffold" and made L1 unreachable by construction. A
// fixed-amount understatement is not conservative, it is wrong, and receipts
// are where labels get quoted. So the spine's rung derivation
// (qa/lib/evidence-level.mjs) takes a ladder from the profile's step pack
// (`evidenceLadder` on steps()' return) and defaults to NOTHING: a profile that
// declares no ladder earns no rung, which is the honest grade for a ladder
// nobody has calibrated. Stage 0 PR 3 moved this constant out of the spine so
// the spine carries no Compose step names at all.
//
// Every field is a list of step names except `release`, one name. `names`
// maps rung → label. A `cmp` L2 and another profile's L2 are different claims
// (receipt.pack says which); see AGNOSTIC-HARNESS-ARCHITECTURE.md §8.2.

/** The scaffold profile's step set (steps-cmp.mjs stepsForProfile.scaffold). */
const SCAFFOLD_CORE = [
  "specCoverage",
  "approvals",
  "componentStories",
  "reachability",
  "e2eCoverage",
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

/** The steps that lift L2 to L3 — every one must PASS (ADR-0016). */
const L3_EXECUTION = ["releaseSmoke"];

const RUNG_NAMES = { L0: "scaffold", L1: "desktop", L2: "device", L3: "release" };

/**
 * The reason texts a pre-`skipKind` receipt (0.19.0 and earlier) used when this
 * profile's runtime tier skipped for an environmental reason. The Stop hook
 * reads them ONLY for a receipt with no `skipKind`, and only for a step this
 * ladder's runtime tier already names — two gates, both ours.
 *
 * They lived in the core (qa/receipt-check.mjs) until 2026-09-10, where they
 * made the one gate that refuses "done" over a tier that never ran carry
 * "no Android device" for every stack. A profile with no legacy receipts
 * declares none, and the fallback simply does not apply.
 */
export const legacySkipReasons = Object.freeze([
  "no Android device",
  "maestro CLI not installed",
  "is held by",
  "devices attached",
  "CMP_DEVICE=none",
]);

export const CMP_LADDER = Object.freeze({
  scaffoldCore: Object.freeze(SCAFFOLD_CORE),
  l0Required: Object.freeze(L0_REQUIRED),
  l1Required: Object.freeze(L1_REQUIRED),
  l2Execution: Object.freeze(DEVICE_EXECUTION),
  l3Execution: Object.freeze(L3_EXECUTION),
  names: Object.freeze(RUNG_NAMES),
});
