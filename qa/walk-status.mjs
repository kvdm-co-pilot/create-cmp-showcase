#!/usr/bin/env node
// walk-status.mjs — where every open change stands, whose turn it is, what
// arrived unplanned. The walk's CLI face (docs/features/walk-status.md D5).
//
//   node qa/walk-status.mjs                # full cards, human-readable
//   node qa/walk-status.mjs --statusline   # the always-on one-liner
//   node qa/walk-status.mjs --inject       # UserPromptSubmit hook JSON
//   node qa/walk-status.mjs --json         # the raw derivation
//
// FAIL-OPEN BY CONTRACT: this runs inside a statusline and a per-prompt hook.
// Any failure — broken ledger, non-cmp directory, anything — exits 0 with
// empty output. A status surface may never block or noise the work it reports
// on. (The console renders the same derivation with full error honesty; this
// surface's honesty is silence.)

import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);

/**
 * The hook's stdin, parsed — UserPromptSubmit delivers {prompt, ...} as JSON.
 * Bounded read, fail-soft: no stdin / non-JSON / no prompt -> null. Only the
 * --inject path consumes this (the statusline gets no stdin and must not wait
 * on one).
 */
async function readHookStdin() {
  if (process.stdin.isTTY) return null;
  try {
    let raw = "";
    for await (const chunk of process.stdin) {
      raw += chunk;
      if (raw.length > 1_000_000) break; // a prompt is never this — stop reading, keep what we have
    }
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

try {
  const { deriveWalks, renderStatusline, renderCard, renderInject } = await import("./lib/walk.mjs");

  // Tier 1 of the chain (studio-drive-mode): record the human's own prompt
  // BEFORE deriving, so this very inject already reflects the new request.
  // Machinery-owned — the words are the hook's, never the agent's.
  if (args.includes("--inject")) {
    const hook = await readHookStdin();
    if (hook && typeof hook.prompt === "string") {
      const { recordRequest } = await import("./lib/plan.mjs");
      recordRequest(ROOT, hook.prompt);
    }
  }

  const data = deriveWalks(ROOT);

  if (args.includes("--json")) {
    process.stdout.write(`${JSON.stringify(data, null, 2)}\n`);
  } else if (args.includes("--statusline")) {
    const line = renderStatusline(data);
    if (line !== "") process.stdout.write(`${line}\n`);
  } else if (args.includes("--inject")) {
    const ctx = renderInject(data);
    if (ctx !== "") {
      process.stdout.write(
        JSON.stringify({
          hookSpecificOutput: { hookEventName: "UserPromptSubmit", additionalContext: ctx },
        }),
      );
    }
  } else {
    if (!data.available) {
      process.stdout.write(`walk-status: not derivable — ${data.reason}\n`);
    } else if (data.walks.length === 0 && data.arrivals.length === 0) {
      process.stdout.write("No open walks. Every accepted feature's brief is its doc-of-record.\n");
    } else {
      const { renderChain } = await import("./lib/plan.mjs");
      const chainText = renderChain(data.chain);
      if (chainText !== "") process.stdout.write(`${chainText}\n\n`);
      for (const w of data.walks) process.stdout.write(`${renderCard(w, data)}\n\n`);
      for (const a of data.arrivals)
        process.stdout.write(`▲ ARRIVED, UNPLANNED — ${a.label} (${a.status}): ${a.reason ?? "no recorded reason"}\n`);
    }
  }
  process.exit(0);
} catch {
  process.exit(0); // fail-open: a status surface never blocks the work
}
