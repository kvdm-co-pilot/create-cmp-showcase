// profiles/cmp — the Compose Multiplatform stack profile. Mobile, by definition.
//
// This is the first profile and, for now, the only one — and it is loaded the
// same way every other profile ever will be: by id, from qa/harness-manifest.json,
// through qa/lib/profile-loader.mjs. The runner does not know this file exists.
//
// Stage 0 (docs/proposals/AGNOSTIC-HARNESS-ARCHITECTURE.md §11.3): PR 2 made
// this module re-export the step pack; PR 4 added `layout` and `tiers`
// (./declarations.mjs) — the paths and tier names the spec scanner used to
// hardcode. The other declarations — artifacts, architecture, ladder, review,
// plants, providers (§4.2) — still live where they always did and move here
// in the PRs that follow, one at a time, with the mobile fleet-check green
// after each. A profile that claimed all nine today would be claiming code it
// does not own yet.

import { createCmpSteps } from "./steps-cmp.mjs";

export const id = "cmp";
export const protocol = 1;

/** Where specs, sources, tests and flows live; which tiers observe what. */
export { layout, tiers, grammar, reports, detect, tools } from "./declarations.mjs";
export { copy as console } from "./console-copy.mjs";

/** What a human signs, in definition order; and whether this tree may record signatures at all. */
export { artifacts, governable } from "./artifacts.mjs";

/**
 * The evidence ladder: which of THIS profile's steps earn which rung, and what
 * the rungs are called. Exported at the top level (not only on the pack's
 * return) so a reader that must not start a lane — the Stop hook deciding
 * whether a tier that could have run did — can ask without instantiating
 * anything. AGNOSTIC-HARNESS-ARCHITECTURE.md §4.2 #6.
 */
export { CMP_LADDER as ladder, legacySkipReasons } from "./ladder.mjs";

/**
 * The source this profile's Rule 0 instrument plants — a citation must sit on
 * a test, so the instrument has to write one in this stack's language
 * (AGNOSTIC-HARNESS-ARCHITECTURE.md §4.2 #8, §7.2).
 */
export { plants } from "./plants.mjs";

/** The step pack — today's createCmpSteps, unchanged. */
export function steps(ctx) {
  return createCmpSteps(ctx);
}
