// step-cache.mjs — fast-mode memoization for the lane's pure-Node steps.
//
// The steps this serves (specCoverage, approvals, componentStories,
// reachability, archDoc) are pure functions of files on disk: no Gradle, no
// git, no network, no clock in the verdict. For those — and ONLY those — a
// verdict can be safely reused when a content hash of the step's declared
// input set is unchanged since the last run that produced it.
//
// GROUND RULES, each load-bearing:
//
//   - THE CACHE IS A CACHE, NEVER EVIDENCE. It lives in the gitignored build
//     dir (composeApp/build/.cmp-step-cache.json), must never be committed,
//     and must never be read by qa/receipt-check.mjs or any other receipt
//     consumer. Deleting it can only cost time, never correctness.
//
//   - THE FULL LANE NEVER READS IT. This is deliberate: it keeps the full
//     lane's integrity property absolute rather than "absolute unless a cache
//     says otherwise". A full run WRITES entries (so the next fast run
//     benefits) but always executes every step itself.
//
//   - ONLY A CACHED PASS IS EVER REUSED. A cached FAIL is always re-run so
//     the failure detail is fresh; a cached SKIP is re-derived (these steps
//     are cheap enough that only the PASS case is worth reusing, and a SKIP's
//     reason — e.g. which approvals are pending — must stay current).
//
//   - THE DECLARED INPUT SET IS THE WHOLE SAFETY ARGUMENT. A step's inputs
//     must cover everything it reads; a wrong input set is a silently-stale
//     gate — the worst possible bug this file could host. Callers over-declare
//     on purpose (a too-broad set only costs cache misses; a too-narrow one
//     costs truth). The declarations live next to the steps in qa/verify.mjs.
//
//   - THE CACHE MUST NEVER BREAK THE LANE. Missing, corrupt, unreadable,
//     unwritable — every failure mode degrades to "execute the step", never
//     to an error and never to a reused verdict.

import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";

export const STEP_CACHE_REL_PATH = "composeApp/build/.cmp-step-cache.json";
export const STEP_CACHE_SCHEMA = "cmp-step-cache/1";

function toPosix(p) {
  return p.split(path.sep).join("/");
}

/** Every file under `dir` (recursive), absolute paths. Missing dir → []. */
function walkAllFiles(dir) {
  const out = [];
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walkAllFiles(p));
    else if (e.isFile()) out.push(p);
  }
  return out;
}

/**
 * Content-hash one step's declared input set: sha256 over the sorted list of
 * `relpath\0bytes` entries. Deterministic: same paths + same bytes → same
 * hash, independent of declaration order, walk order, and platform separators.
 * A file appearing, disappearing, moving, or changing content all change the
 * hash — which is exactly the set of events that can change a pure-Node
 * step's verdict.
 *
 * @param {string} root project root (absolute)
 * @param {string[]} inputs paths relative to root — each a file or a
 *   directory (walked recursively). Missing entries contribute nothing (their
 *   later appearance changes the hash).
 * @returns {{hash: string, fileCount: number}}
 */
export function computeStepInputsHash(root, inputs) {
  const relFiles = new Set();
  for (const rel of inputs ?? []) {
    const abs = path.join(root, rel);
    let stat;
    try {
      stat = fs.statSync(abs);
    } catch {
      continue; // absent input — contributes nothing until it exists
    }
    if (stat.isFile()) {
      relFiles.add(toPosix(rel));
    } else if (stat.isDirectory()) {
      for (const f of walkAllFiles(abs)) {
        relFiles.add(toPosix(path.relative(root, f)));
      }
    }
  }
  // Code-unit sort (default String sort), same stance as inputs-hash.mjs: the
  // hash depends on iteration order and must be identical on every machine.
  const sorted = [...relFiles].sort();
  const overall = createHash("sha256");
  for (const rel of sorted) {
    overall.update(rel);
    overall.update("\0");
    overall.update(fs.readFileSync(path.join(root, rel)));
    overall.update("\n");
  }
  return { hash: overall.digest("hex"), fileCount: sorted.length };
}

/**
 * Load the cache file. Absent, corrupt, or wrong-schema is TOLERATED and
 * returns an empty cache — a cache that cannot be read is a cache miss,
 * never an error (see ground rules).
 * @param {string} root
 * @returns {{schema: string, steps: Record<string, {inputsHash: string, verdict: string, at: string}>}}
 */
export function loadStepCache(root) {
  try {
    const parsed = JSON.parse(fs.readFileSync(path.join(root, STEP_CACHE_REL_PATH), "utf8"));
    if (!parsed || parsed.schema !== STEP_CACHE_SCHEMA || typeof parsed.steps !== "object" || parsed.steps === null || Array.isArray(parsed.steps)) {
      return { schema: STEP_CACHE_SCHEMA, steps: {} };
    }
    return { schema: STEP_CACHE_SCHEMA, steps: parsed.steps };
  } catch {
    return { schema: STEP_CACHE_SCHEMA, steps: {} };
  }
}

/**
 * The reuse decision: return the cached entry iff the step's last EXECUTED
 * verdict was PASS and its inputs hash exactly matches `inputsHash`. A cached
 * FAIL or SKIP is never reused (re-run so the detail is fresh); a hash
 * mismatch is a miss; a malformed entry is a miss.
 * @param {string} root
 * @param {string} stepName
 * @param {string} inputsHash
 * @returns {{inputsHash: string, verdict: string, at: string}|null}
 */
export function lookupCachedPass(root, stepName, inputsHash) {
  const entry = loadStepCache(root).steps[stepName];
  if (!entry || typeof entry !== "object") return null;
  if (entry.verdict !== "PASS") return null; // FAIL/SKIP/ERROR are never reused
  if (typeof inputsHash !== "string" || entry.inputsHash !== inputsHash) return null;
  if (typeof entry.at !== "string") return null;
  return entry;
}

/**
 * Record a step's EXECUTED result (any verdict — the entry always reflects
 * the last real execution; only lookupCachedPass decides reusability).
 * Write failures are swallowed: an unwritable cache costs the next run time,
 * never correctness.
 * @param {string} root
 * @param {string} stepName
 * @param {{inputsHash: string, verdict: string, at?: string}} entry
 */
export function writeStepCacheEntry(root, stepName, { inputsHash, verdict, at = new Date().toISOString() }) {
  try {
    const cache = loadStepCache(root);
    cache.steps[stepName] = { inputsHash, verdict, at };
    const p = path.join(root, STEP_CACHE_REL_PATH);
    fs.mkdirSync(path.dirname(p), { recursive: true });
    fs.writeFileSync(p, `${JSON.stringify(cache, null, 2)}\n`);
  } catch {
    // never a lane failure — see ground rules
  }
}

/**
 * The one memoization flow, shared by every memoized step so the mode rules
 * cannot drift per step:
 *
 *   fast mode:  hash inputs → cached PASS with matching hash → return a
 *               CACHED result (verdict "CACHED", distinct from PASS so a fast
 *               receipt can never be mistaken for a fully-executed one);
 *               otherwise execute, record, return the real result.
 *   full mode:  ALWAYS execute — the cache is never consulted (the full
 *               lane's integrity property stays absolute; see ground rules) —
 *               then record, so the next fast run benefits.
 *
 * Any cache-machinery error (hashing, read, write) degrades to plain
 * execution.
 *
 * @param {object} args
 * @param {boolean} args.fast whether this is a --fast run
 * @param {string} args.root project root
 * @param {string} args.stepName the lane step's name (the cache key)
 * @param {string[]} args.inputs the step's declared input set (see ground rules)
 * @param {() => object} args.run the real step function
 * @returns {object} the step result — either `run()`'s verbatim, or a
 *   `{name, verdict: "CACHED", note, durationMs, details}` reuse marker
 */
export function memoizeStep({ fast, root, stepName, inputs, run }) {
  const started = Date.now();
  let inputsHash = null;
  try {
    // Hashed BEFORE execution so the recorded entry binds the verdict to the
    // tree the step actually saw, not to edits made while it ran.
    inputsHash = computeStepInputsHash(root, inputs).hash;
  } catch {
    inputsHash = null;
  }

  if (fast && inputsHash) {
    const hit = lookupCachedPass(root, stepName, inputsHash);
    if (hit) {
      return {
        name: stepName,
        verdict: "CACHED",
        note: `unchanged since ${hit.at}`,
        durationMs: Date.now() - started,
        details: { inputsHash },
      };
    }
  }

  const result = run();
  if (inputsHash && result && typeof result.verdict === "string") {
    writeStepCacheEntry(root, stepName, { inputsHash, verdict: result.verdict });
  }
  return result;
}
