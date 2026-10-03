#!/usr/bin/env node
// The evidence-binding predicate — answers one question: does the committed
// receipt (qa/evidence/latest.json) validly attest THIS tree, right now?
//
//   node qa/receipt-check.mjs [--hook] [--json]
//
// Both enforcement points reduce to this predicate: the local Stop hook
// (.claude/settings.json) calls it on every turn-end, and CI calls it before
// re-running the lane. See docs/adr/0005-evidence-binding-by-inputs-hash.md.
//
// VALID  iff receipt.verdict === "PASS" && receipt.inputs.hash === recompute(tree)
// Exit codes (normal mode): VALID -> 0, INVALID -> 1.
// Exit codes (--hook mode, Claude Code Stop-hook protocol):
//   stop_hook_active === true  -> 0 (never block twice in a row)
//   INVALID                    -> 2, reason on stderr (Claude Code's block-and-feed-back signal)
//   VALID                      -> 0, silent

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { computeInputsHash } from "./lib/inputs-hash.mjs";
import { attestationStanding, checkDoneEvidence, evaluateReceipt, readReceipt } from "./lib/receipt-validate.mjs";
import { readHold, assessHold, describeHold, holdExplains } from "./lib/agent-hold.mjs";
import { LANE_MARKER_STALE_MS, laneMarkerPath } from "./lib/lane-markers.mjs";
import { resolveHarnessManifest } from "./lib/harness-manifest.mjs";
import { loadProfileSync } from "./lib/profile-loader.mjs";
import { evidenceLadderFor } from "./lib/evidence-ladder.mjs";
import { readLadder } from "./lib/evidence-level.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

/**
 * The lane command, spelled so it resolves from where THIS process stands. The
 * anchored Stop hook runs this file from any directory, and a session opened
 * outside the project is exactly the one a relative `node qa/verify.mjs` fails
 * for (KD-215). At the project root the words are unchanged, byte for byte.
 *
 * ONE SPELLING FOR EVERY MENTION. KD-215 fixed the hook's trailing "Run `…`"
 * and left every refusal reason naming its own bare `node qa/verify.mjs` on the
 * same stderr line — the instance fixed, the class not. Each reason below and
 * the hook's instruction read LANE_COMMAND, so no mention can drift from it.
 */
function laneCommand() {
  const here = (p) => {
    try {
      return fs.realpathSync(p);
    } catch {
      return path.resolve(p);
    }
  };
  if (here(process.cwd()) === here(ROOT)) return "node qa/verify.mjs";
  // Double quotes unless the path carries a character they would not protect.
  const dir = /["$`\\!]/.test(ROOT) ? `'${ROOT.replace(/'/g, "'\\''")}'` : `"${ROOT}"`;
  return `cd ${dir} && node qa/verify.mjs`;
}

const LANE_COMMAND = laneCommand();

const args = process.argv.slice(2);
const asHook = args.includes("--hook");
const asJson = args.includes("--json");

// The lane's own in-flight marker (verify.mjs stamps it, rewriting it at each
// step start with the step name and index) — qa/lib/lane-markers.mjs, one
// path and one bound for every reader.

/**
 * The full check running RIGHT NOW, or null. The gate must still refuse — a
 * lane in flight has not produced a receipt yet — but it must not tell the
 * agent to start one. Repeating "run the lane" at a session whose lane is
 * already ten minutes into its release build is an instruction to do the wrong
 * thing, and it fired ~8 times in one observed session.
 * @returns {{step: string|null, index: number|null, total: number|null}|null}
 */
function laneInFlight() {
  try {
    const p = laneMarkerPath(ROOT);
    const st = fs.statSync(p);
    if (Date.now() - st.mtimeMs >= LANE_MARKER_STALE_MS) return null;
    // Content is a bonus, never a requirement: legacy markers hold "pid iso".
    try {
      const n = JSON.parse(fs.readFileSync(p, "utf8"));
      if (n && typeof n === "object") {
        return { step: n.step ?? null, index: n.index ?? null, total: n.total ?? null };
      }
    } catch {
      /* legacy marker — its EXISTENCE is the fact that matters */
    }
    return { step: null, index: null, total: null };
  } catch {
    return null;
  }
}

function readStdinJson() {
  try {
    const raw = fs.readFileSync(0, "utf8");
    if (!raw.trim()) return {};
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

// The predicate itself lives in qa/lib/receipt-validate.mjs (vendored from the
// prooflane-receipts package — one definition everywhere a receipt is judged); this
// CLI only reads the receipt and frames the exit codes.
function evaluate() {
  const receipt = readReceipt(ROOT);
  if (receipt === null) {
    return { valid: false, reason: `no receipt — run \`${LANE_COMMAND}\``, profile: undefined };
  }
  // THE DONE-EVIDENCE REFUSALS ARE THE LIBRARY'S (KD-266). A receipt written
  // by verify --fast, by the nightly or smoke stage or profile, or with a step
  // SKIPped for an environmental reason is no proof that a change is done —
  // refused before the hash is even recomputed. The rules and their named
  // reasons live in qa/lib/receipt-validate.mjs (checkDoneEvidence), so the
  // hosted receipt check, which calls only that library, refuses exactly what
  // this Stop hook refuses. They used to be stated here, and the notary said
  // "valid" to all six shapes.
  //
  // A step that COULD have run and did not is a gap a human can close, and a
  // change is not done while it stands (2026-09-03). The lane boots its own
  // device, so an environment SKIP — no device, a tool not installed, a lease
  // held elsewhere — is never the project's honest structure; a `structure`
  // SKIP (this project has no such tier at all) is, and is allowed.
  //
  // Stage 0 PR 6c: this used to test `["e2eSmoke", "androidChecks"]` by name
  // and match Android reason text. On any other stack it therefore checked
  // nothing — the one gate that refuses "done" over a tier that never ran,
  // silently inert. `skipKind` is the stack-free signal, so the rule is: ANY
  // step that skipped with `skipKind: "environment"` blocks done, whatever it
  // is called.
  //
  // NOT EVERY CURRENT RECEIPT CARRIES `skipKind`. A step that emits a SKIP with
  // none — the cmp profile's tokenDrift "inspector endpoint not reachable" SKIP,
  // on every headless L2 run (KD-5) — is judged as a legacy receipt would be:
  // by its reason text, which that SKIP's text does not match. So it is not
  // refused, and test/the-notary-refuses-what-the-done-check-refuses.test.mjs
  // pins that.
  //
  // Receipts predating `skipKind` (0.19.0 and earlier) are read by their
  // reason text, and THAT list is the profile's — read from the ladder's
  // execution tier when the profile is loadable, and simply not applied when
  // it is not. A legacy fallback that guessed would be worse than none. The
  // library is stack-agnostic and holds no such text: this reader hands it
  // the profile's lists; a caller that holds none gets no fallback.
  //
  // THE LADDER IS RESOLVED, NOT SPELLED. This used to read `profile.ladder`
  // directly, which is one of the two places a profile may declare its rungs;
  // qa/verify.mjs read the other, and nothing reconciled them. It goes
  // through qa/lib/evidence-ladder.mjs now, so this reader and the lane can
  // never be looking at different declarations of the same thing.
  //
  // NO PACK IS PASSED, AND THAT IS THE POINT. Getting the pack's spelling means
  // calling `steps(ctx)`, and this file is the Stop hook: a done-gate that
  // starts a lane to answer a legacy-compat question is not a gate, it is a
  // second lane. So a profile that declares its ladder ONLY on the pack gives
  // this reader nothing and gets no legacy fallback — the same honest silence
  // as a profile with no ladder at all, and the reason the top-level spelling
  // is the one `harness init` seeds and the one the resolver prefers.
  // AND THE REASON TEXTS ARE THE PROFILE'S TOO. The names came from the ladder
  // since Stage 0 PR 6c, but the patterns beside them stayed literal — "no
  // Android device", "maestro CLI not installed" — so the core still held one
  // stack's vocabulary to read one stack's old receipts. A profile that never
  // emitted a pre-`skipKind` receipt declares none and gets no legacy fallback,
  // which is the honest silence this block already chose over a guess.
  let legacySkips = null;
  try {
    const manifest = resolveHarnessManifest(ROOT);
    if (manifest.ok) {
      const loaded = loadProfileSync(ROOT, manifest.manifest.profile);
      const profile = loaded.ok ? loaded.profile : null;
      const resolved = evidenceLadderFor(profile);
      const ladder = resolved.ok ? resolved.ladder : null;
      const declared = profile?.legacySkipReasons;
      if (ladder && Array.isArray(declared) && declared.length) {
        legacySkips = { names: readLadder(ladder).l2Execution, reasons: declared };
      }
    }
  } catch {
    // The Stop hook never crashes over a legacy-compat lookup.
  }
  const done = checkDoneEvidence(receipt, { laneCommand: LANE_COMMAND, legacySkips });
  if (!done.ok) {
    return { valid: false, reason: done.detail, profile: receipt.profile };
  }
  // A surface this project cannot resolve is a REFUSAL with an explanation,
  // never an unhandled stack trace: this runs as the Stop hook on every turn
  // end, and a crash there reads as a broken harness rather than as the
  // misconfiguration it is. (evidence-economics S8 follow-up: computeInputsHash
  // now throws rather than returning a confident hash of the empty set.)
  let result;
  try {
    result = evaluateReceipt(receipt, () => computeInputsHash(ROOT));
  } catch (err) {
    return {
      valid: false,
      reason: `cannot verify this receipt — ${err && err.message ? err.message : String(err)}`,
      profile: receipt.profile,
    };
  }
  // Surface the receipt's evidence rung (the ladder — qa/lib/evidence-level.mjs)
  // alongside the verdict: the rung is the receipt's own derived field, read
  // verbatim, never recomputed here. Older receipts without it stay valid.
  //
  // AND THE PACK WITH IT, ALWAYS — the two are set in the same breath because
  // NORTH-STAR.md §6.5 requires that every surface showing a rung shows the
  // pack, and §8.9 is why: one pack's L2 and another pack's L2 are different
  // claims. This CLI printed the rung alone, which left every reader of the
  // done-gate exactly where that rule says they must not be. Only the pack's
  // ID is carried: it is the part that carries the meaning, and `pack.version`
  // on a receipt is today the harness lock's number rather than the profile's
  // (docs/adr/0008), so a surface that leaned on it would be repeating a
  // borrowed fact.
  const level = receipt.evidenceLevel;
  if (level && typeof level === "object" && typeof level.rung === "string") {
    result.evidenceLevel = level;
    const id = receipt.pack && typeof receipt.pack.id === "string" ? receipt.pack.id.trim() : "";
    result.packId = id || null;
  }
  // WHO VOUCHES FOR EACH RUNG — a report line, never a verdict (docs/adr/0017).
  // This file is the producer's own copy, and nothing this CLI can read on its
  // own is an attestation by anyone else, so it passes no `attestedThrough`:
  // every rung it reports is self-attested. CI's receipt is the attested one,
  // and it is checked with `gh attestation verify`, not here.
  result.attestation = attestationStanding(receipt);
  return result;
}

const result = evaluate();

if (asHook) {
  const hookInput = readStdinJson();
  if (hookInput.stop_hook_active === true) {
    process.exit(0);
  }
  if (!result.valid) {
    // The walk's vocabulary (walk-legibility L3): this gate IS the Prove
    // stage refusing to close — same fact, same enforcement, words that match
    // every other surface. The precise reason stays verbatim beneath.
    //
    // The REFUSAL never changes with a lane in flight — no receipt yet means
    // not done, and that is the whole point of the gate. What changes is the
    // INSTRUCTION: "run the lane" is wrong advice when one is already running,
    // and a gate that tells you to do the thing you are doing trains you to
    // stop reading it.
    //
    // An agent HOLDING the tree is the same shape of correction one level out:
    // a lane in flight explains a receipt that is about to arrive, a hold
    // explains a tree that is mid-edit and would not compile if you ran one.
    // Reported ~15 false alarms in one evening at payment-blueprint, every one
    // of which should have said "wait". It only ever changes the instruction —
    // and only for the two refusals a working agent legitimately causes
    // (holdExplains), never for a FAIL, a forgery or a skipped device tier,
    // where the hold is not the cause and offering it would misdirect.
    const flight = laneInFlight();
    const hold = holdExplains(result, readReceipt(ROOT)) ? assessHold(readHold(ROOT)) : null;
    const act = flight
      ? `A full check is ALREADY RUNNING${flight.step ? ` (${flight.step}${flight.index && flight.total ? `, step ${flight.index} of ${flight.total}` : ""})` : ""} — ` +
        `wait for it to finish and commit its receipt. Do NOT start a second one; two lanes fight over the same build directory.`
      : hold?.held
        ? describeHold(hold)
        : `Run \`${LANE_COMMAND}\` (it checks every promise and writes the receipt), ` +
          "commit the receipt, or see README §Verification enforcement to bypass.";
    process.stderr.write(
      `■ Prove — not done: the promises are not yet checked against this tree. ${result.reason}. ${act}\n`,
    );
    process.exit(2);
  }
  process.exit(0);
}

// The rung NEVER travels alone (see evaluate() above): a rung is a claim in one
// pack's vocabulary, so the pack is named beside it, and a receipt that names no
// pack is SAID to name none rather than being rendered as if the omission did
// not matter. A rung whose pack is unknown is comparable to nothing, and that is
// a fact about the evidence, not a formatting detail to be tidied away.
const rungSuffix = result.evidenceLevel
  ? ` — evidence ${result.evidenceLevel.rung} · ${result.evidenceLevel.name}` +
    (result.packId ? ` · pack ${result.packId}` : " · pack UNNAMED (this rung is comparable to nothing)")
  : "";

if (asJson) {
  console.log(JSON.stringify(result, null, 2));
} else if (result.valid) {
  console.log(`VALID — ${result.reason}${rungSuffix}`);
  if (result.attestation) {
    console.log(
      `  ${result.attestation.line} (this committed file is its producer's word; ` +
        "CI's own receipt is checked with `gh attestation verify`)",
    );
  }
} else {
  console.error(`INVALID — ${result.reason}`);
}

process.exit(result.valid ? 0 : 1);
