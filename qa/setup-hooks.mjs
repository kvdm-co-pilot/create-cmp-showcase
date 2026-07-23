#!/usr/bin/env node
// Enable this project's shipped git hooks — one-time, idempotent, no dependency
// and no hook-manager. Run it once after `git init`: it points git at the
// tracked .githooks/ directory (core.hooksPath) and makes the hooks executable.
//
// The pre-push hook it activates gates a push on the evidence receipt attesting
// HEAD — the same cheap check CI runs, before your code leaves the machine.

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

function main() {
  try {
    execFileSync("git", ["rev-parse", "--is-inside-work-tree"], { stdio: "ignore" });
  } catch {
    console.error("Not a git repository yet. Run `git init` first, then `node qa/setup-hooks.mjs`.");
    process.exit(1);
  }
  // core.hooksPath (git 2.9+) makes the tracked .githooks/ the hooks directory,
  // so the hooks live in the repo and survive as one source of truth.
  execFileSync("git", ["config", "core.hooksPath", ".githooks"], { stdio: "ignore" });
  try {
    fs.chmodSync(path.join(".githooks", "pre-push"), 0o755);
  } catch {
    // best-effort — on a filesystem without exec bits the hook still runs via core.hooksPath
  }
  console.log("✓ git hooks enabled (core.hooksPath = .githooks).");
  console.log("  pre-push now blocks a push whose committed receipt doesn't attest HEAD.");
  console.log("  Bypass in a pinch with `git push --no-verify`; CI still enforces the check.");
}

main();
