// component-stories.mjs — the component ↔ story parity gate (the IMP-1
// screen↔registry parity idea, applied at component granularity).
//
// Every `@Composable fun` in `composeApp/src/commonMain/**/presentation/
// components/*.kt` must have a preview-registry story whose id is
// `component.<kebab-case-of-the-composable-name>` (AppHeader →
// "component.app-header"), registered in the desktopMain inspector sources
// (ComponentStories.kt, or PreviewRegistry.kt for generated conditional
// components like PlaceholderScreen). The gate fails BOTH directions, like
// specCoverage: a component with no story (the render pipeline is blind to
// it) and a story id with no component (a stale story surviving a rename).
//
// Detection is a pragmatic source scan, not a Kotlin front-end — the same
// stance as the console's components scan (inspector/mcp/src/lib/
// components.mjs, whose @Composable-window heuristic and kebab derivation
// this file mirrors; keep the two in sync).

import fs from "node:fs";
import path from "node:path";

/** PascalCase/camelCase → kebab-case: AppHeader → app-header, ListItemCard → list-item-card. */
export function kebabCase(name) {
  return String(name)
    .replace(/([a-z0-9])([A-Z])/g, "$1-$2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1-$2")
    .toLowerCase();
}

/** The registry story id a component of this name must register. */
export function componentStoryId(name) {
  return `component.${kebabCase(name)}`;
}

// How far past an `@Composable` occurrence to look for the `fun Name(` it
// governs — mirrors the console scan's FUN_SEARCH_WINDOW.
const FUN_SEARCH_WINDOW = 500;

/**
 * Every `@Composable fun Name(` declaration name in one file's text —
 * includes private/internal composables (the console's Components page lists
 * them, so the parity gate covers them too).
 * @param {string} text
 * @returns {string[]}
 */
export function findComposableNames(text) {
  const names = [];
  const composableRe = /@Composable\b/g;
  let m;
  while ((m = composableRe.exec(text))) {
    const window = text.slice(m.index, m.index + FUN_SEARCH_WINDOW);
    const funMatch = window.match(/fun\s+(?:<[^>]*>\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^>]*>)?\s*\(/);
    if (funMatch) names.push(funMatch[1]);
  }
  return names;
}

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

function ktFilesIn(dir) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  return entries
    .filter((e) => e.isFile() && e.name.endsWith(".kt"))
    .map((e) => path.join(dir, e.name));
}

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

const STORY_ID_RE = /"component\.([a-z0-9][a-z0-9.-]*)"/g;

/**
 * Evaluate component ↔ story parity for a project root.
 * SKIPs (never fails) when the surface doesn't exist: no components dir, or
 * no desktopMain inspector dir (the `--no-inspector` scaffold has no preview
 * registry to hold stories).
 * @param {string} root project root (contains composeApp/)
 * @returns {{verdict: "PASS"|"FAIL"|"SKIP", reason?: string, details?: object}}
 */
export function evaluateComponentStoryParity(root) {
  const commonRoot = path.join(root, "composeApp", "src", "commonMain", "kotlin");
  const desktopRoot = path.join(root, "composeApp", "src", "desktopMain", "kotlin");

  const componentsDirs = walkDirs(commonRoot, "presentation")
    .map((p) => path.join(p, "components"))
    .filter((p) => fs.existsSync(p));
  if (componentsDirs.length === 0) {
    return { verdict: "SKIP", reason: "no presentation/components directory under commonMain — nothing to check" };
  }

  const inspectorDirs = walkDirs(desktopRoot, "inspector");
  if (inspectorDirs.length === 0) {
    return {
      verdict: "SKIP",
      reason: "no desktopMain inspector sources (preview harness not included) — no story registry to check against",
    };
  }

  // Components: every @Composable in the registry dir (direct children only —
  // the same surface the console's Components page scans).
  const components = []; // { name, file }
  for (const dir of componentsDirs) {
    for (const file of ktFilesIn(dir)) {
      const rel = path.relative(root, file).split(path.sep).join("/");
      for (const name of findComposableNames(fs.readFileSync(file, "utf8"))) {
        components.push({ name, file: rel });
      }
    }
  }

  // Stories: every quoted "component.<kebab>" id in the inspector sources.
  const storyIds = new Set();
  for (const dir of inspectorDirs) {
    for (const file of walkKtFilesDeep(dir)) {
      const text = fs.readFileSync(file, "utf8");
      for (const match of text.matchAll(STORY_ID_RE)) {
        storyIds.add(`component.${match[1]}`);
      }
    }
  }

  const expectedIds = new Map(components.map((c) => [componentStoryId(c.name), c]));
  const missing = [...expectedIds.entries()].filter(([id]) => !storyIds.has(id));
  const orphans = [...storyIds].filter((id) => !expectedIds.has(id)).sort();

  const details = {
    components: components.length,
    stories: storyIds.size,
    missing: missing.map(([id]) => id),
    orphans,
  };

  if (missing.length === 0 && orphans.length === 0) {
    return { verdict: "PASS", details };
  }

  const lines = ["Component ↔ story parity broken — the components registry and its preview stories have drifted apart:"];
  for (const [id, c] of missing) {
    lines.push(
      `  [${c.name}] ${c.file} — no component story registered. Add ScreenPreview("${id}", …) to composeApp/src/desktopMain/**/inspector/ComponentStories.kt (kebab-case of the composable name).`,
    );
  }
  for (const id of orphans) {
    lines.push(
      `  ["${id}"] story id has no matching @Composable in presentation/components — remove the stale story or fix the id.`,
    );
  }
  return { verdict: "FAIL", reason: lines.join("\n"), details };
}
