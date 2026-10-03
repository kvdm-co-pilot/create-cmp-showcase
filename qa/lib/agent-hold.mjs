// agent-hold.mjs — "an agent is working in this tree right now."
//
// TWO PROBLEMS, ONE MISSING FACT. Both were reported from payment-blueprint's
// adoption on 2026-09-04, and both are the same absence:
//
//   1. LIVENESS. There was no way to answer "is the agent working, or wedged?"
//      without filesystem archaeology. The lead architect guessed twice with two
//      separately broken instruments — a `find` that excluded `build/` (the only
//      directory a proof run writes to) and a `find -newermt` that reported zero
//      writes in 45 minutes while `ls -lT` showed one at 10 — and on the first
//      guess killed a healthy agent mid-proof. An instrument that cannot see the
//      thing it exists to detect is worse than no instrument: it is confidently
//      wrong, which is the same failure class GATE-RULES Rule 1 exists for.
//
//   2. FALSE ALARMS. The Stop hook fires identically whether a receipt is stale
//      because nobody ran the lane or because a subagent is mid-commit on a
//      half-adopted port. It fired ~15 times in one evening while the correct
//      action every time was to WAIT. Anthropic's tool-design guidance is
//      explicit that an error must communicate "specific and actionable
//      improvements"; an alarm whose advice is wrong every time it fires trains
//      its reader to ignore it, which is strictly worse than silence.
//
// WHAT THIS IS NOT. It is not a lock — nothing waits on it, nothing is excluded
// by it. It is not a second journal: the flight recorder still owns lane history
// that belongs in the repo. It is a DECLARATION with an expiry, in the same
// ephemeral, gitignored, hash-excluded family as qa/.plan.json and
// qa/.request.json — because a fact about who is typing must never be able to
// invalidate a receipt.
//
// A HOLD CHANGES THE ADVICE, NEVER THE VERDICT. The Stop hook still refuses:
// no receipt yet means not done, and a file an agent writes about itself must
// never be able to end a turn — that would be turning the gate off by writing a
// file, which is the attack the whole harness exists to refuse. This follows the
// precedent already set for a lane in flight (qa/receipt-check.mjs): same
// refusal, different instruction. "Run the lane" is wrong advice when the tree
// is mid-edit and would not compile, and a gate that tells you to do the thing
// you are already doing trains you to stop reading it.
//
// THE ASYMMETRY THAT KEEPS IT HONEST. A hold EXPLAINS the absence of fresh
// evidence. It never explains CONTRADICTING evidence. A red receipt, a forged
// receipt, a skipped device tier: for those the hold is not the reason and
// saying so would mislead. Only two refusals are explicable by a hold — "no
// receipt yet" and "the tree moved since a PASSing receipt" — exactly the two
// states a working agent legitimately produces, and nothing else.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/agent-hold.mjs in the
// create-cmp repo. The copy in a generated project's qa/lib/ is vendored
// byte-identical at scaffold time — edit the package source, then run
// `node scripts/sync-harness.mjs` in the create-cmp repo.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Ephemeral, gitignored, and excluded from the receipt's hashed input surface
 * (qa/lib/inputs-hash.mjs EXCLUDED_PREFIXES) — the same family as .plan.json.
 */
export const HOLD_REL = "qa/.agent-hold.json";

/**
 * A heartbeat older than this is a crashed writer, not a live agent. The same
 * bound every other marker consumer in this lane applies (qa/lib/plan.mjs), for
 * the same reason: a process that dies leaves its file behind, so freshness —
 * never presence — is what makes a marker mean anything.
 */
export const HEARTBEAT_FRESH_MS = 5 * 60 * 1000;

/**
 * A hold this old is a wedge, not work. Past the ceiling the hook resumes
 * blocking even while heartbeats keep arriving: an agent that has held the tree
 * for three quarters of an hour is exactly the case the human needed to see, and
 * a heartbeat proves the process is alive, never that it is making progress.
 */
export const HOLD_CEILING_MS = 45 * 60 * 1000;

const MAX_TEXT = 200;

const clip = (s, n = MAX_TEXT) => (typeof s === "string" ? s.trim().slice(0, n) : "");

/** Fail-soft like every other status reader here: unreadable reads as absent. */
export function readHold(root) {
  try {
    const parsed = JSON.parse(fs.readFileSync(path.join(root, ...HOLD_REL.split("/")), "utf8"));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function writeJson(root, value) {
  try {
    const p = path.join(root, ...HOLD_REL.split("/"));
    fs.mkdirSync(path.dirname(p), { recursive: true });
    fs.writeFileSync(p, `${JSON.stringify(value, null, 2)}\n`);
    return { ok: true, hold: value };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

/**
 * Claim the tree. `holder` is a name a human will read in an alarm — an agent
 * or session label, not a UUID: Anthropic's tool guidance is that agents (and
 * the people reading after them) do far better with natural-language
 * identifiers than with opaque ids, and this string's whole job is to be read
 * at 3am by someone deciding whether to kill a process.
 */
export function claimHold(root, { holder, note = "", now = Date.now() } = {}) {
  const name = clip(holder, 80) || "an agent";
  const existing = readHold(root);
  const at = existing && assessHold(existing, now).held ? existing.at : new Date(now).toISOString();
  return writeJson(root, {
    holder: name,
    note: clip(note),
    at,
    heartbeatAt: new Date(now).toISOString(),
  });
}

/**
 * Still here, still working. Optionally re-states what "here" means — an agent
 * that only says "alive" is barely better than the `find` that started this.
 */
export function beatHold(root, { note, now = Date.now() } = {}) {
  const existing = readHold(root);
  if (!existing) return { ok: false, error: "no hold to beat — claim one first" };
  return writeJson(root, {
    ...existing,
    note: note === undefined ? existing.note : clip(note),
    heartbeatAt: new Date(now).toISOString(),
  });
}

export function releaseHold(root) {
  try {
    fs.rmSync(path.join(root, ...HOLD_REL.split("/")), { force: true });
    return { ok: true };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

/**
 * Is a hold in force, and what does it say?
 *
 * Every negative branch names WHY, because this feeds an alarm's text: "no
 * agent holds the tree" and "an agent claimed it 50 minutes ago and is past the
 * ceiling" call for opposite actions by the human reading them.
 *
 * @param {object|null} hold
 * @param {number} now
 * @returns {{held: boolean, reason: string, holder?: string, note?: string,
 *            heldMs?: number, sinceBeatMs?: number, expired?: boolean}}
 */
export function assessHold(hold, now = Date.now()) {
  if (!hold || typeof hold !== "object") return { held: false, reason: "no agent holds the tree" };
  const at = Date.parse(hold.at);
  const beat = Date.parse(hold.heartbeatAt ?? hold.at);
  if (Number.isNaN(at) || Number.isNaN(beat)) return { held: false, reason: "the hold file has no readable timestamp" };

  const heldMs = Math.max(0, now - at);
  const sinceBeatMs = Math.max(0, now - beat);
  const holder = clip(hold.holder, 80) || "an agent";
  const note = clip(hold.note);
  const base = { holder, note, heldMs, sinceBeatMs };

  if (sinceBeatMs > HEARTBEAT_FRESH_MS) {
    return {
      ...base,
      held: false,
      expired: true,
      reason: `${holder} last checked in ${formatAge(sinceBeatMs)} — that is a crashed writer, not a live agent`,
    };
  }
  if (heldMs > HOLD_CEILING_MS) {
    return {
      ...base,
      held: false,
      expired: true,
      reason: `${holder} has held the tree for ${formatAge(heldMs)}, past the ${formatAge(HOLD_CEILING_MS)} ceiling — a heartbeat proves the process is alive, not that it is progressing`,
    };
  }
  return { ...base, held: true, reason: `${holder} has held the tree for ${formatAge(heldMs)}` };
}

/**
 * The ONE line an alarm prints instead of demanding a lane run. It says who,
 * since when, what they said they were doing, and what the reader should do —
 * "specific and actionable", which the alarm it replaces was not.
 */
export function describeHold(assessment, { root = ROOT, cwd = process.cwd() } = {}) {
  if (!assessment?.held) return null;
  const what = assessment.note ? ` (${assessment.note})` : "";
  return (
    `${assessment.holder} has held this tree for ${formatAge(assessment.heldMs)}${what} — staleness is expected while it works. ` +
    `Wait for it rather than starting a lane on a half-edited tree; \`${fromRoot(root, cwd, "node qa/plan.mjs --release")}\` if it is gone.`
  );
}

/** This file sits at qa/lib/, so the project root is two directories up. */
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

/**
 * A root-relative command spelled so it runs from ANY cwd — KD-235, the class
 * KD-215 fixed for the Stop gate's remedy (receipt-check.mjs laneCommand()).
 * From the root itself the short spelling stays; from anywhere else it is
 * prefixed with a `cd` to the root, quoted the same way.
 */
function fromRoot(root, cwd, command) {
  const here = (p) => {
    try {
      return fs.realpathSync(p);
    } catch {
      return path.resolve(p);
    }
  };
  if (here(cwd) === here(root)) return command;
  const dir = /["$`\\!]/.test(root) ? `'${root.replace(/'/g, "'\\''")}'` : `"${root}"`;
  return `cd ${dir} && ${command}`;
}

/**
 * Does a hold EXPLAIN this refusal? (It never lifts it — see the header.)
 *
 * The whitelist is the safety property, and it is deliberately two entries
 * long. A hold explains why fresh evidence is ABSENT — no receipt yet, or the
 * tree has moved under one — because those are the two states a working agent
 * legitimately produces. It never explains a receipt that says something is
 * wrong: for a FAIL, a forgery, a skipped device tier or an unreadable surface
 * the hold is simply not the cause, and offering it as one would send the
 * reader to wait for an agent when the actual problem is a red test. Inverting
 * this to a blacklist would mean every refusal added later is treated as
 * agent-explicable by default, which is how an alarm starts lying.
 *
 * @param {{valid: boolean, reason?: string}} result
 * @param {object|null} receipt
 * @returns {boolean}
 */
export function holdExplains(result, receipt) {
  if (!result || result.valid) return false;
  // No receipt at all: the agent has not finished enough to run the lane.
  if (receipt === null || receipt === undefined) return true;
  // The tree moved under a receipt that itself PASSED — the signature of an
  // agent mid-edit. A receipt that was not a PASS is contradicting evidence and
  // is never excused, whatever moved since.
  return receipt.verdict === "PASS" && /^source changed since the receipt/.test(String(result.reason ?? ""));
}

/** "40s" / "12 min" / "1h 5m" — freshness a human can weigh at a glance. */
export function formatAge(ms) {
  if (!(ms >= 0)) return "an unknown time";
  if (ms < 90_000) return `${Math.round(ms / 1000)}s`;
  if (ms < 90 * 60_000) return `${Math.round(ms / 60_000)} min`;
  const h = Math.floor(ms / 3_600_000);
  return `${h}h ${Math.round((ms - h * 3_600_000) / 60_000)}m`;
}
