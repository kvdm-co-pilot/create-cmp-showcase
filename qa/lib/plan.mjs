// plan.mjs — the live chain: what the CURRENT REQUEST is, which step the
// agent is on, and what comes next. docs/features/studio-drive-mode.md is the
// brief of record; this is D8's itinerary (walk-status.md) promoted from
// kickoff prose to a tracked object.
//
// PROVENANCE TIERS, each rendered as what it is — this is the one surface in
// the harness that is not purely derived, and the design is honest about it:
//
//   1. The REQUEST is machinery-owned: the UserPromptSubmit hook records the
//      human's own prompt verbatim (walk-status --inject reads it from the
//      hook's stdin). No agent claim involved.
//   2. The STEPS are agent-declared: written once at kickoff (`node
//      qa/plan.mjs --set`), advanced as work lands (`--step N`). Every
//      rendering carries the declaration's age — a stale plan reads as
//      stale, never as true.
//   3. The CORROBORATION is derived and overrides: the lane/render markers
//      (composeApp/build/.cmp-lane-in-progress / .cmp-render-in-progress,
//      mtime-bounded like every other consumer) say what is ACTUALLY running
//      right now, regardless of what was declared.
//
// THE PLAN GATES NOTHING. The walk (walk.mjs — a pure projection) stays the
// load-bearing truth for doneness; the chain is a windshield, not an
// instrument. Both live in EPHEMERAL dot-files that are excluded from the
// receipt's hashed input surface (qa/lib/inputs-hash.mjs EXCLUDED_PREFIXES —
// a request recorded on every prompt must never invalidate a receipt) and
// gitignored on fresh scaffolds.
//
// FAIL-SOFT EVERYWHERE: readers return null, writers return {ok:false} — a
// status surface never breaks the work it reports on.

import fs from "node:fs";
import path from "node:path";

export const PLAN_REL = "qa/.plan.json";
export const REQUEST_REL = "qa/.request.json";

// A marker older than this is a crashed writer, not a live run — the same
// bound qa/watch.mjs and the preview daemon apply to the same files.
const MARKER_FRESH_MS = 5 * 60 * 1000;
const MAX_REQUEST_CHARS = 500;
const MAX_STEPS = 20;
const MAX_LABEL_CHARS = 120;

function readJson(p) {
  try {
    return JSON.parse(fs.readFileSync(p, "utf8"));
  } catch {
    return null;
  }
}

function writeJson(p, value) {
  try {
    fs.writeFileSync(p, `${JSON.stringify(value, null, 2)}\n`);
    return { ok: true };
  } catch (err) {
    return { ok: false, reason: err?.message ?? String(err) };
  }
}

/**
 * Record the human's latest prompt — tier 1, machinery-owned. Called by the
 * UserPromptSubmit hook path with the hook's own `prompt` field; never by
 * the agent with words of its own choosing.
 */
export function recordRequest(root, text) {
  const t = typeof text === "string" ? text.trim() : "";
  if (t === "") return { ok: false, reason: "empty prompt — nothing to record" };
  return writeJson(path.join(root, REQUEST_REL), {
    text: t.length > MAX_REQUEST_CHARS ? `${t.slice(0, MAX_REQUEST_CHARS - 1)}…` : t,
    at: new Date().toISOString(),
  });
}

/** @returns {{text: string, at: string}|null} */
export function readRequest(root) {
  const r = readJson(path.join(root, REQUEST_REL));
  return r && typeof r.text === "string" ? r : null;
}

/**
 * Declare the chain — tier 2, agent-declared, said so on every rendering.
 * `title` is the agent's triage restatement of the ask (the contract already
 * mandates one); `steps` are plain labels in order. Declaring replaces any
 * previous chain: one request, one chain.
 */
export function setPlan(root, { title, feature, steps } = {}) {
  const labels = (Array.isArray(steps) ? steps : [])
    .map((s) => String(s ?? "").trim())
    .filter((s) => s !== "")
    .slice(0, MAX_STEPS)
    .map((s) => (s.length > MAX_LABEL_CHARS ? `${s.slice(0, MAX_LABEL_CHARS - 1)}…` : s));
  if (labels.length === 0) return { ok: false, reason: "a chain needs at least one step" };
  return writeJson(path.join(root, PLAN_REL), {
    title: typeof title === "string" && title.trim() !== "" ? title.trim() : null,
    feature: typeof feature === "string" && feature.trim() !== "" ? feature.trim() : null,
    steps: labels.map((label, i) => ({ n: i + 1, label, done: false })),
    current: 1,
    updatedAt: new Date().toISOString(),
  });
}

/**
 * Advance to step `n`: everything before it is done, `n` is current. `--done`
 * (n past the end) closes the chain. Refuses without a declared chain —
 * advancing nothing would fabricate a plan that was never stated.
 */
export function markStep(root, n) {
  const plan = readJson(path.join(root, PLAN_REL));
  if (!plan || !Array.isArray(plan.steps) || plan.steps.length === 0)
    return { ok: false, reason: "no declared chain — declare one first: node qa/plan.mjs --set \"step | step | …\"" };
  const step = Number(n);
  if (!Number.isInteger(step) || step < 1 || step > plan.steps.length + 1)
    return { ok: false, reason: `step must be 1..${plan.steps.length + 1} (=${plan.steps.length + 1} closes the chain), got ${n}` };
  for (const s of plan.steps) s.done = s.n < step;
  plan.current = step > plan.steps.length ? null : step;
  plan.updatedAt = new Date().toISOString();
  return writeJson(path.join(root, PLAN_REL), plan).ok ? { ok: true, plan } : { ok: false, reason: "could not write the chain" };
}

/** @returns {object|null} the declared chain, or null. */
export function readPlan(root) {
  const p = readJson(path.join(root, PLAN_REL));
  return p && Array.isArray(p.steps) ? p : null;
}

/** Clear the chain (a landed request leaves no stale windshield behind). */
export function clearPlan(root) {
  try {
    fs.rmSync(path.join(root, PLAN_REL), { force: true });
    return { ok: true };
  } catch (err) {
    return { ok: false, reason: err?.message ?? String(err) };
  }
}

function markerFresh(root, name) {
  try {
    const st = fs.statSync(path.join(root, "composeApp", "build", name));
    return Date.now() - st.mtimeMs < MARKER_FRESH_MS;
  } catch {
    return false;
  }
}

/**
 * Everything a chain-rendering surface needs, with provenance attached:
 * request (tier 1) + plan with its age (tier 2) + what is ACTUALLY running
 * (tier 3 — the markers the lane and preview daemon already stamp).
 * @returns {{request: (object|null), plan: (object|null), planAgeMs: (number|null),
 *   busy: {lane: boolean, render: boolean}}}
 */
export function deriveChain(root) {
  const plan = readPlan(root);
  const at = plan ? Date.parse(plan.updatedAt) : NaN;
  return {
    request: readRequest(root),
    plan,
    planAgeMs: Number.isNaN(at) ? null : Math.max(0, Date.now() - at),
    busy: {
      lane: markerFresh(root, ".cmp-lane-in-progress"),
      render: markerFresh(root, ".cmp-render-in-progress"),
    },
  };
}

/** "40s ago" / "12 min ago" — freshness a human can weigh at a glance. */
export function formatAge(ms) {
  if (!(ms >= 0)) return "age unknown";
  if (ms < 90000) return `${Math.round(ms / 1000)}s ago`;
  if (ms < 90 * 60000) return `${Math.round(ms / 60000)} min ago`;
  return `${Math.round(ms / 3600000)}h ago`;
}

/**
 * The chain as one text block — the CLI's and the inject's rendering.
 * Numbered steps: done ✓, current ◉ (with tier-3 corroboration when a lane
 * or render is genuinely in flight), pending ○. "" when nothing is declared
 * AND no request is recorded (silence, never an empty frame).
 */
export function renderChain(chain) {
  if (!chain || (!chain.plan && !chain.request)) return "";
  const lines = [];
  const title = chain.plan?.title ?? chain.request?.text ?? null;
  if (title) lines.push(`Request: ${title}`);
  if (chain.plan) {
    const p = chain.plan;
    const seq = p.steps
      .map((s) => `${s.done ? "✓" : s.n === p.current ? "◉" : "○"} ${s.n}. ${s.label}`)
      .join("  →  ");
    lines.push(seq);
    const cur = p.steps.find((s) => s.n === p.current) ?? null;
    const busy = chain.busy?.lane ? " · the full check is running NOW" : chain.busy?.render ? " · a preview render is in flight" : "";
    const age = chain.planAgeMs !== null ? ` · declared by the agent, updated ${formatAge(chain.planAgeMs)}` : "";
    lines.push(cur ? `now: step ${cur.n} of ${p.steps.length} — ${cur.label}${busy}${age}` : `chain complete${busy}${age}`);
  } else {
    lines.push("(no declared chain for this request yet — node qa/plan.mjs --set \"step | step | …\")");
  }
  return lines.join("\n");
}
