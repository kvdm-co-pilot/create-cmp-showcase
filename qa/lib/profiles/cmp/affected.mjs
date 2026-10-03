// profiles/cmp/affected.mjs — which changes fan out too widely to subset the
// test suite, and how a changed Compose source maps to a Gradle test filter.
// Mobile's, by definition (docs/NORTH-STAR.md §6).
//
// Stage 0 PR 6d. qa/lib/affected-tests.mjs held both halves: the HONESTY
// CONTRACT (fail open, never fail silent; lane outputs are not changes; an
// unmapped change runs everything) and the STACK RULES (`.gradle.kts` rewires
// compilation, `di/` rewires the object graph, `theme/` and
// `presentation/components/` render into every screen, anything outside
// composeApp/src is not a scoped source edit, and a `.kt` file's parent
// directory is a package segment a Gradle `--tests "*seg*"` pattern matches).
//
// The first half is the core's and is what makes the subsetting safe at all.
// The second half is a description of one build tool and one source layout,
// and vendored into a repo with neither it did something worse than nothing:
// EVERY path failed the `composeApp/src` test, so every fast run fell open to
// the full suite — the optimisation silently off, visible only in one
// parenthetical nobody reads.
//
// The pack passes this module to deriveAffectedFilter. Nothing resolves a
// manifest: the pack already IS the profile, and it can hand the core its own
// rules directly.

import path from "node:path";

/**
 * Paths whose change fans out too widely to subset the suite safely. Returns
 * the human-readable category when `p` is broad-impact, else null. Checked in
 * order; the first match names the reason. The core adds its own neutral rule
 * (qa/ is the harness judging itself) before consulting this.
 * @param {string} p POSIX relpath from the project root
 * @returns {string|null}
 */
export function broadImpact(p) {
  if (p.endsWith(".gradle.kts") || p === "gradle.properties" || p === "gradle/libs.versions.toml") {
    return "build files rewire compilation";
  }
  if (/(^|\/)di\//.test(p)) return "DI rewires the object graph";
  if (/(^|\/)theme\//.test(p)) return "theme/tokens render into every screen";
  if (p.includes("presentation/components/")) return "shared components render into every screen";
  if (!p.startsWith("composeApp/src/")) return "outside composeApp/src";
  return null;
}

/**
 * The Gradle `--tests` patterns for a set of changed paths, or [] when nothing
 * maps (the core then runs everything and says so).
 *
 * Deliberately simple and defensible: each changed `.kt` file under
 * composeApp/src contributes its package's last segment — the parent directory
 * name (`…/presentation/home/HomeViewModel.kt` → `home`, which the template's
 * package-mirrors-path conformance makes a package segment) — and the union
 * becomes patterns matched against test class FQNs. Coarse on purpose:
 * `*home*` runs every test whose FQN mentions the feature, which over-selects
 * a little and under-maintains nothing.
 * @param {string[]} paths POSIX relpaths, already known not to be broad-impact
 * @returns {{patterns: string[], sourcePaths: string[]}}
 */
export function patternsFor(paths) {
  const ktPaths = paths.filter((p) => p.endsWith(".kt"));
  const segments = new Set();
  for (const p of ktPaths) {
    const seg = path.posix.basename(path.posix.dirname(p));
    if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(seg)) segments.add(seg);
  }
  return { patterns: [...segments].sort().map((s) => `*${s}*`), sourcePaths: ktPaths };
}

/** The mapping the pack hands to qa/lib/affected-tests.mjs. */
export const affected = Object.freeze({ broadImpact, patternsFor });
