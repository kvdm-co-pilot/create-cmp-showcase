// framework-check.mjs (lib) — Rule 0's instrument, aimed at an app's OWN tree.
//
// GATE-RULES Rule 0 says: before any real work is pointed at the harness, prove
// the FRAMEWORK returns — a deterministic PASS and a deterministic FAIL, fast,
// through the real lane machinery, with a bound short enough that a hang is
// obvious rather than patient.
//
// create-cmp's own `scripts/framework-check.mjs` proves that for the ENGINE: it
// stamps a scratch app and reads the lane it just shipped. That script has never
// existed inside a generated project, and it never can — it needs `bin/create-
// cmp.mjs` and a tree to stamp. Meanwhile the lane that DOES ship names it four
// times (verify.mjs, steps-cmp.mjs, evidence-badge.mjs, USAGE.md), pointing every
// adopter at a path they do not have.
//
// That dangling reference has a measured cost. payment-blueprint read those
// comments, could not find the file, and hand-built its own copy with nine
// plants; then, months later, briefed a whole wave to prove new gates by hand —
// plant, `./gradlew`, confirm red, revert, build again, 30–60 s per cycle — and
// burned ~38 minutes reproducing exactly what the missing instrument does in
// seconds. Their own diagnosis was "I failed to check what already existed". The
// truer reading is narrower and is ours: the harness advertised a tool it never
// handed over, so there was nothing in their tree to find.
//
// This module is the half of that instrument an app can run against itself. The
// plants are DERIVED from the tree rather than hardcoded, because an adopted
// project is not a stamped Compose app: it may have no `specs/`, no `qa/e2e/`,
// no Kotlin test source at all. A plant whose target is absent is reported as
// unavailable WITH ITS REASON and does not silently vanish — a framework check
// that skips everything and prints PASS is the failure this exists to refuse.
//
// Pure by construction: every function here takes data and returns data. The IO
// — reading the tree, writing the plant, running the lane, reverting in a
// `finally` — lives in the runner (qa/framework-check.mjs), so the decisions
// this file makes are unit-testable without a scaffold.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/framework-check.mjs in the
// create-cmp repo. The copy in a generated project's qa/lib/ is vendored
// byte-identical at scaffold time — edit the package source, then run
// `node scripts/sync-harness.mjs` in the create-cmp repo.

// The core's fallback grammar, for the ONE thing this file reads out of a flow:
// the citation marker. Importing it rather than re-declaring the pattern is the
// point — a second copy of "what a citation looks like" is how the selector and
// the scanner came to disagree about the same file (see `flowCitation`). The
// import performs no IO; every function below still takes data and returns data.
// The badge floor's two halves, imported for the same reason as the grammar
// above: the plant below calibrates THE grader the lane runs and THE definition
// of plant material the runner plants from, not a re-statement of either.
import { evidenceLevel, readLadder } from "./evidence-level.mjs";
import { plantCalibration } from "./plant-calibration.mjs";

/**
 * Per-direction bound. Rule 0's whole claim is about SPEED of refusal, so the
 * default is small on purpose: the smoke profile is every pure-Node gate and no
 * Gradle, which returns in around a second on a real tree. A direction that
 * does not return inside the bound is killed and reported as a hang — the bound
 * IS the assertion, never a courtesy timeout waited out.
 */
export const DEFAULT_BOUND_MS = 10_000;

/**
 * Every plant this instrument knows how to make. The kinds are named so tests
 * (and a report) can talk about them without matching prose.
 */
export const PLANT_KINDS = Object.freeze({
  ORPHANED_CITATION: "orphaned-citation",
  UNBOUND_CITATION: "unbound-citation",
  TIER_UNMET: "tier-unmet",
  FEATURE_WITHOUT_FLOW: "feature-without-flow",
  NESTED_FLOW: "flow-the-lane-never-runs",
  NARROWED_SURFACE: "narrowed-surface",
  EDITED_LANE: "edited-lane",
});

/**
 * The plants that need nothing but a lane. The step that reads the machine-owned
 * region exists in every project that has a lane at all — so these two are the
 * floor. If even these cannot run, the tree has no harness to check and the
 * instrument must say so rather than report a vacuous PASS.
 */
export const FLOOR_KINDS = Object.freeze([PLANT_KINDS.NARROWED_SURFACE, PLANT_KINDS.EDITED_LANE]);

/**
 * How the two floor plants say which receipt row they are about.
 *
 * THE ROW THAT VOUCHES IS THE ROW CARRYING THE VOUCHING DATA, not the row with
 * a particular name — the principle qa/lib/receipt-validate.mjs settled in
 * `checkLaneVouching` one layer out, mirrored here rather than re-invented.
 * These two plants used to declare `step: "harnessIntegrity"` as a literal.
 * That is a name the cmp pack chose for its own step; REQUIRED_EXPORTS never
 * mentions it and a profile author has no way to discover it. On a pack that
 * spells its self-check `harness_integrity`, both floor plants looked for a row
 * that does not exist and the instrument reported "the guard did not FAIL BY
 * NAME" — about a guard that had failed, by name, on the row immediately beside
 * it. An adopter's Rule 0 check therefore fails on a working lane, and the
 * message sends them into the harness instead of into the spelling.
 *
 * `vouching` says: find the row whose `harness` object carries the integrity
 * check's own findings (the field the receipt schema documents, written by
 * every row that performs it). `step` stays as the FALLBACK name, for receipts
 * written before rows carried one — the same two-step lookup checkLaneVouching
 * uses, and the same reason.
 *
 * `hookPattern` is matched against the Stop hook's refusal when the plant's
 * receipt is forged to PASS. It named `harnessIntegrity` too, and the hook's
 * refusal quotes the failing ROW'S name — so on that same pack the hook refused
 * correctly and the instrument called the correct refusal a framework defect.
 * The alternatives here are the core's own two wordings for this class of
 * refusal (qa/lib/receipt-validate.mjs `checkLaneVouching`: the failing-rows
 * branch and the did-not-vouch branch), neither of which is any pack's to spell.
 */
const VOUCHING_STEP = Object.freeze({
  step: "harnessIntegrity",
  vouching: true,
  hookPattern: "vouch|the row is the more specific truth",
});

/**
 * Which of THIS pack's steps observes the violation a plant makes.
 *
 * THE SECOND HALF OF THE SAME LESSON, and the reason a fix applied to instances
 * comes back. `VOUCHING_STEP` above took the literal `harnessIntegrity` out of
 * the two floor plants because it is a name the cmp pack chose (NORTH-STAR
 * §9.1). The other five plants kept theirs — `specCoverage` on the three spec
 * plants, `e2eCoverage` on the two flow ones — and those are the same string in
 * the same position, doing the same damage: a name the profile protocol never
 * mentions, that REQUIRED_EXPORTS does not ask for and a profile author has no
 * way to discover, asserted by the core against every profile there will ever
 * be.
 *
 * Measured rather than reasoned about, because §9.1's lesson is that a stack
 * assumption naming no stack is found only by running. A `harness init`
 * skeleton with its two steps renamed to snake_case and NOTHING else changed:
 * the lane FAILs, `spec_coverage` names the orphaned citation exactly as it
 * should, and this instrument stops at the first plant with
 *
 *   planted "orphaned citation" and the lane said FAIL (specCoverage: no row)
 *   — the guard did not FAIL BY NAME
 *
 * That is worse than the floor-plant case rather than equal to it, because the
 * spec plants run FIRST and a plant failure aborts the run: on such a tree the
 * two plants that were already fixed never execute at all, and the adopter's
 * Rule 0 check accuses their working lane while pointing at the harness.
 *
 * Three ways to fix it; only one is honest here.
 *
 * THE DATA SELECTOR `vouching` USES IS NOT AVAILABLE. It works because the CORE
 * computes the integrity findings and the row carries them — `harness`, the one
 * part of that row the receipt schema documents — so every pack that performs
 * the check answers to it whether it declares anything or not. There is no
 * equivalent for spec or journey coverage. What cmp's coverage row carries is
 * `details` ({clauses, withdrawn, tags, files}), written by cmp's own step
 * function; the generated skeleton writes a different shape ({clauses,
 * citations}) from the same core scanner; and nothing documents either as a
 * contract. Inventing a field now would put an undocumented requirement on
 * every future pack, silently unmet by any pack that omits it — the same
 * undiscoverable literal wearing different clothes.
 *
 * WIDENING TO "ANY ROW THAT WENT RED" IS NOT IT EITHER. The kept plant in
 * test/framework-check-agnostic.test.mjs builds the receipt that refutes it: the
 * integrity row FAILs quoting the planted clause id while the coverage gate sits
 * PASS beside it. A lookup that accepts that reads a broken lock as a working
 * spec gate, which is the mix-up this whole instrument exists to catch.
 *
 * SO THE PACK SAYS — through the declaration that already exists for this
 * instrument. `plants` is NORTH-STAR §6's eighth declaration, "the Rule 0/1
 * violations the instrument runs forever", and its endpoint in
 * AGNOSTIC-HARNESS-ARCHITECTURE.md §5.1 is a `plants(tree)` that returns the
 * plants themselves. Which step asserts a plant is part of that plant, so it
 * belongs there and no tenth declaration is invented to hold it. The KEY is a
 * plant kind — PLANT_KINDS, the core's vocabulary — and the VALUE is the pack's
 * spelling, so neither side has to learn the other's words.
 *
 * AND THE DEFAULT IS NOT cmp's SPELLING. That is the distinction §9.1 drew for
 * `compileStepName`: "the distinction is the KEY's presence, not its value — a
 * pack that declares none short-circuits on NOTHING rather than inheriting
 * another stack's step name." Here a pack that declares no step for a kind gets
 * null, and `plantRow` then holds the LANE instead of a row nobody named: the
 * assertion widens honestly, still refuses a lane that stayed green or went red
 * without naming what was planted, and reports the row it actually read.
 * Declaring narrows it back to exactly one row; declaring nothing never inherits
 * the wrong one.
 *
 * @param {{observedBy?: Record<string, string>}} tree
 * @param {string} kind one of PLANT_KINDS
 * @returns {string|null} this pack's name for the step, or null if it named none
 */
function observingStep(tree, kind) {
  const declared = tree?.observedBy?.[kind];
  return typeof declared === "string" && declared.length > 0 ? declared : null;
}

/** A clause id at the head of a spec list item: `- **HOME-02** — …`. */
const CLAUSE_RE = /^-\s+\*\*([A-Z][A-Z0-9]*-\d{2,})\*\*/m;

/**
 * The ids on a citation line, once the MARKER has already matched it:
 * `# SPEC: HOME-02, HOME-03 — …`. The id grammar is the core's (it is the same
 * one scanCitations uses); the marker in front of it is the profile's.
 */
const CITATION_IDS_RE = /SPEC:\s*([A-Z0-9,\s-]+)/;
const CLAUSE_ID_RE = /^[A-Z][A-Z0-9]*-\d{2,}$/;

/**
 * The first clause id in a spec, or null. Used to pick something real to plant
 * against: a clause that already exists and is already cited is the only kind
 * whose removal proves a gate READS, rather than proving a gate rejects garbage.
 * @param {string} text
 * @returns {string|null}
 */
export function firstClauseId(text) {
  if (typeof text !== "string") return null;
  const m = text.match(CLAUSE_RE);
  return m ? m[1] : null;
}

/**
 * The clause a flow cites, or null.
 *
 * THE MARKER IS THE PROFILE'S. This matched `^#\s*SPEC:` and nothing else,
 * which is true of Maestro YAML and false of a stack whose journeys are .ts,
 * .kt, .rb or anything C-family. The cost was not a refusal: a project whose
 * journeys cite with `//` was told `no "# SPEC:" citation in N flow file(s)`,
 * both e2eCoverage plants stood down, and the check printed PASS with the
 * reason folded into an ⓘ line — a gate reported as calibrated that had never
 * been read. And the disagreement was internal: the core's own fallback marker
 * (spec-model.mjs `DEFAULT_GRAMMAR.citationMarker`) has always accepted `//`,
 * so qa/lib/spec-coverage.mjs `scanCitations` was counting the very citation
 * this function could not see, in the same file, in the same tree.
 *
 * Same shape as scanCitations: test the profile's marker against the trimmed
 * line, then read the ids after it. A profile that declares no `grammar` gets
 * the same fallback the scan uses, so the two answers cannot drift apart again.
 *
 * @param {string} text
 * @param {{citationMarker?: RegExp}} [grammar] the SpecModel's grammar
 * @returns {string|null}
 */
export function flowCitation(text, grammar) {
  if (typeof text !== "string") return null;
  // The marker is the profile's; with none declared nothing can be a citation — and the lane refuses that profile before this runs.
  const MARKER = grammar?.citationMarker;
  if (!(MARKER instanceof RegExp)) return null;
  for (const line of text.split("\n")) {
    const trimmed = line.trim();
    if (!MARKER.test(trimmed)) continue;
    const m = trimmed.match(CITATION_IDS_RE);
    if (!m) continue;
    const id = m[1]
      .split(/[,\s]+/)
      .map((s) => s.trim())
      .find((s) => CLAUSE_ID_RE.test(s));
    if (id) return id;
  }
  return null;
}

/**
 * The clause-id PREFIX ("HOME" from "HOME-02"), used to mint planted ids that
 * cannot collide with a real clause: a spec's own family with a number far above
 * anything hand-authored.
 * @param {string} clause
 * @returns {string}
 */
export function clauseFamily(clause) {
  const m = String(clause ?? "").match(/^([A-Z][A-Z0-9]*)-/);
  return m ? m[1] : "SPEC";
}

/**
 * Decide which plants this tree can support, and say WHY each unavailable one
 * is unavailable.
 *
 * `grammar` is the SpecModel's (qa/lib/spec-model.mjs): the caller passes the
 * profile's, and a caller that passes none gets the same fallback the coverage
 * scan uses. It decides one thing here — what a citation in a journey looks
 * like — and getting it from the profile is what stops this instrument from
 * disagreeing with the gate it is calibrating (see `flowCitation`).
 *
 * `observedBy` is the profile's `plants.observedBy` — plant kind → the name
 * THIS pack gives the step that catches that kind of violation. A kind it does
 * not name gets a null `step`, which is an assertion over the lane rather than
 * over one row, never cmp's spelling by default (see `observingStep`).
 *
 * @param {{specs?: Array<{rel: string, text: string}>,
 *          flows?: Array<{rel: string, text: string}>,
 *          harnessLib?: string[],
 *          testDir?: string|null,
 *          plantsDeclared?: boolean,
 *          unmeetableTier?: string,
 *          observedBy?: Record<string, string>,
 *          grammar?: {citationMarker?: RegExp}}} tree
 * @returns {{plants: Array<{kind: string, label: string, step: string|null,
 *            names: string[], target: object}>,
 *           unavailable: Array<{kind: string, reason: string}>}}
 */
export function selectPlants(tree) {
  const specs = Array.isArray(tree?.specs) ? tree.specs : [];
  const flows = Array.isArray(tree?.flows) ? tree.flows : [];
  const harnessLib = Array.isArray(tree?.harnessLib) ? tree.harnessLib : [];
  const testDir = tree?.testDir ?? null;
  // Where this stack keeps the flow-shaped citation files the lane executes
  // (the profile's `layout.flows.dir`). The nested-flow plant must land INSIDE
  // it — a subdirectory the runner walks past — so the directory cannot be a
  // constant here. Derived from the flows themselves when the caller does not
  // say, so a caller with flows always gets the plant.
  const flowsDir = typeof tree?.flowsDir === "string" && tree.flowsDir
    ? tree.flowsDir
    : (flows.find((f) => typeof f?.rel === "string" && f.rel.includes("/"))?.rel.replace(/\/[^/]*$/, "") ?? null);

  const plants = [];
  const unavailable = [];
  const skip = (kind, reason) => unavailable.push({ kind, reason });

  // ── Spec-derived plants ──────────────────────────────────────────────────
  const spec = specs.find((s) => firstClauseId(s?.text));
  const clause = spec ? firstClauseId(spec.text) : null;

  if (!spec) {
    const why = specs.length
      ? `no clause of the form "- **ID-NN**" in ${specs.length} spec file(s)`
      : "no spec files — nothing declares behavior to plant against";
    for (const kind of [PLANT_KINDS.ORPHANED_CITATION, PLANT_KINDS.UNBOUND_CITATION, PLANT_KINDS.TIER_UNMET]) skip(kind, why);
  } else {
    // Renaming a live clause orphans every citation of it: whichever step reads
    // clause↔test citations must name the id it can no longer find. WHICH step
    // that is is the pack's to spell and never this file's — see observingStep.
    plants.push({
      kind: PLANT_KINDS.ORPHANED_CITATION,
      label: "orphaned citation",
      step: observingStep(tree, PLANT_KINDS.ORPHANED_CITATION),
      names: [clause],
      target: { spec: spec.rel, clause },
    });

    const family = clauseFamily(clause);
    // A tag on a CLASS with no test inside the binding window: the clause
    // exists, the tag exists, and nothing runs. The two remaining spec plants
    // need somewhere to put that Kotlin, so they hang on a test source dir.
    if (!testDir) {
      // TWO different causes reached this branch and the message named only
      // one of them, wrongly: a profile that declares no `plants` never even
      // looks for a test directory (framework-check.mjs passes testDir: null),
      // so an adopter with two perfectly good test directories was told they
      // had none. Worse, the plant this silently skips is `tier-unmet` — the
      // one that calibrates the gate the whole instrument exists to prove —
      // and the run still printed PASS. Saying which cause it is turns an
      // invisible gap into a one-line fix. (And the core names no language.)
      const why = tree?.plantsDeclared === false
        ? "this profile declares no plants — add a `plants` export { testFileBasename, unboundCitationSource, tierUnmetCitationSource, unmeetableTier }"
        : "no test source directory found — a planted citation has nowhere to live";
      skip(PLANT_KINDS.UNBOUND_CITATION, why);
      skip(PLANT_KINDS.TIER_UNMET, why);
    } else {
      plants.push({
        kind: PLANT_KINDS.UNBOUND_CITATION,
        label: "unbound citation",
        step: observingStep(tree, PLANT_KINDS.UNBOUND_CITATION),
        names: [`${family}-99`],
        target: { spec: spec.rel, clause: `${family}-99`, testDir },
      });
      // The tier this plant declares must be the PROFILE'S. The core used to
      // fall back to `?? "e2e"` at the write site — a CMP tier name, asserted
      // by the spine into any stack's spec file. A profile that ships plants
      // but names no unmeetable tier would then plant a clause tagged with a
      // tier it does not define, and the resulting FAIL would be right for
      // entirely the wrong reason. There is no default; there is a skip.
      if (!tree?.unmeetableTier) {
        skip(
          PLANT_KINDS.TIER_UNMET,
          "this profile's plants declare no `unmeetableTier` — name a tier in `tiers.satisfying` that a host-tier test cannot satisfy",
        );
      } else {
        plants.push({
          kind: PLANT_KINDS.TIER_UNMET,
          label: "tier unmet",
          step: observingStep(tree, PLANT_KINDS.TIER_UNMET),
          names: [`${family}-98`],
          target: { spec: spec.rel, clause: `${family}-98`, testDir, unmeetableTier: tree.unmeetableTier },
        });
      }
    }
  }

  // ── Flow-derived plants ──────────────────────────────────────────────────
  // Both strip EVERY citation from every flow, not just one line. e2eCoverage
  // asks whether a screen feature has any device journey at all, so removing a
  // single citation from a flow that carries several leaves the feature
  // covered and the gate — correctly — green. A plant that does not actually
  // produce the violation is worse than no plant: it reads as a calibrated
  // gate while proving nothing.
  //
  // WHICH flows cite is decided by the profile's citation marker, never by `#`:
  // see `flowCitation`. The skip below prints the marker it used, because "no
  // citation in 3 flow files" over three flows that all carry one is a sentence
  // that sends the reader to the flows instead of to the grammar.
  const grammar = tree?.grammar ?? null;
  const marker = grammar?.citationMarker instanceof RegExp ? grammar.citationMarker : null;
  const citingFlows = marker ? flows.filter((f) => flowCitation(f?.text, grammar)) : [];
  if (!citingFlows.length) {
    const why = !marker
      ? "the profile declares no grammar.citationMarker — the plant selector cannot read a citation (the lane refuses this profile for the same reason)"
      : flows.length
      ? `no citation matching /${marker.source}/ in ${flows.length} flow file(s) — the journey-coverage gate has nothing to lose`
      : `no flows${flowsDir ? ` under ${flowsDir}` : ""} — this project declares no journeys`;
    skip(PLANT_KINDS.FEATURE_WITHOUT_FLOW, why);
    skip(PLANT_KINDS.NESTED_FLOW, why);
  } else {
    const rels = citingFlows.map((f) => f.rel);
    // A real feature with a screen and a spec and no device journey at all.
    plants.push({
      kind: PLANT_KINDS.FEATURE_WITHOUT_FLOW,
      label: "feature without a flow",
      step: observingStep(tree, PLANT_KINDS.FEATURE_WITHOUT_FLOW),
      names: [],
      // FAIL BY NAME, without knowing this project's feature names: the gate
      // must name the feature it caught, in the [brackets] its reason uses.
      reasonPattern: String.raw`\[[^\]\s]+\]`,
      target: { flows: rels },
    });
    // The citations move into a subdirectory of the flows directory that the
    // lane's own directory run does not descend into. The tags exist, the flow
    // is real, and nothing executes it — which must read exactly like having no
    // journey at all.
    plants.push({
      kind: PLANT_KINDS.NESTED_FLOW,
      label: "flow the lane never runs",
      step: observingStep(tree, PLANT_KINDS.NESTED_FLOW),
      names: [],
      reasonPattern: String.raw`\[[^\]\s]+\]`,
      target: { flows: rels, nestInto: `${flowsDir}/wip` },
    });
  }

  // ── Region plants — the floor ────────────────────────────────────────────
  // A narrowed declaration un-attests a whole layer while every checker stays
  // intact (payment-blueprint's planted proof); an edited lane cannot vouch for
  // its own verdict. Both are read by the step that vouches for the lane, which
  // needs only a lane — see `vouching` below for how that row is found.
  plants.push({
    kind: PLANT_KINDS.NARROWED_SURFACE,
    label: "narrowed surface declaration",
    ...VOUCHING_STEP,
    // Assert the FILE, not an internal state word. "unrecorded" only holds when
    // the declaration is absent from the lock — true for a freshly stamped app,
    // false for a repo whose lock was taken after `harness init` wrote the
    // surface, where the identical edit reads as "modified". Both are the same
    // correct refusal; pinning one of them made the instrument fail on a lane
    // that was working. What the plant actually cares about is that the refusal
    // NAMES what it refused over, which is now true in either state.
    names: ["qa/verified-surface.json"],
    target: { declaration: "qa/verified-surface.json" },
  });

  const spine = harnessLib.find((rel) => rel.endsWith("/spec-coverage.mjs")) ?? harnessLib[0] ?? null;
  if (!spine) {
    skip(PLANT_KINDS.EDITED_LANE, "no machine-owned lane files found under qa/lib — there is no region to edit");
  } else {
    plants.push({
      kind: PLANT_KINDS.EDITED_LANE,
      label: "edited lane cannot vouch",
      ...VOUCHING_STEP,
      // "modified" is the integrity check's own status word (qa/lib/harness-
      // region.mjs), not a step name and not a stack's — every pack's row
      // reports it, because the core computes it.
      names: ["modified"],
      target: { file: spine },
    });
  }

  return { plants, unavailable };
}

/**
 * Is this set of plants enough to make a claim at all?
 *
 * The refusal is the point. An instrument that finds nothing to plant and
 * prints PASS has proven that it ran, not that the framework returns — the
 * "green with gaps" the harness exists to refuse, applied to itself.
 *
 * @param {Array<{kind: string}>} plants
 * @returns {{ok: true}|{ok: false, reason: string}}
 */
export function assessCoverage(plants) {
  const kinds = new Set((plants ?? []).map((p) => p?.kind));
  const missingFloor = FLOOR_KINDS.filter((k) => !kinds.has(k));
  if (missingFloor.length === FLOOR_KINDS.length) {
    return {
      ok: false,
      reason:
        "no plant could be made at all — this tree has no machine-owned lane to check. " +
        "Run this from a project root whose qa/lib/ carries the vendored harness.",
    };
  }
  if (missingFloor.length) {
    return { ok: false, reason: `the region plants are the floor and ${missingFloor.join(", ")} could not be made` };
  }
  return { ok: true };
}

/**
 * THE BADGE FLOOR'S KEPT PLANT — "a profile with no calibrated plants earns no
 * rung" (NORTH-STAR.md §8.9), watched failing rather than assumed to work.
 *
 * WHY IT IS HERE AND NOT ONLY IN THE HARNESS'S OWN SUITE. GATE-RULES Rule 1 is
 * explicit about where a plant goes — "Add the plant to `qa/framework-check.mjs`
 * and run that" — and the reason is this gate's own subject: the floor exists to
 * protect a claim made on EVERY adopter's receipt, so the plant that proves it
 * still bites has to run in every adopter's tree, forever, not once in ours.
 * A gate proved only in the repository that wrote it is a gate proved on the one
 * profile it was written against, which is how every wrong verdict in
 * NORTH-STAR.md §9.1 survived a green suite.
 *
 * THE VIOLATION PLANTED is the twin `scripts/stage2-gate.mjs` built: this
 * profile's own ladder and a lane whose rows all PASS, graded with the `plants`
 * declaration REMOVED. Before 2026-09-08 that earned `L1` — measured on two
 * scratch adopters differing in exactly one export. It must now earn nothing,
 * and the same rows WITH the declaration must still earn their rung, or the
 * plant would go green on a grader that had simply stopped grading anyone.
 * Those two halves are the same pair criteria F and G of the stage-2 gate make,
 * and they are made here in microseconds instead of two adopted trees.
 *
 * NO TREE IS TOUCHED AND NO LANE IS RUN. The rows are synthesised from the
 * ladder's own step names, so this asserts over the GRADER — which is the only
 * thing the floor lives in. That is also its limit, and it is stated in the
 * runner's own output rather than hidden here: it proves the rung a lane WOULD
 * be given, not that a lane was run.
 *
 * @param {{ladder: object|null|undefined, plants: object|null|undefined}} profile
 *   the profile's resolved ladder (qa/lib/evidence-ladder.mjs) and its `plants`
 *   declaration — both exactly as the lane runner reads them
 * @returns {{ok: true, rung: string}
 *   |{ok: false, reason: string}
 *   |{available: false, reason: string}} `available: false` is neither a pass
 *   nor a failure: it is a plant this tree cannot make, reported with its cause
 *   the same way every unavailable plant is.
 */
export function assessBadgeFloor({ ladder, plants } = {}) {
  const calibrated = plantCalibration(plants);
  if (!calibrated.ok) {
    return { available: false, reason: `${calibrated.reason} — there is no declaration to strip, and no rung to lose` };
  }
  if (!ladder || typeof ladder !== "object") {
    return {
      available: false,
      reason:
        "this profile declares no `ladder` a reader can see without starting a lane, so no rung exists either way " +
        "(a ladder declared only on the object `steps(ctx)` returns is invisible here — qa/lib/evidence-ladder.mjs)",
    };
  }
  // Through `readLadder`, because this is Rule 0's instrument and it is called
  // at top level with no try/catch: a lone-string `l0Required` spread here
  // became eight one-character step names, earned no rung, and took the
  // framework check down with an unhandled TypeError — the one thing that
  // watches the badge floor still bite, brought down by a ladder the loader
  // had graded clean.
  const L = readLadder(ladder);
  const names = [...new Set([...L.l0Required, ...L.l1Required])];
  const rows = names.map((name) => ({ name, verdict: "PASS" }));
  const earned = evidenceLevel(rows, null, { mode: "full", ladder, plants });
  if (!earned) {
    return {
      available: false,
      reason:
        `this profile's ladder grants no rung even with ${names.length ? names.join(", ") : "every declared step"} PASSing, ` +
        "so there is no rung for the floor to withhold",
    };
  }
  // THE PLANT: the same rows, the same ladder, and no plant material.
  const stripped = evidenceLevel(rows, null, { mode: "full", ladder, plants: undefined });
  if (stripped) {
    return {
      ok: false,
      reason:
        `PLANTED the profile's own ladder with its \`plants\` declaration removed and the lane still earned ` +
        `${stripped.rung} · ${stripped.name} — the badge floor is not being applied. NORTH-STAR.md §8.9: "a profile ` +
        `with no calibrated plants earns no rung"; §6.7: a profile ships plants this instrument can run, or it ships ` +
        `without a badge. The grader is qa/lib/evidence-level.mjs and the floor it asks is qa/lib/plant-calibration.mjs`,
    };
  }
  return { ok: true, rung: earned.rung };
}

/**
 * What "FAIL BY NAME" means for one plant, in ONE place.
 *
 * Two callers need this answer — `plantRow`, to pick the row a plant with no
 * declared step is about, and `assessPlantRun`, to say which part of the
 * naming is missing — and a second copy of "what counts as naming" is exactly
 * how `flowCitation` and `scanCitations` came to disagree about the same file
 * in the same tree. So there is one, and the caller that needs a sentence gets
 * the offending part back rather than re-deriving it.
 *
 * @param {{reason?: string}} row
 * @param {{names?: string[], reasonPattern?: string}} plant
 * @returns {{kind: "name"|"pattern", want: string, reason: string}|null} null when the row names everything asked of it
 */
function missingName(row, plant) {
  const reason = String(row?.reason ?? "");
  for (const name of plant?.names ?? []) {
    if (!reason.includes(name)) return { kind: "name", want: name, reason };
  }
  if (plant?.reasonPattern && !new RegExp(plant.reasonPattern).test(reason)) {
    return { kind: "pattern", want: plant.reasonPattern, reason };
  }
  return null;
}

/**
 * The receipt row a plant's assertion is about — found three ways, in the order
 * of how much each one can be trusted about a pack nobody here has met.
 *
 * BY ITS DATA, for the two floor plants. Their assertion is about the step that
 * VOUCHES FOR THE LANE, whatever the pack calls it, and the core computes the
 * findings that row carries — so `harness` identifies it on every pack, with
 * the name only as the pre-`harness` fallback. See VOUCHING_STEP.
 *
 * BY THE NAME THE PACK GAVE IT, when the pack declared one for this plant kind
 * (`plants.observedBy`). This is the sharp case and the one to prefer: a
 * different row failing instead is the mix-up the instrument exists to catch,
 * and only a named row can catch it.
 *
 * BY THE LANE, when the pack declared nothing. There is no honest third source
 * for another pack's step name (see `observingStep`), so the assertion becomes
 * "some row went red naming what was planted" — the row carrying the plant's own
 * fingerprint. Only FAIL rows can satisfy it: an ERROR is a step that fell over,
 * not a gate that read something and refused, and treating the two alike is how
 * a crash comes to read as a calibrated gate. When no FAIL row names the plant,
 * a red row is returned anyway rather than null, because `assessPlantRun` then
 * prints that row's reason — "this is what your lane actually said" is a far
 * better sentence to hand an adopter than "no row" — and the FAIL rows are
 * offered ahead of the ERROR ones, or a step that blew up alongside a gate that
 * fired correctly would be reported as the finding. This branch is weaker than
 * a declared name and is meant to be: it is what a pack gets for not saying,
 * and it still bites.
 *
 * @param {Array<object>} steps
 * @param {{step?: string|null, vouching?: boolean, names?: string[], reasonPattern?: string}} plant
 * @returns {object|null}
 */
export function plantRow(steps, plant) {
  const rows = Array.isArray(steps) ? steps : [];
  const byName = plant?.step ? (rows.find((s) => s && s.name === plant.step) ?? null) : null;
  if (plant?.vouching) return rows.find((s) => s && s.harness && typeof s.harness === "object") ?? byName;
  if (plant?.step) return byName;
  const failed = rows.filter((s) => s && s.verdict === "FAIL");
  return failed.find((s) => !missingName(s, plant)) ?? failed[0] ?? rows.find((s) => s && s.verdict === "ERROR") ?? null;
}

/**
 * Judge one planted run. Every branch here is a distinct framework defect and
 * says which one it is: a hang, a lane that produced no receipt, a guard that
 * did not fail, or a guard that failed WITHOUT NAMING what it caught.
 *
 * "FAIL BY NAME" is not decoration. A gate that fails with a generic message
 * costs the reader the diagnosis every time it fires, and — worse — cannot be
 * told apart from a gate failing for an unrelated reason, which is how a
 * calibration passes on a gate that was never actually read.
 *
 * Every message below names the row it ACTUALLY READ, not the row the plant
 * asked for: on a pack whose steps are spelled differently those are two
 * different strings, and reporting the request instead of the finding is how a
 * lookup miss reads as a gate defect.
 *
 * @param {{hung?: boolean, ms?: number, exit?: number|null,
 *          receipt?: {verdict?: string, steps?: Array<object>}|null,
 *          stderr?: string}} run
 * @param {{label: string, kind?: string, step?: string|null, names?: string[], vouching?: boolean}} plant
 * @param {number} boundMs
 * @returns {{ok: true}|{ok: false, reason: string}}
 */
export function assessPlantRun(run, plant, boundMs) {
  const label = plant?.label ?? "(unnamed plant)";
  if (run?.hung) {
    return { ok: false, reason: `"${label}" did not return inside ${boundMs}ms — the framework HANGS on a failing input` };
  }
  const receipt = run?.receipt;
  if (!receipt) {
    const tail = String(run?.stderr ?? "").slice(-600);
    return { ok: false, reason: `"${label}" returned no receipt (exit ${run?.exit ?? "?"})${tail ? `:\n${tail}` : ""}` };
  }
  const row = plantRow(receipt.steps, plant);
  const rowName = row?.name ?? plant.step ?? "the lane";
  if (receipt.verdict !== "FAIL" || !row || row.verdict !== "FAIL") {
    // A missing row says what was looked for. "harnessIntegrity: no row" over a
    // receipt whose vouching row is called something else is a true sentence
    // that points at the wrong thing. The third branch is the pack that named
    // no step for this kind: "no row" would be the same wrong sentence again,
    // so it says what the lookup actually was AND names the declaration that
    // would make it sharp — an adopter cannot fix a requirement nobody states.
    const found = row
      ? `${rowName}: ${row.verdict}`
      : plant.vouching
        ? `no row carries a \`harness\` object and none is named ${plant.step}`
        : plant.step
          ? `${plant.step}: no row`
          : `no row on this receipt went red at all, and this profile's \`plants.observedBy\` names no step for ${plant.kind ?? "this plant"}`;
    return {
      ok: false,
      reason: `planted "${label}" and the lane said ${receipt.verdict} (${found}) — the guard did not FAIL BY NAME`,
    };
  }
  // Some gates name something the selector cannot know in advance — a feature
  // this project happens to have. `reasonPattern` is how those still assert
  // FAIL BY NAME instead of settling for "it went red"; `missingName` holds
  // both halves so the row lookup above cannot drift from the judgement here.
  const missing = missingName(row, plant);
  if (missing) {
    return missing.kind === "name"
      ? { ok: false, reason: `${rowName} FAILed on "${label}" but did not NAME ${missing.want}:\n${missing.reason}` }
      : { ok: false, reason: `${rowName} FAILed on "${label}" but named nothing matching /${missing.want}/:\n${missing.reason}` };
  }
  return { ok: true };
}

/**
 * Judge the baseline (and the post-revert re-run): a tree that cannot go green
 * on its own has nothing to plant against, and every FAIL below would be
 * unattributable.
 *
 * @param {{hung?: boolean, receipt?: object|null, exit?: number|null, stderr?: string}} run
 * @param {string} phase  "baseline" or "revert"
 * @param {number} boundMs
 * @returns {{ok: true}|{ok: false, reason: string}}
 */
export function assessGreenRun(run, phase, boundMs) {
  if (run?.hung) {
    return { ok: false, reason: `the ${phase} run did not return inside ${boundMs}ms — the framework HANGS on a passing input` };
  }
  if (!run?.receipt) {
    const tail = String(run?.stderr ?? "").slice(-600);
    return { ok: false, reason: `the ${phase} run returned no receipt (exit ${run?.exit ?? "?"})${tail ? `:\n${tail}` : ""}` };
  }
  if (run.receipt.verdict !== "PASS") {
    const bad = (run.receipt.steps ?? [])
      .filter((s) => s?.verdict === "FAIL" || s?.verdict === "ERROR")
      .map((s) => `${s.name}: ${String(s.reason ?? "").split("\n")[0]}`)
      .join("; ");
    const suffix =
      phase === "revert"
        ? " — the plants were not the only cause, or a revert did not restore the tree"
        : " — fix the tree before calibrating anything against it";
    return { ok: false, reason: `the ${phase} run is ${run.receipt.verdict} (${bad || "no failing row named"})${suffix}` };
  }
  return { ok: true };
}

/**
 * Rule 1's stated bound, made checkable.
 *
 * Rule 1 says a calibration is "four steps, seconds each". That sentence has
 * always been prose, and prose does not refuse: payment-blueprint calibrated
 * through a 30–60 s composite Gradle build, which violated the rule on its own
 * terms from the first cycle, and nothing noticed for three occurrences. The
 * cost is not the single cycle — it is per-instance cost times every instance,
 * which is how 38 minutes disappears into something that looks like rigour.
 *
 * A calibration cycle slower than this is not wrong, but it is a finding: it
 * means the plant is being run through the wrong instrument, and the report
 * must say so while the choice is still cheap to change.
 */
export const CALIBRATION_BUDGET_MS = 5_000;

/**
 * @param {Array<{label: string, ms: number}>} cycles
 * @param {number} [budgetMs]
 * @returns {{withinBudget: boolean, slowest: {label: string, ms: number}|null,
 *            totalMs: number, note: string|null}}
 */
export function assessCalibrationCost(cycles, budgetMs = CALIBRATION_BUDGET_MS) {
  const rows = Array.isArray(cycles) ? cycles.filter((c) => Number.isFinite(c?.ms)) : [];
  const totalMs = rows.reduce((n, c) => n + c.ms, 0);
  if (!rows.length) return { withinBudget: true, slowest: null, totalMs: 0, note: null };
  const slowest = rows.reduce((a, b) => (b.ms > a.ms ? b : a));
  if (slowest.ms <= budgetMs) return { withinBudget: true, slowest, totalMs, note: null };
  return {
    withinBudget: false,
    slowest,
    totalMs,
    note:
      `slowest calibration cycle "${slowest.label}" took ${slowest.ms}ms against a ${budgetMs}ms budget. ` +
      `GATE-RULES Rule 1 calls a calibration "four steps, seconds each" — a cycle past that is being run ` +
      `through the wrong instrument, and the cost is paid on every plant forever.`,
  };
}
