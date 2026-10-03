// Does this profile ship plants its Rule 0 instrument can actually run?
//
// ONE QUESTION, TWO READERS, AND THAT IS WHY THIS FILE EXISTS RATHER THAN A
// PREDICATE INSIDE EITHER OF THEM. The instrument (framework-check.mjs and its
// runner) asks it to decide whether it can make the two profile-sourced plants;
// the grader (evidence-level.mjs) asks it to decide whether this profile may be
// graded at all. Those two answers must be the same answer — a profile the
// instrument cannot plant for, graded as if it had been calibrated, is exactly
// the defect below — and this repository already knows what happens when one
// question has two implementations: `flowCitation` and `scanCitations` held two
// copies of "what a citation looks like" and disagreed about the same file in
// the same tree (framework-check.mjs carries that episode in full).
//
// THE DEFECT. `scripts/stage2-gate.mjs` built two adopters differing in exactly
// one export — one shipping `plants`, one shipping none — ran the real lane in
// both, and both earned `L1 · every promise bound`. `evidenceLevel` derived the
// rung from the ladder alone and never asked whether the profile had plants, so
// the twin that could calibrate nothing was graded identically to the twin that
// could. Measured, not read: the ladder is the same ladder in both trees, which
// is what makes the pair evidence rather than an anecdote.
//
// That is a guarantee violation, not a missing feature. NORTH-STAR.md §8.9: "A
// profile with no calibrated plants earns no rung." §3 makes "Earns a rung
// without plants" one of a Stack Profile's four *nevers*. §6.7: "A profile ships
// with plants, run by `qa/framework-check.mjs` through the real runner in
// seconds and restored byte-for-byte, or it ships without a badge."
//
// ── WHICH READING OF "CALIBRATED", AND WHY THIS ONE ─────────────────────────
//
// The guarantee says "calibrated plants" and that word carries three readings
// of increasing strength. The one implemented here is the second, and the
// argument for it is the whole point of this comment.
//
// 1. DECLARED — the profile exports `plants`. Rejected as too weak, and it is
//    weak in a way that is cheap to exploit: `export const plants = {}` would
//    satisfy it. An empty object declares nothing, plants nothing, and would
//    buy the badge outright. A gate a one-line edit defeats is not a gate.
//
// 2. USABLE — the declaration is structurally sufficient for the instrument to
//    make the plants only a profile can supply. THIS ONE. It is not a second
//    opinion invented here: it is the instrument's OWN judgement, the test the
//    runner already applies to decide whether it can plant at all, moved to
//    where both readers can reach it. `{}` fails it, so does three fields out
//    of the three, and so does a `plants` whose sources are not functions.
//
// 3. OBSERVED — the plants have actually been watched failing, on this tree,
//    recently. This is the truest reading of the word, and it is NOT DERIVABLE
//    FROM A LANE RUN, so it is not implemented and not pretended to. The lane
//    does not run the instrument: `qa/framework-check.mjs` is a separate command
//    that plants into the tree, runs the lane N+1 times and restores every byte,
//    and nothing it does leaves a trace the lane could later read. Making it
//    derivable would mean a recorded, hash-bound framework-check result the
//    receipt path consults — a new artifact on the evidence path, which is an
//    ADR before it is any code (fit-test question 5). Until that exists, reading
//    3 is a claim nobody measured, and this file claims reading 2 exactly.
//
// WHAT READING 2 DOES NOT RESIST, said out loud: plant sources that are
// plausible and inert — functions returning text that violates nothing. Those
// are structurally sufficient and would earn the badge here. They are caught by
// the instrument instead, which is the only reader that can catch them:
// `assessPlantRun` fails a plant whose lane stayed green. So the honest division
// is that this file refuses a profile that CANNOT be calibrated, and the
// instrument refuses a profile whose plants do not bite. Neither can do the
// other's half, and a reader of a rung should know which half they are holding.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/plant-calibration.mjs in the
// create-cmp repo. Vendored byte-identical into qa/lib/ — edit the package
// source, then run `node scripts/sync-harness.mjs` in the create-cmp repo.

/**
 * The plant material only a profile can supply, and the instrument cannot make
 * up. A citation has to sit on a test, so two of the seven plants must WRITE a
 * test — in this stack's language, with its test-declaration syntax, at a path
 * it compiles — and those three fields are the whole of what that takes.
 *
 * These are the same three the runner tested inline before this file existed,
 * and the same three `harness init` seeds in the commented `export const plants`
 * skeleton (install/init.mjs). That agreement is deliberate and is
 * the answer to the discoverability objection this repository has already paid
 * for once: `harnessIntegrity` was a requirement no profile author could find,
 * and it cost an adopter a lane that could never mint a valid receipt
 * (NORTH-STAR.md §9.1). A badge floor asking for exactly what the on-ramp
 * already documents asks for nothing an author cannot discover.
 *
 * `unmeetableTier` is DELIBERATELY NOT HERE, and that is the one judgement call
 * in this file. It is a fourth field the skeleton also seeds, and without it the
 * instrument skips the tier-unmet plant by name. But a stack with a single tier
 * has no tier a host test cannot satisfy — the shape `harness init` itself
 * seeds — so requiring it would make the badge unreachable by construction for a
 * legitimate stack rather than for an uncalibrated one. A gate that cannot be
 * passed by a correct profile is a wrong verdict in the other direction, and the
 * instrument already reports that plant's absence per plant, by name, with the
 * field that would fix it.
 */
export const PLANT_MATERIAL = Object.freeze([
  ["testFileBasename", "string", "the file a planted citation lives in"],
  ["unboundCitationSource", "function", "a citation on a type declaration with no test under it"],
  ["tierUnmetCitationSource", "function", "a host-tier test citing a clause only another tier can observe"],
]);

/**
 * Can the Rule 0 instrument plant this profile's own violations?
 *
 * @param {object|null|undefined} plants the profile's `plants` declaration —
 *   NORTH-STAR §6's eighth. `undefined` (the profile exports none) and `null`
 *   mean the same thing here and are the same answer: no.
 * @returns {{ok: true, material: string[]}|{ok: false, reason: string, missing: string[]}}
 *   `missing` names the fields, so a caller can say what would fix it rather
 *   than only that something is wrong.
 */
export function plantCalibration(plants) {
  if (!plants || typeof plants !== "object") {
    return {
      ok: false,
      missing: PLANT_MATERIAL.map(([field]) => field),
      reason:
        "this profile declares no `plants` — the Rule 0 instrument cannot plant the two violations only a " +
        "profile can supply, so nothing it runs would calibrate anything (NORTH-STAR.md §6.7)",
    };
  }
  const missing = PLANT_MATERIAL.filter(([field, kind]) => typeof plants[field] !== kind).map(([field]) => field);
  if (missing.length) {
    return {
      ok: false,
      missing,
      reason:
        `this profile's \`plants\` declaration is missing ${missing.join(", ")} — ` +
        PLANT_MATERIAL.filter(([field]) => missing.includes(field))
          .map(([field, kind, what]) => `${field} (${kind}: ${what})`)
          .join("; ") +
        ". Without it the Rule 0 instrument skips the plants only this profile can supply and calibrates nothing",
    };
  }
  return { ok: true, material: PLANT_MATERIAL.map(([field]) => field) };
}
