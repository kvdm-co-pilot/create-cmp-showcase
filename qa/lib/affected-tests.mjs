// affected-tests.mjs — FAST-MODE-ONLY scoping of the unit-test suite to the
// tests plausibly affected by the working-tree change.
//
// The full lane always runs the whole suite; this module exists so the inner
// loop (`verify --fast`) doesn't pay for every test on a one-file edit. Its
// honesty contract:
//
//   - FALSE NEGATIVES ARE ACCEPTABLE HERE — AND ONLY HERE. A filtered fast
//     run can miss a cross-feature regression; that is tolerable purely
//     because the full, unfiltered suite runs at the checkpoint (the full
//     lane), where done is actually decided. No other gate gets this license.
//   - FAIL OPEN, NEVER FAIL SILENT. No git, a failed git command, an unmapped
//     change, a broad-impact change — every uncertain case runs EVERYTHING,
//     and the caller reports which case it was in the step's output and the
//     receipt, so a filtered run can never be mistaken for the full suite.
//   - The BLAST-RADIUS ESCAPE HATCH is mandatory: some paths fan out too
//     widely to subset safely. qa/ is the harness judging itself — that one is
//     the core's, on every stack. WHICH OTHER paths fan out, and how a changed
//     source maps to a test filter, are facts about one build tool and one
//     source layout, so they come from the PROFILE (Stage 0 PR 6d;
//     profiles/cmp/affected.mjs). Any one broad-impact change disables
//     filtering for the run.
//
// A profile that supplies no mapping gets `mode: "all"` with that as the
// reason — fail open, said out loud. Vendored into a repo whose sources are
// not under composeApp/src, the old hardcoded rules did something worse than
// nothing: every path failed the layout test, so every fast run fell open to
// the full suite with the optimisation silently off.
//
// Pure functions over path lists — git access is injected/separate so the
// engine suite can test every branch with no repo state.

import { execSync } from "node:child_process";
import path from "node:path";

/**
 * Lane OUTPUTS, excluded from the changed-set before any classification.
 * The receipt (qa/evidence/) and hashed artifacts (qa-artifacts/) change on
 * every lane run by design; counting them as "changes" would make the qa/**
 * escape hatch self-triggering forever — run N's receipt forcing run N+1 to
 * the full suite, permanently. They cannot affect a test outcome (the same
 * principle as inputs-hash.mjs's EXCLUDED_PREFIXES: lane outputs are not
 * verdict inputs).
 */
// qa/flight-recorder.jsonl is a lane output in the strictest sense: the lane
// appends one line to it AFTER the receipt is written, on every run. It is
// committed (the journal is the cost record), so after the first run it sits
// in the changed set as a modified tracked file under qa/ — and qa/** is the
// "harness itself" escape hatch. Uncounted here, every --fast run after the
// first fell open to the full suite, visible only in one parenthetical.
// Found by payment-blueprint's spine adoption (2026-09-03), where the same
// line also landed in their locked region.
// qa/.lane-in-progress is the lane's own marker (qa/lib/lane-markers.mjs) —
// present, untracked, for exactly the duration of the run that would read it.
// qa/.lane-steps.ndjson is that marker's sibling: the run's own step stream,
// appended to WHILE the lane runs so the console can render it. Same reason —
// a lane's own progress is not a change to the tree it is checking.
export const LANE_OUTPUT_PREFIXES = ["qa/evidence", "qa-artifacts", "qa/flight-recorder.jsonl", "qa/.lane-in-progress", "qa/.lane-steps.ndjson"];

function isLaneOutput(p) {
  return LANE_OUTPUT_PREFIXES.some((prefix) => p === prefix || p.startsWith(`${prefix}/`));
}

/**
 * The core's own blast-radius rule, on every stack: a change under qa/ is the
 * harness judging itself, so nothing may be subsetted by it.
 * @param {string} p POSIX relpath from the project root
 * @returns {string|null}
 */
export function coreBroadImpactReason(p) {
  if (p === "qa" || p.startsWith("qa/")) return "qa/ is the harness itself";
  return null;
}

/**
 * Derive the fast-mode unit-test filter from a list of changed paths.
 *
 * @param {string[]} changedPaths relpaths (either separator style) — tracked
 *   diffs plus untracked files, as from changedWorkingTreePaths()
 * @param {{broadImpact: (p: string) => (string|null), patternsFor: (paths: string[]) => {patterns: string[], sourcePaths: string[]}}} [mapping]
 *   the profile's rules (profiles/<id>/affected.mjs). Absent = no subsetting.
 * @returns {{mode: "filtered", patterns: string[], sourcePaths: string[]} |
 *   {mode: "all", reason: string, patterns: [], sourcePaths: string[]}}
 *   mode "all" ALWAYS carries the honest reason to report.
 */
export function deriveAffectedFilter(changedPaths, mapping = null) {
  const paths = [...new Set((changedPaths ?? [])
    .filter((p) => typeof p === "string" && p.length > 0)
    .map((p) => p.split(path.sep).join("/")))]
    .filter((p) => !isLaneOutput(p))
    .sort();

  if (paths.length === 0) {
    return { mode: "all", reason: "no working-tree changes to scope by", patterns: [], sourcePaths: [] };
  }

  // No mapping, no subsetting — and the reason says which half is missing, so
  // a profile author sees the optimisation is off rather than wondering why
  // the fast lane costs what the full one does.
  if (!mapping || typeof mapping.broadImpact !== "function" || typeof mapping.patternsFor !== "function") {
    return { mode: "all", reason: "this profile declares no affected-test mapping — every fast run tests everything", patterns: [], sourcePaths: paths };
  }

  for (const p of paths) {
    const broad = coreBroadImpactReason(p) ?? mapping.broadImpact(p);
    if (broad) {
      return { mode: "all", reason: `broad-impact change — ${broad} (${p})`, patterns: [], sourcePaths: paths };
    }
  }

  // Every remaining path is a scoped source edit by the profile's own reckoning.
  // A change that maps to no pattern (resources, manifests) falls open below.
  const { patterns, sourcePaths } = mapping.patternsFor(paths);
  if (!Array.isArray(patterns) || patterns.length === 0) {
    return { mode: "all", reason: "changed files map to no test filter", patterns: [], sourcePaths: paths };
  }

  return { mode: "filtered", patterns, sourcePaths: Array.isArray(sourcePaths) ? sourcePaths : paths };
}

/**
 * Must the expensive tier run for this change?
 *
 * The most costly thing an agent does is run a device or journey tier — minutes
 * of emulator, build and flow — to prove something about a change that tier
 * cannot see. Running it anyway is not rigour, it is latency, and latency is
 * how an agent ends up CLAIMING instead of deriving (G1, G2).
 *
 * THE DECLARATION IS OF IRRELEVANCE, NOT OF RELEVANCE, and getting that
 * backwards turns this into the defect it prevents. The first version of this
 * function asked "is any changed path under a root that FEEDS the tier?" and
 * answered "not required" when none matched — an allowlist, so anything nobody
 * thought to list was silently deferred. `cmp` declares
 * `sourceRoots: ["composeApp/src"]`, which excludes `build.gradle.kts` and
 * `gradle/libs.versions.toml` — both of which absolutely change what runs on a
 * device. A dependency bump would have skipped the device tier and said so
 * confidently (ADR-0009, "what would make this wrong").
 *
 * So: the tier RUNS unless every changed path is under something explicitly
 * declared unable to affect it. A path nobody classified obliges the tier,
 * which is the safe direction — being wrong toward running costs minutes, being
 * wrong toward deferring costs a regression nobody saw.
 *
 * This does not skip anything; it answers a question and always carries the
 * reason, so a caller can record it and a reader can disagree with it. An
 * unrecorded skip is a lie; a recorded one is evidence.
 *
 * @param {string[]|null} changedPaths relpaths, or null when git could not say
 * @param {{irrelevantRoots?: string[], tierName?: string}} [decl] paths declared
 *   unable to affect the tier: a `dir/` prefix, or a `*.ext` suffix
 * @returns {{required: boolean, reason: string, obliging: string[]}}
 */
export function deriveTierNeed(changedPaths, { irrelevantRoots = [], tierName = "the device tier" } = {}) {
  if (!Array.isArray(changedPaths)) {
    return { required: true, reason: `cannot tell what changed — ${tierName} runs`, obliging: [] };
  }
  const paths = changedPaths
    .filter((p) => typeof p === "string" && p.length > 0)
    .map((p) => p.split(path.sep).join("/"))
    .filter((p) => !isLaneOutput(p));
  if (paths.length === 0) {
    return { required: true, reason: `no change to reason about — ${tierName} runs`, obliging: [] };
  }
  // The harness judging itself is always broad impact — the same rule the
  // fast-lane filter uses, so the two cannot disagree about the same edit.
  const core = paths.find((p) => coreBroadImpactReason(p));
  if (core) {
    return { required: true, reason: `broad-impact change — ${coreBroadImpactReason(core)} (${core})`, obliging: [core] };
  }
  const declared = (irrelevantRoots ?? []).filter((r) => typeof r === "string" && r.length > 0);
  if (declared.length === 0) {
    return { required: true, reason: `nothing is declared unable to affect ${tierName} — it runs`, obliging: paths };
  }
  const isIrrelevant = (p) =>
    declared.some((r) => (r.startsWith("*.") ? p.endsWith(r.slice(1)) : p === r.replace(/\/$/, "") || p.startsWith(r.endsWith("/") ? r : `${r}/`)));
  const obliging = paths.filter((p) => !isIrrelevant(p));
  return obliging.length > 0
    ? {
        required: true,
        reason: `${obliging.length} changed path(s) are not declared irrelevant to ${tierName}: ${obliging.slice(0, 3).join(", ")}${obliging.length > 3 ? ", …" : ""}`,
        obliging,
      }
    : { required: false, reason: `every changed path is declared unable to affect ${tierName} (${declared.join(", ")})`, obliging: [] };
}

function defaultRunGit(args, root) {
  try {
    return execSync(`git ${args}`, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
  } catch {
    return null;
  }
}

/**
 * The working-tree change: tracked files differing from HEAD (staged or not)
 * plus untracked-but-not-ignored files — the same "what will this commit
 * touch" surface inputs-hash.mjs hashes.
 *
 * Returns null when git is unavailable or either command fails — the caller
 * MUST treat null as "run everything" (fail open) and say so (never fail
 * silent).
 *
 * @param {string} root project root
 * @param {(args: string, root: string) => string|null} [runGit] injectable for tests
 * @returns {string[]|null}
 */
export function changedWorkingTreePaths(root, runGit = defaultRunGit) {
  const diff = runGit("diff --name-only HEAD", root);
  const untracked = runGit("ls-files --others --exclude-standard", root);
  if (diff === null || untracked === null) return null;
  const lines = (out) => out.replace(/\n+$/, "").split("\n").filter(Boolean);
  return [...new Set([...lines(diff), ...lines(untracked)])];
}
