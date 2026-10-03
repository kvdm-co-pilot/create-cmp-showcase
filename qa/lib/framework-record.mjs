// framework-record.mjs — WHAT RULE 0 LAST SAID, left behind so a reader can ask.
//
// docs/proposals/LIVE-CONSOLE.md §4 names the gap this closes: "Rule 0's last
// result for the *trust* row — framework-check persists nothing in an adopter
// tree." D4a decided the fix and D4b was rejected with it: the console must
// never RE-RUN framework-check to answer a page load ("seconds of latency and
// a mutation of the tree on a page load"). So the instrument writes a small
// record of what it already computed, and every reader reads that.
//
// WHY THE RECORD IS ASKED FOR (`--record`) AND NOT WRITTEN ON EVERY RUN. The
// Rule 0 instrument plants into your real tree, and its promise — the reason
// anyone dares run it mid-change — is that it puts the tree back exactly as it
// found it. That promise is not prose: THREE gates keep it, and they were all
// written before this record existed —
//
//   scripts/framework-check.mjs        hashes the whole scratch app before and
//                                      after the shipped twin runs
//   scripts/stage2-gate.mjs (E)        the same digest, per profile, as a
//                                      Stage 2 exit criterion
//   test/framework-check-agnostic      `git status --porcelain` before/after,
//                                      on a tree the instrument has never seen
//
// A record written unconditionally would make all three red, and the only way
// to land it that way is to edit three gates — one of them a signed stage exit
// — into agreement with a change. The flag is the honest resolution: the
// default invocation still leaves the tree byte-identical, every gate keeps
// asserting exactly what it asserted, and the record is an OUTPUT someone
// asked for rather than residue nobody expected. It is the same shape D3a
// chose for the HTML snapshot in the same proposal, for a related reason.
//
// PURE, except for the two functions whose whole job is a file (`readRecord`,
// and the writer in framework-check.mjs). What a record MEANS is decided here
// with no clock and no filesystem — `now` is a parameter — because the cases
// that matter are a record that is old, a record of a FAILED check, and a
// record whose instrument did not put the tree back, and none of those can be
// produced on demand by a real run.
//
// ONE SPELLING. The console renders this state and so does the `--html`
// snapshot, and neither decides what it means: `trustState` decides, `trustLine`
// says it in words, and test/console-trust.test.mjs refuses any second reader
// of the record — the same inversion test/console-now.test.mjs applies to the
// step stream and test/evidence-ladder.test.mjs to the rung.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/framework-record.mjs in the
// create-cmp repo. Vendored byte-identical into qa/lib/ — edit the package
// source, then run `node scripts/sync-harness.mjs` in the create-cmp repo.

import fs from "node:fs";
import path from "node:path";

/** Where the record lives, beside the receipt it is about. */
export const FRAMEWORK_RECORD_REL = "qa/evidence/framework-check.json";

/** The record's own format name (ADR-0007: a name routes, it never claims). */
export const FRAMEWORK_RECORD_SCHEMA = "prooflane-framework-check/1";

/**
 * The command that WRITES the record — named once, so the two surfaces that
 * report its absence send the reader to the same place. The absence is a row's
 * most common state on a tree nobody has run Rule 0 in, and a row that said
 * only "unknown" would be true and useless.
 */
export const FRAMEWORK_RECORD_COMMAND = "node qa/framework-check.mjs --record";

/** @param {string} root @returns {string} */
export function frameworkRecordPath(root) {
  return path.join(root, ...FRAMEWORK_RECORD_REL.split("/"));
}

/**
 * The record, as an object — every field one the instrument already computed.
 *
 * `treeIdentical` is the instrument's own restore check, not a promise: after
 * putting every file back it compares the bytes to what it read at the start
 * and records the answer. `null` means it did not get far enough to say, which
 * is not the same as `false` and is never rendered as either.
 *
 * @param {object} p
 * @param {"PASS"|"FAIL"} p.verdict
 * @param {string|null} [p.reason] why a FAIL failed, verbatim
 * @param {Array<{kind: string, label: string, step: string|null, observed: string|null,
 *   names: string[], failedByName: boolean, durationMs: number|null}>} p.plants
 * @param {Array<{kind: string, reason: string}>} [p.unavailable]
 * @param {boolean|null} p.treeIdentical
 * @param {string[]} [p.changed] files the restore did NOT put back, when any
 * @param {number|null} [p.totalMs]
 * @param {number|null} [p.boundMs]
 * @param {string} p.generatedAt ISO
 * @param {string|null} [p.commit] the sha the check ran against, when git answered
 */
export function buildFrameworkRecord({
  verdict,
  reason = null,
  plants = [],
  unavailable = [],
  treeIdentical = null,
  changed = [],
  totalMs = null,
  boundMs = null,
  generatedAt,
  commit = null,
}) {
  return {
    schema: FRAMEWORK_RECORD_SCHEMA,
    verdict,
    ...(reason ? { reason } : {}),
    generatedAt,
    commit,
    boundMs,
    totalMs,
    treeIdentical,
    ...(changed.length ? { changed } : {}),
    plants: plants.map((p) => ({
      kind: p.kind,
      label: p.label,
      step: p.step ?? null,
      observed: p.observed ?? null,
      names: Array.isArray(p.names) ? p.names : [],
      failedByName: Boolean(p.failedByName),
      durationMs: typeof p.durationMs === "number" ? p.durationMs : null,
    })),
    unavailable: unavailable.map((u) => ({ kind: u.kind, reason: u.reason })),
  };
}

/**
 * The record off disk, or the reason there is none. Never throws, never
 * fabricates: a missing file is an ABSENCE with a name, which is what the row
 * renders (LIVE-CONSOLE §4 — absence is stated, never dressed as a pass).
 *
 * @param {string} root
 * @returns {{ok: true, record: object, relPath: string}|{ok: false, reason: string, relPath: string}}
 */
export function readFrameworkRecord(root) {
  const abs = frameworkRecordPath(root);
  let text;
  try {
    text = fs.readFileSync(abs, "utf8");
  } catch (err) {
    return {
      ok: false,
      relPath: FRAMEWORK_RECORD_REL,
      reason:
        err && err.code === "ENOENT"
          ? `no Rule 0 record at ${FRAMEWORK_RECORD_REL}`
          : `${FRAMEWORK_RECORD_REL} could not be read (${err && err.message ? err.message : String(err)})`,
    };
  }
  let record;
  try {
    record = JSON.parse(text);
  } catch (err) {
    return { ok: false, relPath: FRAMEWORK_RECORD_REL, reason: `${FRAMEWORK_RECORD_REL} is not parseable JSON (${err.message})` };
  }
  if (!record || typeof record !== "object" || !Array.isArray(record.plants)) {
    return { ok: false, relPath: FRAMEWORK_RECORD_REL, reason: `${FRAMEWORK_RECORD_REL} is not a Rule 0 record (no plants[])` };
  }
  return { ok: true, record, relPath: FRAMEWORK_RECORD_REL };
}

/**
 * What the record MEANS — the *trust* row's one question, answered once.
 *
 * The row asks "can I trust the lane that says so?", and the only honest answer
 * is the one Rule 0 gives: did each of this profile's own plants make the
 * responsible gate refuse BY NAME, and did the instrument leave the tree as it
 * found it. Both are counted here from the record's own rows — nothing is
 * summarised into a score, and there is no arithmetic a reader cannot redo.
 *
 * @param {{ok: boolean, record?: object, reason?: string, relPath?: string}|null} read
 *   readFrameworkRecord()'s result (or a hand-built one, for a pure caller)
 * @param {{now?: number}} [opts]
 * @returns {{available: boolean, reason?: string, relPath: string, verdict?: string,
 *   plants?: number, failedByName?: number, unavailable?: number, treeIdentical?: boolean|null,
 *   changed?: string[], ageMs?: number|null, generatedAt?: string|null, commit?: string|null,
 *   rows?: Array<{label: string, observed: string|null, names: string[], failedByName: boolean, durationMs: number|null}>}}
 */
export function trustState(read, { now = Date.now() } = {}) {
  if (!read || read.ok !== true) {
    return {
      available: false,
      relPath: (read && read.relPath) || FRAMEWORK_RECORD_REL,
      reason: (read && read.reason) || `no Rule 0 record at ${FRAMEWORK_RECORD_REL}`,
    };
  }
  const r = read.record;
  const plants = Array.isArray(r.plants) ? r.plants : [];
  const stamp = typeof r.generatedAt === "string" ? Date.parse(r.generatedAt) : NaN;
  return {
    available: true,
    relPath: read.relPath || FRAMEWORK_RECORD_REL,
    verdict: typeof r.verdict === "string" ? r.verdict : "?",
    reason: typeof r.reason === "string" && r.reason ? r.reason : undefined,
    plants: plants.length,
    failedByName: plants.filter((p) => p && p.failedByName === true).length,
    unavailable: Array.isArray(r.unavailable) ? r.unavailable.length : 0,
    // Tri-state on purpose: true, false, and "the record does not say" are
    // three different readings and only one of them is reassuring.
    treeIdentical: typeof r.treeIdentical === "boolean" ? r.treeIdentical : null,
    changed: Array.isArray(r.changed) ? r.changed : [],
    generatedAt: typeof r.generatedAt === "string" ? r.generatedAt : null,
    commit: typeof r.commit === "string" ? r.commit : null,
    ageMs: Number.isNaN(stamp) ? null : Math.max(0, now - stamp),
    rows: plants.map((p) => ({
      label: typeof p.label === "string" ? p.label : String(p.kind ?? "plant"),
      observed: typeof p.observed === "string" ? p.observed : null,
      names: Array.isArray(p.names) ? p.names : [],
      failedByName: p.failedByName === true,
      durationMs: typeof p.durationMs === "number" ? p.durationMs : null,
    })),
  };
}

/**
 * The row's one line, as TEXT — the same sentence on the console and in the
 * `--html` snapshot, so the two surfaces cannot come to disagree about what
 * Rule 0 said. Text and never markup, for console-evidence.mjs's reason: every
 * caller already owns its own escaping and its own chrome.
 *
 * The AGE is passed in rather than formatted here: how old a thing reads is the
 * surface's own vocabulary (the console's `formatAgeCoarse`, the snapshot's
 * own), and a fifth age formatter in this repo would be a fact with two
 * spellings for the sake of one word.
 *
 * @param {object} state trustState()'s result
 * @param {string|null} [age] the formatted age, e.g. "2h ago"
 * @returns {string}
 */
export function trustLine(state, age = null) {
  const tail = age ? ` · ${age}` : "";
  if (!state || !state.available) {
    // The absence in its own words. The COMMAND that ends it is appended by
    // each surface out of FRAMEWORK_RECORD_COMMAND, for the same reason the
    // age is: `node …` is a thing a console sets in <code> and a terminal does
    // not, and a shared derivation that returned markup could not be shared.
    return (state && state.reason) || `no Rule 0 record at ${FRAMEWORK_RECORD_REL}`;
  }
  if (state.verdict !== "PASS") {
    // A failed Rule 0 is the most important thing this row can say, and it says
    // it in the instrument's own words. No rewording (§7).
    const why = state.reason ? `: ${String(state.reason).split("\n")[0]}` : "";
    return `Rule 0 — FAILED${why}${tail}`;
  }
  const n = state.plants;
  const byName =
    n === 0
      ? "no plants this tree could make"
      : state.failedByName === n
        ? `${n} plant${n === 1 ? "" : "s"} failed by name`
        : `${state.failedByName} of ${n} plants failed by name`;
  const tree =
    state.treeIdentical === true
      ? "tree byte-identical"
      : state.treeIdentical === false
        ? `tree NOT restored — ${state.changed.length} file${state.changed.length === 1 ? "" : "s"} left changed`
        : "the record does not say whether the tree was restored";
  return `Rule 0 — ${byName} · ${tree}${tail}`;
}
