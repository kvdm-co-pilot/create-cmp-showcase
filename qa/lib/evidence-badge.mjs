// The README's evidence badge — the evidence ladder, rendered where a human
// actually looks (roadmap §10 item 2: "render the rung in the console and
// README badge").
//
// The console already shows the rung (rail foot, Evidence section, receipt
// timeline). The README is the surface a human meets FIRST, and the one that
// travels — into a GitHub repo page, a PR, a screenshot in a deck. That makes
// it the surface where an overclaim does the most damage, so the badge obeys
// one rule above all others:
//
//   **The badge is a statement about a specific commit, never about "now".**
//
// A badge that says "L2 device" says nothing about whether the code has moved
// since. So it never renders a bare rung: it renders the rung AND the commit
// it was attested against AND the date. That sentence stays true forever — a
// reader can see at a glance whether the sha still matches what they are
// looking at. Everything else follows from the same rule: no receipt says so,
// a FAIL says so, and a --fast run (which the ladder deliberately grants no
// rung) says so rather than borrowing the last good one.
//
// Written by the lane AFTER the receipt (it is an output derived from the
// receipt, never a gate), and committed alongside it.

import fs from "node:fs";
import path from "node:path";

export const README_REL_PATH = "README.md";
export const BADGE_SECTION_ID = "evidence";

const MARKER_RE = new RegExp(
  `<!-- cmp:generated ${BADGE_SECTION_ID} -->\\n([\\s\\S]*?)<!-- /cmp:generated -->`
);

/** Shields.io colours, one per rung — the ladder read at a glance. */
const RUNG_COLOR = {
  L0: "9E9E9E", // scaffold — grey: a green build, nothing proven about behavior
  L1: "42A5F5", // desktop — blue
  L2: "26A69A", // device — teal
  L3: "43A047", // release — green: the strongest rung this harness can attest
};

/** shields.io escaping: `-` → `--`, `_` → `__`, space → `_`. */
function shieldEscape(s) {
  return String(s).replace(/-/g, "--").replace(/_/g, "__").replace(/ /g, "_");
}

/**
 * The badge body for a receipt — Markdown, no trailing newline handling (the
 * caller frames it). Pure: every degraded state has its own honest rendering
 * and NONE of them fall back to a rung.
 *
 * @param {object|null} receipt parsed qa/evidence/latest.json, or null
 * @returns {string} Markdown
 */
export function renderEvidenceBadge(receipt) {
  const link = "https://github.com/kvdm-co-pilot/create-cmp";
  const badge = (label, message, color, title) =>
    `[![${title}](https://img.shields.io/badge/${shieldEscape(label)}-${shieldEscape(message)}-${color})](${link})`;

  if (!receipt || typeof receipt !== "object") {
    return `${badge("evidence", "none yet", "9E9E9E", "No evidence receipt")} — no verify receipt yet. Run \`node qa/verify.mjs\`.`;
  }

  const verdict = typeof receipt.verdict === "string" ? receipt.verdict : "?";
  const mode = receipt.mode === "fast" ? "fast" : "full";
  const sha = typeof receipt.commit?.sha === "string" ? receipt.commit.sha.slice(0, 7) : null;
  const dirty = Array.isArray(receipt.commit?.dirty) ? receipt.commit.dirty.length : 0;
  const when = typeof receipt.generatedAt === "string" ? receipt.generatedAt.slice(0, 10) : null;

  // Provenance is not decoration — it is what keeps the sentence true later.
  const at = sha ? ` at \`${sha}\`` : "";
  const on = when ? ` on ${when}` : "";
  const uncommitted =
    dirty > 0
      ? ` The tree had ${dirty} uncommitted file${dirty === 1 ? "" : "s"} at attestation, so this describes that run, not that commit.`
      : "";

  if (verdict !== "PASS") {
    return `${badge("evidence", `lane ${verdict}`, "E53935", `Verify lane ${verdict}`)} — the last lane run${at}${on} did not pass. No rung is earned by a failed lane.${uncommitted}`;
  }

  if (mode === "fast") {
    // The inner loop is a signal, never evidence. Borrowing the previous
    // full run's rung here is exactly the lie the ladder exists to prevent.
    return `${badge("evidence", "fast run, no rung", "9E9E9E", "Fast run — no evidence rung")} — the last run${at}${on} was \`--fast\`: the device and release tiers were skipped, so it earns no rung.${uncommitted} Run \`node qa/verify.mjs\` for evidence.`;
  }

  const level = receipt.evidenceLevel;
  if (!level || typeof level.rung !== "string" || typeof level.name !== "string") {
    return `${badge("evidence", `PASS, rung unrecorded`, "9E9E9E", "Lane PASS, no rung recorded")} — the lane passed${at}${on} but the receipt records no evidence rung.`;
  }

  const color = RUNG_COLOR[level.rung] || "9E9E9E";
  const satisfied = Array.isArray(level.satisfiedBy) && level.satisfiedBy.length
    ? ` Earned by: ${level.satisfiedBy.map((s) => `\`${s}\``).join(", ")}.`
    : "";
  return (
    `${badge("evidence", `${level.rung} ${level.name}`, color, `Evidence ${level.rung} — ${level.name}`)}` +
    ` — the verify lane passed${at}${on} at rung **${level.rung} · ${level.name}**.` +
    `${satisfied}${uncommitted}` +
    ` The rung describes that run; it says nothing about changes made since.`
  );
}

/**
 * Read the project's receipt and rewrite README.md's `cmp:generated evidence`
 * block from it. Never creates the marker — a project that removed the block
 * has opted out, and that is honoured silently.
 *
 * NOTE the asymmetry with renderEvidenceBadge above: the RENDERER is total —
 * every receipt, including a `--fast` one, has an honest rendering. The WRITER
 * is selective: a fast receipt is not written to the README at all. Two
 * reasons, and the second is the load-bearing one:
 *   1. The badge reports EVIDENCE. A fast run produces none, so it has nothing
 *      to say — and overwriting a true statement about a real full-lane run
 *      with "no rung" loses information rather than adding honesty.
 *   2. `qa/watch.mjs` runs the fast lane on every save. A writer that fired
 *      there would rewrite README.md on every keystroke-to-save cycle, putting
 *      a permanently-dirty file in the inner loop. A recorder must not disturb
 *      what it records.
 * The badge therefore always describes the last run that could BEAR evidence,
 * and says so by naming that run's commit and date.
 *
 * @param {string} root project root
 * @returns {{changed: boolean, reason?: string}}
 */
export function updateReadmeBadge(root) {
  const readmePath = path.join(root, README_REL_PATH);
  let readme;
  try {
    readme = fs.readFileSync(readmePath, "utf8");
  } catch {
    return { changed: false, reason: `${README_REL_PATH} not found` };
  }
  if (!MARKER_RE.test(readme)) {
    return { changed: false, reason: `${README_REL_PATH} has no cmp:generated ${BADGE_SECTION_ID} block` };
  }

  let receipt = null;
  try {
    receipt = JSON.parse(fs.readFileSync(path.join(root, "qa", "evidence", "latest.json"), "utf8"));
  } catch {
    receipt = null; // no receipt / unreadable → the "none yet" rendering, never a guess
  }

  if (receipt && receipt.mode === "fast") {
    return { changed: false, reason: "fast run — the inner loop bears no evidence, so the badge is left as it stands" };
  }
  // The same rule for the two other receipts qa/receipt-check.mjs refuses as
  // done-evidence: smoke (Rule 0 — proves the framework, never the change) and
  // nightly (proves the harness and the tree's invariants). Both derive no
  // rung, and a smoke run — scripts/framework-check.mjs runs one on every
  // scaffold — was rewriting a true L1 badge to "rung unrecorded". Found on
  // 2026-09-03 by deriving the affected filter on a fresh app: README.md was
  // the dirty file. Receipts predating `stage` are read by profile.
  const stage = receipt && (typeof receipt.stage === "string" ? receipt.stage : receipt.profile);
  if (stage === "smoke" || stage === "nightly") {
    return { changed: false, reason: `${stage} run — refused as done-evidence, so the badge is left as it stands` };
  }

  const body = `${renderEvidenceBadge(receipt)}\n`;
  const next = readme.replace(
    MARKER_RE,
    () => `<!-- cmp:generated ${BADGE_SECTION_ID} -->\n${body}<!-- /cmp:generated -->`
  );
  if (next === readme) return { changed: false };
  fs.writeFileSync(readmePath, next);
  return { changed: true };
}
