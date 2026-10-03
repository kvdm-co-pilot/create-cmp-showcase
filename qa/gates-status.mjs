#!/usr/bin/env node
// gates-status — which of this project's gates are actually active, derived, in one line.
//
//   node qa/gates-status.mjs --line
//
// The SessionStart hook runs this so a session opens knowing what will refuse it, read
// from the tree rather than asserted by prose: the Stop hook as .claude/settings.json
// wires it, the pre-push hook as `git config core.hooksPath` sets it, and the CI Verify
// workflow as .github/workflows/ carries it. Whether GitHub REQUIRES that check is a
// setting on the server, not in the tree, so it is "unknown locally" unless an
// authenticated `gh` answers for the default branch's rulesets — and an unanswered
// question is printed as unknown, never as a pass.
//
// A hook cannot report that hooks are off: when Claude Code loads none (disabled, a
// cloud session, a managed policy), this line is simply absent, and its absence is the
// tell. Plain stdout from a SessionStart hook is added to the session's context.

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

/** A required status check that is this project's Verify workflow: the workflow name, or its job id. */
const VERIFY_CHECK = /^(Verify\b|android$)/;

/**
 * The line, from facts already gathered. Pure: no filesystem, no process.
 * @param {object} facts
 * @param {boolean} facts.stopHook   a Stop hook in .claude/settings.json runs receipt-check
 * @param {boolean} facts.prePush    `git config core.hooksPath` is `.githooks`
 * @param {boolean} facts.workflow   .github/workflows/verify.yml exists
 * @param {"yes"|"no"|"unknown"} facts.required  a ruleset on the default branch requires Verify
 * @returns {string}
 */
export function gatesLine({ stopHook, prePush, workflow, required }) {
  const stop = stopHook ? "Stop (receipt-check)" : "Stop (not wired)";
  const push = `pre-push (.githooks: ${prePush ? "on" : "off"})`;
  let ci;
  if (!workflow) ci = "CI Verify (no workflow)";
  else if (required === "yes") ci = "CI Verify (workflow present; required: yes, by a ruleset)";
  else if (required === "no") ci = "CI Verify (workflow present; required: no ruleset requires it)";
  else ci = "CI Verify (workflow present; required: unknown locally)";
  return `Gates active: ${stop} · ${push} · ${ci}`;
}

/** Does any Stop hook in this parsed settings object run qa/receipt-check.mjs? */
export function stopHookWired(settings) {
  const groups = settings?.hooks?.Stop;
  if (!Array.isArray(groups)) return false;
  return groups.some(
    (g) => Array.isArray(g?.hooks) && g.hooks.some((h) => String(h?.command ?? "").includes("qa/receipt-check.mjs"))
  );
}

/**
 * yes/no/unknown from the body of `gh api repos/{owner}/{repo}/rules/branches/<branch>`
 * (the rules active on that branch, rulesets included). Anything unreadable is unknown.
 * @param {unknown} rules parsed JSON, or null when gh did not answer
 * @returns {"yes"|"no"|"unknown"}
 */
export function requiredFromRules(rules) {
  if (!Array.isArray(rules)) return "unknown";
  const checks = rules
    .filter((r) => r?.type === "required_status_checks")
    .flatMap((r) => r?.parameters?.required_status_checks ?? [])
    .map((c) => String(c?.context ?? ""));
  return checks.some((c) => VERIFY_CHECK.test(c)) ? "yes" : "no";
}

function run(cmd, args, timeout) {
  try {
    return execFileSync(cmd, args, { cwd: ROOT, encoding: "utf8", timeout, stdio: ["ignore", "pipe", "ignore"] }).trim();
  } catch {
    return null;
  }
}

function readSettings() {
  try {
    return JSON.parse(fs.readFileSync(path.join(ROOT, ".claude", "settings.json"), "utf8"));
  } catch {
    return null;
  }
}

/** Ask GitHub, briefly. No gh, no auth, no remote, no answer in time: unknown. */
function requiredOnServer() {
  const branch = run("gh", ["api", "repos/{owner}/{repo}", "--jq", ".default_branch"], 4000);
  if (!branch) return "unknown";
  const body = run("gh", ["api", `repos/{owner}/{repo}/rules/branches/${encodeURIComponent(branch)}`], 4000);
  if (body === null) return "unknown";
  try {
    return requiredFromRules(JSON.parse(body));
  } catch {
    return "unknown";
  }
}

export function gatherFacts() {
  const workflow = fs.existsSync(path.join(ROOT, ".github", "workflows", "verify.yml"));
  return {
    stopHook: stopHookWired(readSettings()),
    prePush: run("git", ["config", "core.hooksPath"], 3000) === ".githooks",
    workflow,
    required: workflow ? requiredOnServer() : "unknown",
  };
}

// Importing this module does nothing but define the exports above: doctor --adherence imports
// stopHookWired and requiredFromRules from here. So the guard itself touches no path it was not
// run as. Under `node -e`, argv[1] is the script's first ARGUMENT, any path at all — and Node's JS
// `realpathSync` never returns on a link like `linked/../settings.json` (the heal-write test's
// corpus). A basename check first, and the system's realpath (`.native`) after it.
const isMain = (() => {
  try {
    const self = fileURLToPath(import.meta.url);
    if (!process.argv[1] || path.basename(process.argv[1]) !== path.basename(self)) return false;
    return fs.realpathSync.native(path.resolve(process.argv[1])) === fs.realpathSync.native(self);
  } catch {
    return false;
  }
})();

if (isMain) {
  if (process.argv.includes("--line")) {
    process.stdout.write(`${gatesLine(gatherFacts())}\n`);
  } else {
    console.error("usage: node qa/gates-status.mjs --line");
    process.exit(1);
  }
}
