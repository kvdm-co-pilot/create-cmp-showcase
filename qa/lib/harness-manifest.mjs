// harness-manifest.mjs — WHICH STACK PROFILE this project uses, and where it
// keeps the things the lane reads. The lane's own reader of
// qa/harness-manifest.json.
//
// WHY THE LANE READS IT NOW. The manifest existed before this file — written
// for the console, read by seven console modules and zero lane modules — so
// the lane kept hardcoding one project's layout (composeApp/src, qa/e2e, the
// Compose step pack imported by name) while the console beside it had learned
// to ask. This closes that: the lane resolves the manifest first, loads the
// profile it names (qa/lib/profile-loader.mjs), and only then knows what a
// "step" or a "test root" is for this project.
//
// THERE IS NO DEFAULT PROFILE (decision 3, 2026-09-04). The Compose profile is
// a profile like any other; a privileged default would be the coupling this
// removes wearing a different name. So an ABSENT manifest is a refusal that
// names the command which writes one:
//
//   a stamped app (create-cmp.json present)  →  create-cmp upgrade --harness
//                                                derives it from what it knows
//   a foreign repo, any stack                 →  create-cmp harness init
//                                                writes the manifest, a working
//                                                profile, the surface and the
//                                                lock, then runs Rule 0
//
// A PRESENT-BUT-MALFORMED manifest is refused too, naming every problem at
// once — never silently defaulted, for the reason the console's reader gives:
// a lane that falls back to the wrong layout reports an honest-looking absence
// of files that exist ten characters away.
//
// The shape is FLAT: the layout fields sit beside `schema` and `profile`, not
// under a nested `layout` key. The console's reader validates field-by-field
// and refuses unknown keys, and a stamped app must satisfy both readers with
// one file. (Stage 0.5 unifies the two readers; the flat shape is what both
// accept today.)
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/harness-manifest.mjs in the
// create-cmp repo. The copy in a generated project's qa/lib/ is vendored
// byte-identical at scaffold time — edit the package source, then run
// `node scripts/sync-harness.mjs` in the create-cmp repo.

import fs from "node:fs";
import path from "node:path";

/** Where a project declares its profile and layout. Required for the lane to run. */
export const MANIFEST_REL_PATH = "qa/harness-manifest.json";

/** The manifest schema this lane writes and understands. */
export const MANIFEST_SCHEMA = "harness-manifest/2";

/** Layout fields that are single project-relative paths. */
export const LAYOUT_PATH_FIELDS = ["receipt", "architectureDoc", "specs", "approvals"];
/** Layout fields that are non-empty lists. `packs` is informational (console). */
export const LAYOUT_LIST_FIELDS = ["citationRoots", "packs"];
const META_FIELDS = ["schema", "profile"];
const KNOWN_FIELDS = new Set([...META_FIELDS, ...LAYOUT_PATH_FIELDS, ...LAYOUT_LIST_FIELDS]);

/** A profile id is one path segment, lowercase, dash-separated — it becomes a directory name. */
export const PROFILE_ID_RE = /^[a-z][a-z0-9-]*$/;

function pathProblem(field, value) {
  if (typeof value !== "string" || value.trim() === "") return `${field} must be a non-empty string`;
  if (value.startsWith("/") || /^[A-Za-z]:[\\/]/.test(value)) return `${field} must be relative to the project root (got "${value}")`;
  if (value.includes("\\")) return `${field} must use "/" separators (got "${value}")`;
  if (value.split("/").some((seg) => seg === "..")) return `${field} may not escape the project root (got "${value}")`;
  return null;
}

/**
 * Every contract violation in a parsed manifest, or [] when it is valid. Never
 * throws — a caller prints all defects at once rather than the first.
 * @param {unknown} parsed
 * @returns {string[]}
 */
export function manifestProblems(parsed) {
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return ["the manifest must be a JSON object"];
  const problems = [];
  for (const key of Object.keys(parsed)) {
    if (!KNOWN_FIELDS.has(key)) problems.push(`unknown field "${key}" (known: ${[...KNOWN_FIELDS].join(", ")})`);
  }
  if ("schema" in parsed && (typeof parsed.schema !== "string" || !parsed.schema.startsWith("harness-manifest/"))) {
    problems.push(`schema must be a string of the form "harness-manifest/<n>" (got ${JSON.stringify(parsed.schema)})`);
  }
  // The profile is the one REQUIRED field: without it the lane cannot know
  // what a step is for this project, and guessing is the bug this file closes.
  if (!("profile" in parsed)) {
    problems.push(`profile is required — add "profile": { "id": "<profile>" } naming the stack profile under qa/lib/profiles/`);
  } else {
    const p = parsed.profile;
    if (!p || typeof p !== "object" || Array.isArray(p)) problems.push("profile must be an object { id, version? }");
    else {
      if (typeof p.id !== "string" || !PROFILE_ID_RE.test(p.id)) {
        problems.push(`profile.id must match ${PROFILE_ID_RE} — it names a directory under qa/lib/profiles/ (got ${JSON.stringify(p.id)})`);
      }
      if ("version" in p && typeof p.version !== "string") problems.push("profile.version must be a string when present");
      for (const key of Object.keys(p)) {
        if (key !== "id" && key !== "version") problems.push(`profile has an unknown field "${key}" (known: id, version)`);
      }
    }
  }
  for (const field of LAYOUT_PATH_FIELDS) {
    if (!(field in parsed)) continue;
    const p = pathProblem(field, parsed[field]);
    if (p) problems.push(p);
  }
  for (const field of LAYOUT_LIST_FIELDS) {
    if (!(field in parsed)) continue;
    const list = parsed[field];
    if (!Array.isArray(list) || list.length === 0) {
      problems.push(`${field} must be a non-empty array of strings`);
      continue;
    }
    list.forEach((entry, i) => {
      const p =
        field === "citationRoots"
          ? pathProblem(`${field}[${i}]`, entry)
          : typeof entry === "string" && entry.trim()
            ? null
            : `${field}[${i}] must be a non-empty string`;
      if (p) problems.push(p);
    });
  }
  return problems;
}

/**
 * The refusal for a MISSING manifest — which names the command that writes
 * one, because the two kinds of project get there differently.
 * @param {string} root
 * @returns {string}
 */
export function absentManifestReason(root) {
  const stamped = fs.existsSync(path.join(root, "create-cmp.json"));
  const how = stamped
    ? "This is a create-cmp app: run `create-cmp upgrade --harness` and it will write the manifest from what it already knows."
    : "Run `create-cmp harness init` — it writes the manifest, a working profile for this project, the verified surface and the lock, then proves the lane returns.";
  return (
    `${MANIFEST_REL_PATH} is missing — the lane cannot run without knowing which stack profile this project uses, ` +
    `and there is no default. ${how}`
  );
}

/**
 * Resolve the project's manifest.
 *
 * @param {string} root project root
 * @returns {{ok: true, manifest: object, relPath: string}
 *         | {ok: false, absent: boolean, reason: string, relPath: string}}
 *   absent  true when no file exists (the reason names the command that writes one)
 *           false when a file exists and is unusable (the reason names every problem)
 */
export function resolveHarnessManifest(root) {
  const file = path.join(root, ...MANIFEST_REL_PATH.split("/"));
  if (!fs.existsSync(file)) {
    return { ok: false, absent: true, relPath: MANIFEST_REL_PATH, reason: absentManifestReason(root) };
  }
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (err) {
    return {
      ok: false,
      absent: false,
      relPath: MANIFEST_REL_PATH,
      reason: `${MANIFEST_REL_PATH} is not valid JSON (${err && err.message ? err.message : String(err)}) — fix it; no layout is assumed while a manifest is present`,
    };
  }
  const problems = manifestProblems(parsed);
  if (problems.length) {
    return {
      ok: false,
      absent: false,
      relPath: MANIFEST_REL_PATH,
      reason: `${MANIFEST_REL_PATH} is malformed: ${problems.join("; ")} — fix it; no layout is assumed while a manifest is present`,
    };
  }
  return { ok: true, manifest: parsed, relPath: MANIFEST_REL_PATH };
}

/**
 * A manifest for a profile, with optional layout overrides — what `attach`
 * writes after its interview and what a stamper ships.
 * @param {string} profileId
 * @param {object} [layout] layout fields to carry (validated on write)
 * @returns {object}
 */
export function manifestFor(profileId, layout = {}) {
  return { schema: MANIFEST_SCHEMA, profile: { id: profileId }, ...layout };
}

/**
 * Write a manifest — after validating it. A writer that could put an invalid
 * manifest on disk would hand the next lane run a refusal it created itself.
 * @param {string} root
 * @param {object} manifest
 * @returns {{ok: true, relPath: string} | {ok: false, reason: string}}
 */
export function writeHarnessManifest(root, manifest) {
  const problems = manifestProblems(manifest);
  if (problems.length) return { ok: false, reason: `refusing to write an invalid manifest: ${problems.join("; ")}` };
  try {
    const file = path.join(root, ...MANIFEST_REL_PATH.split("/"));
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
    return { ok: true, relPath: MANIFEST_REL_PATH };
  } catch (err) {
    return { ok: false, reason: err && err.message ? err.message : String(err) };
  }
}
