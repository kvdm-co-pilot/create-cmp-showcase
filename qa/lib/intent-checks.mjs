// intent-checks.mjs — feature briefs: the governed doc a feature is built FROM,
// and the checks that prove the tree carries what the brief promised.
//
// Why this exists: the genesis walk governs intent at BIRTH (specs/intent.md —
// why this app exists, approved once). Nothing governed the intent of a feature
// added later. The research, the options, and above all the DECISIONS ("the day
// boundary is configurable, default 04:00 — not midnight") lived in chat or in
// an unhashed doc; six months on the reason is gone and the next contributor
// "simplifies" the decision away.
//
// A feature brief is `docs/proposals/<name>.md` carrying ONE fenced block:
//
//   ```json cmp:intent-checks
//   {
//     "touches": ["components", "design-system"],
//     "checks": [
//       { "id": "day-boundary", "kind": "pattern",
//         "file": "composeApp/src/commonMain/kotlin/.../ProfileEntity.kt",
//         "pattern": "dayStartHour" },
//       { "id": "clauses", "kind": "spec-clauses",
//         "file": "specs/meal.spec.md", "clauses": ["MEAL-01", "MEAL-02"] }
//     ]
//   }
//   ```
//
// THE BLOCK IS THE OPT-IN: a doc under docs/proposals/ with no block is an
// ordinary document (a draft, a deep-dive, research notes) and is never
// governed. Adding the block is what turns prose into a feature brief — it
// becomes a `feature-intent:<name>` artifact in the approvals registry, hashed
// and signed like everything else.
//
// `touches` is the brief's DECLARED blast radius — the governed artifacts this
// feature expects to invalidate (components it adds, tokens it changes). It is
// declaration, not enforcement: the artifact hashes already enforce; declaring
// lets the console tell "re-approval, as planned" apart from "undeclared blast".
//
// Delivery state is NOT in this file's block — it lives in qa/approvals.json
// (deliverFeature/acceptFeature in approvals.mjs). Deliberately: the brief is
// approved BEFORE code exists, and its bytes are hash-bound from that moment.
// If "delivered" lived in the doc, flipping it would break the hash and the
// claim of done would register as drift. Ledger state changes; signed prose
// does not. The lifecycle:
//
//   proposed  — the doc + block exist; checks are INFORMATIONAL (0/7 is honest)
//   approved  — a human signed the brief; still informational (code comes next)
//   delivered — the AGENT claims the feature is done; checks ARM — every one
//               must pass or the lane FAILs, naming what's missing
//   accepted  — the human confirms delivery; the card closes
//
// So "I finished the meal feature" stops being a sentence in a summary and
// becomes an assertion the harness can refuse — the receipt idea, applied to a
// feature brief.

import fs from "node:fs";
import path from "node:path";

export const PROPOSALS_DIR_REL = "docs/proposals";

/** The fenced block's info string — ```json cmp:intent-checks */
const CHECKS_FENCE_RE = /```json\s+cmp:intent-checks\s*\n([\s\S]*?)\n```/;

/** Check kinds this evaluator understands. An unknown kind is an ERROR, never a silent pass. */
const KINDS = new Set(["file-exists", "pattern", "spec-clauses"]);

/**
 * Every feature brief under `docs/proposals/` — the docs that CARRY a
 * `cmp:intent-checks` block. Docs without one are not briefs (drafts and
 * research notes stay ungoverned); presence of the block is the opt-in.
 *
 * Code-unit sort (not localeCompare): artifact ids derive from this list, and
 * ICU collation varies with the machine's locale — the registry must read
 * identically everywhere.
 * @param {string} root absolute project root
 * @returns {Array<{name: string, rel: string}>}
 */
export function listProposals(root) {
  const dir = path.join(root, PROPOSALS_DIR_REL);
  let names;
  try {
    names = fs.readdirSync(dir);
  } catch {
    return [];
  }
  const out = [];
  for (const f of names.filter((n) => n.endsWith(".md")).sort()) {
    const rel = `${PROPOSALS_DIR_REL}/${f}`;
    let text;
    try {
      text = fs.readFileSync(path.join(root, rel), "utf8");
    } catch {
      continue; // unreadable — not listable as a brief; surfaces if/when readable
    }
    if (CHECKS_FENCE_RE.test(text)) out.push({ name: f.slice(0, -".md".length), rel });
  }
  return out;
}

/**
 * Parse a brief's `cmp:intent-checks` block.
 *
 * Returns `null` when the text carries no block at all (an ordinary doc). A
 * block that IS present but malformed is the opposite of ignorable: the author
 * wrote a gate and it doesn't parse — that is a real error, surfaced, because
 * silently skipping a malformed gate is how a gate quietly stops gating.
 *
 * @param {string} markdown the doc's full text
 * @returns {{checks: Array<object>, touches: string[], error: (string|null)} | null}
 */
export function parseIntentChecks(markdown) {
  const m = typeof markdown === "string" ? markdown.match(CHECKS_FENCE_RE) : null;
  if (!m) return null;

  let parsed;
  try {
    parsed = JSON.parse(m[1]);
  } catch (err) {
    return { checks: [], touches: [], error: `cmp:intent-checks is not valid JSON — ${err.message}` };
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return { checks: [], touches: [], error: "cmp:intent-checks must be a JSON object" };
  }
  if (!Array.isArray(parsed.checks)) {
    return { checks: [], touches: [], error: "cmp:intent-checks needs a `checks` array" };
  }
  const touches = Array.isArray(parsed.touches) ? parsed.touches.filter((t) => typeof t === "string" && t.trim() !== "") : [];
  return { checks: parsed.checks, touches, error: null };
}

/**
 * Evaluate one check against the tree.
 *
 * Every kind is mechanical — a file exists, a regex matches, a clause id is
 * present. Nothing here takes judgment, because a check that needs judgment
 * belongs in the prose a human reads, not in a gate that fails a build.
 * Quality lives in the test suite; checks assert PRESENCE of what was promised.
 *
 * @param {string} root
 * @param {object} check
 * @returns {{id: string, kind: string, ok: boolean, detail: string}}
 */
export function evaluateCheck(root, check) {
  const id = typeof check?.id === "string" && check.id.trim() !== "" ? check.id.trim() : "(unnamed)";
  const kind = typeof check?.kind === "string" ? check.kind : "(missing)";

  if (!KINDS.has(kind)) {
    return { id, kind, ok: false, detail: `unknown check kind "${kind}" — expected one of ${[...KINDS].join(", ")}` };
  }

  const rel = typeof check.file === "string" ? check.file : null;
  if (!rel) return { id, kind, ok: false, detail: "check is missing `file`" };

  const abs = path.join(root, rel);
  let content = null;
  let exists = false;
  try {
    exists = fs.statSync(abs).isFile();
    if (exists && kind !== "file-exists") content = fs.readFileSync(abs, "utf8");
  } catch {
    exists = false;
  }

  if (kind === "file-exists") {
    return { id, kind, ok: exists, detail: exists ? `${rel} exists` : `${rel} is missing` };
  }
  if (!exists) {
    return { id, kind, ok: false, detail: `${rel} is missing` };
  }

  if (kind === "pattern") {
    if (typeof check.pattern !== "string") {
      return { id, kind, ok: false, detail: "check is missing `pattern`" };
    }
    let re;
    try {
      re = new RegExp(check.pattern);
    } catch (err) {
      // A bad regex is the AUTHOR's error, reported as such — not a silent false.
      return { id, kind, ok: false, detail: `invalid \`pattern\` — ${err.message}` };
    }
    const ok = re.test(content);
    return { id, kind, ok, detail: ok ? `${rel} matches /${check.pattern}/` : `${rel} does not match /${check.pattern}/` };
  }

  // spec-clauses: every listed clause id must appear in the spec file.
  const want = Array.isArray(check.clauses) ? check.clauses.filter((c) => typeof c === "string") : [];
  if (want.length === 0) {
    return { id, kind, ok: false, detail: "check is missing a non-empty `clauses` array" };
  }
  const missing = want.filter((clause) => !content.includes(clause));
  return {
    id,
    kind,
    ok: missing.length === 0,
    detail: missing.length === 0 ? `${rel} carries ${want.length} clause(s)` : `${rel} is missing ${missing.join(", ")}`,
  };
}

/**
 * Resolve one brief into its full reviewable state: parsed block, every
 * check's live result, declared touches, and — given the ledger's word on
 * whether this feature CLAIMS delivery — the failing set.
 *
 * `failing` is deliberately NOT "every check that is false" — it is the set
 * the LANE should fail on, which is empty unless the feature is delivered.
 * The distinction is the whole design: an unbuilt feature's unsatisfied
 * checks are information; only a delivery claim converts them into defects.
 * One exception: a MALFORMED block always fails, delivered or not — the
 * author wrote a gate and it doesn't parse, so nothing it claims can be
 * trusted.
 *
 * @param {string} root
 * @param {{name: string, rel: string}} proposal
 * @param {{delivered?: boolean}} [opts]
 * @returns {{name: string, rel: string, delivered: boolean, touches: string[], error: (string|null), results: Array<object>, satisfied: number, total: number, failing: Array<object>}}
 */
export function resolveProposal(root, proposal, opts = {}) {
  const delivered = opts.delivered === true;
  let markdown = "";
  try {
    markdown = fs.readFileSync(path.join(root, proposal.rel), "utf8");
  } catch {
    // A listed brief that can't be read is reported, not thrown — status
    // surfaces must render every row they promised. The missing FILE itself is
    // the approvals hash's problem (an approved brief that vanishes is drift).
    const bad = { id: "(file)", kind: "(read)", ok: false, detail: `${proposal.rel} could not be read` };
    return { ...proposal, delivered, touches: [], error: bad.detail, results: [bad], satisfied: 0, total: 0, failing: delivered ? [bad] : [] };
  }

  const block = parseIntentChecks(markdown);
  if (block === null) {
    // listProposals only lists docs WITH a block, so this means the block was
    // removed between listing and reading — treat as an empty brief.
    return { ...proposal, delivered, touches: [], error: null, results: [], satisfied: 0, total: 0, failing: [] };
  }
  if (block.error) {
    const bad = { id: "(block)", kind: "(parse)", ok: false, detail: block.error };
    // Malformed → ALWAYS failing, delivered or not (see docblock).
    return { ...proposal, delivered, touches: block.touches, error: block.error, results: [bad], satisfied: 0, total: 0, failing: [bad] };
  }

  const results = block.checks.map((c) => evaluateCheck(root, c));
  const satisfied = results.filter((r) => r.ok).length;
  return {
    ...proposal,
    delivered,
    touches: block.touches,
    error: null,
    results,
    satisfied,
    total: results.length,
    failing: delivered ? results.filter((r) => !r.ok) : [],
  };
}

/**
 * Every brief, resolved against the caller's delivered-set. The one call the
 * verify lane, the CLI status surface, and the console section all share —
 * so the three never disagree about a brief's live state.
 *
 * `deliveredNames` comes from the approvals LEDGER (approvals.mjs owns it);
 * this module never reads qa/approvals.json itself, keeping the dependency
 * one-directional (approvals.mjs imports this file, never the reverse).
 *
 * @param {string} root
 * @param {Iterable<string>} [deliveredNames] brief names claiming delivery
 * @returns {Array<ReturnType<typeof resolveProposal>>}
 */
export function resolveAllProposals(root, deliveredNames = []) {
  const delivered = new Set(deliveredNames);
  return listProposals(root).map((p) => resolveProposal(root, p, { delivered: delivered.has(p.name) }));
}

/**
 * The lane's verdict over every brief.
 *
 *   any failing check on a delivered brief (or any malformed block) → FAIL
 *   ≥1 delivered brief, all checks green                            → PASS
 *   briefs exist but none delivered                                 → SKIP
 *   no briefs at all                                                → SKIP
 *
 * SKIP (not PASS) for in-progress briefs mirrors the approvals gate's stance
 * on unreviewed artifacts: pending human/agent work is warned about, never
 * silently green, and never blocking.
 *
 * @param {string} root
 * @param {Iterable<string>} [deliveredNames]
 * @returns {{verdict: "PASS"|"FAIL"|"SKIP", reason: (string|undefined), proposals: Array<object>}}
 */
export function evaluateIntentChecks(root, deliveredNames = []) {
  const proposals = resolveAllProposals(root, deliveredNames);
  const failures = [];
  for (const p of proposals) {
    for (const f of p.failing) failures.push({ proposal: p.name, id: f.id, detail: f.detail });
  }

  if (failures.length > 0) {
    const lines = ["Feature delivery claim not satisfied — the brief promises what the tree does not carry:"];
    for (const f of failures) {
      lines.push(`  [${f.proposal}] check "${f.id}" — ${f.detail}`);
    }
    lines.push("Fix the tree to satisfy the brief, or withdraw the claim (the brief stays approved; delivery is re-claimed when true).");
    return { verdict: "FAIL", reason: lines.join("\n"), proposals };
  }

  const delivered = proposals.filter((p) => p.delivered);
  if (delivered.length > 0) {
    const building = proposals.length - delivered.length;
    return {
      verdict: "PASS",
      reason:
        `${delivered.length} delivered feature brief(s) satisfy every check` +
        (building > 0 ? `; ${building} still in progress (checks informational until delivered)` : ""),
      proposals,
    };
  }

  if (proposals.length > 0) {
    const lines = [`${proposals.length} feature brief(s) in progress — checks are informational until delivery is claimed:`];
    for (const p of proposals) {
      lines.push(`  [${p.name}] ${p.satisfied}/${p.total} checks satisfied. Claim delivery when done: node qa/approve.mjs --deliver ${p.name}`);
    }
    return { verdict: "SKIP", reason: lines.join("\n"), proposals };
  }

  return { verdict: "SKIP", reason: "no feature briefs (docs/proposals/*.md with a cmp:intent-checks block) in this project", proposals };
}
