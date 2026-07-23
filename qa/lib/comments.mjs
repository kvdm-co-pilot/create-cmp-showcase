// The comments ledger — the console's talk-back channel (VERIFICATION-LAYER-DESIGN.md
// §7.3). Approvals stay binding; comments are advisory input the agent must read, act
// on, and close. This file is the bridge seam: the console's `POST /api/comment` (via a
// dynamic bridge, same degrade-honestly pattern as approvals-bridge.mjs) and the
// `review_comments`/`resolve_comment` MCP tools call the SAME functions this file
// exports — the contract below is binding for both sides and must not drift.
//
// Three concerns, kept separable (mirrors qa/lib/approvals.mjs's split):
//   1. STATE (`qa/comments.json`) — the ledger: { schema, comments: Comment[] }.
//   2. VALIDATION (`addComment`) — refuses empty text and malformed/unknown targets
//      before anything is written. Refusal over fabrication: an invalid comment is
//      never silently coerced into a valid-looking one.
//   3. TRANSITIONS (`addComment`/`resolveComment`) — append-only; resolving never
//      deletes a comment, it flips status and records who closed it and why.
//
// Read/write asymmetry is deliberate and differs from approvals.mjs on purpose:
//   - A MISSING file is tolerated as the empty seed on read (a brand-new project has
//     no comments yet — that's not corruption) and is created on first write.
//   - A file that EXISTS but is corrupt (unparsable JSON, wrong shape, or a schema
//     string that isn't "cmp-comments/1") is NOT tolerated on read — listComments
//     throws a descriptive error instead of returning an empty list. Approvals can
//     safely treat corruption as "all unreviewed" because that is the conservative
//     (non-blocking) default; silently reading a broken comments ledger as "no
//     comments" would instead HIDE real human feedback, which is the one thing this
//     file exists to surface. Honest failure beats a fabricated empty inbox. Writers
//     (addComment/resolveComment) catch that same error and turn it into
//     {ok:false, reason} — they never overwrite a ledger they could not parse.

import fs from "node:fs";
import path from "node:path";

export const COMMENTS_REL_PATH = "qa/comments.json";
export const COMMENTS_SCHEMA = "cmp-comments/1";

/** target.type -> the fields addComment requires on `target` for that type. */
const TARGET_FIELD_REQUIREMENTS = {
  screen: ["screen"],
  element: ["screen", "testTag"],
  "spec-line": ["file", "clauseId"],
  "design-system": ["token"],
  architecture: ["path"],
  general: [],
};

const VALID_TARGET_TYPES = Object.keys(TARGET_FIELD_REQUIREMENTS);

function ledgerPath(root) {
  return path.join(root, COMMENTS_REL_PATH);
}

/**
 * Parse a comments.json payload already read from disk. Throws a descriptive
 * Error for anything that isn't a well-formed `{schema, comments:[]}` ledger —
 * callers decide whether that means "surface it" (read) or "refuse the write"
 * (addComment/resolveComment).
 * @param {string} raw
 * @returns {{schema: string, comments: object[]}}
 */
function parseLedger(raw) {
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error(
      `${COMMENTS_REL_PATH} is not valid JSON — refusing to treat it as an empty ledger (that would silently hide any comments it actually contains). Fix or restore the file.`,
    );
  }
  if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.comments)) {
    throw new Error(
      `${COMMENTS_REL_PATH} has an unexpected shape (expected {schema, comments: [...]}) — refusing to read it as a ledger.`,
    );
  }
  if (parsed.schema !== undefined && parsed.schema !== COMMENTS_SCHEMA) {
    throw new Error(
      `${COMMENTS_REL_PATH} declares schema "${parsed.schema}", expected "${COMMENTS_SCHEMA}" — refusing to read an unknown-schema ledger.`,
    );
  }
  return { schema: COMMENTS_SCHEMA, comments: parsed.comments };
}

/**
 * Read the ledger. A MISSING file is the tolerated empty seed. A file that
 * exists but fails `parseLedger` throws — see the file-level note on why reads
 * do not tolerate corruption the way qa/lib/approvals.mjs does.
 * @param {string} root
 * @returns {{schema: string, comments: object[]}}
 */
function readLedger(root) {
  let raw;
  try {
    raw = fs.readFileSync(ledgerPath(root), "utf8");
  } catch {
    return { schema: COMMENTS_SCHEMA, comments: [] };
  }
  return parseLedger(raw);
}

/**
 * Write the ledger (deterministic key order, trailing newline) — creates
 * qa/comments.json and its parent dir if this is the first write.
 * @param {string} root
 * @param {{comments: object[]}} state
 */
function writeLedger(root, state) {
  const p = ledgerPath(root);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  const out = { schema: COMMENTS_SCHEMA, comments: state.comments };
  fs.writeFileSync(p, `${JSON.stringify(out, null, 2)}\n`);
}

/**
 * Next id: "c" + (1 + the highest existing numeric suffix), so ids are
 * monotonic and never reused even if the ledger is edited by hand between
 * calls (count-based numbering would reuse an id after any external edit;
 * max-based numbering does not).
 * @param {object[]} comments
 * @returns {string}
 */
function nextId(comments) {
  let max = 0;
  for (const c of comments) {
    const m = typeof c.id === "string" && c.id.match(/^c(\d+)$/);
    if (m) max = Math.max(max, Number(m[1]));
  }
  return `c${max + 1}`;
}

function nonEmptyString(v) {
  return typeof v === "string" && v.trim().length > 0;
}

// ── Reads ────────────────────────────────────────────────────────────────────

/**
 * Every comment in the ledger, optionally filtered by status. Throws if
 * qa/comments.json exists but is corrupt or declares an unknown schema (see
 * the file-level note) — callers that must never throw (a console route, a
 * blocking MCP tool) should catch and surface the message rather than
 * swallow it into a fabricated empty list.
 * @param {string} root
 * @param {{status?: "open"|"resolved"}} [opts]
 * @returns {{schema: string, comments: object[]}}
 */
export function listComments(root, opts = {}) {
  const state = readLedger(root);
  const comments = opts.status ? state.comments.filter((c) => c.status === opts.status) : state.comments;
  return { schema: COMMENTS_SCHEMA, comments };
}

// ── Writes ───────────────────────────────────────────────────────────────────

/**
 * Add a comment. Refuses (never throws):
 *   - empty or whitespace-only `text`
 *   - a missing/malformed `target` or an unknown `target.type`
 *   - a `target` missing a field its type requires (screen -> screen; element ->
 *     screen, testTag; spec-line -> file, clauseId; design-system -> token;
 *     architecture -> path; general -> none)
 *   - a ledger that exists but cannot be parsed (corrupt/unknown-schema) — the
 *     write is refused rather than overwriting a file we could not honestly read
 * @param {string} root
 * @param {{target: object, text: string, author?: string}} input
 * @returns {{ok: true, comment: object} | {ok: false, reason: string}}
 */
export function addComment(root, { target, text, author } = {}) {
  if (!nonEmptyString(text)) {
    return { ok: false, reason: "comment text is empty or whitespace-only — refusing to record an empty comment." };
  }
  if (!target || typeof target !== "object" || typeof target.type !== "string") {
    return {
      ok: false,
      reason: `comment target is missing or malformed — expected {type, ...} with type one of: ${VALID_TARGET_TYPES.join(", ")}.`,
    };
  }
  const requiredFields = TARGET_FIELD_REQUIREMENTS[target.type];
  if (!requiredFields) {
    return {
      ok: false,
      reason: `unknown target type "${target.type}" — valid types: ${VALID_TARGET_TYPES.join(", ")}.`,
    };
  }
  const missingFields = requiredFields.filter((f) => !nonEmptyString(target[f]));
  if (missingFields.length > 0) {
    return {
      ok: false,
      reason: `target type "${target.type}" requires ${requiredFields.join(", ")} — missing or empty: ${missingFields.join(", ")}.`,
    };
  }

  let state;
  try {
    state = readLedger(root);
  } catch (err) {
    return { ok: false, reason: err.message };
  }

  const comment = {
    id: nextId(state.comments),
    target,
    text: text.trim(),
    author: nonEmptyString(author) ? author.trim() : "anonymous",
    createdAt: new Date().toISOString(),
    status: "open",
  };
  writeLedger(root, { comments: [...state.comments, comment] });
  return { ok: true, comment };
}

/**
 * Resolve a comment: stamps status "resolved", who closed it, when, and an
 * optional note explaining what changed as a result. Refuses (never throws):
 *   - an unknown id
 *   - a comment that is already resolved (double-resolve)
 *   - a ledger that exists but cannot be parsed
 * @param {string} root
 * @param {string} id
 * @param {{note?: string, author?: string}} [opts]
 * @returns {{ok: true, comment: object} | {ok: false, reason: string}}
 */
export function resolveComment(root, id, opts = {}) {
  let state;
  try {
    state = readLedger(root);
  } catch (err) {
    return { ok: false, reason: err.message };
  }

  const idx = state.comments.findIndex((c) => c.id === id);
  if (idx === -1) {
    const known = state.comments.map((c) => c.id).join(", ") || "(none — the ledger is empty)";
    return { ok: false, reason: `unknown comment id "${id}" — known ids: ${known}.` };
  }
  const existing = state.comments[idx];
  if (existing.status === "resolved") {
    return {
      ok: false,
      reason: `comment "${id}" is already resolved (at ${existing.resolvedAt} by ${existing.resolvedBy}) — refusing to double-resolve.`,
    };
  }

  const resolved = {
    ...existing,
    status: "resolved",
    resolvedAt: new Date().toISOString(),
    resolvedBy: nonEmptyString(opts.author) ? opts.author.trim() : "anonymous",
    ...(nonEmptyString(opts.note) ? { resolutionNote: opts.note.trim() } : {}),
  };
  const comments = [...state.comments];
  comments[idx] = resolved;
  writeLedger(root, { comments });
  return { ok: true, comment: resolved };
}
