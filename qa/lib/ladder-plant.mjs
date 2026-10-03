// ladder-plant.mjs — the instrument that makes L2 falsifiable.
//
// WHAT THIS EXISTS TO CATCH, in the words of the defect that caused it: a real
// Python adopter earned L2 with an integration suite that imported the app and
// called its functions. Named, running, green, and never once a program.
// Nothing lied. Nothing checked either — `l2Execution` is a list of step names
// a profile author writes, and until now no instrument ever asked whether those
// steps DO what the rung claims they do.
//
// The contract's own sentence for the rung is the thing under test:
//
//   "the artifact ran AS THE PROGRAM — assembled into its deployable form,
//    started the way it really starts, and driven through its real entry
//    surface. On this machine."
//
// THE DISCRIMINATION. Break the program's STARTUP in a way that still COMPILES,
// then run the lane twice and compare:
//
//   an L1 step  builds, analyses, or calls the code in-process. It cannot see a
//               crash that happens at launch, so it must stay GREEN. If it goes
//               red the plant broke the build, not the startup, and the run
//               proves nothing.
//   an L2 step  starts the program and drives it. It must go RED. If every one
//               stays green, those steps never started anything, and the rung
//               they earn is the overclaim this ladder exists to prevent.
//
// Both halves are required and they fail for opposite reasons, which is what
// makes the result a measurement rather than a hope: a plant that reddens
// everything is a broken build, and a plant that reddens nothing is a lie.
//
// WHY THE PROFILE OWNS THE BREAK. Only the stack knows what "startup" is and
// which file holds it — an Android Activity, a Ktor `embeddedServer`, a Python
// `__main__`. The core owns the QUESTION and the verdict; the profile owns the
// edit. A profile that declares no startup plant gets no L2 assurance and is
// told so by name, which is GATE-RULES §5.2's rule ("no plants, no badge")
// pointed at the rung rather than at the badge.

/** A step's verdict in a receipt, or null when the receipt does not name it. */
function verdictOf(receipt, name) {
  const steps = Array.isArray(receipt?.steps) ? receipt.steps : [];
  const row = steps.find((s) => s && s.name === name);
  return row && typeof row.verdict === "string" ? row.verdict : null;
}

/**
 * Did breaking startup redden the right steps and only the right steps?
 *
 * PURE, so the expensive half — two real lane runs on a real device — is the
 * only part that needs an emulator, and the judgement it feeds can be tested
 * against receipts written by hand.
 *
 * @param {object} opts
 * @param {object} opts.before the receipt from the lane run with startup INTACT
 * @param {object} opts.after the receipt from the run with startup BROKEN
 * @param {{l1Required: string[], l2Execution: string[]}} opts.ladder the profile's, already normalised
 * @returns {{ok: boolean, reason: string|null, blind: string[], caught: string[], broke: string[]}}
 */
export function assessLadderPlant({ before, after, ladder }) {
  const l1 = Array.isArray(ladder?.l1Required) ? ladder.l1Required : [];
  const l2 = Array.isArray(ladder?.l2Execution) ? ladder.l2Execution : [];

  if (!l2.length) {
    return { ok: false, reason: "this profile declares no l2Execution steps, so there is no claim to test", blind: [], caught: [], broke: [] };
  }

  // A step that did not PASS before the plant proves nothing after it: it was
  // already red, or skipped for an environmental reason, and its verdict in the
  // second run is not this plant's doing. Judging on those would let a run with
  // no device attached "catch" the plant by SKIPping twice.
  const wasGreen = (name) => verdictOf(before, name) === "PASS";

  const l1Green = l1.filter(wasGreen);
  const l2Green = l2.filter(wasGreen);

  // The rule above has two halves and until 2026-09-15 it was enforced for one
  // of them. With no l1Required step green beforehand, `l1Green` is empty, so
  // `broke` is empty, so the disqualifier below cannot fire — and a single red
  // L2 step returned ok:true over a receipt that shows nothing about whether
  // the edit compiled. The sentence it printed said so out loud, with a hole
  // where the blind steps go: "e2eSmoke went red while  stayed green".
  if (!l1Green.length) {
    return {
      ok: false,
      reason:
        `no l1Required step PASSED before the plant (${l1.map((n) => `${n}=${verdictOf(before, n) ?? "absent"}`).join(", ") || "none declared"}), ` +
        "so nothing in this run can show the edit still COMPILES. A red l2Execution step is evidence about " +
        "STARTUP only when the build it started from was green.",
      blind: [], caught: [], broke: [],
    };
  }

  if (!l2Green.length) {
    return {
      ok: false,
      reason:
        `no l2Execution step PASSED before the plant (${l2.map((n) => `${n}=${verdictOf(before, n) ?? "absent"}`).join(", ")}), ` +
        "so nothing here could go red FOR THIS REASON. Run the lane where its execution steps actually run.",
      blind: [], caught: [], broke: [],
    };
  }

  // The two halves. `broke` is the disqualifier: an L1 step that was green and
  // is now red means the edit did not compile, and the whole run is void.
  const broke = l1Green.filter((n) => verdictOf(after, n) !== "PASS");
  const blind = l1Green.filter((n) => verdictOf(after, n) === "PASS");
  const caught = l2Green.filter((n) => verdictOf(after, n) === "FAIL");

  if (broke.length) {
    return {
      ok: false,
      reason:
        `the plant broke the BUILD, not the startup: ${broke.join(", ")} passed with startup intact and ` +
        `${broke.map((n) => `${n}=${verdictOf(after, n)}`).join(", ")} with it broken. An L1 step cannot see a ` +
        "crash at launch, so this run measured nothing about the rung.",
      blind, caught, broke,
    };
  }

  if (!caught.length) {
    return {
      ok: false,
      reason:
        `startup is broken and every l2Execution step still passes (${l2Green.map((n) => `${n}=${verdictOf(after, n)}`).join(", ")}). ` +
        "These steps do not start the program. Whatever they prove, it is not L2 — the rung's own words are " +
        "\"started the way it really starts, and driven through its real entry surface\".",
      blind, caught, broke,
    };
  }

  return { ok: true, reason: null, blind, caught, broke };
}

/**
 * The sentence a human reads. Separate from the verdict so the caller decides
 * whether a failure is fatal, and so the wording is not load-bearing anywhere.
 */
export function describeLadderPlant(result) {
  if (!result.ok) return `ladder plant: FAIL — ${result.reason}`;
  return (
    `ladder plant: PASS — startup broken, and ${result.caught.join(", ")} went red while ` +
    `${result.blind.join(", ")} stayed green. The L2 steps start the program; the L1 steps cannot see that they did not.`
  );
}
