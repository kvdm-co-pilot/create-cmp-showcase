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
// N5 (docs/features/drive-narration.md): closed chains leave a LOCAL trail —
// request, steps, wall time, receipt state at close. Gitignored and excluded
// from the hashed input surface like its siblings above, and deliberately NOT
// a committed journal: it carries raw human prompts. Lane history that
// belongs in the repo stays the flight recorder's.
export const PLAN_HISTORY_REL = "qa/.plan-history.jsonl";
const MAX_HISTORY_LINES = 50;

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
  // N1: the declaration's own write times ARE the timing data — createdAt for
  // the whole chain, startedAt on step 1. No new claims, just timestamps the
  // writes already imply; renderers derive durations from them.
  const now = new Date().toISOString();
  return writeJson(path.join(root, PLAN_REL), {
    title: typeof title === "string" && title.trim() !== "" ? title.trim() : null,
    feature: typeof feature === "string" && feature.trim() !== "" ? feature.trim() : null,
    steps: labels.map((label, i) => ({ n: i + 1, label, done: false, ...(i === 0 ? { startedAt: now } : {}) })),
    current: 1,
    createdAt: now,
    updatedAt: now,
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
  const now = new Date().toISOString();
  for (const s of plan.steps) {
    const willBeDone = s.n < step;
    // N1: stamp doneAt the first time a step closes and startedAt the first
    // time it becomes current — first-write-wins, so re-marking never
    // rewrites history.
    if (willBeDone && !s.done && !s.doneAt) s.doneAt = now;
    s.done = willBeDone;
    if (s.n === step && !s.startedAt) s.startedAt = now;
  }
  const closing = step > plan.steps.length && !plan.closedAt;
  plan.current = step > plan.steps.length ? null : step;
  if (closing) plan.closedAt = now;
  plan.updatedAt = now;
  if (!writeJson(path.join(root, PLAN_REL), plan).ok) return { ok: false, reason: "could not write the chain" };
  // N5: the FIRST close leaves the trail entry; a re-close of an already
  // closed chain never double-writes. Fail-soft — a trail that cannot be
  // written must not fail the advance that was asked for.
  if (closing) appendPlanHistory(root, plan);
  return { ok: true, plan };
}

/** The receipt's verdict + rung right now, for the trail — fail-soft glance. */
function receiptGlance(root) {
  try {
    const r = JSON.parse(fs.readFileSync(path.join(root, "qa/evidence/latest.json"), "utf8"));
    return { verdict: r?.verdict ?? null, rung: r?.evidenceLevel?.rung ?? null };
  } catch {
    return null;
  }
}

function appendPlanHistory(root, plan) {
  try {
    const started = Date.parse(plan.createdAt ?? "");
    const closed = Date.parse(plan.closedAt ?? "");
    const entry = {
      schema: "cmp-plan-history/1",
      at: plan.closedAt ?? new Date().toISOString(),
      request: readRequest(root)?.text ?? null,
      title: plan.title ?? null,
      feature: plan.feature ?? null,
      steps: plan.steps.map((s) => s.label),
      durationMs: Number.isNaN(started) || Number.isNaN(closed) ? null : Math.max(0, closed - started),
      receipt: receiptGlance(root),
    };
    const p = path.join(root, PLAN_HISTORY_REL);
    let lines = [];
    try {
      lines = fs.readFileSync(p, "utf8").split("\n").filter((l) => l.trim() !== "");
    } catch {
      /* first entry */
    }
    lines.push(JSON.stringify(entry));
    fs.writeFileSync(p, `${lines.slice(-MAX_HISTORY_LINES).join("\n")}\n`);
    return { ok: true };
  } catch (err) {
    return { ok: false, reason: err?.message ?? String(err) };
  }
}

/**
 * The last `limit` closed chains, NEWEST FIRST — Drive's "Recent requests"
 * fold. Absent trail or unparsable lines read as an empty/shorter list,
 * never an error.
 * @returns {object[]}
 */
export function readPlanHistory(root, limit = 5) {
  try {
    const raw = fs.readFileSync(path.join(root, PLAN_HISTORY_REL), "utf8");
    const out = [];
    for (const line of raw.split("\n")) {
      if (!line.trim()) continue;
      try {
        const e = JSON.parse(line);
        if (e && typeof e === "object") out.push(e);
      } catch {
        /* skip the line, keep the trail */
      }
    }
    return out.slice(-Math.max(0, limit)).reverse();
  } catch {
    return [];
  }
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

/**
 * A marker read WITH its content (N2, docs/features/drive-narration.md):
 * every other marker consumer is mtime-only, so the content is free to carry
 * the lane's own narration — verify.mjs rewrites the lane marker at each
 * step start with {step, index, total, stepStartedAt, expectedStepMs,
 * expectedLaneMs}. Legacy "pid iso" content (older lanes, the render marker)
 * reads as a bare truthy {} — busy, no narration. Stale/absent -> false.
 * @returns {object|false}
 */
function markerInfo(root, name) {
  const p = path.join(root, "composeApp", "build", name);
  try {
    const st = fs.statSync(p);
    if (Date.now() - st.mtimeMs >= MARKER_FRESH_MS) return false;
    const raw = fs.readFileSync(p, "utf8").trim();
    if (raw.startsWith("{")) {
      try {
        const parsed = JSON.parse(raw);
        return parsed && typeof parsed === "object" ? parsed : {};
      } catch {
        return {};
      }
    }
    return {};
  } catch {
    return false;
  }
}

// The build stage's observed tier (evidence-economics S3). Between the prompt
// and the lane — most of the working time — the chain moved only if the agent
// volunteered `plan.mjs --step`, so an undeclared chain was a still photo until
// the lane landed. The lane marker already corroborates the lane mechanically;
// this corroborates the build stage the same way: writes in the working tree
// since the current request began. No agent cooperation required — which is
// the point.
const ACTIVITY_ROOTS = ["composeApp/src", "specs", "qa", "docs"];
const ACTIVITY_SKIP_DIRS = new Set(["build", ".gradle", ".kotlin", ".git", ".idea", "node_modules", "evidence"]);
// Machinery, not work: the chain's own files and the lane's outputs must not
// count as "the agent wrote something", or the pulse would corroborate itself.
const ACTIVITY_SKIP_FILES = new Set([".plan.json", ".request.json", ".plan-history.jsonl", "flight-recorder.jsonl", "approvals.log.jsonl", ".DS_Store"]);
// Nothing written for this long, with no lane or render running, is a stall
// worth naming — the human is watching a strip that has stopped moving.
export const ACTIVITY_STALL_MS = 10 * 60 * 1000;

/**
 * Files written under the working roots since `sinceIso` — the request stamp.
 * Pure filesystem, no git: a scaffold before `git init` still answers, and a
 * mtime is a fact regardless of what is staged.
 * @param {string} root
 * @param {string|null|undefined} sinceIso the request's `at`
 * @param {{now?: number}} [opts]
 * @returns {{filesChanged: number, lastWriteAgoMs: (number|null), since: string}|null}
 *   null when there is no request to measure from
 */
export function observeActivity(root, sinceIso, { now = Date.now() } = {}) {
  const since = Date.parse(sinceIso ?? "");
  if (Number.isNaN(since)) return null;
  let filesChanged = 0;
  let newest = -Infinity;
  const walk = (dir) => {
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      if (e.isDirectory()) {
        if (!ACTIVITY_SKIP_DIRS.has(e.name)) walk(path.join(dir, e.name));
        continue;
      }
      if (!e.isFile() || ACTIVITY_SKIP_FILES.has(e.name)) continue;
      let m;
      try {
        m = fs.statSync(path.join(dir, e.name)).mtimeMs;
      } catch {
        continue;
      }
      if (m > since) {
        filesChanged += 1;
        if (m > newest) newest = m;
      }
    }
  };
  for (const rel of ACTIVITY_ROOTS) walk(path.join(root, rel));
  return {
    filesChanged,
    lastWriteAgoMs: filesChanged > 0 ? Math.max(0, now - newest) : null,
    since: new Date(since).toISOString(),
  };
}

/**
 * Everything a chain-rendering surface needs, with provenance attached:
 * request (tier 1) + plan with its age (tier 2) + what is ACTUALLY running
 * (tier 3 — the markers the lane and preview daemon already stamp, the lane's
 * now carrying its own step narration) + the local trail of closed chains
 * (N5). `busy.lane`/`busy.render` are truthy objects while fresh — existing
 * truthiness consumers keep working unchanged.
 * @returns {{request: (object|null), plan: (object|null), planAgeMs: (number|null),
 *   busy: {lane: (object|false), render: (object|false)}, history: object[]}}
 */
export function deriveChain(root) {
  const plan = readPlan(root);
  const at = plan ? Date.parse(plan.updatedAt) : NaN;
  const busy = {
    lane: markerInfo(root, ".cmp-lane-in-progress"),
    render: markerInfo(root, ".cmp-render-in-progress"),
  };
  const request = readRequest(root);
  // S3: the build stage's observed tier — writes since the request began.
  const activity = observeActivity(root, request ? request.at : null);
  return {
    request,
    plan,
    planAgeMs: Number.isNaN(at) ? null : Math.max(0, Date.now() - at),
    busy,
    activity,
    // Pre-rendered so every surface (chat, CLI, studio) speaks the observed
    // tier in identical words — the console renders this string, never its
    // own paraphrase of the marker.
    busyText: describeBusy(busy, Date.now(), activity),
    history: readPlanHistory(root, 5),
  };
}

/** "40s ago" / "12 min ago" — freshness a human can weigh at a glance. */
export function formatAge(ms) {
  if (!(ms >= 0)) return "age unknown";
  if (ms < 90000) return `${Math.round(ms / 1000)}s ago`;
  if (ms < 90 * 60000) return `${Math.round(ms / 60000)} min ago`;
  return `${Math.round(ms / 3600000)}h ago`;
}

/** "12s" / "~3 min" — a plain duration (formatAge's sibling, no "ago"). */
export function formatDuration(ms) {
  if (!(ms >= 0)) return "";
  if (ms < 120000) return `${Math.max(1, Math.round(ms / 1000))}s`;
  return `~${Math.round(ms / 60000)} min`;
}

/** A done step's wall time from its own N1 stamps, or null pre-N1. */
function stepDurationMs(s) {
  const a = Date.parse(s.startedAt ?? "");
  const b = Date.parse(s.doneAt ?? "");
  return Number.isNaN(a) || Number.isNaN(b) ? null : Math.max(0, b - a);
}

/**
 * The tier-3 corroboration as one phrase (N2): the lane's own narration when
 * the marker carries it ("full check — unitTests (10/16) · 12s of ~3s,
 * usually ~52s total"), the legacy phrase when it does not. "" when nothing
 * is running. Shared by the text and HTML renderers so the observed tier
 * speaks identically everywhere.
 */
export function describeBusy(busy, now = Date.now(), activity = null) {
  if (!busy) return describeActivity(activity);
  const lane = busy.lane;
  if (lane) {
    if (typeof lane === "object" && typeof lane.step === "string" && lane.step !== "") {
      const pos = Number.isInteger(lane.index) && Number.isInteger(lane.total) ? ` (${lane.index}/${lane.total})` : "";
      const started = Date.parse(lane.stepStartedAt ?? "");
      const elapsed = Number.isNaN(started) ? null : Math.max(0, now - started);
      const stepExpect = typeof lane.expectedStepMs === "number" && lane.expectedStepMs > 0 ? ` of ~${formatDuration(lane.expectedStepMs)}` : "";
      const laneExpect =
        typeof lane.expectedLaneMs === "number" && lane.expectedLaneMs > 0 ? `, usually ${formatDuration(lane.expectedLaneMs)} total` : "";
      return `full check — ${lane.step}${pos}${elapsed !== null ? ` · ${formatDuration(elapsed)}${stepExpect}` : ""}${laneExpect}`;
    }
    return "the full check is running NOW";
  }
  if (busy.render) return "a preview render is in flight";
  // Nothing mechanical is running — but the working tree may still be moving.
  return describeActivity(activity);
}

/**
 * The build stage's phrase (S3). Files written since the request, and how
 * long ago the last one landed; a stall when the tree has stopped moving.
 * "" when there is no request to measure from, or nothing has happened yet.
 */
export function describeActivity(activity) {
  if (!activity) return "";
  if (activity.filesChanged === 0) return "";
  const n = activity.filesChanged;
  const ago = activity.lastWriteAgoMs;
  const files = `${n} file${n === 1 ? "" : "s"} written since the request`;
  if (typeof ago === "number" && ago >= ACTIVITY_STALL_MS) return `${files} · stalled — nothing written for ${formatDuration(ago)}`;
  return typeof ago === "number" ? `${files} · last ${formatDuration(ago)} ago` : files;
}

/**
 * The chain as one text block — the CLI's and the inject's rendering.
 * Numbered steps: done ✓ with wall time, current ◉ with elapsed, pending ○
 * (N1); the tier-3 corroboration is prefixed "observed:" so the machine's
 * word is visibly distinct from the agent's declaration (N3). "" when
 * nothing is declared AND no request is recorded (silence, never an empty
 * frame).
 */
export function renderChain(chain) {
  if (!chain || (!chain.plan && !chain.request)) return "";
  const now = Date.now();
  const lines = [];
  const title = chain.plan?.title ?? chain.request?.text ?? null;
  if (title) lines.push(`Request: ${title}`);
  if (chain.plan) {
    const p = chain.plan;
    const seq = p.steps
      .map((s) => {
        if (s.done) {
          const d = stepDurationMs(s);
          return `✓ ${s.n}. ${s.label}${d !== null ? ` (${formatDuration(d)})` : ""}`;
        }
        if (s.n === p.current) {
          const a = Date.parse(s.startedAt ?? "");
          return `◉ ${s.n}. ${s.label}${Number.isNaN(a) ? "" : ` · ${formatDuration(Math.max(0, now - a))} in`}`;
        }
        return `○ ${s.n}. ${s.label}`;
      })
      .join("  →  ");
    lines.push(seq);
    const cur = p.steps.find((s) => s.n === p.current) ?? null;
    const busyText = typeof chain.busyText === "string" ? chain.busyText : describeBusy(chain.busy, now, chain.activity ?? null);
    const busy = busyText !== "" ? ` · observed: ${busyText}` : "";
    const age = chain.planAgeMs !== null ? ` · declared by the agent, updated ${formatAge(chain.planAgeMs)}` : "";
    lines.push(cur ? `now: step ${cur.n} of ${p.steps.length} — ${cur.label}${busy}${age}` : `chain complete${busy}${age}`);
  } else {
    lines.push("(no declared chain for this request yet — node qa/plan.mjs --set \"step | step | …\")");
    // S3: an undeclared chain is no longer a still photo. The observed tier —
    // a running lane, a render, or writes since the request — is printed even
    // when the agent declared nothing, because it is the machine's word and
    // needs no declaration to exist. This is the case that used to show nothing
    // for forty minutes.
    const observed = typeof chain.busyText === "string" ? chain.busyText : describeBusy(chain.busy, Date.now(), chain.activity ?? null);
    if (observed !== "") lines.push(`observed: ${observed}`);
  }
  return lines.join("\n");
}
