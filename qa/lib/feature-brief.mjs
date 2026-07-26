// feature-brief.mjs — feature briefs and DERIVED doneness.
//
// A feature brief is `docs/features/<name>.md`: the why of a feature — the
// decisions with their rationale ("the day boundary is a configurable
// dayStartHour, default 04:00 — not midnight, because…"), the research, the
// rejected options. Location is the governance opt-in: every doc in
// docs/features/ is a governed `feature-brief:<name>` artifact, hashed and
// signed like anything else, approved BEFORE the feature is built. (Harness
// design standards stay in docs/proposals/ — different directory, different
// meaning.) `<name>` matches the feature's spec: docs/features/meal.md pairs
// with specs/meal.spec.md.
//
// The brief carries at most ONE machine-read block, and it declares — it never
// gates:
//
//   ```json cmp:feature
//   { "touches": ["components", "design-system"], "screens": true }
//   ```
//
// `touches` is the declared blast radius: the governed artifacts this feature
// expects to invalidate. The artifact hashes already enforce; declaring lets
// the console tell "re-approval, as planned" apart from undeclared blast.
// `screens` declares a UI surface: this feature will have its own screens, so
// the walk holds a design gate (feature-design:<name> — signed on RENDERED
// output) between the brief and the behavior contract, BEFORE any screen file
// exists. Like touches it declares, never gates: once presentation/<name>/
// screen files exist on disk, the gate derives from them regardless.
//
// DONENESS IS DERIVED, NEVER CLAIMED. This file's earlier incarnation
// (intent-checks.mjs) let the agent assert delivery over its own grep checks —
// a weaker parallel truth beside the strong one the harness already maintains:
// clause ↔ citing test ↔ lane gate ↔ receipt. That mechanism is gone. A
// feature is provably done when, mechanically:
//
//   1. its spec has live clauses, and every one is cited by a test
//      (spec-coverage.mjs — the same scan the lane's specCoverage gate runs),
//   2. the latest receipt's verdict is PASS, and
//   3. the receipt's inputs.hash matches a recompute of the tree RIGHT NOW —
//      evidence must attest execution of *this* code, not some earlier tree.
//
// No new lane step needed: specCoverage already fails uncovered clauses and
// the test steps already fail broken promises. What remains for humans is
// judgment, not verification: approving the brief (before code) and accepting
// the feature (after proof) — acceptFeature in approvals.mjs refuses until
// provenDone is true.

import fs from "node:fs";
import path from "node:path";

import { computeInputsHash } from "./inputs-hash.mjs";
import { CLAUSE_LINE_RE, scanCitations } from "./spec-coverage.mjs";

export const FEATURES_DIR_REL = "docs/features";

/** The declaration block's info string — ```json cmp:feature */
const FEATURE_FENCE_RE = /```json\s+cmp:feature\s*\n([\s\S]*?)\n```/;

/**
 * The brief with its cmp:feature declaration block removed — the basis the
 * feature-brief approval hash is computed over (approvals.mjs). The human signs
 * the brief's REASONING; the block is machine-read declaration whose claims the
 * harness independently enforces (artifact hashes enforce `touches`; disk
 * presence enforces the design gate), so editing it must never invalidate a
 * signature — the same stance as `architecture`'s cmp:generated stripping.
 * @param {string} markdown
 * @returns {string}
 */
export function stripFeatureBlock(markdown) {
  if (typeof markdown !== "string") return "";
  // Consume the blank space around the block and leave one paragraph break, and
  // normalize the trailing edge — so adding, editing, or removing the block
  // (typically the doc's last element) yields the same basis as never having
  // one. The fence grammar itself stays FEATURE_FENCE_RE — one definition,
  // shared with parseFeatureBlock.
  const stripped = markdown.replace(new RegExp(String.raw`\s*` + FEATURE_FENCE_RE.source + String.raw`\s*`), "\n\n");
  return stripped.trim() === "" ? "" : stripped.replace(/\s+$/, "\n");
}

/**
 * Every feature brief — docs/features/*.md, sorted (code-unit sort: artifact
 * ids derive from this list and must read identically on every machine).
 * @param {string} root
 * @returns {Array<{name: string, rel: string}>}
 */
export function listFeatureBriefs(root) {
  const dir = path.join(root, FEATURES_DIR_REL);
  let names;
  try {
    names = fs.readdirSync(dir);
  } catch {
    return [];
  }
  return names
    .filter((f) => f.endsWith(".md") && f !== "README.md")
    .sort()
    .map((f) => ({ name: f.slice(0, -".md".length), rel: `${FEATURES_DIR_REL}/${f}` }));
}

/**
 * The brief's prose, split on `## ` headings — the SUBSTANCE the human signs.
 * The console renders these on the feature card (the decisions section inline,
 * the rest collapsible): an approval moment must show what is being approved,
 * never just a status shell. Fenced code blocks are kept verbatim inside
 * their section (the cmp:feature block included — it is part of the signed
 * bytes and the reader may want to see it).
 * @param {string} markdown
 * @returns {Array<{heading: string, body: string}>}
 */
export function briefSections(markdown) {
  if (typeof markdown !== "string" || markdown.trim() === "") return [];
  const out = [];
  let current = null;
  let inFence = false;
  for (const line of markdown.split("\n")) {
    if (/^```/.test(line.trim())) inFence = !inFence;
    const m = !inFence && line.match(/^##\s+(.+)$/);
    if (m) {
      if (current) out.push({ heading: current.heading, body: current.lines.join("\n").trim() });
      current = { heading: m[1].trim(), lines: [] };
    } else if (current) {
      current.lines.push(line);
    }
  }
  if (current) out.push({ heading: current.heading, body: current.lines.join("\n").trim() });
  return out;
}

/**
 * A brief's declarations: blast radius (`touches`), UI surface (`screens`),
 * and the reachability exemption (`unrouted` — FI-7's escape hatch: a screen
 * intentionally not wired into the navigation graph yet). A missing block, or
 * one without a field, declares nothing — legal and common. A block that IS
 * present but malformed is surfaced as `error`: a doc that tried to declare
 * and failed should say so, not read as "declares nothing".
 * @param {string} markdown
 * @returns {{touches: string[], screens: boolean, unrouted: boolean, error: (string|null)}}
 */
export function parseFeatureBlock(markdown) {
  const m = typeof markdown === "string" ? markdown.match(FEATURE_FENCE_RE) : null;
  if (!m) return { touches: [], screens: false, unrouted: false, error: null };
  let parsed;
  try {
    parsed = JSON.parse(m[1]);
  } catch (err) {
    return { touches: [], screens: false, unrouted: false, error: `cmp:feature block is not valid JSON — ${err.message}` };
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return { touches: [], screens: false, unrouted: false, error: "cmp:feature must be a JSON object" };
  }
  const touches = Array.isArray(parsed.touches)
    ? parsed.touches.filter((t) => typeof t === "string" && t.trim() !== "")
    : [];
  return { touches, screens: parsed.screens === true, unrouted: parsed.unrouted === true, error: null };
}

/**
 * Parse one spec file's clauses in document order.
 * @param {string} root
 * @param {string} specRel e.g. "specs/meal.spec.md"
 * @returns {Array<{id: string, withdrawn: boolean}>}
 */
function clausesOfSpec(root, specRel) {
  let text;
  try {
    text = fs.readFileSync(path.join(root, specRel), "utf8");
  } catch {
    return [];
  }
  const out = [];
  for (const line of text.split("\n")) {
    const m = line.match(CLAUSE_LINE_RE);
    if (m) out.push({ id: m[2], withdrawn: Boolean(m[1]) });
  }
  return out;
}

/**
 * The latest receipt, reduced to what doneness needs: verdict, and whether its
 * inputs.hash attests the tree AS IT STANDS (same cheap recompute the Stop
 * hook and pre-push gate use). Absent/unparsable receipt -> present:false —
 * never treated as PASS.
 * @param {string} root
 * @returns {{present: boolean, verdict: (string|null), attestsTree: boolean}}
 */
export function receiptAttestation(root) {
  let receipt;
  try {
    receipt = JSON.parse(fs.readFileSync(path.join(root, "qa/evidence/latest.json"), "utf8"));
  } catch {
    return { present: false, verdict: null, attestsTree: false };
  }
  const recorded = receipt?.inputs?.hash;
  let attestsTree = false;
  if (typeof recorded === "string" && recorded !== "") {
    try {
      attestsTree = computeInputsHash(root).hash === recorded;
    } catch {
      attestsTree = false;
    }
  }
  return { present: true, verdict: receipt?.verdict ?? null, attestsTree };
}

/**
 * One feature's full derived state — brief, spec, coverage, receipt, verdict.
 *
 * `provenDone` is strict on purpose; each conjunct closes a specific hole:
 *   - `total > 0`: a spec with no live clauses proves nothing (the vacuous-
 *     approval stance, applied to doneness)
 *   - `covered === total`: every promise has a citing test (specCoverage's own
 *     definition, via the same scan)
 *   - `verdict === "PASS"`: the citing tests actually ran green
 *   - `attestsTree`: they ran green against THIS tree, not an earlier one
 *
 * @param {string} root
 * @param {{name: string, rel: string}} brief
 * @param {{citations?: Array<object>, receipt?: object}} [pre] precomputed
 *   shared scans (callers resolving many features pass these once)
 * @returns {object}
 */
export function deriveFeatureStatus(root, brief, pre = {}) {
  let markdown = "";
  let readable = true;
  try {
    markdown = fs.readFileSync(path.join(root, brief.rel), "utf8");
  } catch {
    readable = false;
  }
  const block = readable ? parseFeatureBlock(markdown) : { touches: [], screens: false, error: `${brief.rel} could not be read` };

  const specRel = `specs/${brief.name}.spec.md`;
  const specExists = fs.existsSync(path.join(root, specRel));
  const citedIds = new Set((pre.citations ?? scanCitations(root)).map((t) => t.id));
  const clauses = clausesOfSpec(root, specRel).map((c) => ({ ...c, cited: citedIds.has(c.id) }));
  const live = clauses.filter((c) => !c.withdrawn);
  const covered = live.filter((c) => c.cited).length;

  const receipt = pre.receipt ?? receiptAttestation(root);
  const provenDone = live.length > 0 && covered === live.length && receipt.verdict === "PASS" && receipt.attestsTree;

  return {
    name: brief.name,
    rel: brief.rel,
    touches: block.touches,
    screens: block.screens,
    blockError: block.error,
    // The signed substance, for surfaces that show WHAT is being approved.
    sections: readable ? briefSections(markdown) : [],
    specRel,
    specExists,
    clauses,
    covered,
    total: live.length,
    receipt,
    provenDone,
    // The one-line honest explanation of why it is / isn't done — the console
    // and --status print this instead of re-deriving their own wording.
    doneReason: provenDone
      ? `${covered}/${live.length} clauses cited · receipt PASS · attests this tree`
      : !specExists
        ? `no spec yet (${specRel}) — behavior starts as clauses there`
        : live.length === 0
          ? `${specRel} has no live clauses — nothing is promised yet`
          : covered < live.length
            ? `${covered}/${live.length} clauses cited — ${live.length - covered} promise(s) have no citing test`
            : !receipt.present
              ? "all clauses cited, but no receipt — run node qa/verify.mjs"
              : receipt.verdict !== "PASS"
                ? `all clauses cited, but the latest receipt is ${receipt.verdict}`
                : "all clauses cited and receipt PASS, but it attests an older tree — re-run node qa/verify.mjs",
  };
}

/**
 * Every feature, derived — the one call the CLI status surface and the console
 * section share. Shared scans (citations, receipt) run once.
 * @param {string} root
 * @returns {Array<ReturnType<typeof deriveFeatureStatus>>}
 */
export function deriveAllFeatures(root) {
  const briefs = listFeatureBriefs(root);
  if (briefs.length === 0) return [];
  const pre = { citations: scanCitations(root), receipt: receiptAttestation(root) };
  return briefs.map((b) => deriveFeatureStatus(root, b, pre));
}
