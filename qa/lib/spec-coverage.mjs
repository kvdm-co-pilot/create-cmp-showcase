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
// An OPTIONAL tier requirement on the clause line itself:
//
//   - **MOTION-13** [tier: device] — Given a cold start, When … Then …
//
// The clause declares what it takes to OBSERVE it, which is a property of the
// promise, not of whatever test happened to cite it. Note this attaches to the
// clause line, not to `[enforced: …]` — that tags docs/ARCHITECTURE.md prose and
// is a different grammar entirely.
const CLAUSE_TIER_RE = /\[tier:\s*(device|e2e)\]/i;
/** Which citing tiers satisfy a declared requirement. */
export const TIERS_SATISFYING = Object.freeze({
  device: ["androidInstrumentedTest", "e2e"],
  e2e: ["e2e"],
});

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
      const tierMatch = line.match(CLAUSE_TIER_RE);
      clauses.set(m[2], {
        file: path.relative(root, abs),
        withdrawn: Boolean(m[1]),
        requiredTier: tierMatch ? tierMatch[1].toLowerCase() : null,
      });
    }
  }
  return clauses;
}

/**
 * Which test tier a citing file belongs to, derived from its path. Tiers are
 * the source-set/harness boundaries that decide what a citation can actually
 * SEE: commonTest and desktopTest run on the JVM (blind to androidMain code),
 * androidInstrumentedTest runs on a device, e2e flows drive the installed app.
 * @param {string} relFile path relative to the project root
 * @returns {"commonTest"|"desktopTest"|"androidInstrumentedTest"|"e2e"|"other"}
 */
export function tierForFile(relFile) {
  const p = relFile.split(path.sep).join("/");
  if (p.includes("/androidInstrumentedTest/")) return "androidInstrumentedTest";
  if (p.includes("/commonTest/")) return "commonTest";
  if (p.includes("/desktopTest/")) return "desktopTest";
  if (p.startsWith("qa/e2e/")) return "e2e";
  return "other";
}

/** Tiers whose tests run entirely on the host JVM — blind to platform source sets. */
export const DESKTOP_TIERS = Object.freeze(["commonTest", "desktopTest"]);

/**
 * Every `// SPEC: ID[, ID…]` / `# SPEC: …` citation tag under composeApp/src
 * and qa/e2e. Each entry carries the citing file's `tier` (see tierForFile).
 * @param {string} root
 * @returns {Array<{id: string, file: string, line: number, tier: string}>}
 */
export function scanCitations(root) {
  const tags = [];
  const searchDirs = [path.join(root, "composeApp/src"), path.join(root, "qa/e2e")];
  const files = searchDirs.flatMap((d) => walkFiles(d, [".kt", ".kts", ".yaml", ".yml"]));
  for (const f of files) {
    const rel = path.relative(root, f);
    const tier = tierForFile(rel);
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
        for (const id of ids) tags.push({ id, file: rel, line: i + 1, tier });
      });
  }
  return tags;
}

/**
 * Per-clause tier visibility — report data only, never a pass/fail input
 * (instrument before you police). For each cited clause: which tiers cite it.
 * `desktopOnly` lists live clauses whose every citation is desktop-tier
 * (commonTest/desktopTest) — behavior claims no device-tier evidence backs.
 * `summaryLine` is the one line the lane's specCoverage step (and any other
 * consumer) can print verbatim; null when nothing is desktop-only.
 * `unmetTier` is the PRESCRIPTIVE half — clauses that declared `[tier: …]` and
 * have no citation from a tier that could observe them. specCoverage FAILS on it:
 * "instrument before you police" was the right first move, and this is the second.
 * @param {Map<string, {file: string, withdrawn: boolean}>} clauses from scanSpecClauses
 * @param {Array<{id: string, tier: string}>} tags from scanCitations
 * @returns {{tiersByClause: Record<string, string[]>, desktopOnly: string[], summaryLine: string|null}}
 */
export function clauseTierCoverage(clauses, tags) {
  const tiersByClause = {};
  for (const t of tags) {
    (tiersByClause[t.id] ??= []).includes(t.tier) || tiersByClause[t.id].push(t.tier);
  }
  // The gate input. A clause that DECLARED the tier it needs and has no citation
  // from that tier is not covered — it is cited by tests structurally incapable
  // of observing it, which is the exact hole `desktopOnly` below could only ever
  // describe. MOTION-13 promised an animation "plays once per process start" and
  // was cited by a desktop Compose test, a tier with no process lifecycle at all:
  // the citation existed, the gate went green, and nothing ever observed the
  // promise. Declared requirements are checked; undeclared clauses are unchanged.
  const unmetTier = [...clauses.entries()]
    .filter(([, c]) => !c.withdrawn && c.requiredTier)
    .map(([id, c]) => ({ id, requiredTier: c.requiredTier, tiers: tiersByClause[id] ?? [], file: c.file }))
    .filter((u) => !(TIERS_SATISFYING[u.requiredTier] ?? []).some((t) => u.tiers.includes(t)));
  const desktopOnly = [...clauses.entries()]
    .filter(([, c]) => !c.withdrawn)
    .map(([id]) => id)
    .filter((id) => {
      const tiers = tiersByClause[id];
      return tiers && tiers.every((t) => DESKTOP_TIERS.includes(t));
    });
  const summaryLine = desktopOnly.length
    ? `${desktopOnly.length} clause${desktopOnly.length === 1 ? "" : "s"} cited only from desktop-tier tests (${desktopOnly.join(", ")})`
    : null;
  return { tiersByClause, desktopOnly, unmetTier, summaryLine };
}
