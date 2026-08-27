#!/usr/bin/env node
// plan.mjs — the live chain's CLI (docs/features/studio-drive-mode.md).
// The agent declares the current request's step chain at kickoff and advances
// it as work lands; the statusline stays the walk's, but the studio's Drive
// strip and the per-prompt inject both render this chain with its age.
//
//   node qa/plan.mjs                                   # show the chain
//   node qa/plan.mjs --set "a | b | c" [--title "…"] [--feature <name>]
//   node qa/plan.mjs --step 3                          # steps 1..2 done, 3 current
//   node qa/plan.mjs --done                            # close the chain
//   node qa/plan.mjs --clear                           # a landed request leaves no stale windshield
//
// Unlike walk-status (a fail-open status surface), this CLI is a WRITER the
// agent invokes deliberately: bad input gets a refusal and exit 1, because a
// silently-dropped declaration would leave the surfaces lying about position.

import path from "node:path";
import { fileURLToPath } from "node:url";

import { deriveChain, markStep, readPlan, renderChain, setPlan, clearPlan } from "./lib/plan.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);

const valueOf = (flag) => {
  const i = args.indexOf(flag);
  return i !== -1 && i + 1 < args.length ? args[i + 1] : null;
};

function out(result) {
  if (!result.ok) {
    process.stderr.write(`plan: ${result.reason}\n`);
    process.exit(1);
  }
  const rendered = renderChain(deriveChain(ROOT));
  process.stdout.write(`${rendered === "" ? "No chain declared." : rendered}\n`);
  process.exit(0);
}

if (args.includes("--set")) {
  const spec = valueOf("--set");
  if (spec === null) {
    process.stderr.write('plan: --set needs a value: --set "step | step | …"\n');
    process.exit(1);
  }
  out(
    setPlan(ROOT, {
      title: valueOf("--title") ?? undefined,
      feature: valueOf("--feature") ?? undefined,
      steps: spec.split("|"),
    }),
  );
} else if (args.includes("--step")) {
  out(markStep(ROOT, valueOf("--step")));
} else if (args.includes("--done")) {
  const plan = readPlan(ROOT);
  out(plan ? markStep(ROOT, plan.steps.length + 1) : { ok: false, reason: "no declared chain to close" });
} else if (args.includes("--clear")) {
  out(clearPlan(ROOT));
} else {
  const rendered = renderChain(deriveChain(ROOT));
  process.stdout.write(`${rendered === "" ? "No chain declared. Declare one: node qa/plan.mjs --set \"step | step | …\"" : rendered}\n`);
  process.exit(0);
}
