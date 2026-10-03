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
// `node scripts/sync-harness.mjs` in the create-cmp repo.

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
 * The lane's OWN tests, when a project carries them (payment-blueprint's
 * qa/test/**, 2026-09-03). Every `.mjs` under it, recursively, is in the
 * region: the tests that prove the gates are locked WITH the gates, or the
 * lane can no longer tell you that the suite vouching for its verdicts is the
 * one it was locked with — a narrower claim in the same words, which is the
 * failure this mechanism exists to prevent. A Compose app has no qa/test and
 * its region (and lock) is unchanged.
 */
export const HARNESS_TEST_DIR = "qa/test";

/**
 * STACK PROFILES — qa/lib/profiles/<id>/**. Every `.mjs` under it, recursively,
 * is machine-owned: a profile is the set of gates, tiers and steps the lane
 * runs for this project, loaded by id from qa/harness-manifest.json
 * (qa/lib/profile-loader.mjs). An edited profile that stayed outside the lock
 * could redefine what "done" means and still have the lane vouch for the
 * result — GATE-RULES Rule 2, the layer you changed cannot certify itself.
 * Named and recursive on purpose, like HARNESS_TEST_DIR: a nested directory
 * enters the region only by someone widening the rule here, never by shape.
 */
export const HARNESS_PROFILES_DIR = "qa/lib/profiles";

/**
 * DECLARATIONS the lane READS to decide what it attests — locked for the same
 * reason verify.mjs is. payment-blueprint's planted proof (2026-09-03): remove
 * one entry from qa/verified-surface.json and 203 files — the whole backend —
 * stop being attested; the next lane run mints a fresh receipt over the smaller
 * surface, harnessIntegrity PASS, nothing in the chain says the coverage moved.
 * An edited checker and an edited definition of what the checker looks at are
 * the same attack. State a lane WRITES (approvals.json, comments.json, the
 * journal, evidence/) stays out — that is the EXCLUDED_PREFIXES distinction.
 * Neither file exists in a Compose app by default; its region is unchanged.
 */
export const HARNESS_DECLARATIONS = ["qa/verified-surface.json", "qa/harness-manifest.json"];

/**
 * MACHINE-OWNED files that are not .mjs and not declarations. Today: the
 * provenance record (lib/harness-source.mjs) — which artifact was vendored and
 * where it came from, written by init and rewritten by upgrade.
 *
 * It is in the region for one reason: inside it, the record is inside
 * `lock.files` and inside `inputs.hash`, so a hand-edited origin FAILs
 * harnessIntegrity by name. Provenance is offered as no kind of check
 * (ADR-0008), and that is precisely why it must not also be silently editable —
 * a field nothing verifies and anyone can rewrite is worse than an absent one.
 *
 * It is deliberately NOT in HARNESS_DECLARATIONS, though it sits beside them:
 * that list is what `harness relock` may re-baseline, and an adopter re-locking
 * their own edited provenance is the one outcome this must never allow.
 *
 * Absent is fine everywhere — `listHarnessFiles` lists what exists, so a lane
 * installed before this file existed has an unchanged region and an unchanged
 * lock.
 */
export const HARNESS_GENERATED = ["qa/harness-source.json"];

/**
 * Of the locked region, WHICH files does the adopter author?
 *
 * The region is one lock but two kinds of file, and conflating them is what
 * bricked the first foreign adopter (2026-09-05). `harness init` generates
 * qa/lib/profiles/<id>/index.mjs with a header that says "This file is YOURS",
 * and the README tells adopters to correct qa/harness-manifest.json and
 * qa/verified-surface.json — then the first such edit FAILs harnessIntegrity
 * with no command that could re-take the lock. Their profile is inside the
 * lock for a good reason (an edited gate pack must not certify itself
 * silently), so the answer is not to unlock it; it is to name the subset a
 * re-lock may cover:
 *
 *   qa/lib/profiles/<id>/**        the profile they wrote — <id> REQUIRED, so a
 *                                  loose qa/lib/profiles/*.mjs stays machine-owned
 *   HARNESS_DECLARATIONS           the two files the lane READS from them
 *
 * Everything else — every .mjs directly under qa/ and qa/lib/, and qa/test/**
 * — is engine code. An edit there is a fork, and `create-cmp harness relock`
 * refuses it rather than re-baselining a lane the harness has never seen.
 *
 * This rule is deliberately NAME-based and stack-free: it says nothing about
 * which profile ids the engine itself vendors (`create-cmp harness relock`
 * layers that on, from the harness package it ships with — the core does not
 * learn a profile id). So a vendored lane can use this to point a failing
 * adopter at the right command; the command is where the gate actually is.
 *
 * @param {string} relPath project-relative path, "/"-separated
 * @returns {boolean}
 */
export function isAdopterOwned(relPath) {
  if (!isHarnessFile(relPath)) return false;
  if (HARNESS_DECLARATIONS.includes(relPath)) return true;
  const prefix = `${HARNESS_PROFILES_DIR}/`;
  return relPath.startsWith(prefix) && relPath.slice(prefix.length).includes("/");
}

/**
 * Is this region member ENGINE CODE — the lane itself, as opposed to what the
 * lane reads about itself or the profile the adopter wrote?
 *
 * The three exclusions are the region's non-engine halves: the declarations
 * (`qa/verified-surface.json`, `qa/harness-manifest.json`), the generated
 * provenance record, and `qa/lib/profiles/<id>/**` — an adopter's own pack is
 * theirs, and a region holding a profile and no spine is still a lane with no
 * engine in it.
 *
 * ADR-0010: a region with zero of these is VACUOUS. It reads `intact` because
 * it genuinely is unmodified since it was locked — it is simply not a lane.
 *
 * @param {string} relPath project-relative path, "/"-separated
 * @returns {boolean}
 */
export function isEngineModule(relPath) {
  if (!isHarnessFile(relPath)) return false;
  if (HARNESS_DECLARATIONS.includes(relPath)) return false;
  if (HARNESS_GENERATED.includes(relPath)) return false;
  if (relPath.startsWith(`${HARNESS_PROFILES_DIR}/`)) return false;
  return relPath.endsWith(".mjs");
}

/**
 * Is this project-relative path part of the machine-owned harness region?
 * @param {string} relPath project-relative path, "/"-separated
 * @returns {boolean}
 */
export function isHarnessFile(relPath) {
  if (typeof relPath !== "string") return false;
  if (HARNESS_DECLARATIONS.includes(relPath)) return true;
  if (HARNESS_GENERATED.includes(relPath)) return true;
  if (!relPath.endsWith(".mjs")) return false;
  if (relPath.startsWith(`${HARNESS_TEST_DIR}/`)) return true;
  if (relPath.startsWith(`${HARNESS_PROFILES_DIR}/`)) return true;
  const dir = relPath.includes("/") ? relPath.slice(0, relPath.lastIndexOf("/")) : "";
  return HARNESS_DIRS.includes(dir);
}

function walkMjs(dirAbs, relPrefix, out) {
  let entries;
  try {
    entries = fs.readdirSync(dirAbs, { withFileTypes: true });
  } catch {
    return;
  }
  for (const ent of entries) {
    const rel = `${relPrefix}/${ent.name}`;
    if (ent.isDirectory()) walkMjs(path.join(dirAbs, ent.name), rel, out);
    else if (ent.isFile() && isHarnessFile(rel)) out.push(rel);
  }
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
  walkMjs(path.join(root, HARNESS_TEST_DIR), HARNESS_TEST_DIR, found);
  walkMjs(path.join(root, HARNESS_PROFILES_DIR), HARNESS_PROFILES_DIR, found);
  // A declaration directly under qa/ is already seen by the scan above (it is
  // a harness file by name); the explicit loop covers one that lives deeper.
  // Deduplicated so no path is hashed twice.
  for (const rel of [...HARNESS_DECLARATIONS, ...HARNESS_GENERATED]) {
    if (fs.existsSync(path.join(root, ...rel.split("/")))) found.push(rel);
  }
  return [...new Set(found)].sort();
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
  return {
    sha256: digest.digest("hex"),
    fileCount: rels.length,
    // ADR-0010: how many of those files are the LANE. `fileCount` counts the
    // region; this counts the engine in it, and zero is the vacuous self-vouch.
    engineFiles: rels.filter(isEngineModule).length,
    files,
  };
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
