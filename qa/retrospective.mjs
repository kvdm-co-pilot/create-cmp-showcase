#!/usr/bin/env node
// The flight recorder's reader — "did this project drift from its tooling?"
// answered mechanically, from qa/flight-recorder.jsonl alone.
//
//   node qa/retrospective.mjs [--json]
//
// The verify lane appends one journal line per run (qa/lib/flight-recorder.mjs);
// this CLI turns those lines into the ten-second report a retrospective
// starts from: fast vs full ratio, which steps SKIP most and why (verbatim
// reasons, grouped), whether the device tier is ever actually reached, the
// longest recorded stretch with no full lane, and which degraded paths fired.
//
// HONESTY RULES — the reader is only as good as its refusals:
//   - it states only what the journal recorded; a missing journal is
//     "no flight data recorded yet" and exit 0, never an error, never a
//     fabricated baseline;
//   - a short journal says it is short instead of letting two entries read
//     as a trend;
//   - it never editorializes about the developer — every line is a count or
//     a date about lane runs, and the human draws the conclusions.

import path from "node:path";
import { fileURLToPath } from "node:url";

import { readFlightJournal, renderFlightReport, summarizeFlightJournal } from "./lib/flight-recorder.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const asJson = process.argv.includes("--json");

const journal = readFlightJournal(ROOT);

if (!journal.exists) {
  if (asJson) console.log(JSON.stringify({ recorded: false }));
  else console.log("no flight data recorded yet — the journal (qa/flight-recorder.jsonl) appears after the first verify-lane run");
  process.exit(0);
}
if (journal.error) {
  console.error(`qa/flight-recorder.jsonl exists but could not be read: ${journal.error}`);
  process.exit(1);
}

const summary = summarizeFlightJournal(journal.entries);

if (asJson) {
  console.log(JSON.stringify({ recorded: true, malformed: journal.malformed, summary }, null, 2));
} else {
  for (const line of renderFlightReport(summary, { malformed: journal.malformed })) {
    console.log(line);
  }
}
process.exit(0);
