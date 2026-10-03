// profiles/cmp/plants.mjs — the SOURCE this profile's Rule 0 instrument plants
// when it proves specCoverage can still read a citation. Mobile's, by
// definition (docs/NORTH-STAR.md §6; GATE-RULES.md Rule 0/Rule 1).
//
// Stage 0 PR 7. Most of qa/framework-check.mjs is already stack-free: which
// plants a tree can support is derived from the tree through the spec model
// (PR 4), the clause grammar and the citation marker are the core scanner's,
// and stripping a flow's citations uses the core's own tag regex. What is NOT
// stack-free is the file a planted citation has to live in: a citation must sit
// on a test, so the instrument has to write a test — in this stack's language,
// with this stack's test-declaration syntax, at a path this stack compiles.
//
// Two plants need it, and they are the two that matter most, because each
// closes an escape that turns a red gate green with no assertions:
//
//   unbound   a `// SPEC:` tag on a CLASS with no test inside the binding
//             window. This is payment-blueprint's real drift: the tag sat on
//             `class PaymentWorkerTest`, three properties above a genuine
//             @Test, and vouched for the whole file. The class body here is
//             deliberately longer than BINDING_WINDOW so a real test cannot
//             wander into range and launder it.
//   tierUnmet a clause only the device tier can observe, cited from a
//             host-tier test that compiles and runs and can never see it.
//
// A profile that declares no plants ships without those two, and the
// instrument says so per plant rather than reporting a quieter green — the
// §5.2 rule: no plants, no badge.

import { PLANT_KINDS } from "../../framework-check.mjs";

/** The file a planted citation lives in, relative to the test directory the instrument found. */
export const testFileBasename = "CmpFrameworkCheckPlanted.kt";

/**
 * A citation on a type declaration with no test under it. The body must be
 * longer than the core's BINDING_WINDOW so proximity alone cannot bind it.
 * @param {string} clause the planted clause id
 * @returns {string}
 */
export function unboundCitationSource(clause) {
  return `// Planted by qa/framework-check.mjs — reverted automatically.\n\n// SPEC: ${clause}\nclass CmpFrameworkCheckPlanted {\n  val a = 1\n  val b = 2\n  val c = 3\n  val d = 4\n  val e = 5\n  val f = 6\n  fun helper() {}\n}\n`;
}

/**
 * A real, compiling, running host-tier test that cites a clause only the
 * device tier could observe.
 * @param {string} clause the planted clause id
 * @returns {string}
 */
export function tierUnmetCitationSource(clause) {
  return `// Planted by qa/framework-check.mjs — reverted automatically.\n\nimport kotlin.test.Test\n\nclass CmpFrameworkCheckPlanted {\n  // SPEC: ${clause}\n  @Test\n  fun planted() {}\n}\n`;
}

/**
 * The tier requirement the tierUnmet plant declares on its clause. It must be
 * one this profile's `tiers.satisfying` defines and that a host-tier test
 * cannot satisfy — otherwise the plant would not plant anything.
 */
export const unmeetableTier = "e2e";

/**
 * WHICH OF THIS PACK'S STEPS CATCHES EACH KIND OF PLANTED VIOLATION.
 *
 * `specCoverage` and `e2eCoverage` are OUR words. They used to be written into
 * the core's plant selector as literals and asserted against every profile
 * there will ever be — the same defect NORTH-STAR §9.1 records for
 * `harnessIntegrity`, in the same file, one fix later. A pack spelling its
 * coverage step `spec_coverage` had this instrument stop at the first plant
 * with "specCoverage: no row — the guard did not FAIL BY NAME", about a gate
 * that had just failed by name on the row beside it, and the two floor plants
 * behind it never ran at all.
 *
 * So the names live here, where they are true, keyed by the CORE's plant kinds
 * so neither side has to learn the other's vocabulary. A pack that omits this
 * loses nothing it had: its plants assert over the lane instead of over one row
 * (lib/framework-check.mjs `observingStep`). Declaring is how a pack asks for
 * the sharper assertion — that THIS gate, not merely some gate, is the one that
 * caught it.
 */
export const observedBy = Object.freeze({
  [PLANT_KINDS.ORPHANED_CITATION]: "specCoverage",
  [PLANT_KINDS.UNBOUND_CITATION]: "specCoverage",
  [PLANT_KINDS.TIER_UNMET]: "specCoverage",
  [PLANT_KINDS.FEATURE_WITHOUT_FLOW]: "e2eCoverage",
  [PLANT_KINDS.NESTED_FLOW]: "e2eCoverage",
});

/**
 * HOW TO BREAK THIS STACK'S STARTUP WITHOUT BREAKING ITS BUILD.
 *
 * The core owns the question — does an l2Execution step actually start the
 * program? — and cannot own the answer, because only the stack knows what
 * starting is. Here it is an Android Activity's `onCreate`; on a Ktor service it
 * would be `embeddedServer(...).start()`, and on a Python app `__main__`.
 *
 * THE EDIT MUST COMPILE. That is the whole discipline of this plant: a throw
 * inside `onCreate` is type-correct Kotlin, so `releaseBuild`, `conformance`,
 * `goldenTrees` and `a11y` cannot tell anything happened, while every step that
 * launches the app dies on the first frame. A plant that failed to compile
 * would redden the L1 steps too and measure nothing — which the instrument
 * refuses by name rather than reading as success.
 *
 * `super.onCreate` is called first deliberately. Throwing before it produces a
 * different crash (a framework-level one about Activity lifecycle) that a lane
 * could plausibly catch for the wrong reason; throwing after it means the
 * Activity started correctly and the APP's own first instruction is what failed.
 */
export const startupPlant = Object.freeze({
  /** The file that holds this stack's entry point, by basename — the instrument finds it under the source roots. */
  entryPointBasename: "MainActivity.kt",

  /** Does this source look like the entry point? Guards against finding a same-named file elsewhere. */
  recognises: (src) => src.includes("override fun onCreate(") && src.includes("super.onCreate("),

  /**
   * Type-correct Kotlin that throws on the app's own first instruction.
   * @param {string} src the original file
   * @returns {string}
   */
  breakStartup: (src) =>
    src.replace(
      /(super\.onCreate\(savedInstanceState\))/,
      "$1\n        // prooflane ladder plant — compiles, and dies at launch.\n" +
        '        throw IllegalStateException("prooflane ladder plant: startup is broken on purpose")',
    ),
});

/** What the instrument reads off the profile. */
export const plants = Object.freeze({
  testFileBasename,
  unboundCitationSource,
  tierUnmetCitationSource,
  unmeetableTier,
  observedBy,
  startupPlant,
});
