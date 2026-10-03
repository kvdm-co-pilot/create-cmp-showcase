// The evidence ladder, resolved — ONE answer, for every reader.
//
// WHY THIS FILE EXISTS. Until 2026-09-08 the ladder had two spellings and each
// had a different reader, and neither reader knew the other existed:
//
//   `export const ladder`         the top-level declaration. `harness init`
//                                 seeds it as THE way to declare rungs
//                                 (install/init.mjs), and
//                                 qa/receipt-check.mjs read it.
//   `evidenceLadder` on the pack  a key on the object `steps(ctx)` returns.
//                                 The lane runner read only this one
//                                 (qa/verify.mjs).
//
// The one real profile exports BOTH, out of the same frozen constant, so
// whichever reader ran got the same object and the split was invisible from
// inside it. A foreign author who did exactly what the seeded skeleton says
// declared `ladder`; the lane looked for `evidenceLadder`, found none, and
// graded the run at NO RUNG with no message — because "no ladder, no rung" is
// the honest answer to a profile that declared nothing and a silently wrong one
// to a profile that declared it in the other place. Found on 2026-09-08 by
// scripts/stage2-gate.mjs running in an ecosystem this code had never met,
// which is how all eight wrong verdicts in NORTH-STAR.md §9.1 were found.
//
// BOTH SPELLINGS STAY, because they answer different questions. The pack's is
// the only one a pack can COMPUTE — it is built inside `steps(ctx)`, where the
// run's own context is in hand. The top-level one is the only one a reader that
// must not START A LANE can ask: the Stop hook deciding whether a tier that
// could have run did holds a project root and nothing else, and calling
// `steps(ctx)` to find out would be a done-gate that runs a lane
// (lib/profiles/cmp/index.mjs states this reason where it re-exports the
// constant).
//
// THE PRECEDENCE, AND WHY IT IS DELIBERATELY UNOBSERVABLE. The top-level
// declaration wins. The argument is not that it is better data; it is that it
// is the data the WEAKEST reader can see, and the weakest reader and the lane
// must grade from the same bytes or the product's own comparability rule
// (NORTH-STAR.md §8.9 — one pack's L2 and another's are different claims)
// becomes a question of which reader ran. Preferring the pack's would mean the
// lane grades from something the Stop hook can never see.
//
// And because the two must be the SAME ladder, the precedence can never change
// a rung: where both are present they must agree, and where they disagree this
// refuses instead of choosing. Deriving a grade from a contradiction would mint
// a claim nobody made, in the vocabulary evidence is actually sold in. That
// refusal is the one gate this module adds, and it is calibrated by a kept
// plant that watches the real lane refuse by name (test/evidence-ladder.test.mjs).
//
// WHAT COUNTS AS DISAGREEMENT is exactly the fields the grader reads
// (evidence-level.mjs), and nothing else. A difference the grader cannot see
// cannot change a rung, so refusing over one would be a gate with no wrong
// verdict behind it — mechanism for its own sake, which NORTH-STAR.md §8.8
// presumes against. The pairing is pinned by a test that derives the read
// fields from evidence-level.mjs's own source, so the two cannot drift apart.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/evidence-ladder.mjs in the
// create-cmp repo. Vendored byte-identical into qa/lib/ — edit the package
// source, then run `node scripts/sync-harness.mjs` in the create-cmp repo.

// The contract is DATA — no imports of its own, no I/O — so reading it here
// costs a resolver's worth of nothing and buys the thing that makes it real:
// the refusals an author is shown are the refusals that fire.
import { ladderRungs, readLadder } from "./evidence-level.mjs";
import { CONTRACT, requiredFields } from "./profile-contract.mjs";

/**
 * The ladder fields `evidenceLevel` actually reads (evidence-level.mjs). Two
 * declarations differing anywhere else are not in disagreement about any grade,
 * so they are not refused.
 */
export const GRADED_FIELDS = Object.freeze(["scaffoldCore", "l0Required", "l1Required", "l2Execution", "l3Execution", "names"]);

/**
 * The graded fields that hold STEP NAMES, derived rather than typed. `names` is
 * the rung-label map and holds words, not steps; everything else on the ladder
 * is a list of step names that must match what the lane PASSed.
 *
 * Derived because the field that broke this file was the one nobody added to a
 * list they were maintaining by hand.
 */
export const STEP_NAME_FIELDS = Object.freeze(GRADED_FIELDS.filter((f) => f !== "names"));

/** The two spellings, named the way an author wrote them, for every message below. */
const DECLARED_SPELLING = "`export const ladder` (the profile's top-level declaration)";
const PACK_SPELLING = "`evidenceLadder` (a key on the object `steps(ctx)` returns)";

/** A declaration is PRESENT when it is neither absent nor an explicit "none". */
function present(v) {
  return v !== undefined && v !== null;
}

/**
 * Deep equality with functions compared by IDENTITY — a ladder is data, and two
 * distinct functions in the same slot are a difference this cannot see through
 * and must not pretend to.
 */
function same(a, b) {
  if (a === b) return true;
  if (Array.isArray(a) || Array.isArray(b)) {
    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) return false;
    return a.every((x, i) => same(x, b[i]));
  }
  if (a && b && typeof a === "object" && typeof b === "object") {
    const ka = Object.keys(a).sort();
    const kb = Object.keys(b).sort();
    if (ka.length !== kb.length || ka.some((k, i) => k !== kb[i])) return false;
    return ka.every((k) => same(a[k], b[k]));
  }
  return false;
}

/** How a value reads in a refusal — short, and never a whole ladder dumped inline. */
function brief(v) {
  if (v === undefined) return "absent";
  if (Array.isArray(v)) return JSON.stringify(v);
  if (v && typeof v === "object") return `{${Object.keys(v).sort().join(", ")}}`;
  return JSON.stringify(v) ?? String(v);
}

/**
 * The evidence ladder this profile declares, however it declared it.
 *
 * @param {object|null|undefined} profile the loaded profile module namespace
 * @param {object|null|undefined} [pack] the object `steps(ctx)` returned, when
 *   the caller has one. A reader that must not start a lane passes nothing and
 *   gets what the profile DECLARES — which is the whole reason the top-level
 *   spelling takes precedence.
 * @returns {{ok: true, ladder: object|null, source: "profile"|"pack"|"both"|"none"}
 *   |{ok: false, reason: string, source: "both"|"profile"|"pack"}}
 *   `ladder: null` with `source: "none"` is the honest grade for a profile that
 *   declared no rungs — it is not a failure, and the caller derives no rung.
 */
export function evidenceLadderFor(profile, pack) {
  const id = profile && typeof profile.id === "string" ? profile.id : "(unnamed)";
  const declared = profile && typeof profile === "object" ? profile.ladder : undefined;
  const packed = pack && typeof pack === "object" ? pack.evidenceLadder : undefined;

  // Present-but-wrong is refused, never ignored — the same rule the profile
  // loader applies to `artifacts` and `governable`. A ladder that is not an
  // object silently grades as no ladder at all, which is the defect this
  // module exists to close wearing a different hat.
  for (const [value, spelling, source] of [
    [declared, DECLARED_SPELLING, "profile"],
    [packed, PACK_SPELLING, "pack"],
  ]) {
    if (present(value) && (typeof value !== "object" || Array.isArray(value))) {
      return {
        ok: false,
        source,
        reason:
          `profile "${id}" declares ${spelling} as ${brief(value)}, which is not an evidence ladder — ` +
          // The field list is DERIVED. It used to be typed here, and after the
          // 2026-09-10 rename it still advertised `release` — so an author who
          // followed this message was refused by the very next check in the
          // same function. A message that names fields must name the fields.
          `a ladder is an object of step names ({ ${["names", ...GRADED_FIELDS.filter((f) => f !== "names" && f !== "scaffoldCore")].join(", ")} }). ` +
          `Fix it or remove it; a profile that declares no ladder earns no rung, which is honest, and this is not that.`,
      };
    }
  }

  // THE SHAPE REFUSAL THAT USED TO LIVE HERE IS GONE (ADR-0016, accepted
  // 2026-09-10). `release` was the one graded field the grader read as a single
  // step NAME while every sibling was a list, and the author of the ktor-backend
  // profile — writing from the contract alone — reached for a list and had their
  // L3 silently unreachable. A refusal was the conservative repair at the time:
  // it changed no grade anywhere, where accepting the list would have.
  //
  // The rename to `l3Execution` deliberately did not settle it; the ADR did. The
  // list IS the shape now, a lone string is still read as a list of one, and the
  // only profiles that can possibly be affected are ones that could not run at
  // all. Nothing that ever produced a receipt grades differently. What goes with
  // the refusal is the reason an author had to be refused: a field shaped unlike
  // its siblings, sitting in the declaration an external author writes first and
  // unaided.
  //
  // Normalisation lives in evidence-level.mjs's `asList`, NOT here. This
  // function compares two DECLARATIONS for disagreement, and a comparator that
  // also reinterpreted shapes could call two genuinely different declarations
  // the same — which is the one thing it exists to catch.
  //
  // WHAT THE DELETED REFUSAL DID STILL HAS TO HAPPEN, one field wider. Dropping
  // malformed entries during normalisation was the first attempt and it was
  // worse than the asymmetry it replaced: `l3Execution: ["ship", null]` dropped
  // the null, saw every SURVIVING entry pass, and awarded L3 with a step the
  // author declared left unproven. `mode: "all"` means proven-by-some is not
  // proven, and a dropped entry is skipped by another name. That same
  // declaration was refused before the reshape.
  //
  // So every step-name field is checked for entries that are not step names,
  // and the field list is DERIVED from GRADED_FIELDS rather than typed — a
  // field added there is validated the day it is added, which is the failure
  // mode the `release` asymmetry taught this file.
  for (const [value, spelling, source] of [
    [declared, DECLARED_SPELLING, "profile"],
    [packed, PACK_SPELLING, "pack"],
  ]) {
    if (!present(value)) continue;
    for (const field of STEP_NAME_FIELDS) {
      const raw = value[field];
      if (raw === undefined || raw === null) continue;
      const entries = Array.isArray(raw) ? raw : [raw];
      const bad = entries.filter((n) => typeof n !== "string" || !n.trim());
      if (!bad.length) continue;
      return {
        ok: false,
        source,
        reason:
          `profile "${id}" declares ${spelling} with ${field} = ${brief(raw)}, ` +
          `which holds ${bad.length} entr${bad.length === 1 ? "y that is" : "ies that are"} not a step name. ` +
          `Every entry must be a non-empty step name: a rung is earned by matching these against the steps that PASSed, ` +
          `so a value that can never match would leave the rung unreachable with nothing said. ` +
          `Remove it, or name the step it was meant to be.`,
      };
    }
  }

  // THE PRE-RENAME SPELLINGS, REFUSED BY NAME. `deviceExecution` and `release`
  // became `l2Execution` and `l3Execution` on 2026-09-10, because the core had
  // no business calling a rung after one stack's runtime — a Python profile
  // declaring `deviceExecution` was naming hardware it does not have, and the
  // receipt field beside it could only ever be empty.
  //
  // Silently reading the old key would be the friendly thing and it is refused,
  // for the reason every fallback in this loader is refused: a profile that
  // still spells it the old way was written against a different meaning of the
  // rung, and grading it anyway would put a rung on a tree whose author never
  // answered the question the new name asks. The refusal names both the field
  // and the command, which is the whole of the migration for a hand-written
  // profile.
  for (const [value, spelling, source] of [
    [declared, DECLARED_SPELLING, "profile"],
    [packed, PACK_SPELLING, "pack"],
  ]) {
    if (!present(value)) continue;
    for (const [was, now] of [
      ["deviceExecution", "l2Execution"],
      ["release", "l3Execution"],
    ]) {
      if (!(was in value)) continue;
      return {
        ok: false,
        source,
        reason:
          `profile "${id}" declares ${spelling} with \`${was}\`, which this lane no longer reads — it is \`${now}\` now. ` +
          `The rungs are local and pre-release for every stack, so they are named by position rather than by one stack's runtime: ` +
          `L2 is the rung where the artifact runs AS THE PROGRAM, L3 the same for the shippable variant. ` +
          `Rename the field (\`node qa/profile.mjs explain ladder.${now}\` says what it means), or run \`prooflane upgrade\`.`,
      };
    }
  }

  // THE CONTRACT'S OWN REFUSALS, ENFORCED HERE. qa/lib/profile-contract.mjs
  // publishes a `refusal` string per field and `node qa/profile.mjs explain`
  // prints it to an author as "REFUSED WHEN IT …". Until this block, nothing
  // implemented any of them: a ladder with no l0Required resolved clean and
  // earned L1 (because `[].every()` is vacuously true), and one declaring an
  // l3Execution with no l2Execution resolved clean with its L3 permanently
  // dark. A contract that describes refusals nobody performs is worse than no
  // contract — it tells an author they are protected.
  //
  // The refusal TEXT is the contract's, never a second copy: a message written
  // here would drift from the one `explain` prints, and an author who read one
  // and hit the other is exactly who this object exists for.
  for (const [value, spelling, source] of [
    [declared, DECLARED_SPELLING, "profile"],
    [packed, PACK_SPELLING, "pack"],
  ]) {
    if (!present(value)) continue;
    const named = (f) => {
      const raw = value[f];
      if (raw === undefined || raw === null) return [];
      return (Array.isArray(raw) ? raw : [raw]).filter((n) => typeof n === "string" && n.trim());
    };
    const refuse = (field) => ({
      ok: false,
      source,
      reason:
        `profile "${id}" ${CONTRACT.ladder.fields[field].refusal}. ` +
        `Declared in ${spelling}; \`node qa/profile.mjs explain ladder.${field}\` says what it is for.`,
    });
    // Only the refusals that describe an UNEARNABLE rung. A rung whose steps
    // are simply not declared is not an error — it is not earned, which is what
    // "no ladder means no rung, the honest grade" has always meant one level up.
    // Refusing those instead was tried on 2026-09-10 and turned three legitimate
    // partial ladders into refused profiles; the vacuous-grade half it was
    // reaching for belongs in the grader and lives there now.
    for (const field of requiredFields("ladder")) if (!named(field).length) return refuse(field);

    // A DECLARED RUNG THAT CANNOT BE REACHED IS REFUSED — the general rule, not
    // one rung's special case. The rungs are climbed in order, so naming steps
    // for L2 while declaring no L1 means those steps can never lift anything:
    // the author declared a rung and the lane will never mention it again.
    // That is the silently-unreachable-rung defect this whole slice exists to
    // close, and it was the shape a second-stack author already hit once.
    //
    // Until now only the L3-without-L2 instance was refused, and a review asked
    // the obvious question: why that one and not the identical shape a rung
    // down? Because it had been written by hand, twice, instead of derived once.
    // `ladderRungs` already decides which rungs a ladder actually declares, so
    // the gap is the difference between that and the fields carrying steps.
    const reachable = new Set(ladderRungs(readLadder(value)).map((r) => r.id));
    for (const [field, rungId] of [["l1Required", "L1"], ["l2Execution", "L2"], ["l3Execution", "L3"]]) {
      if (!named(field).length || reachable.has(rungId)) continue;
      const below = { L1: "l0Required", L2: "l1Required", L3: "l2Execution" }[rungId];
      // THE CONTRACT'S TEXT, NEVER A SECOND COPY — the rule stated ten lines
      // above this block, which the first draft broke. It wrote its own sentence
      // for two of the three gaps and fell back to the contract's for the third,
      // and `(published ? … : "")` was the code knowing it. Worse, the two it
      // wrote for were states the contract told the author were fine: `explain`
      // ended "OPTIONAL — declaring nothing is a valid, honest answer" and
      // printed no REFUSED line, while verify.mjs exited 2 before a step ran.
      // Both now publish, so this reads one and cannot diverge from it.
      return {
        ok: false,
        source,
        reason:
          `profile "${id}" ${CONTRACT.ladder.fields[field].refusal} — declared in ${spelling}, naming ${named(field).join(", ")}. ` +
          `Name the steps that earn ${below}, or remove ${field}: a rung you do not declare is one you do not claim, which is honest. ` +
          `\`node qa/profile.mjs explain ladder.${field}\` says what it is for.`,
      };
    }
  }

  if (present(declared) && present(packed)) {
    const differing = GRADED_FIELDS.filter((f) => !same(declared[f], packed[f]));
    if (differing.length) {
      return {
        ok: false,
        source: "both",
        reason:
          `profile "${id}" declares its evidence ladder TWICE and the two disagree, at ` +
          `${differing.map((f) => `${f} (${brief(declared[f])} vs ${brief(packed[f])})`).join("; ")}. ` +
          `${DECLARED_SPELLING} and ${PACK_SPELLING} must be the same ladder — the top-level one is what a reader ` +
          `that cannot start a lane asks, and a rung graded from whichever declaration a given reader happened to ` +
          `see would be a claim nobody made. Delete one, or export the same object from both.`,
      };
    }
    return { ok: true, ladder: declared, source: "both" };
  }

  if (present(declared)) return { ok: true, ladder: declared, source: "profile" };
  if (present(packed)) return { ok: true, ladder: packed, source: "pack" };
  return { ok: true, ladder: null, source: "none" };
}
