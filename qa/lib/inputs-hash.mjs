// Shared primitive: a content hash of the "verified surface" — every tracked
// file whose content can change the verify lane's verdict, minus the lane's
// own outputs. Both qa/verify.mjs (writes inputs.hash into the receipt) and
// qa/receipt-check.mjs (recomputes it to test validity) import this module so
// there is exactly one definition of the surface and the algorithm.
//
// See docs/adr/0005-evidence-binding-by-inputs-hash.md for the why.

import { execSync } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";

// Directories / files INCLUDED in the verified surface (relative to project ROOT).
// Principle: every tracked file whose content can change the lane's verdict.
export const VERIFIED_SURFACE = [
  "composeApp",
  "specs",
  "qa",
  "gradle/libs.versions.toml",
  "build.gradle.kts",
  "settings.gradle.kts",
  "gradle.properties",
];

// Paths EXCLUDED even though they fall under an included surface dir above —
// these are lane OUTPUTS, not inputs. Including them would make the hash
// depend on the lane's own prior output (or, for qa-artifacts, on binary
// scratch that is deliberately never committed).
const EXCLUDED_PREFIXES = ["qa/evidence", "qa-artifacts"];

function isExcluded(relPath) {
  return EXCLUDED_PREFIXES.some((prefix) => relPath === prefix || relPath.startsWith(`${prefix}/`));
}

// The verified surface is the set of files that WILL be committed: tracked files
// PLUS untracked-but-not-ignored files (`--others --exclude-standard`). A freshly
// generated feature's files are untracked when the lane runs and the receipt is
// written, yet they land in the very same commit as the receipt — so they must be
// hashed, or the committed receipt would never attest its own commit (and CI's
// receipt-matches-HEAD gate would false-fail on every change). Gitignored scratch
// (build outputs, qa-artifacts) is still excluded via --exclude-standard.
function tryGitLsFiles(root) {
  try {
    const out = execSync("git ls-files -z --cached --others --exclude-standard", { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
    return out.split("\0").filter(Boolean);
  } catch {
    return null;
  }
}

// Dependency-free recursive walk, used when git is unavailable (non-git scaffold).
function walkAllFiles(dir) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walkAllFiles(p));
    else if (entry.isFile()) out.push(p);
  }
  return out;
}

// Resolve the verified surface to a flat, sorted list of paths (relative to
// root, POSIX-style `/` separators) that currently exist on disk.
function resolveSurfaceFiles(root) {
  const gitFiles = tryGitLsFiles(root);

  if (gitFiles) {
    return gitFiles
      .map((p) => p.split(path.sep).join("/"))
      .filter((relPath) => VERIFIED_SURFACE.some((surface) => relPath === surface || relPath.startsWith(`${surface}/`)))
      .filter((relPath) => !isExcluded(relPath))
      .filter((relPath) => fs.existsSync(path.join(root, relPath)) && fs.statSync(path.join(root, relPath)).isFile());
  }

  // Fallback: no git available — walk the surface directories directly so a
  // non-git scaffold still produces a stable hash.
  const collected = [];
  for (const surface of VERIFIED_SURFACE) {
    const abs = path.join(root, surface);
    if (!fs.existsSync(abs)) continue;
    const stat = fs.statSync(abs);
    if (stat.isFile()) {
      collected.push(surface);
    } else if (stat.isDirectory()) {
      for (const file of walkAllFiles(abs)) {
        collected.push(path.relative(root, file).split(path.sep).join("/"));
      }
    }
  }
  return collected.filter((relPath) => !isExcluded(relPath));
}

/**
 * Compute the sha256 hash of the verified surface for the project rooted at `root`.
 * Deterministic: same tree (same file paths + same file bytes) → same hash.
 * @param {string} root absolute path to the project root
 * @returns {{ hash: string, fileCount: number }}
 */
export function computeInputsHash(root) {
  const files = [...new Set(resolveSurfaceFiles(root))].sort((a, b) => a.localeCompare(b));

  const overall = createHash("sha256");
  for (const relPath of files) {
    const bytes = fs.readFileSync(path.join(root, relPath));
    const fileSha = createHash("sha256").update(bytes).digest("hex");
    overall.update(`${relPath}\0${fileSha}\n`);
  }

  return { hash: overall.digest("hex"), fileCount: files.length };
}
