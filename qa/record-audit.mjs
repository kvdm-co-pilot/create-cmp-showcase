#!/usr/bin/env node
// Record that a cmp-audit of one androidMain subsystem happened — the
// write half of the audit-cadence report (qa/lib/audit-cadence.mjs).
//
//   node qa/record-audit.mjs <subsystem> [--by <who-or-what>]
//   node qa/record-audit.mjs --list
//
// Recording is a CLAIM — "this subsystem, as of this commit, was audited" —
// so the entry's sha is derived from HEAD by the library, never passed in,
// and the write is REFUSED when there is no git history, when the subsystem
// is not one this app actually has (derived from the tree, printed on
// refusal), or when the subsystem's files differ from HEAD (the record
// would name a commit the audited bytes did not match — commit first).
// Refusal over fabrication, the same stance as qa/approve.mjs.
//
// This CLI exists so the audit loop closes mechanically: the release
// profile's receipt nudges "changed since last audit → cmp-audit <name>",
// and the auditor's last act is this one command.

import path from "node:path";
import { fileURLToPath } from "node:url";

import { AUDITS_REL_PATH, ROOT_SUBSYSTEM, androidMainPackageRoot, evaluateAuditCadence, listSubsystems, recordAudit } from "./lib/audit-cadence.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const USAGE = `node qa/record-audit.mjs <subsystem> [--by <who-or-what>]

Appends one audit record (subsystem, HEAD sha, ISO timestamp, recorder) to
${AUDITS_REL_PATH}. The verify lane's release profile reports which
subsystems changed since their last record. Subsystems are derived from the
tree: the immediate package directories under the androidMain Kotlin source
root ("${ROOT_SUBSYSTEM}" for files directly at the package root).

  --list        print the derived subsystems and their audit status
  --by <name>   who/what recorded this (default: git user.name)
  --help, -h    this usage
`;

const args = process.argv.slice(2);

if (args.includes("--help") || args.includes("-h") || args.length === 0) {
  console.log(USAGE);
  process.exit(args.length === 0 ? 2 : 0);
}

if (args.includes("--list")) {
  const report = evaluateAuditCadence(ROOT);
  if (!report.ok) {
    console.log(report.reason);
    process.exit(0);
  }
  console.log(`androidMain subsystems under ${report.packageRoot} (${report.summary}):`);
  for (const s of report.subsystems) {
    const when = s.audit?.at ? ` — last audit ${s.audit.at.slice(0, 10)} (${s.audit.sha.slice(0, 7)}, by ${s.audit.by ?? "unknown"})` : "";
    console.log(`  ${s.name}: ${s.status}${when}`);
  }
  process.exit(0);
}

const byIdx = args.indexOf("--by");
const by = byIdx >= 0 ? args[byIdx + 1] : undefined;
if (byIdx >= 0 && !by) {
  console.error("--by needs a value");
  process.exit(2);
}
const positional = args.filter((a, i) => !(byIdx >= 0 && (i === byIdx || i === byIdx + 1)));
if (positional.length !== 1 || positional[0].startsWith("--")) {
  console.error(`expected exactly one subsystem name — run node qa/record-audit.mjs --help`);
  process.exit(2);
}

const res = recordAudit(ROOT, { subsystem: positional[0], by });
if (!res.ok) {
  console.error(`refused: ${res.reason}`);
  const pkgRoot = androidMainPackageRoot(ROOT);
  if (pkgRoot.ok) {
    console.error(`derived subsystems: ${listSubsystems(ROOT, pkgRoot.rel).join(", ")}`);
  }
  process.exit(1);
}
console.log(`recorded: audit of ${res.entry.subsystem} against ${res.sha.slice(0, 7)} (by ${res.entry.by}) → ${AUDITS_REL_PATH}`);
console.log("commit the ledger with your change — the release profile's receipt reads it.");
