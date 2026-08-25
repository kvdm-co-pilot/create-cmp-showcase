// qa/harness.lock.json — which lane this app carries, and whether it is intact.
//
// The lock is written at stamp time and rewritten by `create-cmp upgrade
// --harness`. It names the harness version and records a sha256 per
// machine-owned file, so two different questions get two different answers:
//
//   INTEGRITY  "is my lane unmodified since it was installed?"
//              Answered LOCALLY, offline, on every lane run. Needs nothing
//              but the tree and this file.
//
//   AUTHENTICITY  "is my lane the real published @create-cmp/harness@X?"
//              Answered REMOTELY, on request, by comparing this file's
//              `sha256` against the published version's — `create-cmp
//              upgrade --harness` does it, and so can any third party
//              holding a receipt.
//
// Being honest about that split matters. Someone who edits the lane AND
// rewrites this lock defeats the local check — of course they do; it is a
// checksum, not a signature. What it cannot survive is the remote comparison,
// because the attacker cannot change what the registry published under that
// version number. Local integrity catches the accident and the drift (an
// agent "fixing" a lane file, a half-applied upgrade); the remote comparison
// catches the lie. Neither claim is stretched to cover the other's job.
//
// The lock is deliberately NOT a .mjs file, so it is not part of the region it
// describes — a manifest inside its own manifest could never settle.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/harness-lock.mjs in the
// create-cmp repo — edit there, then run `node scripts/sync-harness.mjs`.

import fs from "node:fs";
import path from "node:path";
import { hashHarnessRegion, compareHarnessRegion } from "./harness-region.mjs";

export const LOCK_PATH = "qa/harness.lock.json";
export const LOCK_SCHEMA = "cmp-harness-lock/1";

/**
 * Read the lock, or null when it is absent or unparsable. An unreadable lock
 * is not distinguished from a missing one on purpose: both mean "this tree
 * cannot tell me what lane it carries", and both get the same honest verdict
 * from checkHarnessIntegrity — unknown, never intact.
 * @param {string} root project root
 * @returns {object|null}
 */
export function readHarnessLock(root) {
  try {
    const parsed = JSON.parse(fs.readFileSync(path.join(root, LOCK_PATH), "utf8"));
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * Hash the tree's region and write the lock describing it.
 * Called at stamp time and after an upgrade replaces the region — never by
 * the lane itself, which must only ever READ the lock. A lane that rewrote
 * its own manifest could not fail the integrity check it exists to run.
 * @param {string} root project root
 * @param {{name?: string, version: string}} harness identity to record
 * @returns {{sha256: string, fileCount: number}}
 */
export function writeHarnessLock(root, { name = "@create-cmp/harness", version }) {
  if (typeof version !== "string" || version.length === 0) {
    throw new Error("writeHarnessLock: a harness version is required");
  }
  const region = hashHarnessRegion(root);
  const lock = {
    schema: LOCK_SCHEMA,
    name,
    version,
    sha256: region.sha256,
    fileCount: region.fileCount,
    files: region.files,
  };
  const abs = path.join(root, LOCK_PATH);
  fs.mkdirSync(path.dirname(abs), { recursive: true });
  fs.writeFileSync(abs, `${JSON.stringify(lock, null, 2)}\n`);
  return { sha256: region.sha256, fileCount: region.fileCount };
}

/**
 * Compare the tree's region against its lock.
 *
 * @param {string} root project root
 * @returns {{status: "intact"|"modified"|"unlocked", name: string|null,
 *            version: string|null, sha256: string, recordedSha256: string|null,
 *            modified: string[], missing: string[], extra: string[],
 *            fileCount: number}}
 *   status "unlocked" means no readable lock — an app stamped before locks
 *   existed, or one whose lock was deleted. Reported as its own state rather
 *   than folded into "modified": nothing is known to be wrong, but nothing is
 *   proven either, and a gate that cannot tell those apart teaches people to
 *   ignore it.
 */
export function checkHarnessIntegrity(root) {
  const lock = readHarnessLock(root);
  const region = hashHarnessRegion(root);

  if (!lock || typeof lock.files !== "object" || lock.files === null) {
    return {
      status: "unlocked",
      name: lock?.name ?? null,
      version: typeof lock?.version === "string" ? lock.version : null,
      sha256: region.sha256,
      recordedSha256: typeof lock?.sha256 === "string" ? lock.sha256 : null,
      modified: [],
      missing: [],
      extra: [],
      fileCount: region.fileCount,
    };
  }

  const cmp = compareHarnessRegion(root, lock);
  return {
    status: cmp.intact ? "intact" : "modified",
    name: typeof lock.name === "string" ? lock.name : null,
    version: typeof lock.version === "string" ? lock.version : null,
    sha256: cmp.sha256,
    recordedSha256: typeof lock.sha256 === "string" ? lock.sha256 : null,
    modified: cmp.modified,
    missing: cmp.missing,
    extra: cmp.extra,
    fileCount: region.fileCount,
  };
}

/**
 * One-line human summary of an integrity result — shared by the lane step and
 * the upgrade command so both describe the same state the same way.
 * @param {ReturnType<typeof checkHarnessIntegrity>} r
 * @returns {string}
 */
export function describeIntegrity(r) {
  if (r.status === "intact") {
    return `${r.name ?? "harness"} ${r.version ?? "?"} — ${r.fileCount} files verified`;
  }
  if (r.status === "unlocked") {
    return `no ${LOCK_PATH} — this app's lane version is unrecorded`;
  }
  const parts = [];
  if (r.modified.length) parts.push(`${r.modified.length} modified`);
  if (r.missing.length) parts.push(`${r.missing.length} missing`);
  if (r.extra.length) parts.push(`${r.extra.length} unrecorded`);
  return `${r.name ?? "harness"} ${r.version ?? "?"} — ${parts.join(", ")}`;
}
