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
//
// THE MECHANIC IS THE CORE'S; THE MODEL IS THE PROFILE'S (Stage 0 PR 4,
// docs/NORTH-STAR.md §6). This file knows what a clause is, how a citation
// binds to a test, that coverage runs both ways, and that a clause tagged
// with a tier must be cited from a tier that can observe it. It does NOT know
// where specs live, which trees hold citations, what a source file is called,
// or what the tiers are: those come in as a `model` (qa/lib/spec-model.mjs),
// built from the profile's `layout` and `tiers` declarations. Every scanner
// below takes the model as its last argument; a caller that has only a root
// gets it resolved from the project's manifest. Nothing here names a stack.

import fs from "node:fs";
import path from "node:path";

import { TIER_NAME_RE, requireSpecModel, GRAMMAR_DEFAULTS } from "./spec-model.mjs";

/** `- **HOME-01** — …` (live) or `- ~~**HOME-01**~~ — …` (withdrawn). */
export const CLAUSE_LINE_RE = /^-\s+(~~)?\*\*([A-Z][A-Z0-9]*-\d{2,})\*\*/;
// An OPTIONAL tier requirement on the clause line itself:
//
//   - **MOTION-13** [tier: device] — Given a cold start, When … Then …
//
// The clause declares what it takes to OBSERVE it, which is a property of the
// promise, not of whatever test happened to cite it. The requirement NAME is
// the profile's (its `tiers.satisfying` keys); the grammar accepts any name
// and the coverage check refuses one the profile does not declare. Note this
// attaches to the clause line, not to `[enforced: …]` — that tags
// docs/ARCHITECTURE.md prose and is a different grammar entirely.
const CLAUSE_TIER_RE = /\[tier:\s*([a-z][a-z0-9-]*)\]/i;

// Kept as the module-local fallback; the live values come from the SpecModel.

// A citation is a claim that a TEST covers a clause, so it has to sit on one.
// Counting the tag wherever it appears makes a red specCoverage curable with a
// comment and zero assertions — the one escape this gate exists to close. It is
// not hypothetical: payment-blueprint hit a citation that had drifted onto a
// class declaration, where it counted for the whole file while testing nothing.
//
// So a tag counts only when a test declaration follows it within
// BINDING_WINDOW non-blank lines. The window is small enough that the tag must
// be attached to the test, and loose enough for the @DisplayName / annotation
// stack that idiomatically sits between them.
export const BINDING_WINDOW = GRAMMAR_DEFAULTS.bindingWindow;

// Kotlin @Test, a backticked test function, and the node:test / Maestro-adjacent
// `test(` / `it(` call forms. Deliberately syntactic: a citation's binding must
// be readable without compiling anything.

// A tag whose first meaningful line declares a TYPE is documenting that type,
// not claiming a test — and it must be refused structurally rather than by
// distance, because a short class body puts a real @Test inside the window and
// would otherwise launder the citation. This is exactly payment-blueprint's
// drift: `// SPEC: PP-07` sat on `class PaymentWorkerTest`, three properties
// above a genuine @Test, and vouched for the whole file.

/**
 * The flow-shaped citation files the lane executes: top-level files in the
 * model's flow directory with one of its extensions, sorted, root-relative.
 * ONE list serves two readers — the profile's flow-running step (what runs)
 * and scanCitations (what may count as coverage) — so a citation can only
 * ever come from a flow that executes. Before 2026-09-03 the step ran ONE
 * file by name (smoke.yaml) while the scan walked the whole directory: four
 * hand-written flows on the showcase satisfied clauses without ever running.
 * A profile with no flows (`layout.flows: null`) has none.
 * @param {string} root
 * @param {import("./spec-model.mjs").SpecModel} [model]
 * @returns {string[]}
 */
export function listFlowFiles(root, model = requireSpecModel(root)) {
  if (!model.flows) return [];
  const dir = path.join(root, ...model.flows.dir.split("/"));
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir, { withFileTypes: true })
    .filter((e) => e.isFile() && model.flows.exts.some((ext) => e.name.endsWith(ext)))
    .map((e) => `${model.flows.dir}/${e.name}`)
    .sort();
}

/**
 * Does a test declaration follow `index` within BINDING_WINDOW non-blank lines,
 * skipping block-comment bodies (a tag inside one is documentation, not a claim)?
 * @param {string[]} lines
 * @param {number} index line the tag sits on
 * @returns {boolean}
 */
export function citationIsBound(lines, index, grammar) {
  // The grammar is the PROFILE's. There is no fallback to reach for: a caller
  // without one is a caller about to grade a language it cannot read.
  if (!grammar || !(grammar.testDeclaration instanceof RegExp) || !(grammar.lineComment instanceof RegExp)) {
    throw new Error("citationIsBound needs the profile's grammar (testDeclaration, lineComment) — the core has no fallback grammar");
  }
  // THE GRAMMAR IS THE PROFILE'S. These three patterns decide whether a
  // citation counts at all, and they are the most language-specific thing in
  // the lane — far more so than a directory name. Held in the spine they
  // matched Kotlin and JavaScript and silently discarded every Python, Go and
  // Rust citation, which reported as "declared but never cited" and pointed the
  // reader at the spec file. Passing them in is what makes that a declaration
  // the adopter can see and fix rather than a rule they must reverse-engineer.
  const TEST_DECL = grammar.testDeclaration;
  // No type declaration declared: no citation is ever judged "on a type" — never-match, not Kotlin's.
  const TYPE_DECL = grammar.typeDeclaration instanceof RegExp ? grammar.typeDeclaration : /(?!)/;
  const WINDOW = grammar.bindingWindow ?? GRAMMAR_DEFAULTS.bindingWindow;
  const LINE_COMMENT = grammar.lineComment;
  const BLOCK = grammar.blockComment ?? GRAMMAR_DEFAULTS.blockComment;
  let seen = 0;
  let inBlockComment = false;
  for (let i = index + 1; i < lines.length && seen < WINDOW; i += 1) {
    const line = lines[i].trim();
    if (line === "") continue;
    if (inBlockComment) {
      if (BLOCK && line.includes(BLOCK.close)) inBlockComment = false;
      continue;
    }
    if (BLOCK && line.startsWith(BLOCK.open)) {
      // A same-delimiter block (Python's """) opens and closes with the same
      // token, so a single line carrying it twice is a complete block.
      const closes = BLOCK.open === BLOCK.close ? line.split(BLOCK.close).length - 1 >= 2 : line.includes(BLOCK.close);
      if (!closes) inBlockComment = true;
      continue;
    }
    // A comment line is SKIPPED, not counted. Burning the binding window on
    // prose is how a Python citation with five "#" lines under it was discarded
    // while the byte-identical Kotlin arrangement bound — the window is meant to
    // measure distance from the TEST, not from the documentation.
    if (LINE_COMMENT.test(line)) continue;
    seen += 1;
    // The FIRST meaningful line decides whether this tag is on a test at all.
    if (seen === 1 && TYPE_DECL.test(line)) return false;
    if (TEST_DECL.test(line)) return true;
  }
  return false;
}

/** Is this tag inside a block comment that began earlier in the file? */
function insideBlockComment(lines, index, grammar) {
  // A language with no block comments (declared null) has nothing to be inside of.
  if (!grammar || !grammar.blockComment) return false;
  // A citation inside a block comment is documentation, not a claim, and must
  // never bind. This scanned for `/*` and `*/` only, so a `# SPEC:` sitting in
  // a Python docstring — or a Ruby =begin block — counted as a real citation
  // over whatever test happened to follow. That is the laundering hole the
  // whole binder exists to close, open for every language outside the C family.
  const { open: OPEN, close: CLOSE } = grammar.blockComment;
  // Same-delimiter blocks (Python's triple quote) have no open/close pair to
  // match, so parity is the only honest reading: an odd count before this line
  // means we are inside one.
  if (OPEN === CLOSE) {
    let count = 0;
    for (let i = 0; i < index; i += 1) count += lines[i].split(OPEN).length - 1;
    return count % 2 === 1;
  }
  let open = false;
  for (let i = 0; i < index; i += 1) {
    const line = lines[i];
    let c = 0;
    while (c < line.length) {
      if (!open && line.startsWith(OPEN, c)) {
        open = true;
        c += OPEN.length;
      } else if (open && line.startsWith(CLOSE, c)) {
        open = false;
        c += CLOSE.length;
      } else c += 1;
    }
  }
  return open;
}
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
 * Every clause in every `*.spec.md` under the model's specs directory.
 * @param {string} root
 * @param {import("./spec-model.mjs").SpecModel} [model]
 * @returns {Map<string, {file: string, withdrawn: boolean, requiredTier: string|null}>} id -> where/state
 */
export function scanSpecClauses(root, model = requireSpecModel(root)) {
  const clauses = new Map();
  const specsDir = path.join(root, ...model.specsDir.split("/"));
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
 * Every `// SPEC: ID[, ID…]` / `# SPEC: …` citation tag under the model's
 * citation roots (source files by extension) plus the flows the lane runs.
 * Each entry carries the citing file's `tier` (the profile's `tiers.forFile`).
 * @param {string} root
 * @param {import("./spec-model.mjs").SpecModel} [model]
 * @returns {Array<{id: string, file: string, line: number, tier: string}>}
 */
export function scanCitations(root, model = requireSpecModel(root)) {
  const tags = [];
  const MARKER = model.grammar.citationMarker; // present — specDeclarationProblems refused the profile otherwise
  // How many markers were SEEN, before binding threw any away. The gap between
  // this and tags.length is the single most useful diagnostic the scan has: all
  // markers found and none bound means the grammar does not match this
  // language, which is otherwise indistinguishable from a project that wrote no
  // citations at all. `citationScanDiagnostic` turns it into a sentence.
  let markerCount = 0;
  // Sources anywhere under the citation roots; flows ONLY from the list the
  // lane runs (listFlowFiles) — a flow in a subfolder, or a flow-shaped file
  // under a source root, is not executed and therefore proves nothing.
  const flowExts = model.flows ? model.flows.exts : [];
  const sources = model.citationRoots.flatMap((rel) => walkFiles(path.join(root, ...rel.split("/")), model.citationExts));
  const files = [...sources, ...listFlowFiles(root, model).map((rel) => path.join(root, ...rel.split("/")))];
  const seen = new Set();
  for (const f of files) {
    if (seen.has(f)) continue;
    seen.add(f);
    const rel = path.relative(root, f);
    const tier = model.tiers.forFile(rel);
    fs.readFileSync(f, "utf8")
      .split("\n")
      .forEach((line, i, lines) => {
        const trimmed = line.trim();
        if (!MARKER.test(trimmed)) return;
        markerCount += 1;
        const m = trimmed.match(TAG_IDS_RE);
        if (!m) return;
        // A flow file IS its test; anything else must have a test under the tag.
        const isFlow = flowExts.some((ext) => rel.endsWith(ext));
        if (!isFlow && (insideBlockComment(lines, i, model.grammar) || !citationIsBound(lines, i, model.grammar))) return;
        const ids = m[1]
          .split(/[,\s]+/)
          .map((s) => s.trim())
          .filter((s) => CLAUSE_ID_RE.test(s));
        for (const id of ids) tags.push({ id, file: rel, line: i + 1, tier });
      });
  }
  Object.defineProperty(tags, "markersSeen", { value: markerCount, enumerable: false });
  return tags;
}

/**
 * Per-clause tier visibility — report data AND the one prescriptive check.
 * For each cited clause: which tiers cite it.
 * `hostOnly` lists live clauses whose every citation is from a host-only tier
 * (the profile's `tiers.hostOnly`) — behaviour claims no on-target evidence
 * backs. Report only, never a pass/fail input for UNDECLARED clauses
 * (instrument before you police). `summaryLine` is the one line the lane's
 * specCoverage step (and any other consumer) can print verbatim; null when
 * nothing is host-only.
 * `unmetTier` is the PRESCRIPTIVE half — clauses that declared `[tier: …]`
 * and have no citation from a tier that could observe them, OR that named a
 * requirement the profile does not declare (`unknown: true`, so the message
 * can say which names exist). specCoverage FAILS on it: "instrument before
 * you police" was the right first move, and this is the second.
 * @param {Map<string, {file: string, withdrawn: boolean, requiredTier: string|null}>} clauses from scanSpecClauses
 * @param {Array<{id: string, tier: string}>} tags from scanCitations
 * @param {import("./spec-model.mjs").SpecModel} model
 * @returns {{tiersByClause: Record<string, string[]>, hostOnly: string[], unmetTier: Array<{id: string, requiredTier: string, tiers: string[], file: string, unknown: boolean}>, summaryLine: string|null}}
 */
export function clauseTierCoverage(clauses, tags, model) {
  if (!model || !model.tiers) throw new Error("clauseTierCoverage needs the profile's spec model (qa/lib/spec-model.mjs)");
  const { satisfying, hostOnly: hostTiers } = model.tiers;
  const tiersByClause = {};
  for (const t of tags) {
    (tiersByClause[t.id] ??= []).includes(t.tier) || tiersByClause[t.id].push(t.tier);
  }
  // The gate input. A clause that DECLARED the tier it needs and has no citation
  // from that tier is not covered — it is cited by tests structurally incapable
  // of observing it, which is the exact hole `hostOnly` below could only ever
  // describe. MOTION-13 promised an animation "plays once per process start" and
  // was cited by a desktop Compose test, a tier with no process lifecycle at all:
  // the citation existed, the gate went green, and nothing ever observed the
  // promise. Declared requirements are checked; undeclared clauses are unchanged.
  const unmetTier = [...clauses.entries()]
    .filter(([, c]) => !c.withdrawn && c.requiredTier)
    .map(([id, c]) => {
      const known = TIER_NAME_RE.test(c.requiredTier) && Object.hasOwn(satisfying, c.requiredTier);
      return { id, requiredTier: c.requiredTier, tiers: tiersByClause[id] ?? [], file: c.file, unknown: !known };
    })
    .filter((u) => u.unknown || !satisfying[u.requiredTier].some((t) => u.tiers.includes(t)));
  const hostOnly = [...clauses.entries()]
    .filter(([, c]) => !c.withdrawn)
    .map(([id]) => id)
    .filter((id) => {
      const tiers = tiersByClause[id];
      return tiers && tiers.every((t) => hostTiers.includes(t));
    });
  const summaryLine = hostOnly.length
    ? `${hostOnly.length} clause${hostOnly.length === 1 ? "" : "s"} cited only from host-only tiers (${hostOnly.join(", ")})`
    : null;
  return { tiersByClause, hostOnly, unmetTier, summaryLine };
}

/**
 * Why a scan found no citations — the sentence that would have saved six
 * minutes of reverse-engineering on the first Python adoption.
 *
 * "Declared but never cited" is the right message when a project wrote no
 * citations. It is a badly WRONG message when the project wrote twenty and the
 * grammar could not bind one of them, because it points the reader at the spec
 * file, which is the one place that is not the problem. The gap between markers
 * seen and citations kept is what tells those two cases apart, and nothing was
 * reporting it.
 *
 * @param {{length: number, markersSeen?: number}} tags the result of scanCitations
 * @param {import("./spec-model.mjs").SpecModel} model
 * @returns {string|null} a sentence to append to a coverage failure, or null
 */
export function citationScanDiagnostic(tags, model) {
  const seen = Number(tags?.markersSeen ?? 0);
  const kept = tags?.length ?? 0;
  if (seen === 0 || kept > 0) return null;
  const how = "this profile's `grammar.testDeclaration` matched no line after a citation";
  return (
    `${seen} SPEC marker${seen === 1 ? "" : "s"} found and none bound to a test — ${how}. ` +
    "A citation counts only when a test declaration follows it within " +
    `${model?.grammar?.bindingWindow ?? 5} non-blank lines. Declare \`grammar.testDeclaration\` ` +
    "in your profile with the pattern this language uses for a test declaration — `create-cmp harness init` seeds one per language."
  );
}
