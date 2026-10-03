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
//   AUTHENTICITY  "is my lane the real published prooflane-harness@X?"
//              NOT ANSWERED ANYWHERE IN THIS REPO — see below. The per-file
//              map this lock records is what such a check would compare
//              against a published artifact; nothing performs the comparison.
//
// Being honest about that split matters, and this comment was not. It used to
// say `create-cmp upgrade --harness` performed the remote comparison. It does
// not and never has: that command reads the version out of a LOCAL
// packages/harness/package.json and re-locks from local bytes
// (src/commands/upgrade.mjs:410-413). The only registry call in the tree packs
// `create-cmp-cli@<v>` as an upgrade's merge base. So the sentence promising
// that "the attacker cannot change what the registry published" described a
// defence that does not exist — in the module whose whole job is to be precise
// about which question it answers, which is the failure this harness exists to
// refuse (ADR-0008, which found it).
//
// What is true: someone who edits the lane AND rewrites this lock defeats the
// local check — of course they do; it is a checksum, not a signature. Local
// integrity catches the accident and the drift (an agent "fixing" a lane file,
// a half-applied upgrade). It catches no lie, and until a remote comparison is
// built, nothing here does.
//
// The lock is deliberately NOT a .mjs file, so it is not part of the region it
// describes — a manifest inside its own manifest could never settle.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/harness-lock.mjs in the
// create-cmp repo — edit there, then run `node scripts/sync-harness.mjs`.

import fs from "node:fs";
import path from "node:path";
import { hashHarnessRegion, compareHarnessRegion, isAdopterOwned } from "./harness-region.mjs";

export const LOCK_PATH = "qa/harness.lock.json";
// ADR-0007 deferred this one with a condition: the lock's schema string
// "finishes that journey with the package work, not in this PR". That work has
// landed — the lock has written `name: "prooflane-harness"` since the package
// rename, which is the split ADR-0007 itself pointed at as observable in the
// tree. The condition is met, so the journey finishes here. Nothing reads the
// field; `readHarnessLock` parses the file and never inspects it.
export const LOCK_SCHEMA = "prooflane-harness-lock/1";

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
export function writeHarnessLock(root, { name = "prooflane-harness", version }) {
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
      engineFiles: region.engineFiles,
      vacuous: region.engineFiles === 0,
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
    // ADR-0010: how much of this region is the LANE, and whether it is a lane
    // at all. `status` is deliberately untouched — a region of three
    // declarations genuinely IS unmodified since it was locked; it is simply
    // not a lane, and that is a different question from the one status answers.
    engineFiles: region.engineFiles,
    vacuous: region.engineFiles === 0,
  };
}

/**
 * One-line human summary of an integrity result — shared by the lane step and
 * the upgrade command so both describe the same state the same way.
 * @param {ReturnType<typeof checkHarnessIntegrity>} r
 * @returns {string}
 */
export function describeIntegrity(r) {
  // ADR-0010, AND THIS IS THE REACHABLE HALF OF IT. `status` is honest — a
  // region of declarations really is unmodified since it was locked — so a
  // caller rendering that status alone says "N files verified" over a lane that
  // holds no lane. Every caller that checks a tree it does not LIVE in reaches
  // this: `create-cmp upgrade --harness` and `harden` print it over an
  // arbitrary project directory, `prooflane upgrade` over the adopter's root,
  // and a hosted checker over a repo it fetched. The shipped lane cannot (it
  // derives its root from its own location, so its own module is always in the
  // region) — which is exactly why the fix belongs in the shared voice rather
  // than in one step.
  if (r.vacuous) {
    return `${r.name ?? "harness"} ${r.version ?? "?"} — ${r.fileCount} file(s) locked and NONE of them engine code: this is not a lane, and nothing here can vouch for one`;
  }
  if (r.status === "intact") {
    // The region digest rides beside the version: two lanes can carry the same
    // package version with different content (create-cmp-showcase, 2026-09-03 —
    // seven files changed, "0.16.0" on both receipts), and a receipt must name
    // WHICH lane produced it in words a human reads, not only in the lock file.
    const region = typeof r.sha256 === "string" && r.sha256 ? ` (region ${r.sha256.slice(0, 8)})` : "";
    return `${r.name ?? "harness"} ${r.version ?? "?"}${region} — ${r.fileCount} files verified`;
  }
  if (r.status === "unlocked") {
    return `no ${LOCK_PATH} — this app's lane version is unrecorded`;
  }
  // NAME THE FILES. This used to report counts only — "1 unrecorded" — while
  // holding the paths in `r.extra` and never showing them. The first adopter to
  // hit it was following our own README, whose step 1 writes a file that is in
  // HARNESS_DECLARATIONS: a correct refusal they could not act on, because the
  // one fact that makes it actionable was in the object and not in the sentence.
  // Evidence-or-silence: a gate that refuses names what it refused over.
  const parts = [];
  const show = (list, label) => {
    if (!list.length) return;
    const head = list.slice(0, 3).join(", ");
    parts.push(`${list.length} ${label}: ${head}${list.length > 3 ? `, +${list.length - 3} more` : ""}`);
  };
  show(r.modified, "modified");
  show(r.missing, "missing");
  show(r.extra, "unrecorded");
  // WHICH command helps depends on WHOSE files differ, and the two answers are
  // opposites. An adopter editing their own profile or declaration is doing the
  // one thing the harness tells them to do (the profile header says "This file
  // is YOURS"), and the cure is to re-take the lock. A machine-owned file
  // differing is a fork, and re-taking the lock over THAT would make every
  // later receipt vouch for code the harness has never seen. Naming one command
  // for both is how the first adopter to edit their profile got a lane that
  // could not be un-failed: `harness init` refused ("already exists") and
  // `upgrade --harness` refused (no create-cmp.json).
  //
  // CONDITIONAL on purpose. `isAdopterOwned` is a name rule and cannot know
  // whether a given profile is one the ENGINE vendors (`qa/lib/profiles/cmp/`
  // in every stamped Compose app) — teaching this module a profile id is the
  // coupling Stage 0 removed. So this offers the command rather than asserting
  // the ownership; `create-cmp harness relock` is where the decision is made,
  // and it refuses a shipped profile by name.
  const differing = [...r.modified, ...r.missing, ...r.extra];
  const fix = differing.every(isAdopterOwned)
    ? " — if these are yours (your profile, your declarations), re-lock with `create-cmp harness relock`"
    : r.extra.length && !r.modified.length && !r.missing.length
      ? " — re-lock with `create-cmp upgrade --harness`, or remove the file if it should not be there"
      : "";
  return `${r.name ?? "harness"} ${r.version ?? "?"} — ${parts.join("; ")}${fix}`;
}
