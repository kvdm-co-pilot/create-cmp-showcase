// profiles/cmp/artifacts.mjs — what a human SIGNS in a Compose Multiplatform
// app, in definition order, and how each artifact resolves to files on disk.
// Mobile's governance model, by definition (docs/NORTH-STAR.md §5, §6;
// AGNOSTIC-HARNESS-ARCHITECTURE.md §4.2 #2).
//
// Stage 0 PR 5. Until this file, qa/lib/approvals.mjs hardcoded the six
// genesis artifacts — intent, architecture, exemplar-spec, exemplar-feature,
// design-system, components — with their Kotlin source-set roots, the
// composeApp namespace lookup, the exemplar's eleven-file shape and the
// presentation/<name>/*Screen.kt design surface. A Kotlin backend that adopted
// the harness replaced the whole file (1,636 lines) to change that list. The
// MECHANIC — artifact = path set + hash, signature on the hash, status
// derivation, reopen/accept, the ledger, the gate — is the core's and never
// moved. THIS is the model: which artifacts, in what order, how each is
// hashed. The core reads it through the profile the manifest names.
//
// The neutral entries — feature briefs (the Decide layer), feature designs
// (one per brief with a surface) and feature specs — are built by the core's
// own helpers, so every profile that has briefs gets the same walk; this
// profile only says what a design SURFACE is here (screens on disk) and which
// spec files are not feature specs (the base spec, the exemplar's).

import fs from "node:fs";
import path from "node:path";

import { ARCH_DOC_REL_PATH } from "../../arch-doc.mjs";
import {
  architectureArtifact,
  featureBriefArtifacts,
  featureDesignArtifacts,
  featureSpecArtifacts,
  loadApprovals,
} from "../../approvals.mjs";

// Kotlin source-set roots, relative to project root — mirrors qa/scaffold-feature.mjs's
// SRC() helper (composeApp/src/<sourceSet>/kotlin/<packageDir>).
const KOTLIN_SOURCE_SETS = {
  commonMain: "composeApp/src/commonMain/kotlin",
  commonTest: "composeApp/src/commonTest/kotlin",
  desktopTest: "composeApp/src/desktopTest/kotlin",
};

// The canonical 11-file EXEMPLAR SHAPE (10 kotlin files + 1 spec), parametrized by
// the exemplar's own names — F (PascalCase feature, e.g. "Home"), f (lowercase
// package segment, e.g. "home"), E (PascalCase entity, e.g. "Item"). This is the
// SAME shape qa/scaffold-feature.mjs's ALL_FILES clones FROM (GENESIS-FLOW-DESIGN.md
// §1's "configurable exemplar") — the stamper imports this exact function so the
// clone-source list and the governed-artifact list can never drift from each other
// (single source of truth, not a parallel copy to keep in sync by hand).
// @param {string} F PascalCase feature name (e.g. "Home", "Favorites")
// @param {string} f lowercase package-segment name (e.g. "home", "favorites")
// @param {string} E PascalCase entity name (e.g. "Item", "Favorite")
// @returns {Array<{sourceSet: string, rel: string}>}
export function exemplarKotlinFileSet(F, f, E) {
  return [
    { sourceSet: "commonMain", rel: `domain/model/${E}.kt` },
    { sourceSet: "commonMain", rel: `domain/repository/${E}Repository.kt` },
    { sourceSet: "commonMain", rel: `domain/usecase/Get${E}sUseCase.kt` },
    { sourceSet: "commonMain", rel: `data/remote/${E}RepositoryImpl.kt` },
    { sourceSet: "commonTest", rel: `testing/fakes/Fake${E}Repository.kt` },
    { sourceSet: "commonMain", rel: `presentation/${f}/${F}Screen.kt` },
    { sourceSet: "commonMain", rel: `presentation/${f}/${F}ViewModel.kt` },
    { sourceSet: "commonTest", rel: `presentation/${f}/${F}ViewModelTest.kt` },
    { sourceSet: "desktopTest", rel: `presentation/${f}/${F}ScreenTest.kt` },
    { sourceSet: "desktopTest", rel: `presentation/${f}/${F}GoldenTreeTest.kt` },
  ];
}

// Naive de-pluralization, shared verbatim with qa/scaffold-feature.mjs's own
// entity-name default (a feature stamped without `--entity` gets this exact
// guess). Exported so both the stamper (deriving a NEW feature's entity) and this
// registry (guessing a CONFIGURED exemplar's entity from its feature name alone —
// see resolveExemplarNames) apply the identical heuristic. Unreliable for
// irregular nouns by design (the skill surfaces the guess for human override at
// stamp time); a wrong guess here simply fails to resolve files, which is refused
// (never fabricated), not silently wrong.
export function defaultEntityName(feature) {
  if (feature.endsWith("ies") && feature.length > 3) return `${feature.slice(0, -3)}y`;
  if (feature.endsWith("s") && !feature.endsWith("ss")) return feature.slice(0, -1);
  return feature;
}

function toPascalCase(f) {
  return f.charAt(0).toUpperCase() + f.slice(1);
}

function toUpperSnake(F) {
  return F.replace(/([a-z0-9])([A-Z])/g, "$1_$2").toUpperCase();
}

/**
 * The configured exemplar feature's lowercase name (the package-segment form,
 * e.g. `"home"`, `"favorites"`) — `qa/approvals.json`'s top-level
 * `exemplarFeature` key, defaulting to `"home"` when absent (GENESIS-FLOW-DESIGN.md
 * §1). This is the ONE function both `resolveExemplarNames` (registry) and
 * qa/scaffold-feature.mjs (clone-source resolution) call — never read the raw key
 * directly, so the default lives in exactly one place.
 * @param {string} root
 * @returns {string}
 */
export function getExemplarFeature(root) {
  return loadApprovals(root).exemplarFeature ?? "home";
}

/**
 * Resolve the CONFIGURED exemplar's names — the ones the exemplar-feature/
 * exemplar-spec governed artifacts (and qa/scaffold-feature.mjs's clone source)
 * are built from.
 *
 * `home` (the default, and the only exemplar that predates configurability) is a
 * hardcoded exception: its entity is `Item`, not derivable from `Home` by
 * `defaultEntityName` (which would naively guess `Home`). Every OTHER exemplar is
 * itself a feature that was stamped by qa/scaffold-feature.mjs, so its entity
 * followed defaultEntityName(F) UNLESS it was stamped with an explicit `--entity`
 * override — a choice this config key cannot see. In that mismatch case the guess
 * is wrong and the file set simply fails to resolve (0 or partial files), which
 * `resolveArtifactStatus`/`approveArtifact` already refuse rather than fabricate —
 * the correct failure mode, not a special case to add here.
 * @param {string} root
 * @returns {{f: string, F: string, F_UPPER: string, E: string}}
 */
export function resolveExemplarNames(root) {
  const f = getExemplarFeature(root);
  const F = toPascalCase(f);
  const F_UPPER = toUpperSnake(F);
  const E = f === "home" ? "Item" : defaultEntityName(F);
  return { f, F, F_UPPER, E };
}

// Backward-compatible constants for the DEFAULT (`home`) exemplar — kept exported
// because they describe the shipped template's own exemplar shape independent of
// any project's configuration, and because they're the fixture the "stamping from
// home must be byte-identical" pin (test/genesis-flow.test.mjs) anchors to.
export const EXEMPLAR_FEATURE_KOTLIN_FILES = exemplarKotlinFileSet("Home", "home", "Item");
export const EXEMPLAR_SPEC_REL = "specs/home.spec.md";
export const ARCHITECTURE_SPEC_REL = "specs/app-base.spec.md";
export const INTENT_REL = "specs/intent.md";

// ── Package resolution ───────────────────────────────────────────────────────
// Mirrors qa/scaffold-feature.mjs's resolvePackage() primary path (the
// composeApp/build.gradle.kts namespace). Unlike the stamper, this NEVER dies —
// an unresolved package means the kotlin-rooted artifacts resolve to zero files.
// Zero resolution never CRASHES anything (the lane and the stamper stay up),
// but it is NOT benign for decisions: an approval over zero files would be the
// empty-input sha256 attesting nothing — a silent vacuous PASS, the exact
// failure mode this harness exists to kill (evidence must attest execution).
// So: approveArtifact REFUSES zero-file artifacts, and an already-approved
// artifact whose files stop resolving goes to changed-since-approval (FAIL),
// never PASS.
//
// IMPORTANT: detect "unresolved" by TOKEN SHAPE (`/^__[A-Z_]+__$/`), never by
// comparing against the literal string "__PACKAGE__". This file ships through
// the SAME scaffold pipeline that resolves that token — a literal comparison
// string is itself blindly text-substituted at stamp time (`replaceContents`
// does a global `"__PACKAGE__" -> config.package` replace over every template
// file's content, this one included), which would silently rewrite the
// sentinel into the real package and make the check always fail. A shape
// regex never spells the token out, so the pipeline has nothing to match.
const UNRESOLVED_TOKEN_RE = /^__[A-Z_]+__$/;

function resolvePackageDir(root) {
  const gradleFile = path.join(root, "composeApp", "build.gradle.kts");
  if (!fs.existsSync(gradleFile)) return null;
  let contents;
  try {
    contents = fs.readFileSync(gradleFile, "utf8");
  } catch {
    return null;
  }
  const m = contents.match(/namespace\s*=\s*"([^"]+)"/);
  if (!m || UNRESOLVED_TOKEN_RE.test(m[1])) return null;
  return m[1].split(".").join("/");
}

function kotlinFile(root, sourceSet, rel) {
  const packageDir = resolvePackageDir(root);
  if (!packageDir) return null;
  return path.posix.join(KOTLIN_SOURCE_SETS[sourceSet], packageDir, rel);
}

/**
 * Is the project's package resolvable at all? False in the raw template (the
 * namespace is still a placeholder token) and in any pre-stamp tree — the tell
 * that this is not a generated project. The approve CLI refuses to WRITE
 * approvals in such a tree (recording decisions against a template pollutes
 * the template itself); read-only status remains available.
 * @param {string} root
 * @returns {boolean}
 */
export function isPackageResolvable(root) {
  return resolvePackageDir(root) !== null;
}

/**
 * The profile's answer to "may approvals be RECORDED in this tree?" — the
 * core's `isProjectGovernable` asks every profile this and refuses to write
 * a ledger where the answer is no.
 * @param {string} root
 * @returns {{ok: true} | {ok: false, reason: string}}
 */
export function governable(root) {
  if (isPackageResolvable(root)) return { ok: true };
  return {
    ok: false,
    reason:
      "this tree's package is not resolvable (composeApp/build.gradle.kts namespace is missing or still a placeholder) — " +
      "this looks like the raw template or a pre-stamp tree. Approvals are recorded in a generated project; refusing to write qa/approvals.json here.",
  };
}

// ── Components glob ─────────────────────────────────────────────────────────

/**
 * Sorted list of `presentation/components/*.kt` files under the resolved
 * package, non-recursive (GENESIS-FLOW-DESIGN.md §1's `components` artifact — the
 * component vocabulary conversation 3 approves). Package-unresolvable or a
 * missing/empty directory both yield `[]` — resolveArtifactStatus/approveArtifact
 * already treat a 0-file artifact as unresolvable ("a components glob matching
 * zero files is unresolvable, not approvable-empty" — §1), so no special-casing
 * is needed here beyond returning the honest (possibly empty) list.
 * @param {string} root
 * @returns {string[]} root-relative paths, sorted
 */
function listComponentFiles(root) {
  const dirRel = kotlinFile(root, "commonMain", "presentation/components");
  if (!dirRel) return [];
  let entries;
  try {
    entries = fs.readdirSync(path.join(root, dirRel), { withFileTypes: true });
  } catch {
    return [];
  }
  return entries
    .filter((e) => e.isFile() && e.name.endsWith(".kt"))
    .map((e) => path.posix.join(dirRel, e.name))
    .sort((a, b) => a.localeCompare(b));
}

// ── Feature screens glob ────────────────────────────────────────────────────

/**
 * The screen files of one feature — `presentation/<name>/**\/*Screen.kt`,
 * recursive, sorted. DELIBERATELY only `*Screen.kt`: the design signature
 * covers the FORM (what renders), so binding the whole presentation dir would
 * make every ViewModel edit during a legitimate build read as design drift.
 * This is what a DESIGN SURFACE is on mobile — the core's feature-design
 * mechanic asks the profile for it and knows nothing else.
 * @param {string} root
 * @param {string} name the feature name (presentation/<name>/)
 * @returns {string[]} repo-relative posix paths
 */
export function listFeatureScreenFiles(root, name) {
  const dirRel = kotlinFile(root, "commonMain", `presentation/${name}`);
  if (!dirRel) return [];
  const out = [];
  const walk = (rel) => {
    let entries;
    try {
      entries = fs.readdirSync(path.join(root, rel), { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      const childRel = path.posix.join(rel, e.name);
      if (e.isDirectory()) walk(childRel);
      else if (e.isFile() && e.name.endsWith("Screen.kt")) out.push(childRel);
    }
  };
  walk(dirRel);
  return out.sort((a, b) => a.localeCompare(b));
}

// ── The registry ────────────────────────────────────────────────────────────

/**
 * The governed-artifact registry, resolved against the project at `root` right
 * now. GENESIS-FLOW-DESIGN.md §1 definition order — two ordering principles,
 * one per artifact kind (the dogfooding-run correction):
 *   BEHAVIORAL artifacts are SPEC-FIRST — the exemplar's clauses are proposed
 *   and human-confirmed BEFORE the slice is built (exemplar-spec precedes
 *   exemplar-feature, matching add-feature's discipline).
 *   VISUAL artifacts are UI-FIRST — the design system and component vocabulary
 *   are distilled FROM the real screens, so they lock AFTER the exemplar
 *   exists (a provisional palette carries the build until then).
 * Order: intent(0), then feature-brief:<name> per docs/features/*.md — the
 * DECIDE layer sits directly after intent (a brief speaks intent's
 * vocabulary; only the SPEC needs architecture's) — then architecture,
 * exemplar-spec, exemplar-feature, design-system, components, one
 * feature-design:<name> per brief with a screen surface, and one
 * feature-spec:<name> per non-base, non-CONFIGURED-exemplar spec present.
 *
 * `complete: false` marks an artifact whose kotlin-rooted files could NOT be
 * resolved (unresolvable package — raw template / pre-stamp tree). Such an
 * artifact's `files` list is empty or partial (spec files only), so hashing it
 * would attest nothing (or only a fraction) of what the artifact governs —
 * approveArtifact refuses it, and the status surfaces treat it as unresolvable.
 * @param {string} root absolute path to the project root
 * @returns {Array<{id: string, label: string, files: string[], complete: boolean, hash?: (root: string) => {hash: string, fileCount: number, missing: string[]}}>}
 */
export function artifacts(root) {
  const out = [];
  const packageResolved = resolvePackageDir(root) !== null;
  // Why a kotlin-rooted artifact is incomplete, quoted by the core's refusal.
  const incompleteReason = packageResolved
    ? undefined
    : "the kotlin-rooted files are unresolvable because the project package is not resolvable from composeApp/build.gradle.kts (likely the raw template or a pre-stamp tree — run this in a generated project)";

  out.push({
    id: "intent",
    label: `Intent brief (${INTENT_REL})`,
    files: [INTENT_REL],
    complete: true,
  });

  // The decide layer, directly after intent — the core's helper, so every
  // profile with briefs walks them the same way.
  out.push(...featureBriefArtifacts(root));

  // Hashed via the core's architecture hasher (spec bytes + the doc with its
  // generated sections stripped), NOT raw files — `files` is still the
  // artifact's expected-files surface (missing-file refusals, "what governs
  // this" bookkeeping), just not what gets hashed raw.
  out.push(architectureArtifact(root, { specRel: ARCHITECTURE_SPEC_REL, docRel: ARCH_DOC_REL_PATH }));

  const { f: exemplarF, F: exemplarF_Pascal, E: exemplarE } = resolveExemplarNames(root);
  const exemplarSpecRel = `specs/${exemplarF}.spec.md`;
  const exemplarKotlinFiles = exemplarKotlinFileSet(exemplarF_Pascal, exemplarF, exemplarE);

  // Spec-first: the exemplar's behavior clauses are confirmed BEFORE the slice
  // is built — the definition order is the discipline, not just a display order.
  out.push({
    id: "exemplar-spec",
    label: `Exemplar spec (${exemplarSpecRel})`,
    files: [exemplarSpecRel],
    complete: true,
  });

  out.push({
    id: "exemplar-feature",
    label: `Exemplar feature (${exemplarF} — the file set the stamper clones)`,
    files: [...exemplarKotlinFiles.map((f) => kotlinFile(root, f.sourceSet, f.rel)).filter(Boolean), exemplarSpecRel],
    complete: packageResolved,
    incompleteReason,
  });

  // UI-first: the design system LOCKS on the real exemplar (candidates render on
  // real screens, never stubs), and the component vocabulary is DISTILLED from
  // those screens — both follow the exemplar in the definition order.
  out.push({
    id: "design-system",
    label: "Design system (presentation/theme/Theme.kt, Tokens.kt)",
    files: [kotlinFile(root, "commonMain", "presentation/theme/Theme.kt"), kotlinFile(root, "commonMain", "presentation/theme/Tokens.kt")].filter(Boolean),
    complete: packageResolved,
    incompleteReason,
  });

  out.push({
    id: "components",
    label: "Components (presentation/components/*.kt)",
    files: listComponentFiles(root),
    complete: packageResolved,
    incompleteReason,
  });

  // Feature designs — the core's mechanic (one per brief with a surface,
  // signed on rendered output BEFORE the behaviour contract); this profile
  // says what the surface is: `"screens": true` declared, or *Screen.kt on disk.
  out.push(
    ...featureDesignArtifacts(root, {
      surfaceFiles: (r, name) => listFeatureScreenFiles(r, name),
      declares: (block) => block.screens === true,
      label: (name) => `Feature design (${name} — presentation/${name}/*Screen.kt, signed on rendered output)`,
      complete: (files) => packageResolved && files.length > 0,
      incompleteReason: (files) => (packageResolved ? (files.length === 0 ? "no *Screen.kt rendered yet for this feature" : undefined) : incompleteReason),
    }),
  );

  out.push(...featureSpecArtifacts(root, { specsDir: "specs", exclude: ["app-base.spec.md", `${exemplarF}.spec.md`] }));

  return out;
}
