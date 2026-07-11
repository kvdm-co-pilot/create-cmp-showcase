#!/usr/bin/env node
// The `add-feature` stamper — deterministic vertical-slice generator.
//
//   node qa/scaffold-feature.mjs <FeatureName> [--entity <EntityName>] [--dry-run]
//   node qa/scaffold-feature.mjs <Entity> --preset repository [--dry-run]
//   node qa/scaffold-feature.mjs <FeatureName> --entity <EntityName> --preset screen [--dry-run]
//
// Copies the `home` exemplar file set, applies a curated WHOLE-WORD identifier
// rename (never a blind substring replace — see the rename map below), injects
// the new feature into the three shared files at their `// cmp:anchor` markers,
// and writes a default spec clause set. Pure Node, no dependencies.
//
// Philosophy: skills instruct, scripts stamp (HARNESS-ROADMAP M3). The AI only
// refines spec wording after this runs; the file set + wiring are mechanical.
//
// --preset (default `feature`, unchanged behavior): one stamping mechanic,
// three front-doors. Every FILES entry and every injection step below is
// tagged with the set of presets it belongs to; the active preset filters
// both lists before anything is written. There is no forked copy of this
// script per preset — `feature` is simply `repository` + `screen` + nav wiring
// that spans both, applied together.
//
//   feature    (default) — all 11 files; DI repo+usecase+viewModel; nav
//              route+import; spec FEATURE-01..06.
//   repository <Entity>  — ONLY the 5 data/domain files; DI repo+usecase ONLY;
//              no nav, no viewModel, no spec file, zero SPEC tags. The
//              positional arg IS the entity (no --entity, no feature name).
//   screen     <Feature> --entity <E> — ONLY presentation + tests + spec (3
//              test files carry all 6 SPEC tags); DI viewModel ONLY; nav
//              route+import. Requires the entity's data layer to already
//              exist (validated before anything is written — see below).

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function die(message) {
  console.error(`error: ${message}`);
  process.exit(1);
}

// ── Argument parsing ────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const positional = args.filter((a) => !a.startsWith("--"));
const dryRun = args.includes("--dry-run");
const entityFlagIdx = args.indexOf("--entity");
const entityArg = entityFlagIdx !== -1 ? args[entityFlagIdx + 1] : undefined;

const PRESETS = new Set(["feature", "screen", "repository"]);
const presetFlagIdx = args.indexOf("--preset");
const preset = presetFlagIdx !== -1 ? args[presetFlagIdx + 1] : "feature";
if (!PRESETS.has(preset)) {
  die(`"${preset}" is not a valid --preset — choose one of: feature, screen, repository.`);
}

const USAGE =
  "usage:\n" +
  "  node qa/scaffold-feature.mjs <FeatureName> [--entity <EntityName>] [--dry-run]\n" +
  "  node qa/scaffold-feature.mjs <Entity> --preset repository [--dry-run]\n" +
  "  node qa/scaffold-feature.mjs <FeatureName> --entity <EntityName> --preset screen [--dry-run]";

const positionalName = positional[0];
if (!positionalName) {
  die(`${USAGE}\n  The positional name is required, e.g. \`Favorites\`.`);
}

const IDENTIFIER_RE = /^[A-Z][A-Za-z0-9]*$/;
if (!IDENTIFIER_RE.test(positionalName)) {
  die(
    `"${positionalName}" is not a valid PascalCase Kotlin identifier. Use e.g. "Favorites", "Bookmarks".`,
  );
}

function defaultEntity(feature) {
  // Naive de-pluralization — the skill's interview step should let a human
  // override this via --entity when it's wrong (Categories -> Category, etc).
  if (feature.endsWith("ies") && feature.length > 3) return `${feature.slice(0, -3)}y`;
  if (feature.endsWith("s") && !feature.endsWith("ss")) return feature.slice(0, -1);
  return feature;
}

// `repository` preset: the positional arg IS the entity — no feature name, no
// nav/presentation slice at all. `feature`/`screen`: positional is the feature
// name; --entity defaults via de-pluralization if omitted.
const featureName = preset === "repository" ? undefined : positionalName;
const entityName = preset === "repository" ? positionalName : (entityArg ?? defaultEntity(positionalName));
if (!IDENTIFIER_RE.test(entityName)) {
  die(`"${entityName}" is not a valid PascalCase Kotlin identifier for --entity.`);
}

// `repository` preset has no feature name (no nav/presentation/spec slice), so
// F/f/F_UPPER are never read for it — the rename map still needs harmless
// values to build (its feature-shaped entries never match repository-preset
// file contents, which only reference Item/ItemRepository/GetItemsUseCase).
const F = featureName ?? entityName; // PascalCase feature, e.g. Favorites
const f = F[0].toLowerCase() + F.slice(1); // camelCase/package segment, e.g. favorites
const F_UPPER = F.replace(/([a-z0-9])([A-Z])/g, "$1_$2").toUpperCase(); // FAVORITES
const E = entityName; // PascalCase entity, e.g. Favorite

// ── Resolve the target project's real package ───────────────────────────────
// This script runs POST-scaffold, so com.kvdm.cmpshowcase is already resolved in the
// target project. Parse it from composeApp/build.gradle.kts (namespace) or,
// failing that, from any source file's `package` line.

function resolvePackage() {
  const gradleFile = path.join(ROOT, "composeApp", "build.gradle.kts");
  if (fs.existsSync(gradleFile)) {
    const contents = fs.readFileSync(gradleFile, "utf8");
    const m = contents.match(/namespace\s*=\s*"([^"]+)"/);
    if (m && m[1] !== "com.kvdm.cmpshowcase") return m[1];
  }
  const homeViewModel = path.join(
    ROOT,
    "composeApp/src/commonMain/kotlin",
    ...guessPackageDirFromDisk(),
    "presentation/home/HomeViewModel.kt",
  );
  if (fs.existsSync(homeViewModel)) {
    const m = fs.readFileSync(homeViewModel, "utf8").match(/^package\s+([\w.]+)\.presentation\.home\s*$/m);
    if (m) return m[1];
  }
  die(
    "could not resolve the project's package — expected a resolved `namespace = \"...\"` in " +
      "composeApp/build.gradle.kts (found com.kvdm.cmpshowcase unresolved, or the file is missing). " +
      "Run this script POST-scaffold, in a project that has already been stamped.",
  );
}

// Best-effort directory walk to find the HomeViewModel.kt under some package
// path when build.gradle.kts didn't yield an answer (fallback path only).
function guessPackageDirFromDisk() {
  const base = path.join(ROOT, "composeApp/src/commonMain/kotlin");
  let dir = base;
  const segments = [];
  // Walk down single-child directories until we hit `presentation` or run out.
  while (fs.existsSync(dir)) {
    const entries = fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory());
    if (entries.length !== 1) break;
    if (entries[0].name === "presentation") break;
    segments.push(entries[0].name);
    dir = path.join(dir, entries[0].name);
  }
  return segments;
}

const PACKAGE = resolvePackage();
const PACKAGE_DIR = PACKAGE.split(".").join("/");

// ── The rename map (§3) ──────────────────────────────────────────────────────
// Whole-word (\b-delimited), applied LONGEST KEY FIRST so compound entries
// (ItemRepositoryImpl) resolve before their substrings (ItemRepository, Item).
// Anything not in this list is left untouched by design (see design doc §3
// "LEAVE GENERIC" — awaitItem, items, item, goldenItems, itemId, onItemClick,
// id, title, subtitle, and every androidx./kotlinx./org.koin./kotlin. token).

const RENAME_MAP = [
  ["HomeScreenTest", `${F}ScreenTest`],
  ["HomeViewModelTest", `${F}ViewModelTest`],
  ["HomeGoldenTreeTest", `${F}GoldenTreeTest`],
  ["HomeScreen", `${F}Screen`],
  ["HomeViewModel", `${F}ViewModel`],
  ["HomeUiState", `${F}UiState`],
  ["home_title", `${f}_title`],
  ["home_error", `${f}_error`],
  ["FakeItemRepository", `Fake${E}Repository`],
  ["ItemRepositoryImpl", `${E}RepositoryImpl`],
  ["ItemRepository", `${E}Repository`],
  ["GetItemsUseCase", `Get${E}sUseCase`],
  ["getItemsCallCount", `get${E}sCallCount`],
  ["getItems", `get${E}s`],
  ["Item", E],
  // Spec + test SPEC-tag retargeting (§6): HOME-0N -> <F_UPPER>-0N, then the
  // bare HOME -> <F_UPPER> (must run AFTER the -0 form or "HOME-0" would be
  // partially consumed oddly — longest-key-first already orders this).
  ["HOME-0", `${F_UPPER}-0`],
  ["HOME", F_UPPER],
  // Package segment / path / golden filename / display text. Order matters:
  // must run after HomeXxx / home_xxx above so those compounds are already
  // resolved; the bare `home` word only matches the standalone package
  // segment, golden filename stem, and prose by this point.
  ["home", f],
  ["Home", F],
].sort((a, b) => b[0].length - a[0].length);

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

const COMPILED_RENAMES = RENAME_MAP.map(([from, to]) => [new RegExp(`\\b${escapeRegExp(from)}\\b`, "g"), to]);

function applyRename(text) {
  let out = text;
  for (const [re, to] of COMPILED_RENAMES) out = out.replace(re, to);
  return out;
}

// ── The file set (§4) ───────────────────────────────────────────────────────
// Source paths are relative to composeApp/src/<sourceSet>/kotlin/<PACKAGE_DIR>.

const SRC = (sourceSet) => path.join(ROOT, "composeApp/src", sourceSet, "kotlin", PACKAGE_DIR);

// Every entry is tagged with the presets it belongs to. `feature` gets all 11
// (the union); `repository` gets just the 5 data/domain files; `screen` gets
// just the 6 presentation+tests+spec files. Filtered by the active preset
// right after definition — nothing below this point sees the untagged list.
const ALL_FILES = [
  { from: path.join(SRC("commonMain"), "domain/model/Item.kt"), to: path.join(SRC("commonMain"), `domain/model/${E}.kt`), presets: ["feature", "repository"] },
  { from: path.join(SRC("commonMain"), "domain/repository/ItemRepository.kt"), to: path.join(SRC("commonMain"), `domain/repository/${E}Repository.kt`), presets: ["feature", "repository"] },
  { from: path.join(SRC("commonMain"), "domain/usecase/GetItemsUseCase.kt"), to: path.join(SRC("commonMain"), `domain/usecase/Get${E}sUseCase.kt`), presets: ["feature", "repository"] },
  { from: path.join(SRC("commonMain"), "data/remote/ItemRepositoryImpl.kt"), to: path.join(SRC("commonMain"), `data/remote/${E}RepositoryImpl.kt`), presets: ["feature", "repository"] },
  { from: path.join(SRC("commonTest"), "testing/fakes/FakeItemRepository.kt"), to: path.join(SRC("commonTest"), `testing/fakes/Fake${E}Repository.kt`), presets: ["feature", "repository"] },
  { from: path.join(SRC("commonMain"), "presentation/home/HomeScreen.kt"), to: path.join(SRC("commonMain"), `presentation/${f}/${F}Screen.kt`), presets: ["feature", "screen"] },
  { from: path.join(SRC("commonMain"), "presentation/home/HomeViewModel.kt"), to: path.join(SRC("commonMain"), `presentation/${f}/${F}ViewModel.kt`), presets: ["feature", "screen"] },
  { from: path.join(SRC("commonTest"), "presentation/home/HomeViewModelTest.kt"), to: path.join(SRC("commonTest"), `presentation/${f}/${F}ViewModelTest.kt`), presets: ["feature", "screen"] },
  { from: path.join(SRC("desktopTest"), "presentation/home/HomeScreenTest.kt"), to: path.join(SRC("desktopTest"), `presentation/${f}/${F}ScreenTest.kt`), presets: ["feature", "screen"] },
  { from: path.join(SRC("desktopTest"), "presentation/home/HomeGoldenTreeTest.kt"), to: path.join(SRC("desktopTest"), `presentation/${f}/${F}GoldenTreeTest.kt`), presets: ["feature", "screen"] },
  { from: path.join(ROOT, "specs/home.spec.md"), to: path.join(ROOT, `specs/${f}.spec.md`), isDefaultSpec: true, presets: ["feature", "screen"] },
];

const FILES = ALL_FILES.filter((file) => file.presets.includes(preset));

// Golden baseline: NOT copied (a feature's golden tree is captured fresh via
// UPDATE_GOLDEN=1, per the skill's step 5), but we still verify the source
// files above genuinely exist before doing anything.
for (const file of FILES) {
  if (!fs.existsSync(file.from)) {
    die(
      `exemplar source file missing: ${path.relative(ROOT, file.from)}\n` +
        "This script must run in an unmodified (or already-featured) create-cmp scaffold " +
        "where the `home` exemplar still exists.",
    );
  }
}

// `screen` preset composes on top of an existing entity's data layer — the
// stamped ViewModel test references Get<E>sUseCase/Fake<E>Repository and the
// screen references <E>, so those must already exist. Validate BEFORE writing
// anything (die early, no half-stamp).
if (preset === "screen") {
  const requiredExisting = [
    path.join(SRC("commonMain"), `domain/usecase/Get${E}sUseCase.kt`),
    path.join(SRC("commonTest"), `testing/fakes/Fake${E}Repository.kt`),
    path.join(SRC("commonMain"), `domain/model/${E}.kt`),
  ];
  const missing = requiredExisting.filter((p) => !fs.existsSync(p));
  if (missing.length > 0) {
    die(
      `entity "${E}" not found — run \`node qa/scaffold-feature.mjs ${E} --preset repository\` first, ` +
        "or use --preset feature to generate the data layer too.",
    );
  }
}

// Name-taken check.
const existing = FILES.filter((file) => fs.existsSync(file.to) && !file.isDefaultSpec).map((file) =>
  path.relative(ROOT, file.to),
);
if (existing.length > 0) {
  die(
    `"${preset === "repository" ? E : featureName}" appears to already exist — these target files are already present:\n` +
      existing.map((p) => `  ${p}`).join("\n"),
  );
}
if (FILES.some((file) => file.isDefaultSpec) && fs.existsSync(path.join(ROOT, `specs/${f}.spec.md`))) {
  die(`specs/${f}.spec.md already exists — feature "${featureName}" appears to already exist.`);
}

// ── Default spec clause set (§6) ────────────────────────────────────────────

function defaultSpec() {
  return `# Spec: ${f}

> Generated by \`scaffold-feature.mjs\` from the \`home\` exemplar shape. Refine the clause
> prose below for ${F}'s real behavior (ids stay fixed) before running the verify lane.

- **${F_UPPER}-01** — Given the ${F} screen opens, When ${f} are being loaded, Then a loading
  indicator is shown and no ${f} are visible.
- **${F_UPPER}-02** — Given the repository returns ${f}, When loading completes, Then the ${f}
  are listed with their title and subtitle, and no error is shown.
- **${F_UPPER}-03** — Given the repository fails, When loading completes, Then a human-readable
  error message is shown (\`${f}_error\`) and no ${f} are visible.
- **${F_UPPER}-04** — Given a load has failed, When the data source recovers and the user
  triggers a reload, Then the error clears and the ${f} render.
- **${F_UPPER}-05** — Given ${f} are listed, When the user taps an item, Then the app navigates
  to that item's detail.
- **${F_UPPER}-06** — Given the ${F} screen renders, When its structure is inspected, Then the
  screen matches its committed golden tree (\`qa/golden/${f}.json\`) — structural regressions
  are intentional, declared changes only.
`;
}

// ── Anchor injection (§5) ────────────────────────────────────────────────────
// Idempotent (skip if the feature's line is already present); fails loudly if
// an anchor marker is missing from the shared file. Each function is a pure
// string -> string transform so multiple injections into the SAME file can be
// chained (each one sees the previous one's output) before a single write.

function injectAtAnchor(content, filePathForErrors, anchorName, lineToInsert) {
  const anchorLine = `// cmp:anchor ${anchorName}`;
  const lines = content.split("\n");
  const anchorLineIdx = lines.findIndex((l) => l.trim() === anchorLine);
  if (anchorLineIdx === -1) {
    die(
      `anchor "${anchorName}" not found in ${path.relative(ROOT, filePathForErrors)}. ` +
        "The template shared file may be out of date with this stamper — " +
        "check for the `// cmp:anchor` marker comments.",
    );
  }

  // Idempotency: if the line is already present verbatim (ignoring leading
  // whitespace), skip — running the stamper twice for the same feature must
  // not duplicate wiring.
  const alreadyPresent = lines.some((l) => l.trim() === lineToInsert.trim());
  if (alreadyPresent) return { content, skipped: true, diff: "" };

  // Match the anchor comment's own indentation so the inserted line sits at
  // the same nesting level as its sibling lines (e.g. inside a `module { }`
  // block, or a `NavHost { }` block).
  const anchorIndent = lines[anchorLineIdx].match(/^\s*/)[0];
  const insertedLine = `${anchorIndent}${lineToInsert}`;
  lines.splice(anchorLineIdx, 0, insertedLine);
  return { content: lines.join("\n"), skipped: false, diff: `${insertedLine}\n` };
}

function injectImport(content, filePathForErrors, importLine) {
  if (content.split("\n").some((l) => l.trim() === importLine.trim())) {
    return { content, skipped: true, diff: "" };
  }

  const diImportsAnchor = "// cmp:anchor di-imports";
  const lines = content.split("\n");
  const anchorLineIdx = lines.findIndex((l) => l.trim() === diImportsAnchor);
  if (anchorLineIdx !== -1) {
    lines.splice(anchorLineIdx, 0, importLine);
    return { content: lines.join("\n"), skipped: false, diff: `${importLine}\n` };
  }

  // Fallback: append after the last existing `import ` line (used by
  // AppNavHost.kt, which has no dedicated imports anchor).
  let lastImportIdx = -1;
  lines.forEach((line, i) => {
    if (line.startsWith("import ")) lastImportIdx = i;
  });
  if (lastImportIdx === -1) {
    die(`no import block found in ${path.relative(ROOT, filePathForErrors)} to inject "${importLine}" near.`);
  }
  lines.splice(lastImportIdx + 1, 0, importLine);
  return { content: lines.join("\n"), skipped: false, diff: `${importLine}\n` };
}

// Applies an ordered list of (content -> result) steps to one file, chaining
// each step's output into the next, and returns the final content plus a flat
// diff log. Reads the file once; the caller writes it once.
function applyInjectionSteps(filePath, steps) {
  if (!fs.existsSync(filePath)) {
    die(`shared file missing: ${path.relative(ROOT, filePath)} — cannot inject wiring for the new feature.`);
  }
  let content = fs.readFileSync(filePath, "utf8");
  const log = [];
  for (const step of steps) {
    const result = step(content);
    content = result.content;
    log.push({ skipped: result.skipped, diff: result.diff });
  }
  return { filePath, content, log };
}

// ── Plan ─────────────────────────────────────────────────────────────────────

const plan = {
  feature: F,
  entity: E,
  package: PACKAGE,
  files: FILES.map((file) => ({
    from: path.relative(ROOT, file.from),
    to: path.relative(ROOT, file.to),
  })),
};

const APP_MODULE = path.join(SRC("commonMain"), "di/AppModule.kt");
const SCREEN_KT = path.join(SRC("commonMain"), "presentation/navigation/Screen.kt");
const APP_NAV_HOST = path.join(SRC("commonMain"), "presentation/navigation/AppNavHost.kt");

// Each step is tagged with the presets it belongs to, same mechanism as
// FILES above: `repository` gets repo+usecase DI (+ imports) only; `screen`
// gets viewModel DI (+ import) + nav route + import only; `feature` gets the
// union (unchanged).
const ALL_INJECTION_PLANS = [
  {
    filePath: APP_MODULE,
    steps: [
      { presets: ["feature", "repository"], apply: (c) => injectImport(c, APP_MODULE, `import ${PACKAGE}.data.remote.${E}RepositoryImpl`) },
      { presets: ["feature", "repository"], apply: (c) => injectImport(c, APP_MODULE, `import ${PACKAGE}.domain.repository.${E}Repository`) },
      { presets: ["feature", "repository"], apply: (c) => injectImport(c, APP_MODULE, `import ${PACKAGE}.domain.usecase.Get${E}sUseCase`) },
      { presets: ["feature", "screen"], apply: (c) => injectImport(c, APP_MODULE, `import ${PACKAGE}.presentation.${f}.${F}ViewModel`) },
      { presets: ["feature", "repository"], apply: (c) => injectAtAnchor(c, APP_MODULE, "di-repositories", `single<${E}Repository> { ${E}RepositoryImpl() }`) },
      { presets: ["feature", "repository"], apply: (c) => injectAtAnchor(c, APP_MODULE, "di-usecases", `factory { Get${E}sUseCase(get()) }`) },
      { presets: ["feature", "screen"], apply: (c) => injectAtAnchor(c, APP_MODULE, "di-viewmodels", `viewModelOf(::${F}ViewModel)`) },
    ],
  },
  {
    filePath: SCREEN_KT,
    steps: [
      { presets: ["feature", "screen"], apply: (c) => injectAtAnchor(c, SCREEN_KT, "screen-objects", `data object ${F} : Screen(Routes.${F_UPPER})`) },
      { presets: ["feature", "screen"], apply: (c) => injectAtAnchor(c, SCREEN_KT, "route-consts", `const val ${F_UPPER} = "${f}"`) },
    ],
  },
  {
    filePath: APP_NAV_HOST,
    steps: [
      { presets: ["feature", "screen"], apply: (c) => injectImport(c, APP_NAV_HOST, `import ${PACKAGE}.presentation.${f}.${F}Screen`) },
      { presets: ["feature", "screen"], apply: (c) => injectAtAnchor(c, APP_NAV_HOST, "nav-destinations", `composable(Screen.${F}.route) { ${F}Screen(onItemClick = {}) }`) },
    ],
  },
];

// Filter steps by active preset; drop any file plan left with zero steps
// (e.g. Screen.kt / AppNavHost.kt entirely for `repository`).
const fileInjectionPlans = ALL_INJECTION_PLANS.map((p) => ({
  filePath: p.filePath,
  steps: p.steps.filter((s) => s.presets.includes(preset)).map((s) => s.apply),
})).filter((p) => p.steps.length > 0);

const fileResults = fileInjectionPlans.map((p) => applyInjectionSteps(p.filePath, p.steps));

plan.injections = fileResults.flatMap((r) =>
  r.log.map((entry) => ({ file: path.relative(ROOT, r.filePath), skipped: entry.skipped, diff: entry.diff })),
);

// ── Dry-run: print the plan and exit ────────────────────────────────────────

const writesSpec = FILES.some((file) => file.isDefaultSpec);
const planLabel =
  preset === "repository"
    ? `entity "${E}"`
    : `feature "${F}" (entity "${E}")`;

if (dryRun) {
  console.log(`Plan for ${planLabel}, package "${PACKAGE}", preset "${preset}":\n`);
  console.log("Files to create:");
  for (const pf of plan.files) console.log(`  ${pf.from}\n    -> ${pf.to}`);
  console.log("\nAnchor injections:");
  if (plan.injections.length === 0) console.log("  (none for this preset)");
  for (const inj of plan.injections) {
    if (inj.skipped) {
      console.log(`  ${inj.file}: (already present, skip)`);
    } else {
      console.log(`  ${inj.file}:`);
      for (const line of inj.diff.split("\n").filter(Boolean)) console.log(`    + ${line}`);
    }
  }
  if (writesSpec) {
    console.log(`\nspecs/${f}.spec.md will be written with default clauses ${F_UPPER}-01..06.`);
  } else {
    console.log("\nNo spec file written by this preset (zero SPEC clauses/tags added).");
  }
  console.log("\n(dry run — nothing written)");
  process.exit(0);
}

// ── Execute ──────────────────────────────────────────────────────────────────

let filesWritten = 0;
for (const file of FILES) {
  const contents = file.isDefaultSpec ? defaultSpec() : applyRename(fs.readFileSync(file.from, "utf8"));
  fs.mkdirSync(path.dirname(file.to), { recursive: true });
  fs.writeFileSync(file.to, contents);
  filesWritten += 1;
}

let injectionsApplied = 0;
for (const result of fileResults) {
  const anyApplied = result.log.some((entry) => !entry.skipped);
  if (!anyApplied) continue;
  fs.writeFileSync(result.filePath, result.content);
  injectionsApplied += result.log.filter((entry) => !entry.skipped).length;
}

console.log(`✓ Scaffolded ${planLabel} [preset: ${preset}] — ${filesWritten} files written, ${injectionsApplied} anchor injections applied.`);
if (writesSpec) {
  console.log(`  specs/${f}.spec.md written with default clauses ${F_UPPER}-01..06 — refine the prose next.`);
} else {
  console.log("  No spec file written by this preset (zero SPEC clauses/tags added).");
}
console.log(`  Next: ${preset === "repository" ? "customize the entity + repository impl, then" : "capture the golden tree, then"} run node qa/verify.mjs.`);
