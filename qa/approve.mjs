#!/usr/bin/env node
// The approvals CLI — thin shell over qa/lib/approvals.mjs.
//
//   node qa/approve.mjs <artifact>          records approval (recomputes the artifact's
//                                            hash now, stamps the time, writes qa/approvals.json)
//   node qa/approve.mjs --status            lists every governed artifact + live state
//                                            (unreviewed / approved / changed-since-approval /
//                                            reopened, + mode when set) + short hash
//   node qa/approve.mjs --accept-defaults   express lane (GENESIS-FLOW-DESIGN.md §2): approves
//                                            every currently-resolvable, not-yet-approved
//                                            artifact, each stamped "defaults-accepted"
//   node qa/approve.mjs --reopen <artifact> moves an approved artifact back to "reopened" for
//                                            redesign (refuses anything not currently approved)
//   node qa/approve.mjs --deliver <name>    the AGENT's claim of done for a feature brief
//                                            (feature-intent:<name>): ARMS its cmp:intent-checks —
//                                            from here the lane FAILs on any unsatisfied check
//   node qa/approve.mjs --accept <name>     the HUMAN's bookend on a delivered brief — refused
//                                            while any armed check fails
//
// This file has NO logic of its own — every decision (the registry, hashing,
// state, the transitions) lives in qa/lib/approvals.mjs. That's deliberate: the
// console (VERIFICATION-LAYER-DESIGN.md §4, `POST /api/approve`; GENESIS-FLOW-DESIGN.md
// §2, `POST /api/reopen`) calls the SAME library this CLI calls, so this file is the
// API surface, kept intentionally thin and easy to keep in lockstep.

import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  acceptFeature,
  approveAllDefaults,
  approveArtifact,
  deliverFeature,
  getApprovalStatuses,
  getDeliveredFeatureNames,
  isPackageResolvable,
  listGovernedArtifacts,
  reopenArtifact,
} from "./lib/approvals.mjs";
import { resolveAllProposals } from "./lib/intent-checks.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);

function shortHash(hash) {
  return hash ? hash.slice(0, 8) : "none";
}

function printStatus() {
  const statuses = getApprovalStatuses(ROOT);
  if (statuses.length === 0) {
    console.log("No governed artifacts resolved in this project (no specs/, or the package could not be resolved).");
    return;
  }
  console.log("Approval status:\n");
  for (const s of statuses) {
    const mark =
      s.status === "approved" ? "✓" : s.status === "changed-since-approval" ? "✗" : s.status === "reopened" ? "↺" : "→";
    // An unresolvable artifact (0 files, or a partial kotlin set in a
    // pre-stamp tree) must never display its degraded hash as if it were
    // approvable — approval over an unresolved file set is refused.
    const hashInfo =
      s.status === "reopened"
        ? `reopened at ${s.reopenedAt} (was approved ${shortHash(s.storedHash)})`
        : s.status === "changed-since-approval"
          ? s.resolvable
            ? `approved ${shortHash(s.storedHash)} -> now ${shortHash(s.hash)}`
            : `approved ${shortHash(s.storedHash)} -> unresolvable (${s.fileCount} of expected files resolved)`
          : s.status === "approved"
            ? shortHash(s.hash)
            : s.resolvable
              ? `would approve at ${shortHash(s.hash)}`
              : `unresolvable (${s.fileCount} of expected files resolved) — not approvable`;
    const modeInfo = s.mode ? ` [${s.mode}]` : "";
    // Feature-brief lifecycle (delivered/accepted live on the ledger row, not
    // in the doc — see qa/lib/intent-checks.mjs) — shown as a phase suffix.
    const lifecycle = s.accepted ? ` · accepted ${s.acceptedAt}` : s.delivered ? ` · delivered ${s.deliveredAt} (checks armed)` : "";
    console.log(`${mark} ${s.id}: ${s.status} (${hashInfo})${modeInfo}${lifecycle} — ${s.label}`);
    if (s.missing.length > 0) {
      console.log(`    missing: ${s.missing.join(", ")}`);
    }
  }

  // Per-brief check tallies — the same resolution the intentChecks lane step
  // reports, so --status and the lane never tell different stories.
  const proposals = resolveAllProposals(ROOT, getDeliveredFeatureNames(ROOT));
  if (proposals.length > 0) {
    console.log("\nFeature briefs (cmp:intent-checks):");
    for (const p of proposals) {
      const armed = p.delivered ? " (armed — unsatisfied checks FAIL the lane)" : " (informational until --deliver)";
      console.log(`  ${p.name}: ${p.satisfied}/${p.total} checks${p.error ? ` — BLOCK ERROR: ${p.error}` : armed}`);
    }
  }
}

if (args.includes("--status")) {
  printStatus();
  process.exit(0);
}

// Write guard: refuse to RECORD approvals in a tree whose package is not
// resolvable (the raw template / a pre-stamp tree). Approvals belong to a
// generated project; writing qa/approvals.json into the template pollutes the
// template itself. Read-only --status (above) stays available anywhere. Applies
// to every write operation below (single approve, express lane, reopen).
function refuseIfUnresolvable() {
  if (isPackageResolvable(ROOT)) return;
  console.error(
    "error: this tree's package is not resolvable (composeApp/build.gradle.kts namespace is missing or still a placeholder) — " +
      "this looks like the raw template or a pre-stamp tree. Approvals are recorded in a generated project; refusing to write qa/approvals.json here.",
  );
  process.exit(1);
}

if (args.includes("--accept-defaults")) {
  refuseIfUnresolvable();
  const { approved, skipped } = approveAllDefaults(ROOT);
  for (const id of approved) {
    console.log(`✓ approved ${id} [defaults-accepted]`);
  }
  for (const s of skipped) {
    console.log(`→ skipped ${s.id}: ${s.reason}`);
  }
  console.log(`\n${approved.length} approved (defaults-accepted), ${skipped.length} skipped (unresolvable).`);
  process.exit(0);
}

const reopenFlagIdx = args.indexOf("--reopen");
if (reopenFlagIdx !== -1) {
  refuseIfUnresolvable();
  const artifactId = args[reopenFlagIdx + 1];
  if (!artifactId) {
    console.error("usage: node qa/approve.mjs --reopen <artifact>");
    process.exit(1);
  }
  const result = reopenArtifact(ROOT, artifactId);
  if (!result.ok) {
    console.error(`error: ${result.reason}`);
    process.exit(1);
  }
  console.log(`↺ reopened ${result.artifact} for redesign — at ${result.reopenedAt}`);
  process.exit(0);
}

// Feature-brief lifecycle: --deliver (the agent's claim of done, arms checks)
// and --accept (the human's bookend). Both take the brief NAME, not the full
// artifact id — the docs/proposals/<name>.md filename is what a human knows.
const deliverFlagIdx = args.indexOf("--deliver");
if (deliverFlagIdx !== -1) {
  refuseIfUnresolvable();
  const name = args[deliverFlagIdx + 1];
  if (!name) {
    console.error("usage: node qa/approve.mjs --deliver <name>   (the brief's name — docs/proposals/<name>.md)");
    process.exit(1);
  }
  const result = deliverFeature(ROOT, name);
  if (!result.ok) {
    console.error(`error: ${result.reason}`);
    process.exit(1);
  }
  console.log(
    `● delivered ${result.artifact} at ${result.deliveredAt} — checks armed (${result.satisfied}/${result.total} currently satisfied).` +
      (result.satisfied < result.total ? " The lane now FAILs until every check passes." : " The lane's intentChecks gate is green."),
  );
  process.exit(0);
}

const acceptFlagIdx = args.indexOf("--accept");
if (acceptFlagIdx !== -1) {
  refuseIfUnresolvable();
  const name = args[acceptFlagIdx + 1];
  if (!name) {
    console.error("usage: node qa/approve.mjs --accept <name>   (the brief's name — docs/proposals/<name>.md)");
    process.exit(1);
  }
  const result = acceptFeature(ROOT, name);
  if (!result.ok) {
    console.error(`error: ${result.reason}`);
    process.exit(1);
  }
  console.log(`✓ accepted ${result.artifact} at ${result.acceptedAt} — the feature's card closes; the brief is its doc-of-record.`);
  process.exit(0);
}

if (args.length === 0) {
  const ids = listGovernedArtifacts(ROOT).map((a) => a.id);
  console.error(
    "usage: node qa/approve.mjs <artifact> | --status | --accept-defaults | --reopen <artifact> | --deliver <name> | --accept <name>\n" +
      `  valid artifacts: ${ids.length > 0 ? ids.join(", ") : "(none resolved in this project)"}`,
  );
  process.exit(1);
}

refuseIfUnresolvable();

const artifactId = args[0];
const result = approveArtifact(ROOT, artifactId);
if (!result.ok) {
  console.error(`error: ${result.reason}`);
  process.exit(1);
}
console.log(`✓ approved ${result.artifact} — hash ${shortHash(result.hash)}, at ${result.approvedAt}`);
