// spec-coverage.mjs — the clause ↔ citation scan, as a library.
//
// Extracted from qa/verify.mjs's stepSpecCoverage so there is exactly ONE
// definition of "what is a clause" and "what cites it". Two consumers:
//   - the lane's specCoverage gate (orphans in either direction FAIL)
//   - feature-brief.mjs's derived doneness (a feature is done when every live
//     clause in ITS spec is cited and the receipt attests the tree)
// If these two scanned differently, the Features view and the lane could
// disagree about the same clause — the exact two-truths problem this file
// exists to prevent.

import fs from "node:fs";
import path from "node:path";

/** `- **HOME-01** — …` (live) or `- ~~**HOME-01**~~ — …` (withdrawn). */
export const CLAUSE_LINE_RE = /^-\s+(~~)?\*\*([A-Z][A-Z0-9]*-\d{2,})\*\*/;

const TAG_LINE_RE = /^(?:\/\/|#)\s*SPEC:/;
const TAG_IDS_RE = /SPEC:\s*([A-Z0-9,\s-]+)/;
const CLAUSE_ID_RE = /^[A-Z][A-Z0-9]*-\d{2,}$/;

/** Recursive walk returning files under `dir` ending in one of `exts`. */
export function walkFiles(dir, exts) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walkFiles(p, exts));
    else if (exts.some((ext) => entry.name.endsWith(ext))) out.push(p);
  }
  return out;
}

/**
 * Every clause in every specs/*.spec.md.
 * @param {string} root
 * @returns {Map<string, {file: string, withdrawn: boolean}>} id -> where/state
 */
export function scanSpecClauses(root) {
  const clauses = new Map();
  const specsDir = path.join(root, "specs");
  if (!fs.existsSync(specsDir)) return clauses;
  for (const f of fs.readdirSync(specsDir).filter((n) => n.endsWith(".spec.md"))) {
    const abs = path.join(specsDir, f);
    for (const line of fs.readFileSync(abs, "utf8").split("\n")) {
      const m = line.match(CLAUSE_LINE_RE);
      if (!m) continue;
      clauses.set(m[2], { file: path.relative(root, abs), withdrawn: Boolean(m[1]) });
    }
  }
  return clauses;
}

/**
 * Every `// SPEC: ID[, ID…]` / `# SPEC: …` citation tag under composeApp/src
 * and qa/e2e.
 * @param {string} root
 * @returns {Array<{id: string, file: string, line: number}>}
 */
export function scanCitations(root) {
  const tags = [];
  const searchDirs = [path.join(root, "composeApp/src"), path.join(root, "qa/e2e")];
  const files = searchDirs.flatMap((d) => walkFiles(d, [".kt", ".kts", ".yaml", ".yml"]));
  for (const f of files) {
    fs.readFileSync(f, "utf8")
      .split("\n")
      .forEach((line, i) => {
        const trimmed = line.trim();
        if (!TAG_LINE_RE.test(trimmed)) return;
        const m = trimmed.match(TAG_IDS_RE);
        if (!m) return;
        const ids = m[1]
          .split(/[,\s]+/)
          .map((s) => s.trim())
          .filter((s) => CLAUSE_ID_RE.test(s));
        for (const id of ids) tags.push({ id, file: path.relative(root, f), line: i + 1 });
      });
  }
  return tags;
}
