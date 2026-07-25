// The hash-bound human-approval data model (VERIFICATION-LAYER-DESIGN.md §2,
// extended by GENESIS-FLOW-DESIGN.md §1/§2/§3 — the genesis flow's registry,
// express lane, and reopen mechanics).
//
// Reuses ADR-0005's philosophy exactly (docs/adr/0005-evidence-binding-by-inputs-hash.md
// in the create-cmp repo): an approval is valid iff a stored content hash matches a
// recompute of the SAME files, right now. No new hashing idea — just applied to a
// smaller, human-curated surface (one governed artifact) instead of the whole
// verified tree.
//
// Three concerns, kept separable:
//   1. The REGISTRY (`listGovernedArtifacts`) — artifact id -> resolved file list, in
//      GENESIS-FLOW-DESIGN.md §1 order: intent(0), architecture(1), exemplar-spec(2),
//      exemplar-feature(3), design-system(4), components(5), then one
//      `feature-spec:<name>` (6+) per non-base, non-exemplar spec file present in
//      specs/ right now. (Spec-first: the exemplar's clauses are confirmed before the
//      slice is built. UI-first: design system + components are distilled from the
//      real screens, so they lock after the exemplar.) The exemplar (2/3) is
//      CONFIGURABLE — see
//      `getExemplarFeature`/`resolveExemplarNames` below — defaulting to `home` so
//      every ledger written before this config key existed keeps meaning what it
//      meant. The registry is recomputed on every call — it reflects the tree as it
//      stands, never a stale snapshot.
//   2. STATE (`loadApprovals`/`saveApprovals`) — qa/approvals.json, the human's
//      decisions: { artifact, status, hash, approvedAt, mode?, reopenedAt? } plus the
//      top-level `exemplarFeature` config key. Absent or corrupt is TOLERATED
//      (treated as empty / all-unreviewed / default exemplar) — this ledger must
//      never crash the verify lane or the stamper.
//
//      Ledger migration note (architecture-document-standard.md §4.4): there is no
//      schema-version bump or migration step anywhere in this file today (schema
//      stays `cmp-approvals/1`, additive-only — see GENESIS-FLOW-DESIGN.md §2's
//      express-lane note) — a widened hash BASIS (e.g. the `architecture` artifact
//      growing from spec-only to spec+stripped-doc) is handled the same honest way
//      every other content change is: `resolveArtifactStatus` recomputes on every
//      read and compares against the STORED hash. An approval recorded under the
//      old (narrower) basis simply stops matching the new recompute the first time
//      it's read after this change ships, and correctly reports
//      "changed-since-approval" — never a silent, un-re-earned "approved". This is
//      not a special case: it is the SAME mechanism that already invalidates an
//      approval when the governed files themselves change; widening what counts as
//      "the governed files" for one artifact is just another such change. No
//      separate migration code path exists or is needed.
//   3. The GATE (`evaluateApprovalsGate`) — combines registry + state into one
//      per-artifact status (unreviewed / approved / changed-since-approval /
//      reopened) and one aggregate verdict (PASS/FAIL/SKIP) for the verify-lane step
//      to report. `reopened` behaves like `unreviewed` for the gate (SKIP-warn,
//      non-blocking) — sanctioned redesign is never drift.
//
// Consumers: qa/approve.mjs (the CLI — thin shell over this file), qa/verify.mjs
// (the `approvals` gate), qa/scaffold-feature.mjs (seeds a new feature's spec as
// unreviewed, and resolves its clone-FROM exemplar through `resolveExemplarNames`).
// The console (inspector/mcp/src/lib/approvals-bridge.mjs) calls this same library.

import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";

import { ARCH_DOC_REL_PATH, stripGeneratedSections } from "./arch-doc.mjs";
import { deriveAllFeatures, deriveFeatureStatus, listFeatureBriefs } from "./feature-brief.mjs";

export const APPROVALS_REL_PATH = "qa/approvals.json";
export const APPROVALS_SCHEMA = "cmp-approvals/1";

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
// comparing against the literal string "com.kvdm.fuelled". This file ships through
// the SAME scaffold pipeline that resolves that token — a literal comparison
// string is itself blindly text-substituted at stamp time (`replaceContents`
// does a global `"com.kvdm.fuelled" -> config.package` replace over every template
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

// ── Registry ─────────────────────────────────────────────────────────────────

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
 * Order: intent(0), architecture(1), exemplar-spec(2), exemplar-feature(3),
 * design-system(4), components(5), then one feature-spec:<name> (6+) per
 * non-base, non-CONFIGURED-exemplar spec present.
 *
 * `complete: false` marks an artifact whose kotlin-rooted files could NOT be
 * resolved (unresolvable package — raw template / pre-stamp tree). Such an
 * artifact's `files` list is empty or partial (spec files only), so hashing it
 * would attest nothing (or only a fraction) of what the artifact governs —
 * approveArtifact refuses it, and the status surfaces treat it as unresolvable.
 * @param {string} root absolute path to the project root
 * @returns {Array<{id: string, label: string, files: string[], complete: boolean}>}
 */
export function listGovernedArtifacts(root) {
  const artifacts = [];
  const packageResolved = resolvePackageDir(root) !== null;

  artifacts.push({
    id: "intent",
    label: `Intent brief (${INTENT_REL})`,
    files: [INTENT_REL],
    complete: true,
  });

  artifacts.push({
    id: "architecture",
    label: `Architecture + structure (${ARCHITECTURE_SPEC_REL} + ${ARCH_DOC_REL_PATH}, generated sections stripped)`,
    // Hashed via hashArchitectureArtifact (spec bytes + stripped-doc content),
    // NOT the generic hashArtifactFiles — this list is still the artifact's
    // expected-files surface (missing-file refusal messages, "what governs
    // this" bookkeeping), just not what gets hashed raw. See computeArtifactHash.
    files: [ARCHITECTURE_SPEC_REL, ARCH_DOC_REL_PATH],
    complete: true,
  });

  const { f: exemplarF, F: exemplarF_Pascal, E: exemplarE } = resolveExemplarNames(root);
  const exemplarSpecRel = `specs/${exemplarF}.spec.md`;
  const exemplarKotlinFiles = exemplarKotlinFileSet(exemplarF_Pascal, exemplarF, exemplarE);

  // Spec-first: the exemplar's behavior clauses are confirmed BEFORE the slice
  // is built — the definition order is the discipline, not just a display order.
  artifacts.push({
    id: "exemplar-spec",
    label: `Exemplar spec (${exemplarSpecRel})`,
    files: [exemplarSpecRel],
    complete: true,
  });

  artifacts.push({
    id: "exemplar-feature",
    label: `Exemplar feature (${exemplarF} — the file set the stamper clones)`,
    files: [
      ...exemplarKotlinFiles.map((f) => kotlinFile(root, f.sourceSet, f.rel)).filter(Boolean),
      exemplarSpecRel,
    ],
    complete: packageResolved,
  });

  // UI-first: the design system LOCKS on the real exemplar (candidates render on
  // real screens, never stubs), and the component vocabulary is DISTILLED from
  // those screens — both follow the exemplar in the definition order.
  artifacts.push({
    id: "design-system",
    label: "Design system (presentation/theme/Theme.kt, Tokens.kt)",
    files: [
      kotlinFile(root, "commonMain", "presentation/theme/Theme.kt"),
      kotlinFile(root, "commonMain", "presentation/theme/Tokens.kt"),
    ].filter(Boolean),
    complete: packageResolved,
  });

  artifacts.push({
    id: "components",
    label: "Components (presentation/components/*.kt)",
    files: listComponentFiles(root),
    complete: packageResolved,
  });

  const specsDir = path.join(root, "specs");
  if (fs.existsSync(specsDir)) {
    const featureSpecs = fs
      .readdirSync(specsDir)
      .filter((f) => f.endsWith(".spec.md") && f !== "app-base.spec.md" && f !== `${exemplarF}.spec.md`)
      .sort((a, b) => a.localeCompare(b));
    for (const file of featureSpecs) {
      const name = file.slice(0, -".spec.md".length);
      artifacts.push({
        id: `feature-spec:${name}`,
        label: `Feature spec (specs/${file})`,
        files: [`specs/${file}`],
        complete: true,
      });
    }
  }

  // Feature briefs (feature-brief.mjs): one `feature-brief:<name>` per doc
  // under docs/features/ — LOCATION is the governance opt-in (CHANGE-FLOW-
  // DESIGN.md §2); docs/proposals/ stays ungoverned harness-standards prose.
  // Approving one hashes the doc's bytes, so a signed brief cannot be quietly
  // rewritten; acceptance is a LEDGER field on the same row (acceptFeature
  // below), so the human's bookend never touches the signed bytes.
  for (const brief of listFeatureBriefs(root)) {
    artifacts.push({
      id: `feature-brief:${brief.name}`,
      label: `Feature brief (${brief.rel})`,
      files: [brief.rel],
      complete: true,
    });
  }

  return artifacts;
}

// ── Hashing (mirrors qa/lib/inputs-hash.mjs's computeInputsHash style) ───────

/**
 * sha256 over the sorted `(path, sha256(content))` list of `relFiles` that
 * currently exist under `root`. Deterministic; missing files are reported, not
 * fatal — the hash is simply over what's present.
 * @param {string} root
 * @param {string[]} relFiles
 * @returns {{ hash: string, fileCount: number, missing: string[] }}
 */
export function hashArtifactFiles(root, relFiles) {
  // Code-unit sort (default String sort), NOT localeCompare: the hash depends
  // on iteration order and ICU collation varies with the machine's locale —
  // an approval recorded on one machine must verify on every other.
  const files = [...new Set(relFiles)].sort();
  const present = [];
  const missing = [];
  for (const relPath of files) {
    try {
      if (fs.statSync(path.join(root, relPath)).isFile()) {
        present.push(relPath);
        continue;
      }
    } catch {
      /* fall through to missing */
    }
    missing.push(relPath);
  }

  const overall = createHash("sha256");
  for (const relPath of present) {
    const bytes = fs.readFileSync(path.join(root, relPath));
    const fileSha = createHash("sha256").update(bytes).digest("hex");
    overall.update(`${relPath}\0${fileSha}\n`);
  }
  return { hash: overall.digest("hex"), fileCount: present.length, missing };
}

/**
 * The `architecture` artifact's hash basis (docs/proposals/architecture-document-
 * standard.md §4.4): `${ARCHITECTURE_SPEC_REL}`'s raw bytes + `${ARCH_DOC_REL_PATH}`
 * with every `cmp:generated` marker's BODY stripped — `arch-doc.mjs`'s
 * `stripGeneratedSections` is the ONE definition of "generated" for that doc,
 * reused here rather than forked, so a new/changed marker id is understood
 * identically by the regenerator and this hash.
 *
 * The doc's content is also normalized `\r\n` -> `\n` before hashing (spec
 * files are hashed as raw bytes like every other artifact — a checkout-induced
 * EOL difference in a Markdown prose doc is exactly the kind of accident that
 * must never read as "authored drift", but the .spec.md files this repo ships
 * are LF already and their exact bytes are what the human actually reviewed).
 *
 * Same row-hash shape as `hashArtifactFiles` (`path\0sha256(bytes)\n`, rows
 * sorted by path) so the two schemes read the same way in a hex dump — this is
 * a SEPARATE function (not a generic `hashArtifactFiles` call) only because the
 * doc's bytes must be transformed (stripped + normalized) before hashing, never
 * hashed raw.
 *
 * Regenerating a marker section (`node qa/arch-doc.mjs`) changes only the
 * stripped-away body, so this hash does not move. Editing authored prose
 * anywhere else in the doc — including adding, removing, or reordering a
 * `cmp:generated` marker itself (structural, not generated content) — changes
 * it, same as editing the spec.
 * @param {string} root
 * @returns {{ hash: string, fileCount: number, missing: string[] }}
 */
export function hashArchitectureArtifact(root) {
  const rows = [];
  const missing = [];

  try {
    const specBytes = fs.readFileSync(path.join(root, ARCHITECTURE_SPEC_REL));
    rows.push([ARCHITECTURE_SPEC_REL, createHash("sha256").update(specBytes).digest("hex")]);
  } catch {
    missing.push(ARCHITECTURE_SPEC_REL);
  }

  try {
    const docRaw = fs.readFileSync(path.join(root, ARCH_DOC_REL_PATH), "utf8");
    // Normalize line endings BEFORE stripping: the marker grammar
    // (`arch-doc.mjs`'s MARKER_BLOCK_RE) matches a literal `\n` right after
    // `-->`, so CRLF content would fail to match at all and nothing would be
    // stripped — normalize first so the strip is EOL-independent, same as the
    // hash itself.
    const docNormalized = docRaw.replace(/\r\n/g, "\n");
    const docStripped = stripGeneratedSections(docNormalized);
    rows.push([ARCH_DOC_REL_PATH, createHash("sha256").update(docStripped, "utf8").digest("hex")]);
  } catch {
    missing.push(ARCH_DOC_REL_PATH);
  }

  // Code-unit sort for the same reason as hashArtifactFiles: hash order must
  // not depend on the machine's locale.
  rows.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
  const overall = createHash("sha256");
  for (const [relPath, fileSha] of rows) {
    overall.update(`${relPath}\0${fileSha}\n`);
  }
  return { hash: overall.digest("hex"), fileCount: rows.length, missing };
}

/**
 * Recompute one artifact's hash — `hashArchitectureArtifact` for `architecture`
 * (spec + stripped doc, its own basis), `hashArtifactFiles(root, artifact.files)`
 * for every other artifact (raw file bytes). The ONE dispatch point
 * `resolveArtifactStatus`/`approveArtifact` both call, so the two never
 * disagree about what "the architecture artifact's hash" means.
 * @param {string} root
 * @param {{id: string, files: string[]}} artifact
 * @returns {{ hash: string, fileCount: number, missing: string[] }}
 */
function computeArtifactHash(root, artifact) {
  return artifact.id === "architecture" ? hashArchitectureArtifact(root) : hashArtifactFiles(root, artifact.files);
}

// ── State (qa/approvals.json) ─────────────────────────────────────────────────

/**
 * Load qa/approvals.json. Absent or corrupt (unparsable JSON, wrong shape) is
 * TOLERATED — returns the empty state, which resolves every artifact as
 * "unreviewed" and every exemplar lookup to the default (`home`). Never throws.
 *
 * `exemplarFeature` is `undefined` when the key is absent or not a non-empty
 * string — callers resolve the default (`getExemplarFeature`), never this
 * function directly, so every ledger written before this key existed keeps
 * meaning what it meant (GENESIS-FLOW-DESIGN.md §1).
 * @param {string} root
 * @returns {{ schema: string, artifacts: Array<{artifact: string, status: string, hash: (string|null), approvedAt: (string|null), mode?: string, reopenedAt?: string}>, exemplarFeature: (string|undefined) }}
 */
export function loadApprovals(root) {
  const empty = { schema: APPROVALS_SCHEMA, artifacts: [], exemplarFeature: undefined };
  const p = path.join(root, APPROVALS_REL_PATH);
  let raw;
  try {
    raw = fs.readFileSync(p, "utf8");
  } catch {
    return empty;
  }
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.artifacts)) return empty;
    const exemplarFeature =
      typeof parsed.exemplarFeature === "string" && parsed.exemplarFeature.trim() !== ""
        ? parsed.exemplarFeature.trim()
        : undefined;
    return { schema: parsed.schema ?? APPROVALS_SCHEMA, artifacts: parsed.artifacts, exemplarFeature };
  } catch {
    return empty;
  }
}

/**
 * Write qa/approvals.json (deterministic key order, trailing newline).
 * `exemplarFeature` is included only when the caller explicitly passes one
 * (undefined is omitted, never written as a literal `null`/`"undefined"`) — every
 * internal transition (approveArtifact, seedUnreviewed, approveAllDefaults,
 * reopenArtifact) reloads and threads the CURRENT value through so a write never
 * silently drops a previously-configured exemplar.
 * @param {string} root
 * @param {{ artifacts: Array<object>, exemplarFeature?: string }} state
 */
export function saveApprovals(root, state) {
  const p = path.join(root, APPROVALS_REL_PATH);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  const out = { schema: APPROVALS_SCHEMA, artifacts: state.artifacts };
  if (state.exemplarFeature !== undefined) out.exemplarFeature = state.exemplarFeature;
  fs.writeFileSync(p, `${JSON.stringify(out, null, 2)}\n`);
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
 * Seed one artifact as unreviewed if it isn't already recorded. Idempotent —
 * a second call for the same id is a no-op. Used by qa/scaffold-feature.mjs to
 * seed a new feature's spec (create-if-missing, tolerant when absent — this
 * never throws, so a stamp is never blocked by the approvals ledger).
 * @param {string} root
 * @param {string} artifactId
 * @returns {{ added: boolean }}
 */
export function seedUnreviewed(root, artifactId) {
  const state = loadApprovals(root);
  if (state.artifacts.some((a) => a.artifact === artifactId)) return { added: false };
  state.artifacts.push({ artifact: artifactId, status: "unreviewed", hash: null, approvedAt: null });
  saveApprovals(root, state);
  return { added: true };
}

// ── Status resolution ─────────────────────────────────────────────────────────

function shortHash(hash) {
  return hash ? hash.slice(0, 8) : "none";
}

/**
 * Resolve one artifact's live status: recompute its hash now and compare
 * against the stored record (if any).
 * - no stored record, or stored status !== "approved"/"reopened" -> "unreviewed"
 * - stored status === "reopened" -> "reopened", UNCONDITIONALLY — a reopened
 *   artifact never re-derives "changed-since-approval" from further edits (there
 *   is no live approval to compare against once reopened; it's fluid again by
 *   definition until the next real approveArtifact call). This is the
 *   sanctioned-redesign-vs-drift asymmetry the reopen mechanic exists for
 *   (GENESIS-FLOW-DESIGN.md §2): only an `approved` artifact can go stale.
 * - approved + hash still matches (over >0 files) -> "approved"
 * - approved + hash no longer matches -> "changed-since-approval"
 * - approved + artifact NOW unresolvable (0 files, or an incomplete kotlin
 *   file set) -> "changed-since-approval", UNCONDITIONALLY — even if the
 *   stored hash equals the recompute (a hand-written or legacy vacuous
 *   approval over the degraded set). An approval that covers none (or only a
 *   fraction) of what the artifact governs attests nothing and must never
 *   read as PASS.
 * `resolvable` is false when the artifact resolves to 0 files right now OR its
 * file set is incomplete (kotlin roots unresolvable — see listGovernedArtifacts).
 * `mode` (e.g. `"defaults-accepted"`) and `reopenedAt` are surfaced only when the
 * stored record actually carries them — never as an explicit `undefined` key, so
 * structural equality checks against a plain unreviewed/approved status shape
 * still hold.
 * @returns {{id: string, label: string, status: string, hash: string, storedHash: (string|null), approvedAt: (string|null), fileCount: number, missing: string[], resolvable: boolean, mode?: string, reopenedAt?: string}}
 */
export function resolveArtifactStatus(root, artifact, storedRecord) {
  const recomputed = computeArtifactHash(root, artifact);
  const resolvable = recomputed.fileCount > 0 && artifact.complete !== false;

  if (storedRecord && storedRecord.status === "reopened") {
    return {
      id: artifact.id,
      label: artifact.label,
      status: "reopened",
      hash: recomputed.hash,
      storedHash: storedRecord.hash ?? null,
      approvedAt: storedRecord.approvedAt ?? null,
      fileCount: recomputed.fileCount,
      missing: recomputed.missing,
      resolvable,
      reopenedAt: storedRecord.reopenedAt,
    };
  }

  if (!storedRecord || storedRecord.status !== "approved") {
    return {
      id: artifact.id,
      label: artifact.label,
      status: "unreviewed",
      hash: recomputed.hash,
      storedHash: null,
      approvedAt: null,
      fileCount: recomputed.fileCount,
      missing: recomputed.missing,
      resolvable,
    };
  }
  const changed = !resolvable || storedRecord.hash !== recomputed.hash;
  return {
    id: artifact.id,
    label: artifact.label,
    status: changed ? "changed-since-approval" : "approved",
    hash: recomputed.hash,
    storedHash: storedRecord.hash,
    approvedAt: storedRecord.approvedAt,
    fileCount: recomputed.fileCount,
    missing: recomputed.missing,
    resolvable,
    ...(storedRecord.mode ? { mode: storedRecord.mode } : {}),
    // Feature-brief acceptance (acceptFeature) — surfaced only when the row
    // actually carries the fields, same stance as `mode`: never an explicit
    // `undefined` key, so plain-status structural equality still holds.
    ...(storedRecord.via ? { via: storedRecord.via } : {}),
    ...(storedRecord.accepted ? { accepted: true, acceptedAt: storedRecord.acceptedAt ?? null } : {}),
  };
}

/**
 * Every governed artifact's live status, right now.
 * @param {string} root
 * @returns {Array<ReturnType<typeof resolveArtifactStatus>>}
 */
export function getApprovalStatuses(root) {
  const registry = listGovernedArtifacts(root);
  const state = loadApprovals(root);
  const byId = new Map(state.artifacts.map((a) => [a.artifact, a]));
  return registry.map((artifact) => resolveArtifactStatus(root, artifact, byId.get(artifact.id)));
}

// ── Transitions ────────────────────────────────────────────────────────────────

/**
 * Record an approval: recompute the artifact's hash now, stamp the time,
 * upsert into qa/approvals.json. A fresh record always REPLACES the stored one
 * wholesale (never merges) — so a real approval on a previously
 * defaults-accepted or reopened artifact automatically clears `mode` and
 * `reopenedAt`, with no separate "clear" step needed.
 *
 * REFUSES an unresolvable artifact — one that resolves to 0 files, or whose
 * kotlin-rooted file set could not be resolved at all (`complete: false`). An
 * approval over 0 files would record the empty-input sha256; an approval over
 * a partial set would attest only a fraction of what the artifact governs.
 * Both are silently vacuous — the exact failure mode this harness exists to
 * kill (evidence must attest execution). Refusal cases: the project package is
 * unresolvable (raw template / pre-stamp tree), the artifact's expected files
 * are all missing on disk, or (a dynamic artifact, e.g. `components`) nothing
 * currently matches its pattern.
 * A fresh approval also clears a feature brief's `accepted` field: re-signing
 * a brief whose bytes changed is a NEW signature over new content, and an
 * acceptance given against the old content does not carry over — the human
 * re-accepts once the new content is provenDone again. Same wholesale-replace
 * semantics that already clear `mode`/`reopenedAt`.
 * @param {string} root
 * @param {string} artifactId
 * @param {{mode?: string, via?: string}} [options] `mode` (e.g.
 *   `"defaults-accepted"`) is stamped onto the record when the express lane
 *   approves a resolvable-but-unshaped artifact (GENESIS-FLOW-DESIGN.md §2).
 *   Omitted for a normal/real approval. `via` records the surface the approval
 *   came through (`"console"` / `"cli"`) — an audit field, never behavior.
 * @returns {{ok: true, artifact: string, hash: string, approvedAt: string, mode?: string} | {ok: false, reason: string}}
 */
export function approveArtifact(root, artifactId, options = {}) {
  const registry = listGovernedArtifacts(root);
  const artifact = registry.find((a) => a.id === artifactId);
  if (!artifact) {
    const known = registry.map((a) => a.id).join(", ") || "(none — no governed artifacts resolved in this project)";
    return { ok: false, reason: `unknown artifact "${artifactId}" — valid ids: ${known}` };
  }
  const resolved = computeArtifactHash(root, artifact);
  if (artifact.complete === false) {
    return {
      ok: false,
      reason:
        `cannot approve "${artifactId}" — its file set cannot be fully resolved: the kotlin-rooted files are unresolvable because ` +
        "the project package is not resolvable from composeApp/build.gradle.kts (likely the raw template or a pre-stamp tree — " +
        `run this in a generated project); only ${resolved.fileCount} file(s) resolved. ` +
        "A partial or empty approval is vacuous (it attests nothing for the unresolved files) and is refused.",
    };
  }
  if (resolved.fileCount === 0) {
    const reason =
      artifact.files.length === 0
        ? `cannot approve "${artifactId}" — it resolves to 0 files; nothing currently matches this artifact's pattern (nothing to approve yet). An approval over zero files is vacuous (the empty-input hash attests nothing) and is refused.`
        : `cannot approve "${artifactId}" — it resolves to 0 files; its expected files are all missing on disk: ` +
          `${artifact.files.join(", ")}. An approval over zero files is vacuous (the empty-input hash attests nothing) and is refused.`;
    return { ok: false, reason };
  }
  const state = loadApprovals(root);
  const others = state.artifacts.filter((a) => a.artifact !== artifactId);
  const approvedAt = new Date().toISOString();
  const record = { artifact: artifactId, status: "approved", hash: resolved.hash, approvedAt };
  if (options.mode) record.mode = options.mode;
  if (options.via) record.via = options.via;
  others.push(record);
  saveApprovals(root, { artifacts: others, exemplarFeature: state.exemplarFeature });
  return { ok: true, artifact: artifactId, hash: resolved.hash, approvedAt, ...(options.mode ? { mode: options.mode } : {}) };
}

/**
 * Express lane (GENESIS-FLOW-DESIGN.md §2): approve every currently-resolvable,
 * not-yet-approved governed artifact in one pass, each stamped
 * `mode: "defaults-accepted"`. An artifact already `"approved"` (real OR a prior
 * defaults-accepted run) is left untouched — the express lane never overwrites a
 * standing approval, shaped or not. Unresolvable artifacts are SKIPPED with the
 * exact refusal `approveArtifact` would have printed (never a silent skip).
 * @param {string} root
 * @returns {{ok: true, approved: string[], skipped: Array<{id: string, reason: string}>}}
 */
export function approveAllDefaults(root) {
  const registry = listGovernedArtifacts(root);
  const state = loadApprovals(root);
  const byId = new Map(state.artifacts.map((a) => [a.artifact, a]));
  const approved = [];
  const skipped = [];
  for (const artifact of registry) {
    const live = resolveArtifactStatus(root, artifact, byId.get(artifact.id));
    if (live.status === "approved") continue; // already settled — never overwritten by the express lane
    const result = approveArtifact(root, artifact.id, { mode: "defaults-accepted" });
    if (result.ok) approved.push(artifact.id);
    else skipped.push({ id: artifact.id, reason: result.reason });
  }
  return { ok: true, approved, skipped };
}

/**
 * Reopen for redesign (GENESIS-FLOW-DESIGN.md §2): move an `approved` artifact
 * (real or defaults-accepted — both are status `"approved"`) to `"reopened"`,
 * recording `reopenedAt` and clearing any `mode` (a reopened artifact is fluid
 * again, not "the defaults, still"). REFUSES an unknown id, and refuses any
 * artifact whose LIVE status is not `"approved"` — reopening the unreviewed, the
 * already-reopened, or a changed-since-approval artifact is meaningless (there is
 * nothing sanctioned to walk back from).
 * @param {string} root
 * @param {string} artifactId
 * @returns {{ok: true, artifact: string, reopenedAt: string} | {ok: false, reason: string}}
 */
export function reopenArtifact(root, artifactId) {
  const registry = listGovernedArtifacts(root);
  const artifact = registry.find((a) => a.id === artifactId);
  if (!artifact) {
    const known = registry.map((a) => a.id).join(", ") || "(none — no governed artifacts resolved in this project)";
    return { ok: false, reason: `unknown artifact "${artifactId}" — valid ids: ${known}` };
  }
  const state = loadApprovals(root);
  const stored = state.artifacts.find((a) => a.artifact === artifactId);
  const live = resolveArtifactStatus(root, artifact, stored);
  if (live.status !== "approved") {
    return {
      ok: false,
      reason: `cannot reopen "${artifactId}" — it is "${live.status}", not "approved". Only an approved artifact (shaped or defaults-accepted) can be reopened for redesign.`,
    };
  }
  const others = state.artifacts.filter((a) => a.artifact !== artifactId);
  const reopenedAt = new Date().toISOString();
  const record = { artifact: artifactId, status: "reopened", hash: stored.hash, approvedAt: stored.approvedAt, reopenedAt };
  others.push(record);
  saveApprovals(root, { artifacts: others, exemplarFeature: state.exemplarFeature });
  // `artifact` is the ID STRING — the same convention approveArtifact returns
  // (one library, one shape; the console bridge relies on the symmetry).
  return { ok: true, artifact: artifactId, reopenedAt };
}

// ── The verify-lane gate ─────────────────────────────────────────────────────

/**
 * The `approvals` verify-lane gate's pure decision function (qa/verify.mjs
 * wraps this in the step's name/duration bookkeeping — same split as
 * compareTokenDrift/qa/lib/token-drift.mjs).
 *
 * Aggregate verdict:
 *   - any artifact "changed-since-approval"       -> FAIL (names each + the
 *     re-approval command — NEVER names a merely-reopened artifact; see below)
 *   - else any artifact "unreviewed"/"reopened"   -> SKIP (warns, non-blocking)
 *   - else (all approved + matching)              -> PASS
 *
 * The sanctioned-redesign-vs-drift asymmetry (GENESIS-FLOW-DESIGN.md §2) lives
 * right here: `reopened` is grouped with `unreviewed` as non-blocking pending
 * work, `changed-since-approval` is checked FIRST and returns immediately — so a
 * run with one reopened artifact and one genuinely drifted (changed-since-
 * approval) artifact FAILs, and the FAIL reason names only the drifted one.
 * @param {string} root
 * @returns {{verdict: "PASS"|"FAIL"|"SKIP", reason: (string|undefined), statuses: Array<object>}}
 */
export function evaluateApprovalsGate(root) {
  const statuses = getApprovalStatuses(root);
  const mismatched = statuses.filter((s) => s.status === "changed-since-approval");
  const pending = statuses.filter((s) => s.status === "unreviewed" || s.status === "reopened");

  if (mismatched.length > 0) {
    const lines = ["Approval invalidated — a governed artifact changed after sign-off:"];
    for (const s of mismatched) {
      if (!s.resolvable) {
        lines.push(
          `  [${s.id}] ${s.label} — approved at ${shortHash(s.storedHash)}, but its files no longer fully resolve (${s.fileCount} present — deleted or unresolvable). Restore the files, then re-approve if the change was intended (approval over an unresolved file set is refused).`,
        );
      } else {
        lines.push(
          `  [${s.id}] ${s.label} — approved at ${shortHash(s.storedHash)}, now ${shortHash(s.hash)}. Re-approve: node qa/approve.mjs ${s.id}`,
        );
      }
    }
    return { verdict: "FAIL", reason: lines.join("\n"), statuses };
  }

  if (pending.length > 0) {
    const lines = ["Governed artifacts awaiting human approval (non-blocking — approve when ready):"];
    for (const s of pending) {
      if (s.status === "reopened") {
        lines.push(
          `  [${s.id}] ${s.label} — reopened for redesign at ${s.reopenedAt} (non-blocking until re-approved). Approve: node qa/approve.mjs ${s.id}`,
        );
      } else if (!s.resolvable) {
        lines.push(`  [${s.id}] ${s.label} — unreviewed, currently unresolvable (${s.fileCount} of expected files resolved) — not approvable in this tree.`);
      } else {
        lines.push(`  [${s.id}] ${s.label} — unreviewed. Approve: node qa/approve.mjs ${s.id}`);
      }
    }
    return { verdict: "SKIP", reason: lines.join("\n"), statuses };
  }

  return { verdict: "PASS", reason: undefined, statuses };
}

// ── Feature-brief lifecycle (feature-brief.mjs is the doc/doneness model; ────
// ── this file owns the LEDGER side: `accepted` lives on the approval row) ────

/** The `feature-brief:` id prefix — one place, so the CLI/console/gate never drift on it. */
export const FEATURE_BRIEF_PREFIX = "feature-brief:";

/**
 * The human's bookend: mark a feature brief `accepted` — "the proven thing is
 * what I wanted" (CHANGE-FLOW-DESIGN.md §1). There is no agent claim in
 * between: doneness is DERIVED (deriveFeatureStatus — clauses cited, receipt
 * PASS, receipt attests this tree), so acceptance refuses until the harness
 * can prove done, and needs nothing else.
 *
 * Refusals, each a real gap in the acceptance's standing:
 *   - unknown brief (no docs/features/<name>.md)
 *   - brief not approved (accepting an unsigned plan attests nothing — the
 *     walk is sign the brief, build, then accept), and a drifted brief must be
 *     re-approved first (the bytes being accepted must be the bytes signed)
 *   - not provenDone (the refusal quotes the derived doneReason verbatim —
 *     uncited clause, red receipt, or a receipt attesting an older tree)
 * Acceptance never gates the lane — it closes the card, on the human's schedule.
 * @param {string} root
 * @param {string} name the brief's name (docs/features/<name>.md)
 * @returns {{ok: true, artifact: string, acceptedAt: string} | {ok: false, reason: string}}
 */
export function acceptFeature(root, name) {
  const artifactId = `${FEATURE_BRIEF_PREFIX}${name}`;
  const registry = listGovernedArtifacts(root);
  const artifact = registry.find((a) => a.id === artifactId);
  if (!artifact) {
    const briefs = registry.filter((a) => a.id.startsWith(FEATURE_BRIEF_PREFIX)).map((a) => a.id.slice(FEATURE_BRIEF_PREFIX.length));
    return {
      ok: false,
      reason: `unknown feature brief "${name}" — known briefs: ${briefs.join(", ") || "(none — a brief is any docs/features/<name>.md; the location is the governance opt-in)"}`,
    };
  }
  const state = loadApprovals(root);
  const stored = state.artifacts.find((a) => a.artifact === artifactId);
  const live = resolveArtifactStatus(root, artifact, stored);
  if (live.status !== "approved") {
    return {
      ok: false,
      reason:
        `cannot accept "${name}" — the brief is "${live.status}", not "approved". ` +
        (live.status === "changed-since-approval"
          ? `It changed after sign-off; re-approve it first (node qa/approve.mjs ${artifactId}).`
          : "The walk is: sign the brief, build, then accept the proven result."),
    };
  }
  const derived = deriveFeatureStatus(root, { name, rel: artifact.files[0] });
  if (!derived.provenDone) {
    return { ok: false, reason: `cannot accept "${name}" — not provenDone: ${derived.doneReason}` };
  }
  const acceptedAt = new Date().toISOString();
  // MERGE into the approved row (not wholesale-replace): the signature —
  // hash/approvedAt/mode/via — must survive the acceptance verbatim. Contrast
  // approveArtifact, where replacement is the point (a new signature clears
  // an old acceptance).
  const next = state.artifacts.map((a) => (a.artifact === artifactId ? { ...a, accepted: true, acceptedAt } : a));
  saveApprovals(root, { artifacts: next, exemplarFeature: state.exemplarFeature });
  return { ok: true, artifact: artifactId, acceptedAt };
}

/**
 * The console's per-feature view — every brief with its full live state, in
 * one call, so the section renders without composing (the console never
 * re-implements the model; VERIFICATION-LAYER-DESIGN.md §4).
 *
 * Per feature: the DERIVED doneness (deriveFeatureStatus — clauses with their
 * citation state, receipt attestation, provenDone, the one-line doneReason),
 * the brief's approval record, a phase for the card chip, and the DECLARED
 * blast radius resolved against each touched artifact's live status — so the
 * console can render "components: changed-since-approval (as declared)" as
 * expected work, not alarm.
 *
 * Phase vocabulary (CHANGE-FLOW-DESIGN.md §6): `proposed` (no signature yet) →
 * `approved` (signed, building) → `proven` (provenDone, awaiting the human) →
 * `accepted`; a drifted or reopened brief reads as itself.
 *
 * `undeclared`: governed artifacts currently `changed-since-approval` that NO
 * open brief (approved, not yet accepted) declared in `touches` — with the
 * feature-brief/feature-spec families excluded (a brief's own doc drifting is
 * its card's business; spec drift is the feature-spec artifact's own row). The
 * plan drifted from reality; the console shows it as exactly that.
 * @param {string} root
 * @returns {{features: Array<object>, undeclared: Array<{id: string, label: string}>}}
 */
export function getFeatureBoard(root) {
  const statuses = getApprovalStatuses(root);
  const byId = new Map(statuses.map((s) => [s.id, s]));

  const features = deriveAllFeatures(root).map((d) => {
    const record = byId.get(`${FEATURE_BRIEF_PREFIX}${d.name}`) ?? null;
    // Drift outranks acceptance: a brief edited after sign-off reads as
    // changed-since-approval even if it was accepted — a sneaky post-
    // acceptance edit must surface, never hide behind the closed card.
    const phase =
      !record || record.status === "unreviewed"
        ? "proposed"
        : record.status !== "approved"
          ? record.status // changed-since-approval / reopened read as themselves
          : record.accepted
            ? "accepted"
            : d.provenDone
              ? "proven"
              : "approved";
    return {
      ...d,
      record,
      phase,
      touches: d.touches.map((id) => {
        const t = byId.get(id);
        return t ? { id, status: t.status, label: t.label } : { id, status: "unknown", label: `(no governed artifact "${id}")` };
      }),
    };
  });

  const declaredByOpenBriefs = new Set();
  for (const f of features) {
    if (f.record && f.record.status === "approved" && !f.record.accepted) {
      for (const t of f.touches) declaredByOpenBriefs.add(t.id);
    }
  }
  const undeclared = statuses
    .filter(
      (s) =>
        s.status === "changed-since-approval" &&
        !s.id.startsWith(FEATURE_BRIEF_PREFIX) &&
        !s.id.startsWith("feature-spec:") &&
        !declaredByOpenBriefs.has(s.id),
    )
    .map((s) => ({ id: s.id, label: s.label }));

  return { features, undeclared };
}
