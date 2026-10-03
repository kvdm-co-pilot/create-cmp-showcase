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
//   - A PROFILE THAT SHIPS NO PLANTS EARNS NO RUNG — the badge floor. A ladder
//     names rungs; plants are what prove the steps under them still bite. A
//     profile with the second and not the first is a vocabulary nobody
//     calibrated, and grading it says something about a lane that nothing
//     checked. See "the badge floor" below for the episode and the argument.
//   - The rung is COARSE by design. The per-step list (and the existing
//     strength string) stays the fine print alongside it — steps that may
//     SKIP for honest configuration absence (approvals unreviewed, no
//     exported schemas) are visible there; only the always-run steps gate
//     the desktop rungs, and only executed PASSes gate the device rungs.

// The Compose ladder that used to live here is qa/lib/profiles/cmp/ladder.mjs
// (Stage 0 PR 3). This module derives a rung from WHATEVER ladder it is handed;
// it carries no step names of its own and defaults to none — a profile that
// declares no ladder earns no rung. The rung vocabulary above is one profile's
// example, kept because it is the clearest statement of what a rung is for.
//
// WHERE THAT LADDER COMES FROM IS NOT THIS MODULE'S QUESTION, and saying it was
// is how a defect hid: this comment used to name one of the two spellings a
// profile may use (`evidenceLadder`, on the pack), which is the one the runner
// happened to read while the Stop hook read the other. Resolving between them
// is qa/lib/evidence-ladder.mjs's single job, and every caller goes through it.
// The fields read below are the fields that resolution compares — the pairing
// is derived from this source by test/evidence-ladder.test.mjs so the two
// cannot drift.

// ── THE BADGE FLOOR, AND WHY THE GRADER IS WHERE IT LIVES ───────────────────
//
// Until 2026-09-08 this module derived a rung from the ladder alone. Two
// scratch adopters built by scripts/stage2-gate.mjs, differing in EXACTLY one
// export — one shipping `plants`, one shipping none — ran the real lane and
// both earned `L1 · every promise bound`. The twin that could calibrate nothing
// was graded identically to the twin that could, against a guarantee that was
// already binding (NORTH-STAR.md §8.9, §6.7, and §3's third *never*).
//
// The floor could have been enforced one layer out, in the lane runner, leaving
// this function a pure ladder→rung derivation. It is here instead, because a
// grader that can be called without the floor is a grader that WILL be: the
// ladder's own two-spellings defect (§9.2) happened precisely because two
// readers reached the same question by different routes and only one of them
// was right. There is one grader, it takes the profile's plant declaration, and
// a caller that does not pass one gets NO RUNG rather than an ungrounded one —
// forgetting fails CLOSED, which is the only direction a floor may fail in.
//
// WHAT COUNTS AS PLANTS is deliberately not decided here. It is the
// instrument's judgement, and asking qa/lib/plant-calibration.mjs is how the
// grader and the instrument are kept from holding two definitions of the same
// word — the `flowCitation`/`scanCitations` failure, which cost a gate nobody
// was calibrating. That file also carries the argument for WHICH reading of
// "calibrated" is implemented (structurally usable, not merely declared, and
// not the observed reading, which no lane run can derive).
import { plantCalibration } from "./plant-calibration.mjs";

/**
 * A ladder's step-name field as a list, whichever shape it was declared in.
 *
 * `l3Execution` was the one graded field read as a single NAME while every
 * sibling was a list, and the asymmetry was not theoretical: the author of the
 * second-stack profile, writing from the contract alone, declared a list — and
 * their L3 was silently unreachable, because a list matched nothing. ADR-0016
 * makes the list the shape and keeps a lone string accepted, so no ladder that
 * graded before grades differently now: one name is a list of one.
 *
 * SHAPE ONLY — it normalises, it does not filter. The first draft dropped blank
 * and non-string entries here, on the reasoning that a value which is not a step
 * name can never be PASSed anyway. That was wrong, and a review proved it in one
 * line: `l3Execution: ["ship", null]` dropped the null, found every SURVIVING
 * entry passed, and awarded L3 while a step the author declared went unproven.
 * `mode: "all"` exists precisely because proven-by-some is not proven, and a
 * silently dropped entry is skipped by another name. The same declaration was
 * refused before this change.
 *
 * So malformed entries are REFUSED, in evidence-ladder.mjs where refusals live,
 * and this function is left with the one job it can do without judgement.
 *
 * @param {unknown} value
 * @returns {unknown[]} the declared entries, unfiltered, as a list
 */
function asList(value) {
  if (value === undefined || value === null) return [];
  return Array.isArray(value) ? [...value] : [value];
}

/**
 * A rung id → label map with every rung labelled and every label a string.
 *
 * The default IS the rung id: a ladder without labels still grades, and "L2" is
 * a truthful name for L2. Anything a profile declares that is not a string is
 * not a label and is replaced rather than propagated — the grader used to write
 * such a value straight onto the receipt while the console showed the id.
 *
 * @param {unknown} declared
 * @returns {{L0: string, L1: string, L2: string, L3: string}}
 */
function rungLabels(declared) {
  const d = declared && typeof declared === "object" ? declared : {};
  const out = {};
  for (const id of ["L0", "L1", "L2", "L3"]) out[id] = typeof d[id] === "string" && d[id].trim() ? d[id] : id;
  return out;
}

/**
 * ONE READER OF A LADDER. Every step-name field, normalised the same way, for
 * every consumer.
 *
 * THE CLASS, not the instances. A review found `rungFor` reading `l2Execution`
 * raw while `ladderStanding` read it through `asList`; the fix put `asList` on
 * that field in both — and the NEXT review found the same defect one field over,
 * because `scaffoldCore`, `l0Required` and `l1Required` were still read raw in
 * one reader and array-guarded in the other. Five fields had five different
 * normalisation expressions across two functions.
 *
 * Two readers of one declaration must normalise identically, or they are two
 * declarations. The way to guarantee that is not to write the same expression
 * twice carefully; it is to have one expression. Adding a sixth field cannot
 * reintroduce the split, because there is nowhere else to add it.
 *
 * A lone string is a list of one EVERYWHERE, not just where ADR-0016 argued it:
 * the argument was uniformity, and applying it to one field would have been the
 * asymmetry again under a new name. Malformed entries are not dropped here —
 * evidence-ladder.mjs refuses them, because a dropped entry is a step the author
 * declared and nobody proved.
 *
 * @param {object|null|undefined} ladder
 * @returns {{scaffoldCore: string[], l0Required: string[], l1Required: string[], l2Execution: string[], l3Execution: string[], names: object}}
 */
export function readLadder(ladder) {
  const L = ladder && typeof ladder === "object" ? ladder : {};
  const names = (v) => asList(v).filter((n) => typeof n === "string" && n.trim()).map((n) => n.trim());
  return {
    scaffoldCore: names(L.scaffoldCore),
    l0Required: names(L.l0Required),
    l1Required: names(L.l1Required),
    l2Execution: names(L.l2Execution),
    l3Execution: names(L.l3Execution),
    // THE LABEL RULE, ONCE. `names` is the sixth field, and it was the one the
    // two consumers still read differently: the grader spread the raw map over
    // defaults, the console applied `typeof === "string" ? … : rung id`. Where a
    // label is a string they agree; where it is a number or a list the grader
    // wrote the raw value onto the receipt and the console showed the rung id —
    // two labels for one rung of one ladder, and `names` is not a step-name
    // field so nothing refused it. A non-string label is not a label.
    names: rungLabels(L.names),
  };
}

/**
 * The rungs a ladder DECLARES, in order, with what earns each — derived once so
 * that the grader and every no-lane reader agree about which rungs exist before
 * anyone asks which are earned.
 *
 * A rung whose steps are not named is not declared. That is the same statement
 * "declares no ladder, earns no rung" has always made, one level down, and it
 * has to be made HERE rather than in each consumer: the previous fix gated the
 * grader on `l1Required.length` and left `ladderStanding` listing L0 and L1
 * unconditionally, so the console drew an L1 the lane could never mint — the
 * very defect the gate had just been corrected for, recreated by correcting it.
 *
 * @param {ReturnType<typeof readLadder>} L
 * @returns {Array<{id: string, requires: string[], mode: "all"|"any"}>}
 */
export function ladderRungs(L) {
  const rungs = [];
  if (L.l0Required.length) rungs.push({ id: "L0", requires: [...L.l0Required], mode: "all" });
  if (rungs.length && L.l1Required.length) rungs.push({ id: "L1", requires: [...L.l1Required], mode: "all" });
  if (rungs.length === 2 && L.l2Execution.length) rungs.push({ id: "L2", requires: [...L.l2Execution], mode: "any" });
  if (rungs.length === 3 && L.l3Execution.length) rungs.push({ id: "L3", requires: [...L.l3Execution], mode: "all" });
  return rungs;
}

/** Whether a rung's requirement is met by the set of PASSed step names. */
function rungMet(rung, passed) {
  return rung.mode === "any" ? rung.requires.some((n) => passed.has(n)) : rung.requires.every((n) => passed.has(n));
}

/**
 * Derive the receipt's evidence rung AND the sentence explaining an absent one.
 *
 * TWO RETURNS, ONE DECISION. `evidenceLevel` below is this function's `.level`
 * and nothing else — there is no second copy of the derivation — because the
 * rung and the reason it is missing must never be able to disagree. The reason
 * exists because "no rung, no explanation" is itself a defect this project has
 * already paid for: a foreign author who declared their ladder in the seeded
 * spelling got a green lane, no rung and not one word about why, and it took a
 * gate written in another ecosystem to find it (NORTH-STAR.md §9.2).
 *
 * The `why` is NOT written to the receipt. A receipt records what was earned;
 * this is a sentence for a human reading a lane, and putting it on the receipt
 * would change the receipt's schema, which is a decision for an ADR rather than
 * for this fix (fit-test question 5).
 *
 * @param {Array<{name: string, verdict: string}>} stepResults the lane's steps
 * @param {string} [profile] the profile that was REQUESTED (scaffold | local |
 *   ci | …) — recorded context only, never part of the derivation, and
 *   deliberately never quoted in `why`: it names the RUN, not the pack, and a
 *   message that confused the two would send a reader to the wrong file
 * @param {{mode?: string, ladder?: object|null, plants?: object|null}} [opts]
 * @returns {{level: {rung: string, name: string, satisfiedBy: string[]}|null, why: string|null}}
 *   `why` is null exactly when `level` is not.
 */
export function gradeEvidence(stepResults, profile, { mode, ladder, plants } = {}) { // eslint-disable-line no-unused-vars
  const none = (why) => ({ level: null, why });
  if (mode === "fast") return none("no evidence rung: this run was `--fast`, the inner loop — a signal, never evidence");

  // THE BADGE FLOOR IS ASKED FIRST, before the ladder, because it decides
  // whether this profile may be graded AT ALL: a ladder over uncalibrated
  // plants is a vocabulary for a claim nobody checked, so the ladder question
  // is moot until this one is answered. Both absences are reported together
  // when both apply — an author who fixes one and comes back for the other has
  // been charged two round trips by a message that knew both answers.
  const calibrated = plantCalibration(plants);
  if (!calibrated.ok) {
    const alsoNoLadder = !ladder || typeof ladder !== "object";
    return none(
      // The §6.7 citation is already inside `calibrated.reason`; repeating it
      // here made the most-read line in the lane cite the same section twice.
      `no evidence rung: ${calibrated.reason}. A profile ships plants its Rule 0 instrument can run — ` +
        "`node qa/framework-check.mjs` — or it ships without a badge (§8.9)" +
        (alsoNoLadder ? ", and this profile declares no `ladder` either, which alone earns no rung" : ""),
    );
  }

  // No ladder → no rung. There is no default: a rung is a claim in a
  // profile's own vocabulary, and the spine has none to lend. `null` and
  // `undefined` mean the same thing here — the pack declared nothing.
  if (!ladder || typeof ladder !== "object") {
    return none("no evidence rung: this profile declares no `ladder`, so there are no rungs to earn — which is the honest grade, not a failure");
  }
  const level = rungFor(stepResults, ladder);
  if (level) return { level, why: null };
  const steps = Array.isArray(stepResults) ? stepResults.filter((s) => s && typeof s.name === "string") : [];
  const red = steps.filter((s) => s.verdict === "FAIL" || s.verdict === "ERROR");
  if (red.length) {
    return none(
      `no evidence rung: ${red.map((s) => `${s.name} ${s.verdict}`).join(", ")} — a lane that failed, or that could not check, has no rung`,
    );
  }
  // THROUGH `readLadder`, like every other read. This line was the FOURTH reader
  // of a ladder field and it read raw — so `l0Required: "assemble"`, which the
  // loader accepts as a list of one, threw `.filter is not a function` here.
  // Not on the green path: on the path a lane with one SKIP takes, at
  // verify.mjs:641, after every step has run and before the receipt is written.
  // The commit that introduced `readLadder` claimed there was nowhere left to
  // read a ladder from; there were two more, and this is why the claim is now a
  // test rather than a sentence.
  const l0 = readLadder(ladder).l0Required;
  const floor = l0.filter((name) => !steps.some((s) => s.name === name && s.verdict === "PASS"));
  return none(
    `no evidence rung: this ladder's floor rung needs ${l0.join(", ") || "(nothing)"} to PASS and ` +
      `${floor.join(", ")} did not — a SKIP never earns a rung`,
  );
}

/**
 * Derive the receipt's evidence rung from the lane's step results.
 *
 * THE ONE GRADER. Everything that shows a rung derives it here or reads one a
 * run of this function wrote onto a receipt; nothing recomputes it its own way.
 *
 * @param {Array<{name: string, verdict: string}>} stepResults the lane's steps
 *   as recorded on the receipt (verdict PASS | FAIL | SKIP per step)
 * @param {string} [profile] the profile that was REQUESTED — recorded context
 *   only, never part of the derivation (see honesty rules above)
 * @param {{mode?: string, ladder?: object|null, plants?: object|null}} [opts]
 *   the run's mode ("full" | "fast"), the PROFILE's resolved ladder
 *   (qa/lib/evidence-ladder.mjs) and the PROFILE's `plants` declaration.
 *   "fast" derives null unconditionally — the inner loop earns no rung.
 *   Absent/other values for mode mean full; an absent `plants` means no rung,
 *   because the badge floor fails closed (see above).
 * @returns {{rung: "L0"|"L1"|"L2"|"L3", name: string, satisfiedBy: string[]}|null}
 *   null when the profile ships no usable plants (the badge floor), when it
 *   declares no ladder, when any step FAILed (a failed lane has no rung), when
 *   the run was fast-mode (the inner loop is never evidence), or when even the
 *   L0 floor was not earned. `satisfiedBy` lists the PASSed steps the rung
 *   counts as its evidence, in lane order. A caller that wants the SENTENCE for
 *   an absent rung calls `gradeEvidence` and reads `.why` — same decision, one
 *   implementation.
 */
export function evidenceLevel(stepResults, profile, opts = {}) {
  return gradeEvidence(stepResults, profile, opts).level;
}

/**
 * The ladder half of the derivation, once the badge floor has been cleared:
 * which rung THESE steps earn under THIS ladder, and nothing about whether the
 * profile was entitled to be graded. Private on purpose — `gradeEvidence` is
 * the only caller, so the floor cannot be walked around from inside this file
 * any more than it can from outside it.
 *
 * @param {Array<{name: string, verdict: string}>} stepResults
 * @param {object} ladder
 * @returns {{rung: string, name: string, satisfiedBy: string[]}|null}
 */
function rungFor(stepResults, ladder) {
  // ONE READER, ONE RUNG TABLE. Everything this function used to normalise for
  // itself now comes from `readLadder`, and which rungs exist comes from
  // `ladderRungs` — the same two calls `ladderStanding` makes. Two readers that
  // derive the same thing from the same bytes cannot disagree about it; two that
  // each write the expression cannot be relied on not to.
  const L = readLadder(ladder);
  const rungs = ladderRungs(L);
  const steps = Array.isArray(stepResults) ? stepResults.filter((s) => s && typeof s.name === "string") : [];
  // A failed lane has no rung — and a lane with a step that could not run
  // (ERROR) has none either: a rung is evidence, and "could not check" is not.
  if (steps.some((s) => s.verdict === "FAIL" || s.verdict === "ERROR")) return null;
  const passed = new Set(steps.filter((s) => s.verdict === "PASS").map((s) => s.name));

  // Climbed in order, stopping at the first unmet rung. A ladder that declares
  // none earns none — which is what a rung with no steps named for it has always
  // meant, and is now decided once, in `ladderRungs`, rather than by each
  // consumer remembering to check a length.
  const counted = new Set(L.scaffoldCore);
  let rung = null;
  for (const r of rungs) {
    if (!rungMet(r, passed)) break;
    rung = r.id;
    for (const name of r.requires) counted.add(name);
  }
  if (!rung) return null;

  const inLaneOrder = (names) => steps.filter((s) => names.has(s.name) && passed.has(s.name)).map((s) => s.name);
  return { rung, name: L.names[rung], satisfiedBy: inLaneOrder(counted) };
}

/**
 * WHAT WOULD EARN THE NEXT RUNG — the ladder read forward instead of backward.
 *
 * docs/proposals/LIVE-CONSOLE.md's fifth question ("what would earn the next
 * rung?") and §4's second gap: "the profile's unmet requirement is derived and
 * NAMED, not summarised". The names returned are the ladder's own step names,
 * verbatim — this function writes no prose about what a rung means, because a
 * sentence about `releaseSmoke` written here is a sentence that stops being
 * true in the first pack that spells its steps differently.
 *
 * THIS DOES NOT GRADE, AND THAT IS THE WHOLE POINT. The rung a tree has earned
 * arrives as `earned` — read from the receipt the lane wrote, whose grade came
 * from `gradeEvidence` above. A second derivation of the same rung, on a
 * console page, is exactly the defect NORTH-STAR.md §9.2 catalogues: two
 * readers, one question, only one of them right. So this lives in the grader's
 * own file, beside `rungFor`, reading the same fields off the same ladder — and
 * it takes the verdict rather than reaching for it.
 *
 * WHICH RUNGS EXIST is derived from the declaration too, never assumed to be
 * four. `rungFor` can only reach L2 when `l2Execution` names a step, and
 * only reach L3 when `release` does — so a ladder that names neither has two
 * rungs, and drawing four with two forever dark would be the console promising
 * an adopter a rung their profile cannot mint.
 *
 * @param {object|null} ladder the profile's resolved ladder (evidence-ladder.mjs)
 * @param {{earned?: string|null, passed?: string[]}} [opts] `earned` is the
 *   receipt's own `evidenceLevel` rung, read verbatim; `passed` the step names
 *   that PASSed in that same run, used only to say which of a rung's named
 *   requirements are still outstanding.
 * @returns {{available: false, reason: string}
 *   |{available: true, earned: string|null, atTop: boolean, orphanRung: boolean,
 *     rungs: Array<{id: string, name: string, earned: boolean, requires: string[], mode: "all"|"any"}>,
 *     next: {id: string, name: string, requires: string[], unmet: string[], mode: "all"|"any"}|null}}
 */
export function ladderStanding(ladder, { earned = null, passed = [] } = {}) {
  if (!ladder || typeof ladder !== "object") {
    return {
      available: false,
      reason: "this profile declares no `ladder`, so there are no rungs to earn — which is the honest grade, not a failure",
    };
  }
  // THE SAME TWO CALLS THE GRADER MAKES. This function used to normalise four
  // fields for itself and build its own rung list, gating L2 and L3 on length
  // but listing L0 and L1 unconditionally. When the grader was corrected so that
  // an empty required list earns nothing, this reader was not — so a ladder
  // declaring only `l0Required` graded L0 forever while the console drew an L1
  // with no steps in it, telling the adopter their next rung required nothing
  // and was somehow unearned. The fix for two readers disagreeing cannot itself
  // be written twice.
  const L = readLadder(ladder);
  const declared = ladderRungs(L).map((r) => ({ ...r, name: L.names[r.id] }));

  const earnedId = typeof earned === "string" && earned.trim() ? earned.trim() : null;
  const earnedIdx = earnedId ? declared.findIndex((r) => r.id === earnedId) : -1;
  const passedSet = new Set((Array.isArray(passed) ? passed : []).filter((s) => typeof s === "string"));
  const rungs = declared.map((r, i) => ({ ...r, earned: earnedIdx >= 0 && i <= earnedIdx }));
  // A rung the receipt NAMES and this ladder does not declare is reported as
  // exactly that. Nothing is marked earned (there is no rung here to mark), and
  // `orphanRung` says why — a receipt graded under a ladder that has since
  // changed is a real state, and quietly drawing every rung dark would make a
  // console that had lost track of a rung look like a tree that never earned
  // one. Which of the two is wrong is not a console's question; reporting the
  // pair is.
  const nextIdx = earnedIdx + 1;
  const next =
    nextIdx < declared.length
      ? { ...declared[nextIdx], unmet: declared[nextIdx].requires.filter((n) => !passedSet.has(n)) }
      : null;
  return {
    available: true,
    earned: earnedId,
    orphanRung: Boolean(earnedId) && earnedIdx === -1,
    atTop: next === null && earnedIdx >= 0,
    rungs,
    next,
  };
}
