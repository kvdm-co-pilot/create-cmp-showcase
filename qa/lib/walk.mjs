// walk.mjs — the walk: where every open change stands, whose turn it is, and
// what arrives unplanned. docs/features/walk-status.md is the brief of record.
//
// A PROJECTION, not new state (D1): everything here re-renders the derivation
// getFeatureBoard already computes (phase, nextStep with owner, clause
// coverage, provenDone). No new ledger, nothing agent-declared, nothing to
// forget to update — if a number here could disagree with the board, this
// module is wrong.
//
// Vocabulary (D2/D3): six stages — Decide · Design · Contract · Build ·
// Prove · Sign-off — and clauses rendered as PROMISES ("keeping promise 5 of
// 7"). Mechanically exact: a clause is a behavioral promise, a citing green
// test is that promise kept, provenDone is all of them kept with the receipt
// attesting this tree.

import fs from "node:fs";
import path from "node:path";

import { getFeatureBoard, getApprovalStatuses, readJournal } from "./approvals.mjs";
import { CLAUSE_LINE_RE } from "./spec-coverage.mjs";

/** The six stages, in walk order. `label` is the only user-facing name (D2). */
export const STAGES = [
  { key: "decide", label: "Decide" },
  { key: "design", label: "Design" },
  { key: "contract", label: "Contract" },
  { key: "build", label: "Build" },
  { key: "prove", label: "Prove" },
  { key: "signoff", label: "Sign-off" },
];

/** nextStep.key -> the stage that step belongs to. `closed` maps to none. */
const STEP_STAGE = {
  "sign-brief": "decide",
  "re-approve": "decide",
  design: "design",
  audit: "design",
  "sign-design": "design",
  contract: "contract",
  "sign-spec": "contract",
  build: "build",
  // A sanctioned redesign is promises being re-kept — Build, not a seventh stage.
  redesign: "build",
  prove: "prove",
  accept: "signoff",
};

/**
 * A promise's human title: the clause line's own words after the id, first
 * sentence-ish, capped. "" when the line carries nothing after the id — the
 * card then falls back to the bare id rather than inventing prose.
 */
function promiseTitle(rest) {
  const cleaned = String(rest ?? "")
    .replace(/^[\s—:-]+/, "")
    .replace(/\*\*/g, "")
    .trim();
  if (cleaned === "") return "";
  const cut = cleaned.search(/[.;]\s/);
  const first = cut === -1 ? cleaned : cleaned.slice(0, cut);
  return first.length > 110 ? `${first.slice(0, 107)}…` : first;
}

/**
 * The spec's promises, in file order: id, title, withdrawn. Reads the spec
 * directly (same CLAUSE_LINE_RE the coverage gate uses) because the board's
 * clause list carries ids only — the words are the whole point here (D3).
 * @returns {Array<{id: string, title: string, withdrawn: boolean}>}
 */
export function listPromises(root, specRel) {
  let text;
  try {
    text = fs.readFileSync(path.join(root, specRel), "utf8");
  } catch {
    return [];
  }
  const out = [];
  for (const line of text.split("\n")) {
    const m = line.match(CLAUSE_LINE_RE);
    if (!m) continue;
    const rest = line.slice(m[0].length).replace(/^~~/, "");
    out.push({ id: m[2], title: promiseTitle(rest), withdrawn: Boolean(m[1]) });
  }
  return out;
}

/** The human stops remaining from (and including) the current stage. */
function remainingStops(stages, currentIdx) {
  const stops = [];
  for (let i = Math.max(currentIdx, 0); i < stages.length; i++) {
    const s = stages[i];
    if (s.state === "done" || s.state === "skipped") continue;
    if (s.key === "decide") stops.push("Decide");
    if (s.key === "design") stops.push("Design sign-off");
    if (s.key === "contract") stops.push("Contract");
    if (s.key === "signoff") stops.push("Sign-off");
  }
  return stops;
}

/**
 * One feature's walk. Stage states are POSITIONAL around the derived current
 * step: the board's deriveNextStep already resolves every competing condition
 * (drift outranks acceptance, design before contract, redesign splits by
 * provenDone) to ONE next act — stages before it are done, after it pending.
 * Design is `skipped` when the feature honestly has no UI surface (D2).
 */
function walkOfFeature(root, f) {
  const currentKey = STEP_STAGE[f.nextStep?.key] ?? null; // null => closed
  const currentIdx = currentKey ? STAGES.findIndex((s) => s.key === currentKey) : STAGES.length;
  const stages = STAGES.map((s, i) => {
    if (s.key === "design" && f.design === null)
      return { ...s, state: "skipped", note: "no UI surface" };
    return { ...s, state: i < currentIdx ? "done" : i === currentIdx ? "current" : "pending" };
  });

  const promises = listPromises(root, f.specRel).filter((p) => !p.withdrawn);
  // The promise being kept NOW: first live clause without a citing test —
  // board clause order, titles from the spec's own words.
  const citedIds = new Set(f.clauses.filter((c) => c.cited).map((c) => c.id));
  const current = promises.find((p) => !citedIds.has(p.id)) ?? null;

  // Whose turn (D4): from the board's own owner, never re-derived.
  const owner = f.nextStep?.owner ?? null;
  const you =
    owner === null
      ? { turn: "none", act: null }
      : owner === "human"
        ? { turn: "you", act: f.nextStep.label }
        : owner.includes("human")
          ? { turn: "agent", act: f.nextStep.label, then: "your signature" }
          : { turn: "agent", act: f.nextStep.label };

  return {
    name: f.name,
    phase: f.phase,
    open: f.phase !== "accepted",
    stages,
    currentStage: currentKey,
    promises: { total: f.total, kept: f.covered, current },
    you,
    stops: remainingStops(stages, currentIdx),
    doneReason: f.doneReason,
  };
}

/**
 * Everything the status surfaces render (D5): open walks, plus ARRIVALS (D7)
 * — governed artifacts drifted or reopened that NO open walk accounts for
 * (the board's undeclared set, widened to reopens, e.g. a harness upgrade's
 * rule-change wave). Each arrival carries the journal's last reopen reason so
 * the surface can say WHY it arrived, not only that it did.
 * @param {string} root
 * @returns {{available: boolean, reason?: string, walks: object[], arrivals: object[]}}
 */
export function deriveWalks(root) {
  let board, statuses;
  try {
    board = getFeatureBoard(root);
    statuses = getApprovalStatuses(root);
  } catch (err) {
    return { available: false, reason: err?.message ?? String(err), walks: [], arrivals: [] };
  }

  // Open walks, MOST RECENTLY ACTIVE first (journal recency across the walk's
  // family) — five features parked at Sign-off must not bury the one being
  // worked on today (seen on the showcase: bfl-catalog, alphabetical, outshouted
  // the live navigation-ia walk in the statusline).
  const journalForOrder = readJournal(root);
  const lastActivity = (name) => {
    const family = new Set([`feature-brief:${name}`, `feature-design:${name}`, `feature-spec:${name}`]);
    for (let i = journalForOrder.length - 1; i >= 0; i--) {
      if (family.has(journalForOrder[i].artifact)) return i;
    }
    return -1;
  };
  const walks = board.features
    .map((f) => walkOfFeature(root, f))
    .filter((w) => w.open)
    .sort((a, b) => lastActivity(b.name) - lastActivity(a.name));

  // Ids an open walk accounts for: its own family (brief/design/spec) and its
  // declared touches. A reopened design mid-walk is that walk's Design stage,
  // never an arrival (brief, edge cases).
  const owned = new Set();
  for (const f of board.features) {
    if (f.phase === "accepted") continue;
    owned.add(`feature-brief:${f.name}`);
    owned.add(`feature-design:${f.name}`);
    owned.add(`feature-spec:${f.name}`);
    for (const t of f.touches) owned.add(t.id);
  }
  const journal = journalForOrder;
  const lastReopenReason = (id) => {
    for (let i = journal.length - 1; i >= 0; i--) {
      if (journal[i].artifact === id && journal[i].verb === "reopen") return journal[i].reason ?? null;
    }
    return null;
  };
  const arrivals = statuses
    .filter((s) => (s.status === "changed-since-approval" || s.status === "reopened") && !owned.has(s.id))
    .map((s) => ({
      id: s.id,
      label: s.label ?? s.id,
      status: s.status,
      reason: s.status === "reopened" ? lastReopenReason(s.id) : "changed since its signature",
    }));

  return { available: true, walks, arrivals };
}

// ── Renderings — one grammar, four slots (D4) ────────────────────────────────

const bar = (stages) =>
  stages.map((s) => (s.state === "done" ? "●" : s.state === "current" ? "◐" : s.state === "skipped" ? "·" : "○")).join("");

const stageLabel = (w) => STAGES.find((s) => s.key === w.currentStage)?.label ?? "Closed";

/** The walk whose state is loudest: YOUR TURN beats agent-working. */
function loudest(walks) {
  return walks.find((w) => w.you.turn === "you") ?? walks[0] ?? null;
}

/**
 * The always-on one-liner (statusline). "" when there is nothing to say — an
 * ungoverned project's statusline stays silent, never fabricated.
 */
export function renderStatusline({ available, walks, arrivals }) {
  if (!available || walks.length === 0) return "";
  const w = loudest(walks);
  const extra = walks.length > 1 ? ` · +${walks.length - 1} walk${walks.length > 2 ? "s" : ""}` : "";
  const arrived = arrivals.length > 0 ? ` · ▲${arrivals.length} arrived` : "";
  if (w.you.turn === "you") return `■ YOUR TURN — ${w.name}: ${w.you.act}${extra}${arrived}`;
  const now =
    w.currentStage === "build" && w.promises.total > 0
      ? `keeping promise ${Math.min(w.promises.kept + 1, w.promises.total)}/${w.promises.total}`
      : stageLabel(w);
  return `${w.name} ${bar(w.stages)} ${now} · you: nothing${extra}${arrived}`;
}

/** One walk's full card — the CLI default and the loud stop-card's body. */
export function renderCard(w) {
  const line = w.stages
    .map((s) => `${s.state === "current" ? "▶" : s.state === "done" ? "●" : s.state === "skipped" ? "·" : "○"} ${s.label}${s.state === "skipped" ? ` (${s.note})` : ""}`)
    .join("  ");
  const nowLine =
    w.currentStage === "build" && w.promises.current
      ? `Now: keeping promise ${Math.min(w.promises.kept + 1, w.promises.total)} of ${w.promises.total} — “${w.promises.current.title || w.promises.current.id}”`
      : `Now: ${w.you.act ?? w.doneReason}`;
  const youLine =
    w.you.turn === "you"
      ? `■ YOUR TURN: ${w.you.act}`
      : w.you.turn === "agent"
        ? `You: nothing needed${w.stops.length ? ` · next stop${w.stops.length > 1 ? "s" : ""} for you: ${w.stops.join(", ")}` : ""}`
        : "Closed.";
  return `${w.name} — stage: ${stageLabel(w)}\n${line}\n${nowLine}\n${youLine}`;
}

/**
 * The per-prompt context block (UserPromptSubmit --inject): position + the
 * standing protocol reminders, re-delivered every turn so the narration rules
 * are decay-proof — re-told, never remembered (D5/D6).
 */
export function renderInject({ available, walks, arrivals }) {
  if (!available || (walks.length === 0 && arrivals.length === 0)) return "";
  const parts = [];
  if (walks.length > 0) {
    parts.push("[walk-status — derived from the ledgers; render this state, never your own memory of it]");
    for (const w of walks) parts.push(renderCard(w));
  }
  for (const a of arrivals)
    parts.push(`▲ ARRIVED, UNPLANNED — ${a.label} (${a.status}): ${a.reason ?? "no recorded reason"}. Offer: handle now, or after the current walk lands (recommended: after).`);
  parts.push(
    "Protocol: speak stages as Decide·Design·Contract·Build·Prove·Sign-off and clauses as promises. Quiet while working (one line per stage transition). At any human gate, render the full stop card (stage, what it is in plain words, exactly what to do, what comes after). Never open a second walk silently.",
  );
  return parts.join("\n\n");
}
