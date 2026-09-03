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
//      definition order (GENESIS-FLOW-DESIGN.md §1 + CHANGE-FLOW-DESIGN.md §6):
//      intent(0), feature-brief:<name> per docs/features/*.md (the decide layer,
//      directly after intent), architecture, exemplar-spec, exemplar-feature,
//      design-system, components, then one `feature-spec:<name>` per non-base,
//      non-exemplar spec file present in specs/ right now. (Decide-first: a brief
//      speaks intent's vocabulary. Spec-first: the exemplar's clauses are confirmed
//      before the slice is built. UI-first: design system + components are distilled
//      from the real screens, so they lock after the exemplar.) The exemplar is
//      CONFIGURABLE — see
//      `getExemplarFeature`/`resolveExemplarNames` below — defaulting to `home` so
//      every ledger written before this config key existed keeps meaning what it
//      meant. The registry is recomputed on every call — it reflects the tree as it
//      stands, never a stale snapshot.
//   2. STATE (`loadApprovals`/`saveApprovals`) — qa/approvals.json, the human's
//      decisions: { artifact, status, hash, approvedAt, mode?, reopenedAt?, via?,
//      reason? } plus the top-level `exemplarFeature` config key. Absent or corrupt is TOLERATED
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
import { deriveAllFeatures, deriveFeatureStatus, listFeatureBriefs, parseFeatureBlock, stripFeatureBlock } from "./feature-brief.mjs";

export const APPROVALS_REL_PATH = "qa/approvals.json";
export const APPROVALS_SCHEMA = "cmp-approvals/1";
// The JOURNAL (2026-07-28 flow audit, fix 1): qa/approvals.json is a mutable
// snapshot — every transition overwrites the row, so the ledger cannot answer
// "what happened while I was away?". The journal is the append-only memory
// beside it: one JSON line per human-meaningful transition (approve / reopen /
// accept), each carrying {at, verb, artifact, via, reason?}. State stays
// DERIVED from the snapshot exactly as before; the journal gates nothing and
// no lane step reads it — which is why it sits in inputs-hash.mjs's
// EXCLUDED_PREFIXES (like qa/comments.json): appending history must never
// invalidate the receipt for a tree whose code did not change.
export const APPROVALS_JOURNAL_REL_PATH = "qa/approvals.log.jsonl";

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

/** The `feature-design:` id prefix — one place, so the CLI/console/gate never drift on it. */
export const FEATURE_DESIGN_PREFIX = "feature-design:";

/**
 * The screen files of one feature — `presentation/<name>/**\/*Screen.kt`,
 * recursive, sorted. DELIBERATELY only `*Screen.kt`: the design signature
 * covers the FORM (what renders), so binding the whole presentation dir would
 * make every ViewModel edit during a legitimate build read as design drift.
 * @param {string} root
 * @param {string} name the feature name (presentation/<name>/)
 * @returns {string[]} repo-relative posix paths
 */
function listFeatureScreenFiles(root, name) {
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
 * Order: intent(0), then feature-brief:<name> per docs/features/*.md — the
 * DECIDE layer sits directly after intent (a brief speaks intent's
 * vocabulary; only the SPEC needs architecture's) — then architecture,
 * exemplar-spec, exemplar-feature, design-system, components, and one
 * feature-spec:<name> per non-base, non-CONFIGURED-exemplar spec present.
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

  // Feature briefs (feature-brief.mjs): one `feature-brief:<name>` per doc
  // under docs/features/ — LOCATION is the governance opt-in (CHANGE-FLOW-
  // DESIGN.md §2); docs/proposals/ stays ungoverned harness-standards prose.
  // They sit DIRECTLY after intent: the decide layer. At genesis the first
  // feature's brief is drafted from the intent interview before architecture
  // is even walked; post-genesis every decision-carrying change enters here.
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

  // Feature designs (brief → design → spec → build, decided 2026-07-25): one
  // `feature-design:<name>` per BRIEF with a UI surface — declared
  // (`"screens": true` in the cmp:feature block, so the gate exists before any
  // file does) or evident (presentation/<name>/*Screen.kt on disk). Signed on
  // RENDERED output, never descriptions, BEFORE the behavior contract pins the
  // form down. Briefs only, deliberately: legacy features (governed by
  // exemplar-feature or nothing) never sprout retro-governance. With no screen
  // files yet, `complete: false` — approveArtifact refuses, exactly right: you
  // cannot sign a design that has nothing rendered.
  for (const brief of listFeatureBriefs(root)) {
    let declaresScreens = false;
    try {
      declaresScreens = parseFeatureBlock(fs.readFileSync(path.join(root, brief.rel), "utf8")).screens;
    } catch {
      /* an unreadable brief declares nothing */
    }
    const screenFiles = listFeatureScreenFiles(root, brief.name);
    if (!declaresScreens && screenFiles.length === 0) continue;
    artifacts.push({
      id: `${FEATURE_DESIGN_PREFIX}${brief.name}`,
      label: `Feature design (${brief.name} — presentation/${brief.name}/*Screen.kt, signed on rendered output)`,
      files: screenFiles,
      complete: packageResolved && screenFiles.length > 0,
    });
  }

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
  if (artifact.id === "architecture") return hashArchitectureArtifact(root);
  if (artifact.id.startsWith(FEATURE_BRIEF_PREFIX)) return hashFeatureBriefArtifact(root, artifact);
  return hashArtifactFiles(root, artifact.files);
}

/**
 * A feature brief's hash basis: the doc's content, EOL-normalized, with the
 * cmp:feature declaration block stripped (`stripFeatureBlock` — the ONE
 * definition of the block grammar, shared with parseFeatureBlock so the
 * stripper and the parser can never disagree about what a block is).
 *
 * Same rationale as `hashArchitectureArtifact`'s cmp:generated stripping: the
 * human signs the brief's reasoning; `touches`/`screens` are declarations the
 * harness independently enforces (artifact hashes enforce blast radius; disk
 * presence enforces the design gate) and can authorise nothing — so adding
 * mandatory machine-read metadata to a signed brief must never manufacture a
 * human re-approval. Normalized `\r\n` -> `\n` first, because the fence
 * grammar matches a literal `\n` and a checkout-induced EOL flip in prose must
 * never read as authored drift.
 * @param {string} root
 * @param {{id: string, files: string[]}} artifact
 * @returns {{ hash: string, fileCount: number, missing: string[] }}
 */
function hashFeatureBriefArtifact(root, artifact) {
  const files = [...new Set(artifact.files)].sort();
  const rows = [];
  const missing = [];
  for (const relPath of files) {
    let raw;
    try {
      raw = fs.readFileSync(path.join(root, relPath), "utf8");
    } catch {
      missing.push(relPath);
      continue;
    }
    const stripped = stripFeatureBlock(raw.replace(/\r\n/g, "\n"));
    rows.push([relPath, createHash("sha256").update(stripped, "utf8").digest("hex")]);
  }
  const overall = createHash("sha256");
  for (const [relPath, fileSha] of rows) {
    overall.update(`${relPath}\0${fileSha}\n`);
  }
  return { hash: overall.digest("hex"), fileCount: rows.length, missing };
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

// ── The journal (append-only; the snapshot above stays the derived state) ────

/**
 * Append one transition to qa/approvals.log.jsonl. TOLERANT — a failed append
 * (read-only fs, weird mount) must never block the transition it records, so
 * this returns {ok:false} rather than throwing; the snapshot write (the state
 * that gates) has already happened or is about to, and history-keeping is
 * strictly subordinate to it.
 * @param {string} root
 * @param {{verb: string, artifact: string, via?: string, reason?: string, [k: string]: unknown}} event
 * @returns {{ok: boolean}}
 */
export function appendJournal(root, event) {
  const p = path.join(root, APPROVALS_JOURNAL_REL_PATH);
  try {
    fs.mkdirSync(path.dirname(p), { recursive: true });
    fs.appendFileSync(p, `${JSON.stringify({ at: new Date().toISOString(), ...event })}\n`);
    return { ok: true };
  } catch {
    return { ok: false };
  }
}

/**
 * Every journal event, oldest first. Absent file -> []. A corrupt LINE is
 * skipped, never fatal (same tolerance stance as loadApprovals) — one mangled
 * append must not blind the console to the rest of the history.
 * @param {string} root
 * @returns {Array<{at: string, verb: string, artifact: string, via?: string, reason?: string}>}
 */
export function readJournal(root) {
  let raw;
  try {
    raw = fs.readFileSync(path.join(root, APPROVALS_JOURNAL_REL_PATH), "utf8");
  } catch {
    return [];
  }
  const events = [];
  for (const line of raw.split("\n")) {
    if (line.trim() === "") continue;
    try {
      const parsed = JSON.parse(line);
      if (parsed && typeof parsed === "object" && typeof parsed.verb === "string") events.push(parsed);
    } catch {
      /* skip the mangled line, keep the history */
    }
  }
  return events;
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
 *
 * `hash` is always the LIVE recompute and `storedHash` always what was actually
 * signed. They are equal for every artifact signed on the current basis, which is
 * why they are easy to conflate — but a display that means "the signature" must
 * read `storedHash`. The one case where they legitimately differ on an `approved`
 * row is `hashBasis: "raw-bytes"` (below): the stored hash is on a superseded
 * basis, so the live recompute is a number NOBODY EVER SIGNED and must never be
 * labelled as one.
 * @returns {{id: string, label: string, status: string, hash: string, storedHash: (string|null), approvedAt: (string|null), fileCount: number, missing: string[], resolvable: boolean, mode?: string, reopenedAt?: string, hashBasis?: string}}
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
      approvedBy: storedRecord.approvedBy ?? null,
      fileCount: recomputed.fileCount,
      missing: recomputed.missing,
      resolvable,
      reopenedAt: storedRecord.reopenedAt,
      // Attribution (2026-07-28 flow audit): who walked the signature back and
      // why, read straight off the row — surfaced only when actually recorded
      // (pre-audit rows carry neither; absence is the honest answer there).
      ...(storedRecord.via ? { via: storedRecord.via } : {}),
      ...(storedRecord.reason ? { reason: storedRecord.reason } : {}),
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
      approvedBy: null,
      fileCount: recomputed.fileCount,
      missing: recomputed.missing,
      resolvable,
    };
  }
  let changed = !resolvable || storedRecord.hash !== recomputed.hash;
  // Legacy feature-brief approvals (pre block-stripping) stored the RAW-bytes
  // hash. If the stored hash still matches the raw bytes on disk, the file is
  // byte-identical to what the human signed — strictly stronger than a
  // stripped-basis match — so the signature stands. Permanent and safe: this
  // path can only accept content that has not changed at all since signing.
  let hashBasis = null;
  if (
    changed &&
    resolvable &&
    artifact.id.startsWith(FEATURE_BRIEF_PREFIX) &&
    storedRecord.hash === hashArtifactFiles(root, artifact.files).hash
  ) {
    changed = false;
    // Say so. This is the ONE approved state where storedHash !== hash
    // legitimately, and a row that shows the disagreement without naming its
    // cause is indistinguishable from tolerated drift — the exact ambiguity the
    // governance surface exists to remove. Emitted only when the path actually
    // fires; absence means "signed on the current basis", the normal case.
    hashBasis = "raw-bytes";
  }
  return {
    id: artifact.id,
    label: artifact.label,
    status: changed ? "changed-since-approval" : "approved",
    hash: recomputed.hash,
    storedHash: storedRecord.hash,
    approvedAt: storedRecord.approvedAt,
    // WHO signed. Null on rows written before signers were recorded; the gate
    // treats a signed-by-nobody approval as FAIL, because that row cannot tell
    // a human's sign-off from an agent's.
    approvedBy: storedRecord.approvedBy ?? null,
    fileCount: recomputed.fileCount,
    missing: recomputed.missing,
    resolvable,
    ...(hashBasis ? { hashBasis } : {}),
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
  if (!options.approvedBy || !String(options.approvedBy).trim()) {
    return {
      ok: false,
      reason:
        `cannot approve "${artifactId}" — no signer was given. An approval is a person's ` +
        "signature on a hash; a row that records no signer cannot distinguish a human's sign-off " +
        "from an agent's, and an agent that invalidates an approval can clear it by re-approving. " +
        "Pass the signer: `node qa/approve.mjs <artifact> --as \"Name <email>\"`.",
    };
  }
  const record = {
    artifact: artifactId,
    status: "approved",
    hash: resolved.hash,
    approvedAt,
    approvedBy: String(options.approvedBy).trim(),
  };
  if (options.mode) record.mode = options.mode;
  if (options.via) record.via = options.via;
  others.push(record);
  saveApprovals(root, { artifacts: others, exemplarFeature: state.exemplarFeature });
  appendJournal(root, {
    verb: "approve",
    artifact: artifactId,
    hash: resolved.hash,
    approvedBy: String(options.approvedBy).trim(),
    ...(options.via ? { via: options.via } : {}),
    ...(options.mode ? { mode: options.mode } : {}),
  });
  return { ok: true, artifact: artifactId, hash: resolved.hash, approvedAt, approvedBy: record.approvedBy, ...(options.mode ? { mode: options.mode } : {}) };
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
export function approveAllDefaults(root, approvedBy) {
  const registry = listGovernedArtifacts(root);
  const state = loadApprovals(root);
  const byId = new Map(state.artifacts.map((a) => [a.artifact, a]));
  const approved = [];
  const skipped = [];
  for (const artifact of registry) {
    const live = resolveArtifactStatus(root, artifact, byId.get(artifact.id));
    if (live.status === "approved") continue; // already settled — never overwritten by the express lane
    const result = approveArtifact(root, artifact.id, { mode: "defaults-accepted", approvedBy });
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
 *
 * REFUSES a missing `reason` (2026-07-28 flow audit, fix 2): a reopen is a
 * state change on a SIGNED document — the one act in this file that walks back
 * a human's signature. The ledger used to record neither who did it nor why,
 * so the signer came back to "reopened" with no way to learn what happened.
 * ECO discipline: every change to a released document carries initiator and
 * justification, mechanically required. `via`/`reason` land on the row (they
 * are outside the inputs-hash projection, so no receipt is invalidated) and
 * in the journal.
 * @param {string} root
 * @param {string} artifactId
 * @param {{reason: string, via?: string, feature?: string}} options `reason` is
 *   REQUIRED — one plain sentence for the human who signed. `via` records the
 *   surface ("console"/"cli"). `feature` groups the reopens of one
 *   `reopenFeature` walk under the brief's name.
 * @returns {{ok: true, artifact: string, reopenedAt: string} | {ok: false, reason: string}}
 */
export function reopenArtifact(root, artifactId, options = {}) {
  const why = typeof options.reason === "string" ? options.reason.trim() : "";
  if (why === "") {
    return {
      ok: false,
      reason:
        `cannot reopen "${artifactId}" without a reason — a reopen walks back a signature, and the signer ` +
        `must be able to read why from the ledger itself. Pass one plain sentence (CLI: --reason "…").`,
    };
  }
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
  const record = { artifact: artifactId, status: "reopened", hash: stored.hash, approvedAt: stored.approvedAt, reopenedAt, reason: why };
  if (options.via) record.via = options.via;
  others.push(record);
  saveApprovals(root, { artifacts: others, exemplarFeature: state.exemplarFeature });
  appendJournal(root, {
    verb: "reopen",
    artifact: artifactId,
    reason: why,
    ...(options.via ? { via: options.via } : {}),
    ...(options.feature ? { feature: options.feature } : {}),
  });
  // `artifact` is the ID STRING — the same convention approveArtifact returns
  // (one library, one shape; the console bridge relies on the symmetry).
  return { ok: true, artifact: artifactId, reopenedAt };
}

/**
 * Reopen one FEATURE as one recorded change (2026-07-28 flow audit, fix 4):
 * the brief is the change container — a human edits "the meal-plan feature",
 * not four artifact ids at four timestamps. This walks the brief's set —
 * `feature-brief:<name>`, `feature-spec:<name>`, `feature-design:<name>`, and
 * every artifact the brief DECLARES in `touches` — and reopens each one that
 * is currently `approved`, all under the same reason, each journal event
 * carrying `feature: <name>` so the history reads as one change.
 *
 * Artifacts in the set that are not currently approved are SKIPPED and
 * reported (already reopened, unreviewed, or drifted — each already tells its
 * own story; silently "fixing" their state here would erase it). Refuses only
 * when the set contains nothing approved at all — then there is no signature
 * to walk back and the caller's premise is wrong.
 * @param {string} root
 * @param {string} name the brief's name (docs/features/<name>.md)
 * @param {{reason: string, via?: string}} options same contract as reopenArtifact
 * @returns {{ok: true, feature: string, reopened: string[], skipped: Array<{id: string, status: string}>} | {ok: false, reason: string}}
 */
export function reopenFeature(root, name, options = {}) {
  const why = typeof options.reason === "string" ? options.reason.trim() : "";
  if (why === "") {
    return {
      ok: false,
      reason: `cannot reopen feature "${name}" without a reason — pass one plain sentence (CLI: --reason "…").`,
    };
  }
  const briefId = `${FEATURE_BRIEF_PREFIX}${name}`;
  const registry = listGovernedArtifacts(root);
  if (!registry.some((a) => a.id === briefId)) {
    const briefs = registry.filter((a) => a.id.startsWith(FEATURE_BRIEF_PREFIX)).map((a) => a.id.slice(FEATURE_BRIEF_PREFIX.length));
    return { ok: false, reason: `unknown feature "${name}" — known briefs: ${briefs.join(", ") || "(none)"}` };
  }
  const derived = deriveAllFeatures(root).find((d) => d.name === name);
  // The spec side of the family follows the brief's own pairing (a multi-spec
  // brief reopens every spec its promises live in), defaulting to the name.
  const specIds = (derived?.specNames ?? [name]).map((n) => `feature-spec:${n}`);
  // WHAT A FEATURE REOPEN WALKS BACK (evidence-economics S5, aligning this
  // function with CHANGE-FLOW-DESIGN.md §"touches": "hashes enforce,
  // declaration lets the console tell as-planned from undeclared blast").
  //
  //   reopened     the brief, its declared spec(s), and its design when the
  //                brief declares a UI surface — the documents the change
  //                will AMEND. Their signatures are walked back on purpose.
  //   stillSigned  the declared `touches`. Before this, every one of them was
  //                reopened too, and every one came back byte-identical:
  //                twelve signatures for zero changes (design-system
  //                d8fbdce8 → d8fbdce8). An `approved` artifact is, by
  //                definition, one whose bytes still match what was signed —
  //                so reopening it re-asks a question the hash has already
  //                answered. Worse than wasted: it trains the signer to
  //                approve without reading, the exact habit approvals exist
  //                to prevent. They stay signed. If the change DOES move one,
  //                its hash flips it to `changed` and demands a fresh
  //                signature — the enforcement the doc always assigned to the
  //                hash, not to this verb.
  const amendSet = [briefId, ...specIds, ...(derived?.screens ? [`${FEATURE_DESIGN_PREFIX}${name}`] : [])];
  const touchSet = (derived ? derived.touches : []).filter((id) => !amendSet.includes(id));
  const byId = new Map(getApprovalStatuses(root).map((s) => [s.id, s]));
  const reopened = [];
  const skipped = [];
  for (const id of [...new Set(amendSet)]) {
    const live = byId.get(id);
    if (!live) continue; // resolves to no governed artifact — nothing to reopen
    if (live.status !== "approved") {
      skipped.push({ id, status: live.status });
      continue;
    }
    const result = reopenArtifact(root, id, { reason: why, via: options.via, feature: name });
    if (result.ok) reopened.push(id);
    else skipped.push({ id, status: `refused: ${result.reason}` });
  }
  const stillSigned = [];
  for (const id of [...new Set(touchSet)]) {
    const live = byId.get(id);
    if (!live) continue;
    stillSigned.push({ id, status: live.status, hash: typeof live.hash === "string" ? live.hash.slice(0, 8) : null });
  }
  if (reopened.length === 0) {
    return {
      ok: false,
      reason:
        `nothing in "${name}"'s amend set is currently approved — there is no signature to walk back. ` +
        `Set: ${[...new Set(amendSet)].join(", ")}; states: ${skipped.map((s) => `${s.id}=${s.status}`).join(", ") || "(unresolved)"}`,
    };
  }
  return { ok: true, feature: name, reopened, skipped, stillSigned };
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
  // An "approved" row with no signer attests nothing about WHO signed, which is
  // the one fact an approval exists to record. It cannot distinguish a human's
  // sign-off from an agent's, and an agent that invalidates an approval can
  // clear it by re-approving — the gate then guards only against accident, not
  // against the population it is pointed at. Rows written before signers were
  // recorded land here; the fix is one re-approval each, and the message says so.
  const unsigned = statuses.filter((s) => s.status === "approved" && !s.approvedBy);

  if (unsigned.length > 0) {
    const lines = ["Approval recorded without a signer — re-approve to say who signed:"];
    for (const s of unsigned) {
      lines.push(`  [${s.id}] ${s.label} — approved ${shortHash(s.storedHash)} by nobody. Re-approve: node qa/approve.mjs ${s.id} --as "Your Name <you@example.com>"`);
    }
    return { verdict: "FAIL", reason: lines.join("\n"), statuses };
  }

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
          `  [${s.id}] ${s.label} — reopened for redesign at ${s.reopenedAt}${s.reason ? ` (reason: ${s.reason})` : ""} (non-blocking until re-approved). Approve: node qa/approve.mjs ${s.id}`,
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
 * @param {{via?: string}} [options] `via` records the surface ("console"/"cli") in the journal
 * @returns {{ok: true, artifact: string, acceptedAt: string} | {ok: false, reason: string}}
 */
export function acceptFeature(root, name, options = {}) {
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
  // The design signature is part of the contract (brief → design → spec →
  // build): a feature with a UI surface cannot be accepted past an unsigned
  // or drifted design — "the proven thing is what I wanted" includes its form.
  const designArtifact = registry.find((a) => a.id === `${FEATURE_DESIGN_PREFIX}${name}`);
  if (designArtifact) {
    const designStored = state.artifacts.find((a) => a.artifact === designArtifact.id);
    const designLive = resolveArtifactStatus(root, designArtifact, designStored);
    if (designLive.status !== "approved") {
      return {
        ok: false,
        reason:
          `cannot accept "${name}" — feature-design:${name} is "${designLive.status}", not "approved". ` +
          `The feature's screens are signed on rendered output before acceptance (node qa/approve.mjs feature-design:${name}).`,
      };
    }
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
  appendJournal(root, { verb: "accept", artifact: artifactId, ...(options.via ? { via: options.via } : {}) });
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
// How many recorded edge cases satisfy the `audit` rung. A count cannot judge an
// audit's QUALITY — it can only insist the pass happened and left written output,
// which is the whole point: findings then land in the same signing round rather
// than reopening a signed artifact. One is too easy to satisfy accidentally;
// three is the smallest number that requires actually looking.
const MIN_AUDITED_EDGE_CASES = 3;

export function getFeatureBoard(root) {
  const statuses = getApprovalStatuses(root);
  const byId = new Map(statuses.map((s) => [s.id, s]));

  // The DERIVED next step (CHANGE-FLOW-DESIGN.md §4): computed from live
  // state exactly like provenDone, never claimed. An approval HANDS OFF, it
  // never commands — so each step names its owner: the agent drafts/builds/
  // proves, the human signs/accepts. For a change to EXISTING features the
  // contract step includes the declared amendments: every touched
  // feature-spec:* that is still signed must be reopened and amended, and the
  // step says so by name — that is what the human's signature set in motion.
  const deriveNextStep = (d, phase) => {
    // The brief's PAIRED specs (feature-brief.mjs pairedSpecNames — the one
    // pairing function): a multi-spec brief waits on ALL of them being
    // signed, and its contract step names each one still waiting.
    const specArtifacts = (d.specNames ?? [d.name])
      .map((n) => byId.get(`feature-spec:${n}`))
      .filter(Boolean);
    const designArtifact = byId.get(`${FEATURE_DESIGN_PREFIX}${d.name}`) ?? null;
    const declaredSpecAmendments = d.touches
      .filter((id) => id.startsWith("feature-spec:") && byId.get(id)?.status === "approved")
      .map((id) => id.slice("feature-spec:".length));
    const amendNote =
      declaredSpecAmendments.length > 0 ? ` + reopen & amend ${declaredSpecAmendments.map((n) => `specs/${n}.spec.md`).join(", ")} (declared)` : "";
    // PRE-SIGNATURE AGENT WORK (the anti-churn ordering). A feature with a UI
    // surface gets its design drafted AND adversarially audited before the human
    // is asked for a single signature. The old ladder asked for the brief first,
    // so the design — and the audit that attacks it — happened against an already
    // signed artifact, and every finding reopened it. Measured on meal-plan
    // (2026-07-27): three signing rounds, the third triggered by an audit that
    // found nine gaps including three defects in signed clauses. The work did not
    // change; only when it happens relative to the gate.
    const designPending = designArtifact !== null && designArtifact.status !== "approved";
    const designUndrafted = designPending && !designArtifact.resolvable;
    const auditMissing = designPending && d.edgeCases < MIN_AUDITED_EDGE_CASES;

    if (phase === "accepted") return { key: "closed", owner: null, label: "closed — the brief is this feature's doc-of-record" };
    if (phase === "changed-since-approval")
      return { key: "re-approve", owner: "human", label: `re-approve the brief — it changed after signing (or revert the edit)` };
    // `reopened` is ONE stored state covering two OPPOSITE situations (2026-07-28
    // flow audit, fix 3): mid-redesign it waits on the WORK; once the redesign is
    // proven (provenDone — every live clause cited + receipt PASS + receipt
    // attests this tree) it waits on the SIGNATURE. The split is derived, never
    // claimed — the same derivation acceptance already trusts. Before this,
    // meal-plan sat reopened AND 23/23-proven simultaneously: the card said
    // "waiting on you" while the guided queue said "nothing waits on you".
    if (phase === "reopened") {
      return d.provenDone
        ? { key: "re-approve", owner: "human", label: "redesign proven — re-approve the brief" }
        : { key: "redesign", owner: "agent", label: "redesign in progress — finish and prove it; the brief then returns for your signature" };
    }
    if (designUndrafted)
      return {
        key: "design",
        owner: "agent drafts → human signs",
        label: `design: draft the ${d.name} screens on stub data and render them — you sign what renders, never a description`,
      };
    if (auditMissing)
      return {
        key: "audit",
        owner: "agent",
        label:
          `audit the ${d.name} design for edge cases — record each case and how it resolves under ` +
          `"## Edge cases" in ${d.rel}. Findings land BEFORE the signature, not after it`,
      };
    if (phase === "proposed")
      return { key: "sign-brief", owner: "human", label: "sign the brief — decisions close before code, and the design below is audited" };
    // Design before contract (brief → design → spec → build): the form is
    // signed on RENDERED output before behavior clauses pin it down — and
    // before acceptance, so these rungs outrank `proven`.
    if (designPending) {
      if (designArtifact.status === "reopened")
        return { key: "design", owner: "agent", label: `redesign in progress: finish the ${d.name} screens, then re-approve the design` };
      return {
        key: "sign-design",
        owner: "human",
        label:
          designArtifact.status === "changed-since-approval"
            ? `re-approve the design (feature-design:${d.name}) — the screens changed after signing (or revert)`
            : `sign the design (feature-design:${d.name}) — audited, judged on the rendered screens`,
      };
    }
    if (phase === "proven") return { key: "accept", owner: "human", label: "accept — the proven thing awaits your judgment" };
    // phase === "approved": building — which part of the loop is open?
    if (!d.specExists || d.total === 0)
      return { key: "contract", owner: "agent drafts → human signs", label: `contract: write the clauses in ${d.specRel}${amendNote}` };
    const unsignedSpecs = specArtifacts.filter((a) => a.status !== "approved");
    if (unsignedSpecs.length > 0)
      return { key: "sign-spec", owner: "human", label: `sign the contract (${unsignedSpecs.map((a) => a.id).join(", ")})${amendNote}` };
    if (d.covered < d.total)
      return { key: "build", owner: "agent", label: `build & cite: ${d.total - d.covered} clause(s) have no citing test yet` };
    return { key: "prove", owner: "agent", label: "prove: run node qa/verify.mjs so the receipt attests this tree" };
  };

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
    const designStatus = byId.get(`${FEATURE_DESIGN_PREFIX}${d.name}`) ?? null;
    return {
      ...d,
      record,
      phase,
      // The design gate's live state, for the card: null = no UI surface
      // (a pure-logic feature honestly has no design rung to show).
      design: designStatus
        ? { id: designStatus.id, status: designStatus.status, resolvable: designStatus.resolvable, fileCount: designStatus.fileCount }
        : null,
      nextStep: deriveNextStep(d, phase),
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
