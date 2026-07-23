// The evidence-binding predicate and its service-grade extensions, as pure
// dependency-free functions. `evaluateReceipt` is the exact predicate the
// generated project's qa/receipt-check.mjs (and its Stop hook + CI) runs;
// the additional checks (freshness, execution plausibility, SKIP listing) are
// consumed by hosted validators that judge a receipt fetched from a repo
// tarball rather than the working tree.
//
// SINGLE SOURCE OF TRUTH: packages/receipts/src/receipt-validate.mjs in the
// create-cmp repo (the `cmp-receipts` package). The copy in a generated
// project's qa/lib/ is vendored byte-identical at scaffold time and pinned by
// test/receipts-parity.test.mjs — edit the package source, then run
// `node scripts/sync-receipts.mjs`.
//
// See docs/adr/0005-evidence-binding-by-inputs-hash.md for the why.

import fs from "node:fs";
import path from "node:path";

import { computeInputsHash } from "./inputs-hash.mjs";

/** Where a generated project keeps its committed receipt, relative to root. */
export const RECEIPT_REL_PATH = "qa/evidence/latest.json";

/**
 * Read and parse the committed receipt for the project rooted at `root`.
 * @param {string} root absolute path to the project root
 * @returns {object|null} the parsed receipt, or null when absent/unparsable
 */
export function readReceipt(root, relPath = RECEIPT_REL_PATH) {
  try {
    return JSON.parse(fs.readFileSync(path.join(root, relPath), "utf8"));
  } catch {
    return null;
  }
}

/**
 * The core predicate: does this receipt validly attest the tree whose inputs
 * hash `recompute()` returns? Reasons are the exact refusal strings the
 * generated project's receipt-check CLI (and Stop hook) prints.
 *
 * @param {object} receipt parsed receipt JSON
 * @param {() => {hash: string, fileCount: number}} recompute lazily invoked —
 *   never called when the receipt fails structurally first (missing binding,
 *   FAIL verdict), so callers don't pay for a hash they don't need.
 * @returns {{valid: boolean, reason: string, profile: (string|undefined), recomputed?: {hash: string, fileCount: number}}}
 */
export function evaluateReceipt(receipt, recompute) {
  const profile = receipt.profile;

  if (!receipt.inputs || typeof receipt.inputs.hash !== "string") {
    return {
      valid: false,
      reason: `receipt predates evidence binding — re-run the lane (attesting profile: ${profile ?? "unknown"})`,
      profile,
    };
  }

  if (receipt.verdict === "FAIL") {
    return {
      valid: false,
      reason: `the committed receipt is a FAIL (attesting profile: ${profile ?? "unknown"})`,
      profile,
    };
  }

  const recomputed = recompute();

  if (receipt.inputs.hash !== recomputed.hash) {
    return {
      valid: false,
      reason: `source changed since the receipt — re-run the lane (attesting profile: ${profile ?? "unknown"})`,
      profile,
      recomputed,
    };
  }

  if (receipt.verdict !== "PASS") {
    return {
      valid: false,
      reason: `receipt verdict is "${receipt.verdict}", not PASS (attesting profile: ${profile ?? "unknown"})`,
      profile,
    };
  }

  return { valid: true, reason: `receipt is valid — PASS, attesting profile: ${profile ?? "unknown"}`, profile, recomputed };
}

// ── Service-grade checks (hosted validators; the local predicate above does
//    not enforce these — the tree it checks is by definition "now") ─────────

/** Default policy for hosted validation. Every knob is overridable. */
export const DEFAULT_POLICY = {
  /** A receipt older than this no longer counts as fresh (hosted check only). */
  maxAgeMs: 30 * 24 * 60 * 60 * 1000, // 30 days
  /**
   * Executed (non-SKIP) gates must report at least this much total wall time.
   * A "PASS" receipt whose executed gates sum to less cannot attest a real
   * lane run — the tell for replayed/cached or hand-written verdicts
   * (evidence must attest execution, not results).
   */
  minExecutedMs: 5000,
};

/**
 * Freshness: is the receipt's generatedAt within maxAgeMs of `now`?
 * @returns {{ok: boolean, detail: string, ageMs?: number}}
 */
export function checkFreshness(receipt, { now = Date.now(), maxAgeMs = DEFAULT_POLICY.maxAgeMs } = {}) {
  const generatedAt = Date.parse(receipt?.generatedAt ?? "");
  if (Number.isNaN(generatedAt)) {
    return { ok: false, detail: "receipt has no parsable generatedAt timestamp" };
  }
  const ageMs = now - generatedAt;
  if (ageMs < -60_000) {
    // A receipt from the future is a clock lie, not a rounding artifact.
    return { ok: false, detail: `receipt claims a future generatedAt (${receipt.generatedAt})`, ageMs };
  }
  if (ageMs > maxAgeMs) {
    const days = Math.floor(ageMs / 86_400_000);
    return { ok: false, detail: `receipt is stale — generated ${days} day(s) ago, older than the ${Math.floor(maxAgeMs / 86_400_000)}-day freshness window`, ageMs };
  }
  return { ok: true, detail: `receipt generated ${receipt.generatedAt}`, ageMs };
}

/**
 * Execution plausibility: do the executed (non-SKIP) gates report durations a
 * real lane run could produce? Catches replayed/cached greens and hand-edited
 * receipts whose numbers were never lived.
 * @returns {{ok: boolean, detail: string, executedMs?: number, executedSteps?: number}}
 */
export function checkExecutionPlausibility(receipt, { minExecutedMs = DEFAULT_POLICY.minExecutedMs } = {}) {
  const steps = Array.isArray(receipt?.steps) ? receipt.steps : null;
  if (!steps || steps.length === 0) {
    return { ok: false, detail: "receipt lists no verify-lane steps — nothing was executed" };
  }
  const executed = steps.filter((s) => s && s.verdict !== "SKIP");
  if (executed.length === 0) {
    return { ok: false, detail: "every step in the receipt is a SKIP — the lane verified nothing" };
  }
  let total = 0;
  for (const step of executed) {
    if (typeof step.durationMs !== "number" || !Number.isFinite(step.durationMs) || step.durationMs < 0) {
      return { ok: false, detail: `step "${step.name ?? "?"}" reports an invalid duration (${step.durationMs}) — durations must be real, non-negative numbers` };
    }
    total += step.durationMs;
  }
  if (total < minExecutedMs) {
    return {
      ok: false,
      detail: `implausibly fast — executed gates report ${total}ms total, below the ${minExecutedMs}ms floor; a receipt this fast cannot attest a real lane run (evidence must attest execution)`,
      executedMs: total,
      executedSteps: executed.length,
    };
  }
  return { ok: true, detail: `${executed.length} executed gate(s), ${total}ms total`, executedMs: total, executedSteps: executed.length };
}

/**
 * List the SKIPped steps with their honest reasons. SKIPs are reported, not
 * failed — green-with-gaps must be visible, never silently equated with
 * fully-verified (or silently punished).
 * @returns {Array<{name: string, reason: string}>}
 */
export function listSkippedSteps(receipt) {
  const steps = Array.isArray(receipt?.steps) ? receipt.steps : [];
  return steps
    .filter((s) => s && s.verdict === "SKIP")
    .map((s) => ({ name: s.name ?? "?", reason: s.reason ?? "no reason recorded" }));
}

/**
 * The hosted composite: validate the receipt found in an extracted repo tree
 * (e.g. a tarball at a PR's head SHA) with the full service-grade policy.
 *
 * @param {object} args
 * @param {string} args.root absolute path to the extracted tree's project root
 * @param {number} [args.now] epoch ms, for freshness (defaults to Date.now())
 * @param {object} [args.policy] overrides for DEFAULT_POLICY
 * @returns {{
 *   status: "missing"|"valid"|"invalid",
 *   reason: string,
 *   profile?: string,
 *   checks: Array<{id: string, ok: boolean, detail: string}>,
 *   skips: Array<{name: string, reason: string}>,
 * }}
 */
export function validateReceiptForTree({ root, now = Date.now(), policy = {} } = {}) {
  const effective = { ...DEFAULT_POLICY, ...policy };
  const receipt = readReceipt(root);

  if (receipt === null) {
    return {
      status: "missing",
      reason: `no receipt at ${RECEIPT_REL_PATH} — this repo does not carry the create-cmp evidence harness (that is not a failure)`,
      checks: [{ id: "receipt-present", ok: false, detail: `no parsable receipt at ${RECEIPT_REL_PATH}` }],
      skips: [],
    };
  }

  const checks = [{ id: "receipt-present", ok: true, detail: RECEIPT_REL_PATH }];
  const skips = listSkippedSteps(receipt);

  // The core predicate (binding + verdict + hash), verbatim local semantics.
  const core = evaluateReceipt(receipt, () => computeInputsHash(root));
  checks.push({ id: "binding-and-hash", ok: core.valid, detail: core.reason });

  // Service-grade extensions run regardless, so a failing receipt reports
  // every violated rule at once (refusals name what failed, all of it).
  const freshness = checkFreshness(receipt, { now, maxAgeMs: effective.maxAgeMs });
  checks.push({ id: "freshness", ok: freshness.ok, detail: freshness.detail });

  const plausibility = checkExecutionPlausibility(receipt, { minExecutedMs: effective.minExecutedMs });
  checks.push({ id: "execution-plausibility", ok: plausibility.ok, detail: plausibility.detail });

  const failed = checks.filter((c) => !c.ok);
  if (failed.length > 0) {
    return {
      status: "invalid",
      reason: failed.map((c) => c.detail).join("; "),
      profile: core.profile,
      checks,
      skips,
    };
  }

  return {
    status: "valid",
    reason: core.reason,
    profile: core.profile,
    checks,
    skips,
  };
}
