// flight-recorder.mjs — the lane's append-only journal of its own runs.
//
// The retrospective that reshaped this harness was only possible because
// session transcripts happened to exist. This module removes the "happened
// to": every verify-lane run appends one JSON line — profile, mode, verdict,
// evidence rung, per-step verdicts, every SKIP reason VERBATIM, and every
// degraded-path activation — so "did this project drift from its tooling?"
// is answerable mechanically (qa/retrospective.mjs), from the repo alone.
// The SKIP reasons are the signal that matters: they are where a harness
// quietly stops being used ("no device attached" forever, "maestro not
// installed" forever) without any single run ever failing.
//
// GROUND RULES, each load-bearing:
//
//   - IN-REPO ONLY, NO PHONE-HOME. That is a product constraint, not a
//     preference: the journal is the app's own artifact, and nothing here
//     records a machine, a hostname, or a user beyond what git itself
//     already records (the commit sha).
//
//   - THE RECORDER MUST NEVER BREAK THE LANE. A recorder that fails the
//     thing it observes is worse than no recorder: every write is wrapped,
//     every failure degrades to {ok: false, reason} for the lane to NOTE in
//     its own output — never to throw, never to change a verdict.
//
//   - THE JOURNAL IS COMMITTED, NOT GITIGNORED — deliberately. The console's
//     Evidence timeline reconstructs history from the git log of committed
//     receipts; a gitignored journal could answer nothing about the past on
//     a fresh clone, which is exactly the question this file exists to
//     answer. It follows qa/evidence/latest.json's precedent: a lane output
//     that is committed with the change and EXCLUDED from the receipt's
//     hashed input surface (qa/lib/inputs-hash.mjs), because a lane output
//     inside the hash would invalidate the very receipt that produced it.
//
//   - THE READER STATES ONLY WHAT THE JOURNAL RECORDED. Summaries are
//     counts and dates, never extrapolation: a short journal says it is
//     short, a single full run yields no "stretch" arithmetic, and nothing
//     here editorializes about the developer — the journal records lane
//     runs, not people.
//
// ONE DELIBERATE GAP, stated because a silent one would be a lie: qa/watch.mjs
// passes `--no-journal`, so save-triggered fast runs are never journaled. The
// rule it follows is the same one the README evidence badge follows — THE INNER
// LOOP DOES NOT WRITE TO COMMITTED FILES. A watcher that appended on every save
// would add hundreds of lines a day to a committed file, turn the app's history
// into keystroke noise, and leave a permanently-dirty tree inside the very loop
// this recorder exists to observe. What survives is every full lane and every
// deliberate fast run — which is what the retrospective's questions actually
// rest on (SKIP reasons, degraded paths, the longest stretch with no full
// lane). renderFlightReport states the gap in its own output.

import fs from "node:fs";
import path from "node:path";

export const FLIGHT_JOURNAL_REL_PATH = "qa/flight-recorder.jsonl";
export const FLIGHT_SCHEMA = "cmp-flight/1";

// Below this many entries the report carries an explicit shortness note —
// two entries are two facts, not a trend, and the report must say so rather
// than let a reader infer a pattern from a journal that cannot support one.
const SHORT_JOURNAL_FLOOR = 5;

/**
 * Shape one lane run into a journal entry. Pure — verify.mjs passes what it
 * already computed for the receipt, so the journal can never disagree with
 * the receipt about the same run.
 *
 * @param {object} run
 * @param {string} run.profile lane profile (scaffold | local | ci | release)
 * @param {string} run.mode "full" | "fast"
 * @param {string} run.verdict "PASS" | "FAIL"
 * @param {{rung: string}|null} run.evidenceLevel the derived rung (or null —
 *   fast runs and FAILed lanes carry none, and the journal records that
 *   honestly rather than borrowing a rung from elsewhere)
 * @param {Array<{name: string, verdict: string, reason?: string}>} run.steps
 *   the lane's step results, verbatim
 * @param {string|null} run.sha parent HEAD at run time (null before git init)
 * @param {number} run.durationMs wall time of the step loop
 * @param {string[]} run.onDeviceSteps device-tier steps that actually PASSed
 *   (verify.mjs's own strength derivation — reused, not recomputed, so the
 *   two can never drift)
 * @param {string[]} run.degraded degraded-path activations the lane observed
 *   (self-heals, fallbacks) — each a short verbatim description
 * @returns {object} one journal entry (JSON-serializable)
 */
export function buildFlightEntry({ profile, mode, verdict, evidenceLevel, steps, sha, durationMs, onDeviceSteps, degraded }) {
  const stepList = Array.isArray(steps) ? steps.filter((s) => s && typeof s.name === "string") : [];
  return {
    schema: FLIGHT_SCHEMA,
    at: new Date().toISOString(),
    commit: sha ?? null,
    profile,
    mode,
    verdict,
    evidenceRung: evidenceLevel?.rung ?? null,
    durationMs,
    steps: stepList.map((s) => ({ name: s.name, verdict: s.verdict })),
    // SKIP reasons verbatim — the journal's core signal (see file header).
    skips: stepList.filter((s) => s.verdict === "SKIP").map((s) => ({ step: s.name, reason: s.reason ?? "" })),
    deviceSteps: Array.isArray(onDeviceSteps) ? onDeviceSteps : [],
    degraded: Array.isArray(degraded) ? degraded : [],
  };
}

/**
 * Append one entry to the journal. NEVER throws — a recorder that breaks the
 * lane is worse than no recorder (see ground rules). The caller is expected
 * to surface a failed append in the lane's own output.
 * @param {string} root project root (absolute)
 * @param {object} entry a buildFlightEntry() result
 * @returns {{ok: true}|{ok: false, reason: string}}
 */
export function appendFlightRecord(root, entry) {
  try {
    const p = path.join(root, FLIGHT_JOURNAL_REL_PATH);
    fs.mkdirSync(path.dirname(p), { recursive: true });
    fs.appendFileSync(p, `${JSON.stringify(entry)}\n`);
    return { ok: true };
  } catch (err) {
    return { ok: false, reason: err?.message ?? String(err) };
  }
}

/**
 * Read the journal. Absent is not an error — it is the honest "no flight
 * data recorded yet" state. Unparsable lines are counted, never silently
 * dropped into the totals: the report must be able to say "N lines could
 * not be read" instead of quietly under-counting.
 * @param {string} root project root (absolute)
 * @returns {{exists: boolean, entries: object[], malformed: number, error?: string}}
 */
export function readFlightJournal(root) {
  const p = path.join(root, FLIGHT_JOURNAL_REL_PATH);
  if (!fs.existsSync(p)) return { exists: false, entries: [], malformed: 0 };
  let raw;
  try {
    raw = fs.readFileSync(p, "utf8");
  } catch (err) {
    return { exists: true, entries: [], malformed: 0, error: err?.message ?? String(err) };
  }
  const entries = [];
  let malformed = 0;
  for (const line of raw.split("\n")) {
    if (!line.trim()) continue;
    try {
      const parsed = JSON.parse(line);
      if (parsed && typeof parsed === "object") entries.push(parsed);
      else malformed += 1;
    } catch {
      malformed += 1;
    }
  }
  return { exists: true, entries, malformed };
}

function countBy(entries, keyFn) {
  const out = new Map();
  for (const e of entries) {
    const k = keyFn(e);
    if (k === undefined || k === null) continue;
    out.set(k, (out.get(k) ?? 0) + 1);
  }
  return out;
}

function fmtDay(iso) {
  return typeof iso === "string" ? iso.slice(0, 10) : "unknown";
}

function fmtGap(ms) {
  const days = Math.floor(ms / 86_400_000);
  const hours = Math.round((ms % 86_400_000) / 3_600_000);
  if (days > 0) return `${days}d ${hours}h`;
  const mins = Math.round((ms % 3_600_000) / 60_000);
  return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
}

/**
 * Summarize journal entries into the facts the retrospective report prints.
 * Pure arithmetic over recorded entries — no clock reads except the caller-
 * supplied `now` (used only for the clearly-labeled "as of" distance to the
 * last full run), no filesystem, no extrapolation.
 * @param {object[]} entries parsed journal entries, in append (chronological) order
 * @param {{now?: Date}} [opts]
 * @returns {object} summary — see the field-by-field construction below
 */
export function summarizeFlightJournal(entries, { now = new Date() } = {}) {
  const runs = entries.filter((e) => e && typeof e === "object");
  const byMode = countBy(runs, (e) => e.mode ?? "unknown");
  const byProfile = countBy(runs, (e) => e.profile ?? "unknown");
  const byVerdict = countBy(runs, (e) => e.verdict ?? "unknown");

  // SKIP reasons, grouped VERBATIM — the reason string is the key on purpose:
  // paraphrasing or normalizing would erase exactly the signal the journal
  // exists to keep (two different reasons are two different problems).
  const skipGroups = new Map();
  for (const e of runs) {
    for (const s of Array.isArray(e.skips) ? e.skips : []) {
      // JSON-array key: reasons are arbitrary text, so a delimiter-joined
      // string key would be ambiguous — and ambiguity here merges two
      // different problems into one count.
      const key = JSON.stringify([s.step ?? "?", s.reason ?? ""]);
      skipGroups.set(key, (skipGroups.get(key) ?? 0) + 1);
    }
  }
  const skipReasons = [...skipGroups.entries()]
    .map(([key, count]) => {
      const [step, reason] = JSON.parse(key);
      return { step, reason, count };
    })
    .sort((a, b) => b.count - a.count || a.step.localeCompare(b.step));

  const degradedGroups = countBy(
    runs.flatMap((e) => (Array.isArray(e.degraded) ? e.degraded : [])).map((d) => ({ d })),
    (x) => x.d,
  );

  const fullRuns = runs.filter((e) => e.mode === "full");
  const deviceReached = runs.filter((e) => Array.isArray(e.deviceSteps) && e.deviceSteps.length > 0);
  const rungOrder = { L0: 0, L1: 1, L2: 2, L3: 3 };
  const highestRung = runs
    .map((e) => e.evidenceRung)
    .filter((r) => typeof r === "string" && r in rungOrder)
    .sort((a, b) => rungOrder[b] - rungOrder[a])[0] ?? null;

  // Longest stretch with no full lane — only computable BETWEEN two recorded
  // full runs. One full run is a date, not a stretch; the report says so
  // instead of inventing a gap against "now" or the journal's edges.
  let longestFullGap = null;
  for (let i = 1; i < fullRuns.length; i += 1) {
    const a = Date.parse(fullRuns[i - 1].at);
    const b = Date.parse(fullRuns[i].at);
    if (Number.isNaN(a) || Number.isNaN(b)) continue;
    const gap = b - a;
    if (!longestFullGap || gap > longestFullGap.ms) {
      longestFullGap = { ms: gap, from: fullRuns[i - 1].at, to: fullRuns[i].at };
    }
  }

  const lastFull = fullRuns.length ? fullRuns[fullRuns.length - 1].at : null;
  const lastFullAgoMs = lastFull && !Number.isNaN(Date.parse(lastFull)) ? now.getTime() - Date.parse(lastFull) : null;

  return {
    total: runs.length,
    span: runs.length ? { from: runs[0].at, to: runs[runs.length - 1].at } : null,
    short: runs.length > 0 && runs.length < SHORT_JOURNAL_FLOOR,
    byMode: Object.fromEntries(byMode),
    byProfile: Object.fromEntries(byProfile),
    byVerdict: Object.fromEntries(byVerdict),
    skipReasons,
    degraded: [...degradedGroups.entries()].map(([what, count]) => ({ what, count })),
    fullRuns: {
      count: fullRuns.length,
      last: lastFull,
      lastAgoMs: lastFullAgoMs,
      longestGap: longestFullGap,
    },
    device: { reachedRuns: deviceReached.length, highestRung },
  };
}

/**
 * Render the summary as the plain-text report a human reads in ten seconds.
 * Every line is a recorded fact; the honesty notes (short journal, single
 * full run, malformed lines) are part of the report, not caveats around it.
 * @param {object} summary a summarizeFlightJournal() result
 * @param {{malformed?: number}} [opts]
 * @returns {string[]} report lines
 */
export function renderFlightReport(summary, { malformed = 0 } = {}) {
  const lines = [];
  if (summary.total === 0) {
    lines.push("flight recorder: journal exists but holds no readable entries");
    if (malformed > 0) lines.push(`  ${malformed} line(s) could not be parsed`);
    return lines;
  }

  lines.push(`flight recorder — ${summary.total} lane run(s) recorded (${fmtDay(summary.span.from)} → ${fmtDay(summary.span.to)})`);
  if (malformed > 0) lines.push(`  ${malformed} line(s) could not be parsed and are not counted`);
  if (summary.short) {
    lines.push(`  only ${summary.total} run(s) recorded — the counts below are individual facts, not a trend`);
  }

  const modeBits = ["full", "fast"].map((m) => `${summary.byMode[m] ?? 0} ${m}`).join(" · ");
  lines.push(`modes:    ${modeBits}`);
  // Disclosure, not a footnote: qa/watch.mjs passes --no-journal, so
  // save-triggered fast runs are deliberately absent (a committed journal must
  // not grow by hundreds of lines a day, and the inner loop must not leave the
  // tree dirty). The fast count is therefore DELIBERATE fast runs only, and
  // saying so here keeps the ratio from being read as a complete census.
  lines.push("          (fast = deliberate runs only; qa/watch.mjs save-triggered runs are not journaled)");
  lines.push(`verdicts: ${Object.entries(summary.byVerdict).map(([v, n]) => `${n} ${v}`).join(" · ")}`);
  lines.push(`profiles: ${Object.entries(summary.byProfile).map(([p, n]) => `${p} ${n}`).join(" · ")}`);

  if (summary.device.reachedRuns > 0) {
    lines.push(
      `device tier: reached in ${summary.device.reachedRuns} of ${summary.total} run(s)${summary.device.highestRung ? ` (highest evidence rung recorded: ${summary.device.highestRung})` : ""}`,
    );
  } else {
    lines.push("device tier: never reached in any recorded run (no device-tier step ever PASSed)");
  }

  if (summary.fullRuns.count === 0) {
    lines.push("full lane: never recorded — every recorded run was --fast (the inner loop; no run earned evidence)");
  } else {
    if (summary.fullRuns.longestGap) {
      lines.push(
        `full lane: longest recorded stretch with no full run: ${fmtGap(summary.fullRuns.longestGap.ms)} (${fmtDay(summary.fullRuns.longestGap.from)} → ${fmtDay(summary.fullRuns.longestGap.to)})`,
      );
    } else {
      lines.push(`full lane: one full run recorded (${fmtDay(summary.fullRuns.last)}) — no stretch to measure between full runs`);
    }
    if (summary.fullRuns.lastAgoMs !== null) {
      lines.push(`  last full lane: ${fmtDay(summary.fullRuns.last)} (${fmtGap(summary.fullRuns.lastAgoMs)} before this report)`);
    }
  }

  if (summary.skipReasons.length > 0) {
    lines.push("skip reasons (verbatim, grouped):");
    for (const s of summary.skipReasons) {
      lines.push(`  ${s.count}× [${s.step}] ${s.reason.split("\n")[0]}`);
    }
  } else {
    lines.push("skip reasons: none recorded");
  }

  if (summary.degraded.length > 0) {
    lines.push("degraded paths activated:");
    for (const d of summary.degraded) lines.push(`  ${d.count}× ${d.what}`);
  }

  return lines;
}
