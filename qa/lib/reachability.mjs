// reachability.mjs — the navigation-reachability gate (task FI-7,
// docs/AUTONOMY-GAPS.md §3): a feature that passes spec coverage, conformance,
// goldens, a11y, and on-device smoke — and is STILL unreachable because no
// destination in the navigation graph points at it — is a confident false
// green. `MealTrayScreen` was accepted that way: its `MealTrayRoute` composable
// was referenced by nothing but its own tests. This gate exists to catch
// exactly that shape of drift, mechanically, so it can never happen silently
// again.
//
// Pure Node, no Gradle — same stance as component-stories.mjs and
// spec-coverage.mjs: a pragmatic source scan, not a Kotlin front end.
//
// A "feature" is any top-level directory under commonMain's presentation/,
// except `components` and `theme` (registry/design surfaces, not screens),
// that contains at least one `*Screen.kt` file (recursive). Its ENTRY
// composables are the top-level `fun <Name>(` declarations in those files
// whose name ends in `Screen` or `Route` — the navigation-entry naming
// convention this template's own exemplar follows (HomeScreen, DetailScreen).
// A feature is REACHABLE if any entry composable's name is referenced —
// word-boundary, not a call-site parse — from a commonMain `.kt` file OUTSIDE
// its own presentation/<feature>/ directory. The navigation graph
// (AppNavHost) is the expected referencer.
//
// desktopMain (PreviewRegistry.kt, ComponentStories.kt) and test sources
// deliberately do NOT count: registering a screen in the preview gallery is
// not wiring it into the app a user runs — that is exactly the false green
// this gate exists to close, so only commonMain counts as a live reference.
//
// A feature with no entry composable at all (nothing named `*Screen`/`*Route`
// declared at top level) has nothing this gate can check reachability FOR —
// it is passed through untouched rather than flagged, the same "nothing to
// check" stance component-stories.mjs takes for an empty registry.

import fs from "node:fs";
import path from "node:path";

import { parseFeatureBlock } from "./feature-brief.mjs";

// Mirrors component-stories.mjs's walkDirs: every directory named `wanted`
// anywhere under `root`, recursive.
function walkDirs(root, wanted) {
  const out = [];
  (function walk(dir) {
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      if (!e.isDirectory()) continue;
      const p = path.join(dir, e.name);
      if (e.name === wanted) out.push(p);
      else walk(p);
    }
  })(root);
  return out;
}

// Mirrors component-stories.mjs's walkKtFilesDeep: every `.kt` file under
// `dir`, recursive.
function walkKtFilesDeep(dir) {
  const out = [];
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walkKtFilesDeep(p));
    else if (e.name.endsWith(".kt")) out.push(p);
  }
  return out;
}

// Top-level `fun Name(` — unindented (column 0), the same line-anchored
// scanning stance as spec-coverage.mjs's CLAUSE_LINE_RE: a pragmatic source
// scan, not a Kotlin front end. Deliberately does not require `@Composable`
// on the preceding line — the entry-naming convention (Screen/Route suffix)
// is the signal, not the annotation.
const TOP_LEVEL_FUN_RE = /^fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^>]*>)?\s*\(/gm;

/**
 * The entry composables one `*Screen.kt` file's text declares: top-level
 * `fun`s whose name ends `Screen` or `Route`. Order-preserving, de-duplicated
 * by the caller across a feature's files.
 * @param {string} text
 * @returns {string[]}
 */
export function findEntryComposables(text) {
  const names = [];
  TOP_LEVEL_FUN_RE.lastIndex = 0;
  let m;
  while ((m = TOP_LEVEL_FUN_RE.exec(text))) {
    if (/(Screen|Route)$/.test(m[1])) names.push(m[1]);
  }
  return names;
}

/**
 * Evaluate navigation reachability for a project root.
 * @param {string} root project root (contains composeApp/)
 * @returns {{verdict: "PASS"|"FAIL"|"SKIP", reason?: string, details: {features: Array<{name: string, reachable: boolean, unrouted?: boolean, entryComposables: string[]}>}}}
 */
export function evaluateReachability(root) {
  const commonRoot = path.join(root, "composeApp", "src", "commonMain", "kotlin");
  if (!fs.existsSync(commonRoot)) {
    return {
      verdict: "SKIP",
      reason: "no commonMain/kotlin directory under composeApp/src — kotlin root unresolvable, nothing to check",
      details: { features: [] },
    };
  }

  const presentationDirs = walkDirs(commonRoot, "presentation");
  if (presentationDirs.length === 0) {
    return { verdict: "SKIP", reason: "no presentation directory under commonMain — nothing to check", details: { features: [] } };
  }

  // The whole commonMain .kt surface, read once — both the per-feature scan
  // source and the reachability search space (desktopMain/test deliberately
  // excluded: see the file header).
  const allKtFiles = walkKtFilesDeep(commonRoot);
  const textOf = new Map(allKtFiles.map((f) => [f, fs.readFileSync(f, "utf8")]));

  const features = []; // { name, dir, entryComposables }
  for (const presentationDir of presentationDirs) {
    let entries;
    try {
      entries = fs.readdirSync(presentationDir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const e of entries) {
      if (!e.isDirectory() || e.name === "components" || e.name === "theme") continue;
      const featureDir = path.join(presentationDir, e.name);
      const screenFiles = walkKtFilesDeep(featureDir).filter((f) => path.basename(f).endsWith("Screen.kt"));
      if (screenFiles.length === 0) continue; // not a feature per this gate's definition

      const entryComposables = [];
      for (const f of screenFiles) {
        for (const name of findEntryComposables(textOf.get(f) ?? "")) {
          if (!entryComposables.includes(name)) entryComposables.push(name);
        }
      }
      features.push({ name: e.name, dir: featureDir, entryComposables });
    }
  }

  if (features.length === 0) {
    return { verdict: "SKIP", reason: "no presentation/<feature> directory has a *Screen.kt file — nothing to check", details: { features: [] } };
  }

  const resultFeatures = [];
  const unreachable = [];
  for (const feature of features) {
    // Nothing named Screen/Route was declared at all — this gate has nothing
    // to check reachability for; pass it through rather than flag it.
    if (feature.entryComposables.length === 0) {
      resultFeatures.push({ name: feature.name, reachable: true, entryComposables: [] });
      continue;
    }

    const featurePrefix = feature.dir + path.sep;
    const outsideFiles = allKtFiles.filter((f) => !f.startsWith(featurePrefix));
    const reachable = feature.entryComposables.some((name) => {
      const re = new RegExp(`\\b${name}\\b`);
      return outsideFiles.some((f) => re.test(textOf.get(f) ?? ""));
    });

    if (reachable) {
      resultFeatures.push({ name: feature.name, reachable: true, entryComposables: feature.entryComposables });
      continue;
    }

    // Exemption: docs/features/<name>.md's cmp:feature block declares
    // { "unrouted": true } — the same declare-not-gate mechanism
    // feature-brief.mjs already defines for `touches`/`screens`.
    let unrouted = false;
    try {
      const briefMarkdown = fs.readFileSync(path.join(root, "docs", "features", `${feature.name}.md`), "utf8");
      unrouted = parseFeatureBlock(briefMarkdown).unrouted === true;
    } catch {
      unrouted = false;
    }

    if (unrouted) {
      resultFeatures.push({ name: feature.name, reachable: true, unrouted: true, entryComposables: feature.entryComposables });
      continue;
    }

    resultFeatures.push({ name: feature.name, reachable: false, entryComposables: feature.entryComposables });
    unreachable.push(feature);
  }

  if (unreachable.length === 0) {
    return { verdict: "PASS", details: { features: resultFeatures } };
  }

  const lines = [
    "Reachability broken — a feature's screen passed every other gate but nothing in the navigation graph points at it (a screen nobody can navigate to is not a delivered feature):",
  ];
  for (const f of unreachable) {
    lines.push(
      `  [${f.name}] entry composable(s) ${f.entryComposables.join(", ")} — not referenced anywhere in commonMain outside presentation/${f.name}/. Fix it one of two ways: wire a destination for it in the navigation graph (AppNavHost), or, if it is intentionally not routed yet, declare { "unrouted": true } in docs/features/${f.name}.md's cmp:feature block.`,
    );
  }
  return { verdict: "FAIL", reason: lines.join("\n"), details: { features: resultFeatures } };
}
