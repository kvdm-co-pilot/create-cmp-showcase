// profile-loader.mjs — load the stack profile the manifest names. NEVER by name.
//
// The one rule of the agnostic harness (docs/proposals/AGNOSTIC-HARNESS-
// ARCHITECTURE.md §3.2): nothing in the core imports a profile by name. Until
// this file, qa/verify.mjs imported the Compose step pack by name — which is
// why a Kotlin backend that wrote its own step pack correctly still had to
// fork eleven spine files to use it (§1.3). The runner could not start without
// Compose's pack.
//
// Now the runner asks the manifest which profile, and this loader imports
// qa/lib/profiles/<id>/index.mjs. The loader knows the SHAPE of a profile
// (§5.1) and nothing about any particular one. Terraform's core/provider rule;
// LSP's client/server rule.
//
// Every failure is a refusal by name: a missing directory, an id that does not
// match the manifest, a protocol the core does not speak, a module without the
// exports the runner needs. None of them fall back to anything.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/profile-loader.mjs in the
// create-cmp repo. Vendored byte-identical into qa/lib/ — edit the package
// source, then run `node scripts/sync-harness.mjs` in the create-cmp repo.

import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { pathToFileURL } from "node:url";

import { PROFILE_ID_RE } from "./harness-manifest.mjs";

/**
 * The profile protocol this core speaks. A profile declares the protocol it
 * implements; mismatch is a refusal naming both, with the upgrade command.
 * One integer — the Terraform handshake.
 *
 * Protocol 1 is still being drawn: its required exports grew during Stage 0
 * (`layout` and `tiers` in PR 4) while the only implementer ships in this
 * tree, vendored beside the core it matches. It freezes at Stage 2, when a
 * profile can be versioned apart from the harness; from then on a new
 * required export is a new protocol number.
 */
/**
 * The protocol this lane SPEAKS. Bumped to 2 when `extends` landed, because
 * inheritance changes what a profile must export: an heir declares only what it
 * changes, and only a loader that can DERIVE the rest can load it.
 *
 * Without the bump an heir would declare 1, and an older loader would refuse it
 * with "profile X must export layout" — pointing the author at their own file
 * when the real fix is to upgrade the harness. The protocol refusal below says
 * exactly that instead, which is why the version has to move for the message to
 * be worth anything.
 */
export const PROFILE_PROTOCOL = 3;

/**
 * Every protocol this lane can load. A profile at 1 is one written before
 * `extends` existed; it is complete on its own and nothing about it changed, so
 * refusing it would be a rename dressed as a version — the failure ADR-0007
 * refused for the receipt, one layer down.
 */
export const SUPPORTED_PROFILE_PROTOCOLS = Object.freeze([1, 2, 3]);

/** The protocol a profile must declare before it may use `extends`. */
export const EXTENDS_PROTOCOL = 2;

/** Where profiles live, relative to the project root. Inside the lock region. */
export const PROFILES_DIR_REL = "qa/lib/profiles";

/**
 * The exports a profile MUST provide for the runner to start. `layout` and
 * `tiers` are the spec scanner's model (qa/lib/spec-model.mjs validates their
 * shape); `steps(ctx)` is the pack.
 */
export const REQUIRED_EXPORTS = Object.freeze(["id", "protocol", "layout", "tiers", "steps"]);

/**
 * The project-relative path of a profile's entry module.
 * @param {string} id
 * @returns {string}
 */
export function profileEntryRel(id) {
  return `${PROFILES_DIR_REL}/${id}/index.mjs`;
}

/**
 * Judge a loaded module against the protocol — pure, so the refusals are
 * unit-testable without a filesystem.
 * @param {object} mod the imported module namespace
 * @param {string} id the id the manifest named
 * @returns {{ok: true} | {ok: false, reason: string}}
 */
export function validateProfileModule(mod, id) {
  if (!mod || typeof mod !== "object") return { ok: false, reason: `profile "${id}" did not load as a module` };
  const missing = REQUIRED_EXPORTS.filter((name) => !(name in mod));
  if (missing.length) {
    return { ok: false, reason: `profile "${id}" is missing required export(s): ${missing.join(", ")} (a profile must export ${REQUIRED_EXPORTS.join(", ")})` };
  }
  if (mod.id !== id) {
    return { ok: false, reason: `profile "${id}" exports id ${JSON.stringify(mod.id)} — the manifest and the profile disagree about what this project is; fix one of them` };
  }
  if (!SUPPORTED_PROFILE_PROTOCOLS.includes(mod.protocol)) {
    return {
      ok: false,
      reason: `profile "${id}" implements profile protocol ${JSON.stringify(mod.protocol)}; this lane speaks ${SUPPORTED_PROFILE_PROTOCOLS.join(" and ")} — upgrade the harness or the profile so they match (\`prooflane upgrade\`)`,
    };
  }
  if (typeof mod.steps !== "function") return { ok: false, reason: `profile "${id}" must export steps(ctx) as a function` };
  if (!mod.layout || typeof mod.layout !== "object") return { ok: false, reason: `profile "${id}" must export layout as an object (where specs, sources, tests and flows live)` };
  if (!mod.tiers || typeof mod.tiers !== "object") return { ok: false, reason: `profile "${id}" must export tiers as an object (which test tiers exist and which can observe which promise)` };
  // Optional declarations: absent is allowed (the core then applies its floor);
  // present-but-wrong is refused, never ignored.
  for (const name of ["artifacts", "governable"]) {
    if (name in mod && typeof mod[name] !== "function") return { ok: false, reason: `profile "${id}" exports ${name} but it is not a function (${name}(root))` };
  }
  return { ok: true };
}

/**
 * Locate the profile's entry module for `id`, refusing an unsafe id or a
 * missing directory by name. Shared by the async and sync loaders.
 * @param {string} root
 * @param {string} id
 * @returns {{ok: true, entryRel: string, entryAbs: string} | {ok: false, reason: string}}
 */
function locateProfile(root, id) {
  if (typeof id !== "string" || !PROFILE_ID_RE.test(id)) {
    return { ok: false, reason: `profile id ${JSON.stringify(id)} is not a valid profile name (${PROFILE_ID_RE}) — it names a directory under ${PROFILES_DIR_REL}/` };
  }
  const entryRel = profileEntryRel(id);
  const entryAbs = path.join(root, ...entryRel.split("/"));
  if (!fs.existsSync(entryAbs)) {
    let present = [];
    try {
      present = fs
        .readdirSync(path.join(root, ...PROFILES_DIR_REL.split("/")), { withFileTypes: true })
        .filter((e) => e.isDirectory())
        .map((e) => e.name);
    } catch {
      /* no profiles dir at all */
    }
    const have = present.length ? `profiles present: ${present.join(", ")}` : `no profiles are installed under ${PROFILES_DIR_REL}/`;
    return { ok: false, reason: `the manifest names profile "${id}" but ${entryRel} does not exist (${have}) — install the profile or fix ${"qa/harness-manifest.json"}` };
  }
  return { ok: true, entryRel, entryAbs };
}

/**
 * WHICH DECLARATION NAMES A BASE. `extends` is a reserved word — legal as an
 * export NAME (`export { BASE as extends }`) but not as a binding — so a
 * profile author may reasonably reach for either spelling. Both are read, and
 * neither is privileged: an author should not have to guess which one the core
 * happens to prefer.
 */
const BASE_KEYS = Object.freeze(["extends", "extendsProfile"]);

/**
 * The base a profile declares, or null. A base is DATA — the id of another
 * installed profile — never an import: an heir that imports its base is an ESM
 * re-export wearing the word, and the core derived nothing.
 * @param {object} mod
 * @returns {string|null}
 */
export function declaredBase(mod) {
  for (const key of BASE_KEYS) {
    const value = mod?.[key];
    if (typeof value === "string" && value.length > 0) return value;
  }
  return null;
}

/**
 * The declarations an heir inherits when it does not make them itself.
 *
 * WHY A NAMED LIST RATHER THAN "every export the base has". Spreading a module
 * namespace would inherit `id` and `protocol` too — an heir would silently
 * become its base, and a receipt would name the wrong pack. It would also
 * inherit anything a base happens to export for its own internal use, which is
 * not a contract. So inheritance covers exactly the protocol's own surface: the
 * five REQUIRED_EXPORTS minus identity, plus the optional declarations the
 * loader and the lane already know by name.
 */
const INHERITABLE = Object.freeze([
  "layout",
  "tiers",
  "steps",
  "artifacts",
  "governable",
  "grammar",
  "reports",
  "detect",
  "tools",
  "ladder",
  // Read against `ladder` by the Stop hook, so it must inherit WITH it: an heir
  // that kept the step names and lost the reason texts would leave the gate
  // that refuses "done" over a tier that never ran silently inert for its own
  // legacy receipts. The halves are one fact.
  "legacySkipReasons",
  "plants",
  "console",
  "version",
]);

/**
 * Resolve a profile's inheritance chain into one module-shaped object.
 *
 * THE HEIR WINS, ALWAYS, and only for what it actually declares — `in` rather
 * than a truthiness check, so a profile can override a declaration with `null`
 * (a stack with no flows does exactly that) instead of having the base's value
 * silently restored underneath it.
 *
 * A CYCLE IS REFUSED BY NAME, not survived. `a extends b extends a` is an
 * author error, and the useful output is the chain that closed it; hanging or
 * blowing the stack tells them nothing.
 *
 * @param {object} mod the heir, already imported
 * @param {string} id its own id
 * @param {(baseId: string) => {ok: true, profile: object} | {ok: false, reason: string}} load
 *   how to load a base by id — the caller supplies sync or async resolution
 * @returns {{ok: true, profile: object, chain: string[]} | {ok: false, reason: string}}
 */
export function resolveInheritance(mod, id, load) {
  const chain = [id];
  const merged = {};
  let current = mod;

  const ownBase = declaredBase(mod);
  if (ownBase && mod.protocol < EXTENDS_PROTOCOL) {
    return {
      ok: false,
      reason:
        `profile "${id}" declares a base ("${ownBase}") but implements profile protocol ${JSON.stringify(mod.protocol)} — ` +
        `\`extends\` arrived in protocol ${EXTENDS_PROTOCOL}. Declare \`protocol = ${EXTENDS_PROTOCOL}\` so an older lane refuses it by naming the protocol ` +
        `rather than by naming a declaration you deliberately left out`,
    };
  }

  for (;;) {
    const base = declaredBase(current);
    if (!base) break;
    if (chain.includes(base)) {
      return {
        ok: false,
        reason: `profile "${id}" has a circular \`extends\` chain: ${[...chain, base].join(" → ")} — a profile cannot inherit from itself, however many steps around`,
      };
    }
    const loaded = load(base);
    if (!loaded.ok) {
      return { ok: false, reason: `profile "${chain[chain.length - 1]}" extends "${base}", which did not load: ${loaded.reason}` };
    }
    chain.push(base);
    // Nearest ancestor wins over a more distant one: only fill what is still
    // absent as the walk moves away from the heir.
    for (const key of INHERITABLE) {
      if (!(key in merged) && key in loaded.profile) merged[key] = loaded.profile[key];
    }
    current = loaded.profile;
  }

  if (chain.length === 1) return { ok: true, profile: mod, chain };

  // The heir's own declarations sit on top, and identity is never inherited.
  const profile = { ...merged };
  for (const key of Object.keys(mod)) profile[key] = mod[key];
  profile.id = mod.id;
  profile.protocol = mod.protocol;
  return { ok: true, profile, chain };
}

/**
 * loadProfile, synchronously — for the readers that only have a project root
 * and sit in a sync chain (the spec scanner via feature-brief and approvals,
 * the console's Specs bridge). A profile is plain ESM without top-level
 * await, so `require()` loads it: supported since Node 20.19 / 22.12 and
 * sharing the module cache with `import()`, so both loaders hand back the
 * same instance. On an older Node the refusal names the floor instead of
 * guessing a layout.
 * @param {string} root project root
 * @param {{id: string}} named the manifest's `profile`
 * @returns {{ok: true, profile: object, entryRel: string} | {ok: false, reason: string}}
 */
export function loadProfileSync(root, { id } = {}) {
  const where = locateProfile(root, id);
  if (!where.ok) return where;
  let mod;
  try {
    mod = createRequire(import.meta.url)(where.entryAbs);
  } catch (err) {
    const code = err && err.code;
    if (code === "ERR_REQUIRE_ESM" || code === "ERR_REQUIRE_ASYNC_MODULE") {
      return { ok: false, reason: `profile "${id}" cannot be loaded synchronously on Node ${process.version} — the harness needs Node 20.19 or 22.12 or newer (require() of ES modules); upgrade Node` };
    }
    return { ok: false, reason: `profile "${id}" failed to load from ${where.entryRel}: ${err && err.message ? err.message : String(err)}` };
  }
  // INHERITANCE BEFORE VALIDATION. The protocol's required exports are a
  // property of the RESOLVED profile, not of the file: an heir that declares
  // only what it changes is complete once its base is merged in, and
  // validating the raw module would refuse it for missing a declaration it
  // legitimately inherits.
  const resolved = resolveInheritance(mod, id, (baseId) => loadProfileSync(root, { id: baseId }));
  if (!resolved.ok) return resolved;
  const verdict = validateProfileModule(resolved.profile, id);
  if (!verdict.ok) return verdict;
  return { ok: true, profile: resolved.profile, entryRel: where.entryRel, chain: resolved.chain };
}

/**
 * Load and validate the profile the manifest names.
 * @param {string} root project root
 * @param {{id: string}} named the manifest's `profile`
 * @returns {Promise<{ok: true, profile: object, entryRel: string} | {ok: false, reason: string}>}
 */
export async function loadProfile(root, { id } = {}) {
  const where = locateProfile(root, id);
  if (!where.ok) return where;
  let mod;
  try {
    mod = await import(pathToFileURL(where.entryAbs).href);
  } catch (err) {
    return { ok: false, reason: `profile "${id}" failed to load from ${where.entryRel}: ${err && err.message ? err.message : String(err)}` };
  }
  // The async twin resolves the chain the same way; `loadProfileSync` shares
  // the module cache with `import()`, so both loaders hand back one instance
  // of every base and cannot disagree about what an heir inherited.
  const resolved = resolveInheritance(mod, id, (baseId) => loadProfileSync(root, { id: baseId }));
  if (!resolved.ok) return resolved;
  const verdict = validateProfileModule(resolved.profile, id);
  if (!verdict.ok) return verdict;
  return { ok: true, profile: resolved.profile, entryRel: where.entryRel, chain: resolved.chain };
}
