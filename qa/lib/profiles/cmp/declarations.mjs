// profiles/cmp/declarations.mjs — what a Compose Multiplatform app IS, to the
// harness: where its specs, sources, tests and flows live, and which test
// tiers can observe which kind of promise. Mobile's facts, by definition.
//
// Stage 0 PR 4 (docs/proposals/AGNOSTIC-HARNESS-ARCHITECTURE.md §11.3 step 4;
// docs/NORTH-STAR.md §6). Until this file, qa/lib/spec-coverage.mjs carried
// these as constants — `composeApp/src`, `qa/e2e`, `.kt`, the four tier names,
// which tier satisfies `[tier: device]` — so a Kotlin backend that adopted the
// spine had to fork the scanner to change a path. The scanner is the core's
// (the clause grammar, the citation binding window, both-direction coverage,
// tier-must-observe); THESE are the profile's. The core reads them through
// qa/lib/spec-model.mjs and never names any of them.
//
// Two declarations of the nine (§4.2): #1 layout and #4 tiers. The rest move
// here in the PRs that follow, one at a time.

/**
 * Layout — where the things the scanner reads live, relative to the project
 * root, posix-separated.
 *
 *   specs          the directory of `*.spec.md` files
 *   citationRoots  the trees walked for `// SPEC:` citations
 *   sourceRoots    the trees a human edits — watched by the inner loop, counted
 *                  as activity by the chain view
 *   citationExts   the source files a citation may sit in
 *   buildDir       the stack's build output directory, when it has one — the
 *                  provider's render marker lives there (optional)
 *   flows          flow-shaped citation files: the file IS the test, so a tag
 *                  in one binds to the flow rather than to a declaration
 *                  inside it. Only TOP-LEVEL files in `dir` count — the lane
 *                  runs that directory, and a citation may only come from a
 *                  flow that executes (2026-09-03: four nested hand-written
 *                  flows on the showcase satisfied clauses without running).
 */
export const layout = Object.freeze({
  specs: "specs",
  citationRoots: Object.freeze(["composeApp/src", "qa/e2e"]),
  // The trees a human edits when they work on this app — what the inner-loop
  // watcher watches and what the chain view counts as observed activity.
  // Narrower than citationRoots: qa/e2e is a citation source but not app source.
  sourceRoots: Object.freeze(["composeApp/src"]),
  citationExts: Object.freeze([".kt", ".kts"]),
  flows: Object.freeze({ dir: "qa/e2e", exts: Object.freeze([".yaml", ".yml"]) }),
  // Gradle's output directory for the app module — where the eyes stamp their
  // render marker (qa/lib/lane-markers.mjs) and where KSP's single-owner
  // incremental storage lives (steps-cmp.mjs's coexistence self-heal).
  buildDir: "composeApp/build",
  // Directories the inputs hash and the activity scan skip when there is no
  // git and no .gitignore to ask. The repo's own .gitignore is the truth
  // (git ls-files --exclude-standard, or the walk that reads the same file);
  // this is the floor beneath it, and it is THIS stack's to declare — the core
  // used to hardcode .gradle and .kotlin for every ecosystem.
  ignore: Object.freeze([".gradle", ".kotlin", ".idea"]),
});

/**
 * THE GRAMMAR — what a citation and a test declaration look like in Kotlin.
 *
 * PATTERN: declaration over fallback (the shape tree-sitter uses — one query
 * file per language, named captures, nothing inferred). WHY IT WORKS: a
 * required declaration cannot be silently wrong for the profile that forgot
 * it; until 2026-09-08 this regex lived in the core as a FALLBACK and `cmp`
 * itself never declared one, so any profile that omitted `grammar` was graded
 * with Kotlin's — and nothing said so. HOW IT FAILS: an author copies another
 * language's regex and it binds nothing, or binds the wrong lines. WHAT WE DO:
 * the Rule 0 instrument plants `unboundCitationSource` in THIS language and
 * watches the grammar fail to bind it by name, so a grammar that cannot see
 * its own language is caught before it grades anything; and the coverage
 * diagnostic prints "N markers seen, 0 bound" rather than a quiet PASS.
 */
export const grammar = Object.freeze({
  // TWO citation dialects, both this profile's: Kotlin sources cite with `//`, and
  // the Maestro YAML journeys under qa/e2e cite with `#`. Declared here — the
  // old core fallback happened to accept both, which is how nobody noticed.
  citationMarker: /^(?:\/\/|#)\s*SPEC:/,
  lineComment: /^(?:\/\/|\*)/,
  blockComment: Object.freeze({ open: "/*", close: "*/" }),
  testDeclaration: /@Test\b|\bfun\s+`[^`]+`\s*\(/,
  typeDeclaration: /^(?:@\w+\s+)*(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|data\s+|enum\s+)*(?:class|object|interface)\s+\w+/,
  bindingWindow: 5,
});

/**
 * THE REPORT FORMAT the lane's runners emit, so the core parses what was
 * declared and never assumes. PATTERN: JUnit XML as the lingua franca (pytest
 * --junitxml, go-junit-report, cargo2junit, jest-junit, swift test
 * --xunit-output all emit it). WHY IT WORKS: one parser, every ecosystem, and
 * the declaration is a fact the profile author knows. HOW IT FAILS: a runner
 * emits a dialect (no classname, nested suites) and the parser reads {} — an
 * empty leg that looks like "no tests". WHAT WE DO: the parser refuses an
 * undeclared or unsupported format by name instead of returning {}, and the
 * determinism probe treats an empty outcome map as a refusal, never a pass.
 */
export const reports = Object.freeze({ format: "junit-xml", dir: "composeApp/build/test-results" });

/**
 * The top-level `qa/*.mjs` tools that are THIS profile's, not the spine's, and
 * are therefore never vendored into a repo that does not run this profile.
 *
 * PATTERN: the contribution point — the core holds no list of stacks, and the
 * only thing that knows a tool belongs to Compose is the Compose profile.
 * WHY IT WORKS: the fact is declared once, where it is known, and the installer
 * derives the set (install/portability.mjs) instead of mirroring it by hand in
 * two packages, which is what it did until 2026-09-08. HOW IT FAILS: this
 * profile grows a fifth tool and nobody adds it here, so a foreign repo is
 * vendored a module that names a stack it is not. WHAT WE DO: the import
 * closure is the check — `undeclaredProfileTools` refuses a tool whose own
 * imports reach this profile and that is missing from this list, so the only
 * gap this can leave is a tool that lies in strings alone.
 *
 * These four live at the spine's path for historical reasons; the debt is
 * recorded in test/agnostic-lint.test.mjs, and the exit is that they move into
 * this directory, at which point this declaration becomes redundant and goes.
 */
export const tools = Object.freeze([
  "preview-gallery.mjs",
  "refusal-demo.mjs",
  "scaffold-feature.mjs",
  "walkthrough.mjs",
]);

/**
 * Does this tree belong to THIS profile? PATTERN: Cloud Native Buildpacks'
 * `bin/detect` — each stack recognises itself from marker files and the
 * platform holds no table. WHY IT WORKS: the profile author knows the markers;
 * `harness init` asks every known profile and keeps no language list of its
 * own. HOW IT FAILS: an eager detect claims a tree that is not its (any Gradle
 * repo is not a Compose app), or two profiles claim one tree. WHAT WE DO:
 * evidence is returned and printed, never a bare boolean; two claims refuse
 * and ask; and the claim needs BOTH the build file and the module the stamper
 * writes, not either.
 */
export function detect(root, fs) {
  const has = (rel) => fs.existsSync(`${root}/${rel}`);
  const evidence = [];
  if (has("composeApp/build.gradle.kts")) evidence.push("composeApp/build.gradle.kts");
  if (has("settings.gradle.kts") || has("settings.gradle")) evidence.push("settings.gradle(.kts)");
  const claims = evidence.length === 2;
  return { claims, evidence, reason: claims ? "a Compose Multiplatform app: the composeApp module and a Gradle settings file" : `not a Compose Multiplatform app (found: ${evidence.join(", ") || "neither marker"})` };
}


/**
 * Evidence tiers — the source-set / harness boundaries that decide what a
 * citing test can actually SEE. commonTest and desktopTest run on the host
 * JVM, blind to androidMain and to every OS fact (lifecycle, alarms,
 * notifications, permissions); androidInstrumentedTest runs in the app's
 * process on a device; e2e flows drive the installed app.
 *
 *   names       every tier this profile knows, in ascending observability
 *   hostOnly    tiers that run on the host and cannot observe the target
 *               platform — a clause cited only from these is REPORTED
 *   satisfying  a clause tagged `[tier: X]` must be cited from one of
 *               satisfying[X], or specCoverage FAILS by name (MOTION-13: an
 *               animation "plays once per process start", cited only from a
 *               desktop test with no process lifecycle at all)
 *   journey     the tier whose citation proves a device journey — what a UI
 *               feature needs at least one of to be done (feature-brief)
 *   forFile     the tier a citing file belongs to, from its path
 */
export const tiers = Object.freeze({
  names: Object.freeze(["commonTest", "desktopTest", "androidInstrumentedTest", "e2e"]),
  hostOnly: Object.freeze(["commonTest", "desktopTest"]),
  satisfying: Object.freeze({
    device: Object.freeze(["androidInstrumentedTest", "e2e"]),
    e2e: Object.freeze(["e2e"]),
  }),
  journey: "e2e",
  /**
   * @param {string} rel path relative to the project root (either separator)
   * @returns {"commonTest"|"desktopTest"|"androidInstrumentedTest"|"e2e"|"other"}
   */
  forFile(rel) {
    const p = String(rel).split("\\").join("/");
    if (p.includes("/androidInstrumentedTest/")) return "androidInstrumentedTest";
    if (p.includes("/commonTest/")) return "commonTest";
    if (p.includes("/desktopTest/")) return "desktopTest";
    if (p.startsWith("qa/e2e/")) return "e2e";
    return "other";
  },
});
