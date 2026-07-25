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
//   { "touches": ["components", "design-system"] }
//   ```
//
// `touches` is the declared blast radius: the governed artifacts this feature
// expects to invalidate. The artifact hashes already enforce; declaring lets
// the console tell "re-approval, as planned" apart from undeclared blast.
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
 * A brief's declared blast radius. A missing block, or one without `touches`,
 * declares nothing — legal and common. A block that IS present but malformed
 * is surfaced as `error`: a doc that tried to declare and failed should say
 * so, not read as "declares nothing".
 * @param {string} markdown
 * @returns {{touches: string[], error: (string|null)}}
 */
export function parseFeatureBlock(markdown) {
  const m = typeof markdown === "string" ? markdown.match(FEATURE_FENCE_RE) : null;
  if (!m) return { touches: [], error: null };
  let parsed;
  try {
    parsed = JSON.parse(m[1]);
  } catch (err) {
    return { touches: [], error: `cmp:feature block is not valid JSON — ${err.message}` };
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return { touches: [], error: "cmp:feature must be a JSON object" };
  }
  const touches = Array.isArray(parsed.touches)
    ? parsed.touches.filter((t) => typeof t === "string" && t.trim() !== "")
    : [];
  return { touches, error: null };
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
  const block = readable ? parseFeatureBlock(markdown) : { touches: [], error: `${brief.rel} could not be read` };

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
    blockError: block.error,
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
