#!/usr/bin/env node
// The evidence-binding predicate — answers one question: does the committed
// receipt (qa/evidence/latest.json) validly attest THIS tree, right now?
//
//   node qa/receipt-check.mjs [--hook] [--json]
//
// Both enforcement points reduce to this predicate: the local Stop hook
// (.claude/settings.json) calls it on every turn-end, and CI calls it before
// re-running the lane. See docs/adr/0005-evidence-binding-by-inputs-hash.md.
//
// VALID  iff receipt.verdict === "PASS" && receipt.inputs.hash === recompute(tree)
// Exit codes (normal mode): VALID -> 0, INVALID -> 1.
// Exit codes (--hook mode, Claude Code Stop-hook protocol):
//   stop_hook_active === true  -> 0 (never block twice in a row)
//   INVALID                    -> 2, reason on stderr (Claude Code's block-and-feed-back signal)
//   VALID                      -> 0, silent

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { computeInputsHash } from "./lib/inputs-hash.mjs";
import { evaluateReceipt, readReceipt } from "./lib/receipt-validate.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const args = process.argv.slice(2);
const asHook = args.includes("--hook");
const asJson = args.includes("--json");

function readStdinJson() {
  try {
    const raw = fs.readFileSync(0, "utf8");
    if (!raw.trim()) return {};
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

// The predicate itself lives in qa/lib/receipt-validate.mjs (vendored from the
// cmp-receipts package — one definition everywhere a receipt is judged); this
// CLI only reads the receipt and frames the exit codes.
function evaluate() {
  const receipt = readReceipt(ROOT);
  if (receipt === null) {
    return { valid: false, reason: "no receipt — run `node qa/verify.mjs`", profile: undefined };
  }
  return evaluateReceipt(receipt, () => computeInputsHash(ROOT));
}

const result = evaluate();

if (asHook) {
  const hookInput = readStdinJson();
  if (hookInput.stop_hook_active === true) {
    process.exit(0);
  }
  if (!result.valid) {
    process.stderr.write(
      `Not done: ${result.reason}. Run \`node qa/verify.mjs\` and commit the receipt, or see README §Verification enforcement to bypass.\n`,
    );
    process.exit(2);
  }
  process.exit(0);
}

if (asJson) {
  console.log(JSON.stringify(result, null, 2));
} else if (result.valid) {
  console.log(`VALID — ${result.reason}`);
} else {
  console.error(`INVALID — ${result.reason}`);
}

process.exit(result.valid ? 0 : 1);
