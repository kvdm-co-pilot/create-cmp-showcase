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
//     widely to subset safely (build files rewire compilation, DI rewires
//     object graphs, theme/tokens and shared components render into every
//     screen, qa/ is the harness judging itself, and anything outside
//     composeApp/src is by definition not a scoped source edit). Any one such
//     change disables filtering for the run.
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
export const LANE_OUTPUT_PREFIXES = ["qa/evidence", "qa-artifacts", "qa/flight-recorder.jsonl"];

function isLaneOutput(p) {
  return LANE_OUTPUT_PREFIXES.some((prefix) => p === prefix || p.startsWith(`${prefix}/`));
}

/**
 * The mandatory blast-radius escape hatch: paths whose change fans out too
 * widely to subset the suite safely. Returns the human-readable category when
 * `p` is broad-impact, else null. Checked in order; the first match names the
 * reason.
 * @param {string} p POSIX relpath from the project root
 * @returns {string|null}
 */
export function broadImpactReason(p) {
  if (p.endsWith(".gradle.kts") || p === "gradle.properties" || p === "gradle/libs.versions.toml") {
    return "build files rewire compilation";
  }
  if (/(^|\/)di\//.test(p)) return "DI rewires the object graph";
  if (/(^|\/)theme\//.test(p)) return "theme/tokens render into every screen";
  if (p.includes("presentation/components/")) return "shared components render into every screen";
  if (p === "qa" || p.startsWith("qa/")) return "qa/ is the harness itself";
  if (!p.startsWith("composeApp/src/")) return "outside composeApp/src";
  return null;
}

/**
 * Derive the fast-mode unit-test filter from a list of changed paths.
 *
 * Mapping (deliberately simple and defensible): each changed `.kt` file under
 * composeApp/src contributes its package's last segment — the parent
 * directory name (`…/presentation/home/HomeViewModel.kt` → `home`, which the
 * template's package-mirrors-path conformance makes a package segment) — and
 * the union becomes Gradle `--tests "*<seg>*"` patterns matched against test
 * class FQNs. Coarse on purpose: `*home*` runs every test whose FQN mentions
 * the feature, which over-selects a little and under-maintains nothing.
 *
 * @param {string[]} changedPaths relpaths (either separator style) — tracked
 *   diffs plus untracked files, as from changedWorkingTreePaths()
 * @returns {{mode: "filtered", patterns: string[], sourcePaths: string[]} |
 *   {mode: "all", reason: string, patterns: [], sourcePaths: string[]}}
 *   mode "all" ALWAYS carries the honest reason to report.
 */
export function deriveAffectedFilter(changedPaths) {
  const paths = [...new Set((changedPaths ?? [])
    .filter((p) => typeof p === "string" && p.length > 0)
    .map((p) => p.split(path.sep).join("/")))]
    .filter((p) => !isLaneOutput(p))
    .sort();

  if (paths.length === 0) {
    return { mode: "all", reason: "no working-tree changes to scope by", patterns: [], sourcePaths: [] };
  }

  for (const p of paths) {
    const broad = broadImpactReason(p);
    if (broad) {
      return { mode: "all", reason: `broad-impact change — ${broad} (${p})`, patterns: [], sourcePaths: paths };
    }
  }

  // Every remaining path is a scoped file under composeApp/src. Only .kt
  // files map to test patterns; a change that maps to nothing (resources,
  // manifests) falls open to the full suite below.
  const ktPaths = paths.filter((p) => p.endsWith(".kt"));
  const segments = new Set();
  for (const p of ktPaths) {
    const seg = path.posix.basename(path.posix.dirname(p));
    if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(seg)) segments.add(seg);
  }

  if (segments.size === 0) {
    return { mode: "all", reason: "changed files map to no test filter", patterns: [], sourcePaths: paths };
  }

  return {
    mode: "filtered",
    patterns: [...segments].sort().map((s) => `*${s}*`),
    sourcePaths: ktPaths,
  };
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
