// The harness region — which files in a stamped app are MACHINE-OWNED.
//
// A create-cmp app carries two kinds of file. App-owned files are the app: its
// screens, specs, goldens, approvals, e2e flows. Machine-owned files are the
// verify lane itself — executable harness code that is identical in every app
// ever stamped, carrying no app content whatsoever.
//
// Treating the second kind like the first is what made upgrades expensive: a
// three-way merge over 10k lines of engine code produced ~1,000 conflicted
// lines per app with ZERO app-specific tokens in them. The right operation for
// a derived artifact is replace, not merge. This module draws that line.
//
// The rule is deliberately mechanical, with no per-file list to keep in sync:
//
//     machine-owned  ==  the .mjs files directly under qa/ and qa/lib/
//
// Everything else under qa/ is app state (approvals.json, comments.json,
// evidence/, golden/) or app content (e2e/*.yaml — seeded once at stamp time,
// app-owned forever after, because apps edit their smoke flow as tabs change).
//
// Three consequences, each load-bearing:
//
//   1. NEVER STAMPED. The region is copied byte-identical from the engine —
//      token replacement must not touch it. It used to: qa/lib/approvals.mjs
//      carries a comment warning that a literal "__PACKAGE__" in lane source
//      gets silently rewritten at stamp time, and qa/scaffold-feature.mjs
//      shipped an error message that meant to name the unresolved token and
//      instead named the app's real package. Anything app-specific the lane
//      needs is read at RUNTIME from create-cmp.json.
//
//   2. VERIFIABLE. Because the copy is byte-identical to a known version, an
//      app can prove offline that its lane is the real one. Without this a
//      receipt is unfalsifiable: edit qa/verify.mjs to force every step green
//      and the receipt still validates, since the edited file is simply part
//      of the hashed surface.
//
//   3. REPLACEABLE. `create-cmp upgrade --harness` overwrites the region
//      wholesale instead of merging it.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/harness-region.mjs in the
// create-cmp repo. The copy in a generated project's qa/lib/ is vendored
// byte-identical at scaffold time — edit the package source, then run
// `node scripts/sync-harness.mjs`.

import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";

/**
 * Directories whose direct `.mjs` children are machine-owned, relative to the
 * project root. Direct children only — a nested directory added later is not
 * silently swept into the region without someone editing this list.
 */
export const HARNESS_DIRS = ["qa", "qa/lib"];

/**
 * Is this project-relative path part of the machine-owned harness region?
 * @param {string} relPath project-relative path, "/"-separated
 * @returns {boolean}
 */
export function isHarnessFile(relPath) {
  if (typeof relPath !== "string" || !relPath.endsWith(".mjs")) return false;
  const dir = relPath.includes("/") ? relPath.slice(0, relPath.lastIndexOf("/")) : "";
  return HARNESS_DIRS.includes(dir);
}

/**
 * Every machine-owned file present under `root`, as project-relative posix
 * paths, sorted — so the list (and any hash over it) is deterministic.
 * @param {string} root project root
 * @returns {string[]}
 */
export function listHarnessFiles(root) {
  const found = [];
  for (const dir of HARNESS_DIRS) {
    let names;
    try {
      names = fs.readdirSync(path.join(root, dir), { withFileTypes: true });
    } catch {
      continue; // a project without qa/lib yet is not an error here
    }
    for (const ent of names) {
      if (!ent.isFile()) continue;
      const rel = `${dir}/${ent.name}`;
      if (isHarnessFile(rel)) found.push(rel);
    }
  }
  return found.sort();
}

/** sha256 of one file's bytes, hex. */
function fileHash(abs) {
  return createHash("sha256").update(fs.readFileSync(abs)).digest("hex");
}

/**
 * Content hash of the whole region, plus the per-file hashes it was built from.
 *
 * The digest covers PATHS as well as content, so moving a file between the two
 * harness directories changes the hash even if no byte of any file changed.
 * NUL separators keep the encoding unambiguous — no filename can forge a
 * boundary.
 *
 * @param {string} root project root
 * @returns {{sha256: string, fileCount: number, files: Record<string,string>}}
 */
export function hashHarnessRegion(root) {
  const rels = listHarnessFiles(root);
  const files = {};
  const digest = createHash("sha256");
  for (const rel of rels) {
    const h = fileHash(path.join(root, rel));
    files[rel] = h;
    digest.update(rel, "utf8").update("\0").update(h, "utf8").update("\n");
  }
  return { sha256: digest.digest("hex"), fileCount: rels.length, files };
}

/**
 * Compare a tree's region against a recorded manifest of per-file hashes.
 * Reports WHICH files differ, not just that something did — an app that
 * patched its lane needs to see the list, and an upgrade needs it to decide
 * what to preserve.
 *
 * @param {string} root project root
 * @param {{sha256?: string, files?: Record<string,string>}} recorded
 * @returns {{intact: boolean, sha256: string, modified: string[],
 *            missing: string[], extra: string[]}}
 *   modified  present in both, different content
 *   missing   recorded but absent from the tree
 *   extra     present in the tree but not recorded
 */
export function compareHarnessRegion(root, recorded) {
  const actual = hashHarnessRegion(root);
  // `typeof null === "object"`, and an array would enumerate as index keys —
  // a manifest that is absent or malformed must read as NOT intact, never crash
  // the lane step that calls this.
  const f = recorded?.files;
  const expected = f && typeof f === "object" && !Array.isArray(f) ? f : {};
  const modified = [];
  const missing = [];
  const extra = [];

  for (const [rel, hash] of Object.entries(expected)) {
    if (!(rel in actual.files)) missing.push(rel);
    else if (actual.files[rel] !== hash) modified.push(rel);
  }
  for (const rel of Object.keys(actual.files)) {
    if (!(rel in expected)) extra.push(rel);
  }

  return {
    intact: modified.length === 0 && missing.length === 0 && extra.length === 0,
    sha256: actual.sha256,
    modified: modified.sort(),
    missing: missing.sort(),
    extra: extra.sort(),
  };
}
