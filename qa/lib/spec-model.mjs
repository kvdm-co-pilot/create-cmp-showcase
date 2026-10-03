// spec-model.mjs — the scanner's view of a stack: what a profile's `layout`
// and `tiers` declarations become once the core has checked them.
//
// qa/lib/spec-coverage.mjs owns the MECHANIC — the clause grammar, the
// citation binding window, coverage in both directions, tier-must-observe.
// The profile owns the MODEL — where specs and sources live, what a citation
// file looks like, which tiers exist and which can observe which promise
// (docs/NORTH-STAR.md §6; AGNOSTIC-HARNESS-ARCHITECTURE.md §4.2 #1 and #4).
// This module joins them: it builds one validated `model` object from a
// profile, and it resolves that model from a project root for the callers
// that only have a root — the console's Specs bridge, feature-brief's derived
// doneness, the framework-check runner.
//
// Resolution is SYNCHRONOUS on purpose. Every root-only caller is a sync
// function deep in a sync chain (approvals → feature-brief → scanCitations),
// and the profile is plain ESM with no top-level await, so it is loaded with
// `require()` — supported for ESM since Node 20.19 / 22.12 and sharing the
// module cache with `import()`, so the lane and a sync reader see the same
// instance. Node 18 and 20.18 are end-of-life; the engines floor says so.
//
// Two override rules, both deliberate:
//   - the manifest's layout fields (`specs`, `citationRoots`) override the
//     profile's, field by field — the same semantics the console applies, so
//     an attached foreign repo that told `attach` where its tests are gets the
//     same scan from both readers. A stamped app's manifest carries the
//     profile's own values, so the override is the identity there.
//   - there is NO fallback model. A profile that declares no `layout` or no
//     `tiers` is refused by name at load; a root with no manifest is refused
//     with the command that writes one. A scanner that guessed a layout would
//     report honest-looking absences about files ten characters away.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/spec-model.mjs in the
// create-cmp repo. Vendored byte-identical into qa/lib/ — edit the package
// source, then run `node scripts/sync-harness.mjs` in the create-cmp repo.

import { resolveHarnessManifest } from "./harness-manifest.mjs";
import { loadProfileSync } from "./profile-loader.mjs";

/** A clause's declared requirement name: `[tier: device]` → "device". One path segment of letters/digits/dashes. */
export const TIER_NAME_RE = /^[a-z][a-z0-9-]*$/i;

function isStringList(v) {
  return Array.isArray(v) && v.length > 0 && v.every((s) => typeof s === "string" && s.trim() !== "");
}

function relPathProblem(field, value) {
  if (typeof value !== "string" || value.trim() === "") return `${field} must be a non-empty string`;
  if (value.startsWith("/") || /^[A-Za-z]:[\\/]/.test(value)) return `${field} must be relative to the project root (got "${value}")`;
  if (value.includes("\\")) return `${field} must use "/" separators (got "${value}")`;
  if (value.split("/").some((seg) => seg === "..")) return `${field} may not escape the project root (got "${value}")`;
  return null;
}

/**
 * Every contract violation in a profile's `layout` and `tiers`, or [] when
 * they are usable. Never throws; a caller prints all defects at once.
 * @param {{layout?: unknown, tiers?: unknown}} profile
 * @returns {string[]}
 */
/**
 * The core's fallback grammar: Kotlin/JVM and JavaScript. It is a FALLBACK and
 * not a default in the approving sense — `SpecModel.grammar.isDefault` records
 * that a profile declared none of it, and the coverage scan says so out loud
 * when nothing binds. A stack whose tests look like anything else declares its
 * own `grammar` export; `create-cmp harness init` seeds one from the language
 * it detects.
 */
/**
 * THE GRAMMAR IS DECLARED, NEVER DEFAULTED. Until 2026-09-08 this constant held
 * Kotlin's and JavaScript's test-declaration regexes as the core's FALLBACK, and
 * `cmp` itself never declared a grammar — so every profile that forgot the
 * export was graded with Kotlin's, silently. PATTERN: declaration over
 * inference, the shape tree-sitter uses (one query per language, named
 * captures). WHY IT WORKS: a required field cannot be silently wrong for the
 * author who forgot it — the lane refuses by name. HOW IT FAILS: a copied regex
 * from another language binds nothing or the wrong lines. WHAT WE DO: the Rule
 * 0 instrument plants an unbound citation in THIS language and watches the
 * grammar fail it by name; the coverage diagnostic prints "N markers seen, 0
 * bound". The two fields below are language-neutral and may default.
 */
export const GRAMMAR_REQUIRED = Object.freeze(["citationMarker", "testDeclaration", "lineComment"]);
export const GRAMMAR_DEFAULTS = Object.freeze({ blockComment: null, bindingWindow: 5 });

/** A profile may declare a pattern as a RegExp or as a source string. */
function regexOr(value, fallback) {
  if (value instanceof RegExp) return value;
  if (typeof value === "string" && value.trim()) {
    try {
      return new RegExp(value);
    } catch {
      return fallback;
    }
  }
  return fallback;
}

export function specDeclarationProblems(profile) {
  const out = [];
  const layout = profile?.layout;
  const tiers = profile?.tiers;
  if (!layout || typeof layout !== "object") out.push("layout must be an object");
  else {
    const p = relPathProblem("layout.specs", layout.specs);
    if (p) out.push(p);
    if (!isStringList(layout.citationRoots)) out.push("layout.citationRoots must be a non-empty list of project-relative paths");
    else for (const r of layout.citationRoots) { const q = relPathProblem("layout.citationRoots[]", r); if (q) out.push(q); }
    if (!isStringList(layout.citationExts) || !layout.citationExts.every((e) => e.startsWith("."))) out.push('layout.citationExts must be a non-empty list of file extensions, each starting with "."');
    if (layout.sourceRoots != null) {
      if (!isStringList(layout.sourceRoots)) out.push("layout.sourceRoots must be a non-empty list of project-relative paths");
      else for (const r of layout.sourceRoots) { const q = relPathProblem("layout.sourceRoots[]", r); if (q) out.push(q); }
    }
    if (layout.buildDir != null) {
      const q = relPathProblem("layout.buildDir", layout.buildDir);
      if (q) out.push(q);
    }
    if (layout.flows != null) {
      if (typeof layout.flows !== "object") out.push("layout.flows must be null or {dir, exts}");
      else {
        const q = relPathProblem("layout.flows.dir", layout.flows.dir);
        if (q) out.push(q);
        if (!isStringList(layout.flows.exts) || !layout.flows.exts.every((e) => e.startsWith("."))) out.push('layout.flows.exts must be a non-empty list of file extensions, each starting with "."');
      }
    }
  }
  if (!tiers || typeof tiers !== "object") out.push("tiers must be an object");
  else {
    if (!isStringList(tiers.names)) out.push("tiers.names must be a non-empty list of tier names");
    const names = new Set(isStringList(tiers.names) ? tiers.names : []);
    if (!Array.isArray(tiers.hostOnly) || !tiers.hostOnly.every((t) => names.has(t))) out.push("tiers.hostOnly must list tiers from tiers.names");
    if (!tiers.satisfying || typeof tiers.satisfying !== "object") out.push("tiers.satisfying must map a requirement name to the tiers that satisfy it");
    else {
      for (const [req, list] of Object.entries(tiers.satisfying)) {
        if (!TIER_NAME_RE.test(req)) out.push(`tiers.satisfying has an invalid requirement name "${req}"`);
        if (!isStringList(list) || !list.every((t) => names.has(t))) out.push(`tiers.satisfying.${req} must list tiers from tiers.names`);
      }
    }
    if (tiers.journey != null && !names.has(tiers.journey)) out.push("tiers.journey must be one of tiers.names (or null when this stack has no journey tier)");
    if (typeof tiers.forFile !== "function") out.push("tiers.forFile(rel) must be a function returning the citing file's tier");
  }
  const grammar = profile?.grammar;
  if (!grammar || typeof grammar !== "object") {
    out.push("grammar is required — declare citationMarker, testDeclaration and lineComment for this stack's language; the core has no fallback grammar (`create-cmp harness init` seeds one per language)");
  } else {
    for (const f of GRAMMAR_REQUIRED) {
      const v = grammar[f];
      if (!(v instanceof RegExp) && !(typeof v === "string" && v.trim())) out.push(`grammar.${f} is required — a RegExp or a pattern string for this language`);
      else if (typeof v === "string") {
        // A pattern that does not compile used to fall back to Kotlin's. It is a
        // declaration problem, named here, so the lane refuses before it grades.
        try { new RegExp(v); } catch (err) { out.push(`grammar.${f} is not a valid pattern: ${err.message}`); }
      }
    }
  }
  return out;
}

/**
 * Build the scanner's model from a profile's declarations, applying manifest
 * layout overrides field by field. Pure.
 * @param {{id?: string, layout: object, tiers: object}} profile
 * @param {{specs?: string, citationRoots?: string[]}} [overrides] manifest layout fields
 * @returns {{ok: true, model: SpecModel} | {ok: false, reason: string}}
 */
export function specModelFrom(profile, overrides = {}) {
  const problems = specDeclarationProblems(profile);
  if (problems.length) {
    return { ok: false, reason: `profile ${JSON.stringify(profile?.id ?? "?")} declares an unusable layout/tiers: ${problems.join("; ")}` };
  }
  const { layout, tiers } = profile;
  const specsDir = typeof overrides.specs === "string" && overrides.specs.trim() ? overrides.specs : layout.specs;
  const citationRoots = isStringList(overrides.citationRoots) ? [...overrides.citationRoots] : [...layout.citationRoots];
  const flows = layout.flows ? { dir: layout.flows.dir, exts: [...layout.flows.exts] } : null;
  return {
    ok: true,
    model: Object.freeze({
      profileId: typeof profile.id === "string" ? profile.id : null,
      specsDir,
      citationRoots: Object.freeze(citationRoots),
      citationExts: Object.freeze([...layout.citationExts]),
      // Absent means "the citation roots are the source roots" — a profile that
      // never distinguished them keeps working, and nothing is invented.
      sourceRoots: Object.freeze(isStringList(layout.sourceRoots) ? [...layout.sourceRoots] : [...citationRoots]),
      flows: flows ? Object.freeze(flows) : null,
      buildDir: typeof layout.buildDir === "string" ? layout.buildDir : null,
      // THE GRAMMAR — what a citation and a test declaration LOOK LIKE in this
      // stack's language. Stage 0 moved names, paths and tier names into the
      // profile and left this behind, which was the more dangerous half: a
      // directory name that is wrong produces a refusal, and a grammar that is
      // wrong produces a WRONG VERDICT. The core's fallbacks match Kotlin/JVM
      // and JavaScript only, so a Python or Go project scanned with them finds
      // every marker and binds none — every clause reads as uncited and the
      // message points at the spec file, which is not the problem. Measured on
      // a real Python adoption, 2026-09-05. Field-by-field override: a profile
      // that declares only `testDeclaration` keeps the rest.
      grammar: Object.freeze({
        citationMarker: regexOr(profile.grammar?.citationMarker, null),
        testDeclaration: regexOr(profile.grammar?.testDeclaration, null),
        typeDeclaration: regexOr(profile.grammar?.typeDeclaration, null),
        lineComment: regexOr(profile.grammar?.lineComment, null),
        blockComment: Object.freeze(
          profile.grammar?.blockComment && typeof profile.grammar.blockComment.open === "string" && typeof profile.grammar.blockComment.close === "string"
            ? { open: profile.grammar.blockComment.open, close: profile.grammar.blockComment.close }
            : GRAMMAR_DEFAULTS.blockComment,
        ),
        bindingWindow:
          Number.isInteger(profile.grammar?.bindingWindow) && profile.grammar.bindingWindow > 0
            ? profile.grammar.bindingWindow
            : GRAMMAR_DEFAULTS.bindingWindow,
        // Always false since 2026-09-08: there is no fallback to be running on.
        // Kept as a field because two readers print it.
        isDefault: false,
      }),
      tiers: Object.freeze({
        names: Object.freeze([...tiers.names]),
        hostOnly: Object.freeze([...tiers.hostOnly]),
        satisfying: Object.freeze(Object.fromEntries(Object.entries(tiers.satisfying).map(([k, v]) => [k, Object.freeze([...v])]))),
        journey: tiers.journey ?? null,
        forFile: (rel) => {
          const t = tiers.forFile(rel);
          return typeof t === "string" && t ? t : "other";
        },
      }),
    }),
  };
}

/**
 * The model for a project root: manifest → profile (sync) → declarations,
 * with the manifest's layout fields overriding the profile's.
 * @param {string} root
 * @returns {{ok: true, model: SpecModel} | {ok: false, reason: string}}
 */
export function resolveSpecModel(root) {
  const manifest = resolveHarnessManifest(root);
  if (!manifest.ok) return { ok: false, reason: manifest.reason };
  const loaded = loadProfileSync(root, manifest.manifest.profile);
  if (!loaded.ok) return { ok: false, reason: loaded.reason };
  return specModelFrom(loaded.profile, { specs: manifest.manifest.specs, citationRoots: manifest.manifest.citationRoots });
}

/**
 * resolveSpecModel for callers with no refusal channel: throws the reason.
 * @param {string} root
 * @returns {SpecModel}
 */
export function requireSpecModel(root) {
  const r = resolveSpecModel(root);
  if (!r.ok) throw new Error(r.reason);
  return r.model;
}

/**
 * @typedef {object} SpecModel
 * @property {string|null} profileId
 * @property {string} specsDir
 * @property {readonly string[]} citationRoots
 * @property {readonly string[]} citationExts
 * @property {readonly string[]} sourceRoots
 * @property {{dir: string, exts: readonly string[]}|null} flows
 * @property {string|null} buildDir
 * @property {{names: readonly string[], hostOnly: readonly string[], satisfying: Record<string, readonly string[]>, journey: string|null, forFile: (rel: string) => string}} tiers
 */
