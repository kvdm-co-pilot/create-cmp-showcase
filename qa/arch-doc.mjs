#!/usr/bin/env node
// The architecture-doc generator/checker — thin shell over qa/lib/arch-doc.mjs,
// mirroring qa/approve.mjs's CLI-over-library split.
//
//   node qa/arch-doc.mjs            regenerate docs/ARCHITECTURE.md's
//                                    `cmp:generated` sections in place from a
//                                    real tree walk — touches NOTHING outside
//                                    the markers
//   node qa/arch-doc.mjs --check    exit nonzero (naming every stale/missing
//                                    section) if regenerating would change the
//                                    file; never writes
//
// This file has no logic of its own — every decision (what each section
// derives from, the marker grammar) lives in qa/lib/arch-doc.mjs. The verify
// lane's `archDoc` step (qa/verify.mjs) calls the SAME library in --check mode.

import path from "node:path";
import { fileURLToPath } from "node:url";

import { ARCH_DOC_REL_PATH, regenerateArchDoc, writeArchDoc } from "./lib/arch-doc.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const checkOnly = process.argv.includes("--check");

function reportUnknown(result) {
  if (result.unknownSections.length === 0) return false;
  console.error(
    `error: ${ARCH_DOC_REL_PATH} has cmp:generated marker(s) with no registered generator: ${result.unknownSections.join(", ")}`,
  );
  return true;
}

if (checkOnly) {
  const result = regenerateArchDoc(ROOT);
  if (!result.ok) {
    console.error(`error: ${result.reason}`);
    process.exit(1);
  }
  if (reportUnknown(result)) process.exit(1);

  const stale = result.changed || result.missingSections.length > 0;
  if (!stale) {
    console.log(`✓ ${ARCH_DOC_REL_PATH} is fresh — every generated section matches the tree.`);
    process.exit(0);
  }

  console.error(`✗ ${ARCH_DOC_REL_PATH} is stale:`);
  for (const id of result.changedSections) {
    console.error(`  [${id}] regenerating would change this section — the tree no longer matches the doc`);
  }
  for (const id of result.missingSections) {
    console.error(`  [${id}] marker missing from the doc entirely — never generated`);
  }
  console.error("Run: node qa/arch-doc.mjs");
  process.exit(1);
}

const result = writeArchDoc(ROOT);
if (!result.ok) {
  console.error(`error: ${result.reason}`);
  process.exit(1);
}
if (reportUnknown(result)) process.exit(1);

if (!result.wrote) {
  console.log(`✓ ${ARCH_DOC_REL_PATH} already fresh — nothing to regenerate.`);
  process.exit(0);
}
console.log(`✓ regenerated ${ARCH_DOC_REL_PATH} — updated section(s): ${result.changedSections.join(", ")}`);
