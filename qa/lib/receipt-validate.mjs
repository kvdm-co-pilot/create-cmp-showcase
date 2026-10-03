// The evidence-binding predicate and its service-grade extensions, as pure
// dependency-free functions. `evaluateReceipt` is the exact predicate the
// generated project's qa/receipt-check.mjs (and its Stop hook + CI) runs;
// the additional checks (freshness, execution plausibility, SKIP listing) are
// consumed by hosted validators that judge a receipt fetched from a repo
// tarball rather than the working tree.
//
// SINGLE SOURCE OF TRUTH: packages/receipts/src/receipt-validate.mjs in the
// create-cmp repo (the `prooflane-receipts` package). The copy in a generated
// project's qa/lib/ is vendored byte-identical at scaffold time and pinned by
// test/receipts-parity.test.mjs — edit the package source, then run
// `node scripts/sync-harness.mjs` in the create-cmp repo.
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
/**
 * Does this receipt's own row-level evidence support its PASS?
 *
 * The receipt is necessarily excluded from the inputs hash it carries — a file
 * cannot hash itself — so steps[] is the only thing between this gate and a text
 * editor, and the top-level verdict is the most editable field on it.
 *
 * Two failures this catches, both observed downstream (payment-blueprint F2/F3):
 * a receipt whose verdict was hand-edited from FAIL to PASS while its rows still
 * said otherwise, and a lane made green by DELETING harness.lock.json, which
 * downgraded harnessIntegrity from FAIL to SKIP and took the lane's verdict with
 * it — a lane vouching for a tree with nothing vouching for the lane.
 *
 * @param {{verdict?: string, steps?: Array<{name?: string, verdict?: string}>}} receipt
 * @returns {{ok: boolean, detail: string}}
 */
export function checkLaneVouching(receipt) {
  const steps = Array.isArray(receipt?.steps) ? receipt.steps : null;
  if (!steps || steps.length === 0) {
    return { ok: false, detail: "receipt lists no verify-lane steps — a PASS over nothing attests nothing" };
  }
  const failed = steps.filter((s) => s && (s.verdict === "FAIL" || s.verdict === "ERROR"));
  if (failed.length > 0) {
    const names = failed.map((s) => `${s.name ?? "?"} (${s.verdict})`).join(", ");
    return {
      ok: false,
      detail: `the receipt's verdict is PASS but ${failed.length} step(s) did not pass: ${names} — the row is the more specific truth`,
    };
  }
  // THE ROW THAT VOUCHES IS THE ROW CARRYING THE VOUCHING DATA, not the row with
  // a particular name. This found a step named exactly `harnessIntegrity` — a
  // name the cmp pack chose, that `REQUIRED_EXPORTS` never mentions, and that a
  // profile author has no way to discover. A green lane whose self-vouching step
  // was spelled `harness_integrity` minted receipts that were invalid FOREVER,
  // in every reader, and the refusal accused the lane of not vouching for
  // itself. The `harness` object on a step row is what the schema already
  // documents as the integrity check's own findings, so it is the honest key.
  // The name is kept as a fallback for receipts written before rows carried it.
  const integrity = steps.find((s) => s && s.harness && typeof s.harness === "object") ?? steps.find((s) => s && s.name === "harnessIntegrity");
  if (!integrity) {
    return {
      ok: false,
      detail:
        "no step on this receipt vouches for the lane — no row carries a `harness` object and none is named harnessIntegrity, " +
        "so nothing attests that the lane's own code is the code that ran",
    };
  }
  if (integrity.verdict !== "PASS") {
    return {
      ok: false,
      detail: `${integrity.name ?? "the integrity step"} is ${integrity.verdict}, not PASS — the lane did not vouch for itself, so its PASS over the tree cannot be trusted`,
    };
  }
  return { ok: true, detail: `lane vouched for itself (${integrity.name ?? "integrity step"} PASS, no failing rows)` };
}

export function evaluateReceipt(receipt, recompute) {
  const profile = receipt.profile;

  if (!receipt.inputs || typeof receipt.inputs.hash !== "string") {
    return {
      valid: false,
      reason: `receipt predates evidence binding — re-run the lane (attesting profile: ${profile ?? "unknown"})`,
      profile,
    };
  }

  // WHICH PACK GRADED THIS — ADR-0011. §8.9's comparability rule rests entirely
  // on this field ("a `cmp` L2 and any other pack's L2 are different claims"),
  // and until now no predicate read it: remove `pack` and a receipt kept its
  // rung while losing the only thing that says what the rung is a rung OF.
  //
  // REFUSED, not flagged, and the cost is known. A receipt written before the
  // field existed (2026-09-04) is refused too, because nothing in a receipt can
  // tell "never had one" from "had one, and it was removed" — and of those two
  // errors, accepting tampering is the one a predicate exists to prevent. The
  // remedy is the one the binding check above already offers for the same class
  // of staleness, and costs the same: re-run the lane.
  //
  // This is deliberately NOT the ADR-0007 case. There a label moved and no
  // assertion changed, so invalidating old receipts would have been pure loss.
  // Here the receipt is genuinely missing the field that makes its rung mean
  // something — it is not being punished for a name, it is being asked for a
  // claim it never made.
  if (!receipt.pack || typeof receipt.pack.id !== "string" || receipt.pack.id.length === 0) {
    return {
      valid: false,
      reason:
        `receipt names no step pack — re-run the lane (attesting profile: ${profile ?? "unknown"}). ` +
        `A rung is comparable only within its pack, so a receipt that does not name one cannot be compared to any other`,
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

  // Did the lane vouch for ITSELF? See checkLaneVouching — the top-level verdict
  // is the most editable field on a file the hash cannot cover.
  const vouching = checkLaneVouching(receipt);
  if (!vouching.ok) {
    return { valid: false, reason: `${vouching.detail} (attesting profile: ${profile ?? "unknown"})`, profile, recomputed };
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
   * An absolute wall-time floor for executed (non-SKIP) gates. `null` — OFF by
   * default, and that is a decision rather than an omission.
   *
   * This was 5000, with the reasoning that a PASS receipt summing to less
   * cannot attest a real lane run: the tell for a replayed/cached green or a
   * hand-written verdict. That reasoning holds for a Gradle lane and is FALSE
   * for a Go service, a Rust crate, a Python package or a TypeScript library,
   * whose lanes honestly finish in hundreds of milliseconds. Those adopters
   * were told their evidence was fabricated — the one accusation this product
   * cannot afford to make wrongly.
   *
   * The number was not the defect. ONE receipt carries nothing that could
   * justify any number: no start time, no top-level duration, no baseline —
   * `generatedAt` is a timestamp, not an interval — so nothing on it can be
   * cross-checked against anything else on it. A floor is therefore a fact
   * about the STACK, and this module does not know the stack. It is the
   * notary's to set, from data the notary has and the receipt does not: a
   * lane that has taken thirty seconds every day for a month and today claims
   * forty-two milliseconds is a real finding, and it is a finding about a
   * HISTORY, not about a receipt.
   *
   * What is lost, said plainly: a hand-written receipt claiming small
   * durations is no longer refused here. It was never much of a defence — a
   * forger types a larger number — and every stack-independent check that
   * does catch fabrication is untouched below.
   */
  minExecutedMs: null,
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
 * Execution plausibility: did this lane execute anything, and are its numbers
 * real numbers?
 *
 * Three refusals, all stack-independent and all about the SHAPE of the
 * evidence rather than its size: a receipt with no steps, a receipt whose every
 * step is a SKIP or an ERROR (neither measured anything), and a step whose
 * duration is not a finite non-negative number. An absolute wall-time floor is
 * a fourth check and is OFF unless a caller sets `minExecutedMs` — see
 * DEFAULT_POLICY for why a default one is a claim about the stack.
 * @returns {{ok: boolean, detail: string, executedMs?: number, executedSteps?: number}}
 */
export function checkExecutionPlausibility(receipt, { minExecutedMs = DEFAULT_POLICY.minExecutedMs } = {}) {
  const steps = Array.isArray(receipt?.steps) ? receipt.steps : null;
  if (!steps || steps.length === 0) {
    return { ok: false, detail: "receipt lists no verify-lane steps — nothing was executed" };
  }
  // Executed = produced a verdict about the tree. SKIP did not try; ERROR
  // tried and could not (a deadline, zero tests, a throw) — neither measured
  // anything, so neither counts toward "this lane verified something".
  const executed = steps.filter((s) => s && s.verdict !== "SKIP" && s.verdict !== "ERROR");
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
  // Applied only when a caller supplies one. The message attributes the floor
  // to whoever set it and states the measurement, rather than asserting that a
  // fast receipt cannot be real — which this module has no way to know.
  if (typeof minExecutedMs === "number" && minExecutedMs > 0 && total < minExecutedMs) {
    return {
      ok: false,
      detail: `executed gates report ${total}ms total, below this validator's configured ${minExecutedMs}ms floor — for a fast stack that may be honest, so treat it as a finding to explain rather than proof of fabrication`,
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

/** The rung order every ladder shares (the schema's enum). */
const RUNG_ORDER = ["L0", "L1", "L2", "L3"];

/**
 * Which of a receipt's rungs a party other than its producer vouches for, and
 * which rest on the producer's word alone (docs/adr/0017). A REPORT, never a
 * verdict: nothing here changes what `evaluateReceipt` or `checkDoneEvidence`
 * decide.
 *
 * THE MARKER IS NOT IN THE RECEIPT, AND CANNOT BE. An attestation covers the
 * receipt's bytes, so writing it into the receipt afterwards would change the
 * bytes it covers; and any field the producer writes — `attestation`,
 * `producedBy: "ci"` — is the producer's word again, which is the very gap
 * this reports (a hand-edited receipt passes every check that reads it). So
 * the only thing that moves a rung to "attested" is `attestedThrough`: the
 * highest rung a CALLER has checked an attestation for, outside this file
 * (e.g. `gh attestation verify` over the CI run's own receipt). A caller that
 * has checked nothing passes nothing, and every rung is self-attested.
 *
 * @param {object} receipt the receipt as read
 * @param {{attestedThrough?: string|null}} [opts] the highest rung an
 *   independently verified attestation covers; rungs at or below it are
 *   attested, rungs above it are self-attested
 * @returns {{claimed: string[], attested: string[], selfAttested: string[], line: string}}
 */
export function attestationStanding(receipt, { attestedThrough = null } = {}) {
  const rung = receipt?.evidenceLevel?.rung;
  const top = RUNG_ORDER.indexOf(typeof rung === "string" ? rung : "");
  const claimed = top < 0 ? [] : RUNG_ORDER.slice(0, top + 1);
  const through = RUNG_ORDER.indexOf(typeof attestedThrough === "string" ? attestedThrough : "");
  const attested = claimed.filter((r) => RUNG_ORDER.indexOf(r) <= through);
  const selfAttested = claimed.filter((r) => RUNG_ORDER.indexOf(r) > through);
  const list = (rs) => (rs.length ? rs.join(", ") : "none");
  const line = claimed.length
    ? `attestation — CI-attested: ${list(attested)} · self-attested: ${list(selfAttested)}`
    : "attestation — no rung claimed, nothing to attest";
  return { claimed, attested, selfAttested, line };
}

/** How a refusal names the lane when its caller does not spell it. */
export const DEFAULT_LANE_COMMAND = "node qa/verify.mjs";

/**
 * THE DONE-EVIDENCE REFUSALS — a receipt that binds to its tree and says PASS
 * can still be no proof that a change is done, and says so about itself. Four
 * shapes, each refused with a named reason:
 *
 *   - `mode: "fast"` — verify --fast is an inner-loop signal, never done evidence;
 *   - `stage` or `profile` `nightly` — it proves the harness under a forced
 *     double-run, never a change;
 *   - `stage` or `profile` `smoke` — the framework check (GATE-RULES Rule 0)
 *     runs no build and no tests: it proves the instrument, not the change;
 *   - any step SKIPped with `skipKind: "environment"` — a tier that COULD have
 *     run and did not is a gap a human can close (2026-09-03). A `structure`
 *     SKIP — this project has no such tier at all — is honest and allowed.
 *
 * ONE SOURCE OF TRUTH (KD-266). These lived only in the harness's
 * qa/receipt-check.mjs, before it called this library, so a hosted validator
 * calling only the library (validateReceiptForTree) said "valid" to all six
 * shapes. The done-check now calls this; the reasons are its words.
 *
 * A SKIP WITH NO `skipKind` IS NOT REFUSED BY DEFAULT. Not every current SKIP
 * carries one — the cmp profile's tokenDrift "inspector endpoint not reachable"
 * SKIP, emitted on every headless L2 run, does not (KD-5) — and refusing the
 * unlabelled would refuse every such receipt. Receipts predating `skipKind`
 * (0.19.0 and earlier) were judged by reason text, and those texts and step
 * names are a PROFILE's, never this stack-agnostic library's: a caller that
 * holds the profile passes them as `legacySkips`; a caller that does not
 * (a hosted validator) gets no legacy fallback. A fallback that guessed would
 * be worse than none.
 *
 * @param {object} receipt the parsed receipt
 * @param {object} [opts]
 * @param {string} [opts.laneCommand] how the refusal names the lane (default `node qa/verify.mjs`)
 * @param {{names?: string[], reasons?: string[]}|null} [opts.legacySkips] a profile's pre-`skipKind`
 *   fallback: an unlabelled SKIP of a step in `names` whose reason contains any of `reasons` is
 *   environmental. Applied only when both are non-empty.
 * @returns {{ok: boolean, refusal: (null|"fast-mode"|"nightly"|"smoke"|"environment-skip"), detail: string}}
 */
export function checkDoneEvidence(receipt, { laneCommand = DEFAULT_LANE_COMMAND, legacySkips = null } = {}) {
  const lane = `\`${laneCommand}\``;
  if (receipt?.mode === "fast") {
    return {
      ok: false,
      refusal: "fast-mode",
      detail: `the last verify run was --fast (inner-loop only); run the full lane (${lane}) before finishing`,
    };
  }
  if (receipt?.stage === "nightly" || receipt?.profile === "nightly") {
    return {
      ok: false,
      refusal: "nightly",
      detail: `the last verify run was the nightly stage (it proves the harness, not this change); run the change-stage lane (${lane}) before finishing`,
    };
  }
  if (receipt?.stage === "smoke" || receipt?.profile === "smoke") {
    return {
      ok: false,
      refusal: "smoke",
      detail: `the last verify run was the smoke profile (the framework check — no build, no tests; it proves the instrument, not this change); run the change-stage lane (${lane}) before finishing`,
    };
  }
  const names = Array.isArray(legacySkips?.names) ? legacySkips.names : [];
  const reasons = Array.isArray(legacySkips?.reasons) ? legacySkips.reasons.map(String) : [];
  const legacyEnvironmental = (s) =>
    names.length > 0 && reasons.length > 0 && names.includes(s.name) && reasons.some((r) => String(s.reason ?? "").includes(r));
  const envSkipped = (Array.isArray(receipt?.steps) ? receipt.steps : []).filter((s) => {
    if (!s || s.verdict !== "SKIP") return false;
    if (s.skipKind) return s.skipKind === "environment";
    return legacyEnvironmental(s);
  });
  if (envSkipped.length) {
    return {
      ok: false,
      refusal: "environment-skip",
      detail:
        `a tier did not run — ${envSkipped.map((s) => `${s.name}: ${String(s.reason ?? "").split("\n")[0]}`).join("; ")}. ` +
        `Those steps skipped for an environmental reason, not because this project lacks them; fix the cause and run ${lane} again before finishing`,
    };
  }
  return { ok: true, refusal: null, detail: "done evidence — a full change-stage run with no environment SKIP" };
}

/**
 * The hosted composite: validate the receipt found in an extracted repo tree
 * (e.g. a tarball at a PR's head SHA) with the full service-grade policy.
 *
 * @param {object} args
 * @param {string} args.root absolute path to the extracted tree's project root
 * @param {number} [args.now] epoch ms, for freshness (defaults to Date.now())
 * @param {object} [args.policy] overrides for DEFAULT_POLICY
 * @param {{names?: string[], reasons?: string[]}|null} [args.legacySkips] a profile's pre-`skipKind`
 *   SKIP fallback, handed to checkDoneEvidence; omitted, no legacy fallback applies
 * @returns {{
 *   status: "missing"|"valid"|"invalid",
 *   reason: string,
 *   profile?: string,
 *   checks: Array<{id: string, ok: boolean, detail: string}>,
 *   skips: Array<{name: string, reason: string}>,
 * }}
 */
export function validateReceiptForTree({ root, now = Date.now(), policy = {}, legacySkips = null } = {}) {
  const effective = { ...DEFAULT_POLICY, ...policy };
  const receipt = readReceipt(root);

  if (receipt === null) {
    return {
      status: "missing",
      reason: `no receipt at ${RECEIPT_REL_PATH} — this repo does not carry the prooflane evidence harness (that is not a failure)`,
      checks: [{ id: "receipt-present", ok: false, detail: `no parsable receipt at ${RECEIPT_REL_PATH}` }],
      skips: [],
    };
  }

  const checks = [{ id: "receipt-present", ok: true, detail: RECEIPT_REL_PATH }];
  const skips = listSkippedSteps(receipt);

  // The done-evidence refusals the harness's own done-check applies (KD-266),
  // so a notary refuses what the Stop hook refuses — with one stated exception:
  // a pre-`skipKind` receipt's environmental SKIP is judged by the profile's
  // reason text, and a caller that passes no `legacySkips` cannot judge it.
  const done = checkDoneEvidence(receipt, { legacySkips });
  checks.push({ id: "done-evidence", ok: done.ok, detail: done.detail });

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
