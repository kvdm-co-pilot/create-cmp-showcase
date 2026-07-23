// arch-doc.mjs — the ARCHITECTURE.md walker (Wave B,
// docs/proposals/architecture-document-standard.md §4.2/§6). Regenerates the
// `<!-- cmp:generated <section> -->` marker blocks Wave A seeded in
// docs/ARCHITECTURE.md from a REAL walk of the tree — never hand-maintained,
// never fabricated. Pure functions, no deps beyond node builtins, mirroring
// the rest of qa/lib/'s style (approvals.mjs, tree.mjs).
//
// Four sections, each a pure `(root) => markdown` generator:
//   - expect-actual-table   — every top-level `expect` in commonMain, matched
//                              against its `actual` counterpart (by declared
//                              name, ignoring receiver) in androidMain/iosMain/
//                              desktopMain. An expect with no on-disk actual is
//                              reported honestly — never invented — with a
//                              specific note when the expect's own declaration
//                              names a known compiler-plugin marker type
//                              (`RoomDatabaseConstructor`: Room's KSP-generated
//                              actual never appears in source).
//   - layer-file-inventory  — every `.kt` file under presentation/domain/data/
//                              core/di, grouped by source set (commonMain +
//                              the three platform actual source sets), so a
//                              new component or a new platform actual shows up
//                              automatically instead of rotting a hand-counted
//                              "(12 files)" note.
//   - adr-index             — docs/adr/*.md (excluding the template), parsed
//                              for `# ADR-NNNN: Title` + `- **Status:** ...`.
//   - glossary               — NOT a term-extraction: specs/intent.md carries
//                              a `## Glossary` section the genesis intent
//                              conversation (conversation 0) fills with the
//                              app's domain nouns; this generator mechanically
//                              LIFTS that section's body verbatim (never
//                              parses prose for nouns itself — that would be
//                              guessing at vocabulary, the thing this file's
//                              header promises never to do).
//
// The marker grammar (`<!-- cmp:generated ID -->` … `<!-- /cmp:generated -->`)
// is generic: regenerateArchDoc() rewrites the body of every marker it finds
// whose id has a registered generator, leaves an unrecognized id untouched
// (and flags it — never silently drops a marker), and reports any registered
// section whose marker is missing from the doc entirely. Everything outside a
// marker is byte-for-byte untouched — the reconciliation rule this file exists
// to enforce: "marker content is the derivable core; judgment lives outside
// the markers."
//
// Consumers: qa/arch-doc.mjs (the CLI — thin shell, mirrors qa/approve.mjs's
// split), qa/verify.mjs (the `archDoc` lane step).

import fs from "node:fs";
import path from "node:path";

export const ARCH_DOC_REL_PATH = "docs/ARCHITECTURE.md";
export const ADR_DIR_REL_PATH = "docs/adr";
export const INTENT_REL_PATH = "specs/intent.md";

// The shipped platform source sets the layer/expect-actual walks cover —
// deliberately excludes commonTest/androidDebug/androidRelease/desktopTest:
// those are QA/dev-time source sets, not part of the documented layer model
// (§4/§5 of the doc describe the app's shipped shape).
const PLATFORM_SOURCE_SETS = ["androidMain", "iosMain", "desktopMain"];
const SOURCE_SET_KOTLIN_ROOT = {
  commonMain: "composeApp/src/commonMain/kotlin",
  androidMain: "composeApp/src/androidMain/kotlin",
  iosMain: "composeApp/src/iosMain/kotlin",
  desktopMain: "composeApp/src/desktopMain/kotlin",
};

// The five documented layers (§5 — `core` promoted to an official layer by
// Wave A / ARCH-10), in the order the doc's layer-model diagram lists them.
const LAYERS = ["presentation", "domain", "data", "core", "di"];

function toPosix(p) {
  return p.split(path.sep).join("/");
}

/**
 * Find the app's kotlin package directory under commonMain: a real fs walk
 * for the first directory that itself contains a `presentation` subdirectory
 * (every create-cmp scaffold has `presentation` as a direct child of the
 * package dir — same technique inspector/mcp/src/lib/architecture.mjs uses
 * for the console's layer map, duplicated here on purpose: this file ships
 * inside a generated project with zero cross-package imports).
 * @param {string} root
 * @returns {{ packageDir: string, packageRel: string } | null} packageRel is
 *   POSIX-style, relative to composeApp/src/commonMain/kotlin.
 */
export function findPackageDir(root) {
  const kotlinRoot = path.join(root, SOURCE_SET_KOTLIN_ROOT.commonMain);
  if (!fs.existsSync(kotlinRoot)) return null;
  let found = null;
  (function walk(dir) {
    if (found) return;
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    if (entries.some((e) => e.isDirectory() && e.name === "presentation")) {
      found = dir;
      return;
    }
    for (const e of entries) {
      if (found) return;
      if (e.isDirectory()) walk(path.join(dir, e.name));
    }
  })(kotlinRoot);
  if (!found) return null;
  return { packageDir: found, packageRel: toPosix(path.relative(kotlinRoot, found)) };
}

/** Every `.kt` file under `dir` (recursive), as sorted POSIX-relative paths. */
function walkKotlinFiles(dir) {
  const out = [];
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    const abs = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walkKotlinFiles(abs).map((f) => `${e.name}/${f}`));
    else if (e.name.endsWith(".kt")) out.push(e.name);
  }
  return out.sort((a, b) => a.localeCompare(b));
}

// ── expect/actual ────────────────────────────────────────────────────────────

// Column-0 anchored on purpose: nested members of an `expect class { ... }`
// body (e.g. `    actual val isOnline: ...`) are indented and must NOT be
// mistaken for a second top-level expect/actual pair. Modifiers between the
// expect/actual keyword and the declaration kind (`expect suspend fun`,
// `expect enum class`, `actual data class`, …) are consumed so those
// declarations are not silently omitted from a table that promises "every
// top-level expect".
const DECL_RE =
  /^(?:public\s+|internal\s+|private\s+)?(expect|actual)\s+(?:(?:suspend|abstract|open|sealed|data|enum|annotation|value|inline|external)\s+)*(class|fun|object|val|var|interface)\s+(?:([A-Za-z_][\w.]*)\.)?([A-Za-z_]\w*)/;

/**
 * Scan every `.kt` file under `dir` for top-level expect/actual declarations.
 * @returns {Array<{ keyword: "expect"|"actual", kind: string, receiver: (string|null), name: string, file: string, rawLine: string }>}
 */
function scanDeclarations(dir) {
  const out = [];
  for (const relFile of walkKotlinFiles(dir)) {
    const lines = fs.readFileSync(path.join(dir, relFile), "utf8").split("\n");
    for (const line of lines) {
      const m = line.match(DECL_RE);
      if (!m) continue;
      out.push({ keyword: m[1], kind: m[2], receiver: m[3] ?? null, name: m[4], file: relFile, rawLine: line.trim() });
    }
  }
  return out;
}

function displayName({ kind, receiver, name }) {
  const base = receiver ? `${receiver}.${name}` : name;
  return kind === "fun" ? `${base}()` : base;
}

/**
 * The expect/actual boundary table (§4): every top-level `expect` in
 * commonMain, one row each, matched against its `actual` (by declared name,
 * receiver ignored) in every platform source set. A platform column reads
 * `_(no actual found in source)_` when nothing on disk matches — EXCEPT when
 * the expect's own declaration line names `RoomDatabaseConstructor` (Room's
 * KSP compiler plugin generates that actual at build time; it never exists as
 * a source file), which gets the specific, honest note instead of a bare gap.
 * @param {string} root
 * @returns {string} markdown table (no trailing newline)
 */
export function generateExpectActualTable(root) {
  const pkg = findPackageDir(root);
  if (!pkg) {
    return "_No `presentation/` package directory found under `composeApp/src/commonMain/kotlin` — nothing to derive an expect/actual table from._";
  }

  const expects = scanDeclarations(pkg.packageDir).filter((d) => d.keyword === "expect");
  if (expects.length === 0) {
    return "_No top-level `expect` declarations found in `commonMain`._";
  }

  const actualsByPlatform = {};
  for (const platform of PLATFORM_SOURCE_SETS) {
    const platformDir = path.join(root, SOURCE_SET_KOTLIN_ROOT[platform], pkg.packageRel);
    const actuals = fs.existsSync(platformDir) ? scanDeclarations(platformDir).filter((d) => d.keyword === "actual") : [];
    const byName = new Map();
    for (const a of actuals) if (!byName.has(a.name)) byName.set(a.name, a.file);
    actualsByPlatform[platform] = byName;
  }

  const rows = [...expects].sort((a, b) => a.name.localeCompare(b.name));

  const header = `| Declaration | commonMain (expect) | ${PLATFORM_SOURCE_SETS.map((p) => `${p} (actual)`).join(" | ")} |`;
  const divider = `|${"---|".repeat(2 + PLATFORM_SOURCE_SETS.length)}`;
  const lines = [header, divider];
  for (const decl of rows) {
    const cells = PLATFORM_SOURCE_SETS.map((platform) => {
      const file = actualsByPlatform[platform].get(decl.name);
      if (file) return `\`${file}\``;
      if (/RoomDatabaseConstructor/.test(decl.rawLine)) return "_(Room KSP-generated — no actual in source)_";
      return "_(no actual found in source)_";
    });
    lines.push(`| \`${displayName(decl)}\` | \`${decl.file}\` | ${cells.join(" | ")} |`);
  }
  return lines.join("\n");
}

// ── layer file inventory ────────────────────────────────────────────────────

/**
 * The layer-model file inventory (§5): every `.kt` file under each of the
 * five documented layers (`presentation`/`domain`/`data`/`core`/`di`), one
 * bullet per layer, grouped by source set (commonMain plus whichever platform
 * source sets actually have files there — e.g. `data/local`'s platform
 * `DatabaseBuilder.*.kt` actuals). A layer with zero files anywhere is
 * reported as such, never omitted.
 * @param {string} root
 * @returns {string} markdown bullet list (no trailing newline)
 */
export function generateLayerFileInventory(root) {
  const pkg = findPackageDir(root);
  if (!pkg) {
    return "_No `presentation/` package directory found under `composeApp/src/commonMain/kotlin` — nothing to derive a layer inventory from._";
  }

  const sourceSets = ["commonMain", ...PLATFORM_SOURCE_SETS];
  const lines = [];
  for (const layer of LAYERS) {
    const groups = [];
    for (const sourceSet of sourceSets) {
      const layerDir = path.join(root, SOURCE_SET_KOTLIN_ROOT[sourceSet], pkg.packageRel, layer);
      if (!fs.existsSync(layerDir)) continue;
      const files = walkKotlinFiles(layerDir);
      if (files.length === 0) continue;
      groups.push(`${sourceSet}: ${files.map((f) => `\`${f}\``).join(", ")}`);
    }
    lines.push(groups.length > 0 ? `- \`${layer}/\` — ${groups.join("; ")}` : `- \`${layer}/\` — _(no files found)_`);
  }
  return lines.join("\n");
}

// ── ADR index ────────────────────────────────────────────────────────────────

const ADR_TITLE_RE = /^#\s*ADR-(\d+):\s*(.+?)\s*$/m;
const ADR_STATUS_RE = /^-\s*\*\*Status:\*\*\s*(.+?)\s*$/m;

/**
 * The ADR index (§8): every `docs/adr/NNNN-*.md` file (the ADR template
 * itself, `template.md`, is excluded — it is not a decision), parsed for its
 * `# ADR-NNNN: Title` heading and `- **Status:** ...` line, sorted by ADR
 * number. A file that doesn't match the expected heading grammar is skipped
 * with an honest note rather than a fabricated title.
 * @param {string} root
 * @returns {string} markdown table (no trailing newline)
 */
export function generateAdrIndex(root) {
  const adrDir = path.join(root, ADR_DIR_REL_PATH);
  if (!fs.existsSync(adrDir)) {
    return `_No ${ADR_DIR_REL_PATH}/ directory found._`;
  }
  const files = fs
    .readdirSync(adrDir, { withFileTypes: true })
    .filter((e) => e.isFile() && e.name.endsWith(".md") && e.name !== "template.md" && /^\d{4}-/.test(e.name))
    .map((e) => e.name)
    .sort((a, b) => a.localeCompare(b));

  if (files.length === 0) {
    return "_No ADRs recorded yet._";
  }

  const rows = [];
  const unparsed = [];
  for (const file of files) {
    const content = fs.readFileSync(path.join(adrDir, file), "utf8");
    const titleMatch = content.match(ADR_TITLE_RE);
    if (!titleMatch) {
      unparsed.push(file);
      continue;
    }
    const statusMatch = content.match(ADR_STATUS_RE);
    rows.push({
      id: Number.parseInt(titleMatch[1], 10),
      idLabel: titleMatch[1],
      file,
      title: titleMatch[2],
      status: statusMatch ? statusMatch[1] : "_(no Status line found)_",
    });
  }
  rows.sort((a, b) => a.id - b.id);

  const lines = ["| ADR | Title | Status |", "|---|---|---|"];
  for (const r of rows) {
    lines.push(`| [${r.idLabel}](./adr/${r.file}) | ${r.title} | ${r.status} |`);
  }
  for (const file of unparsed) {
    lines.push(`\n_${file} does not match the \`# ADR-NNNN: Title\` heading grammar — skipped, not fabricated._`);
  }
  return lines.join("\n");
}

// ── glossary ─────────────────────────────────────────────────────────────────

// The exact placeholder text specs/intent.md ships every unfilled section
// with (qa/scaffold's seed) — presence in a section's body means that
// section hasn't been filled in by the genesis intent interview yet.
const INTENT_PLACEHOLDER_MARKER = "_not yet captured";

// specs/intent.md's `## Glossary` heading — a level-2 heading, matched at
// column 0 so it can't fire on a nested heading inside another section's
// body. The NEXT level-2 heading (or EOF) closes the section.
const GLOSSARY_HEADING_RE = /^##\s+Glossary\s*$/m;
const NEXT_HEADING_RE = /^##\s+\S/m;

/**
 * The domain glossary (§8). specs/intent.md ships a `## Glossary` section
 * that the genesis intent conversation (conversation 0) fills with the
 * app's domain nouns (its own vocabulary — "their feature names", per
 * GENESIS-FLOW-DESIGN.md). This generator does NOT extract terms from
 * prose itself (that would be guessing); it mechanically LIFTS that
 * section's body verbatim, the same "derived, not fabricated" contract
 * every other section in this file keeps. Honest about every state: the
 * file missing, the section missing (an older intent.md pre-dating this
 * section), the section present but still carrying the unfilled
 * placeholder, and the section genuinely filled in.
 * @param {string} root
 * @returns {string} markdown/prose (no trailing newline)
 */
export function generateGlossary(root) {
  const intentPath = path.join(root, INTENT_REL_PATH);
  if (!fs.existsSync(intentPath)) {
    return `_Domain glossary — \`${INTENT_REL_PATH}\` not found, so there is nothing to seed it from yet._`;
  }
  const content = fs.readFileSync(intentPath, "utf8");

  const headingMatch = content.match(GLOSSARY_HEADING_RE);
  if (!headingMatch) {
    return (
      `_Domain glossary — [\`${INTENT_REL_PATH}\`](../${INTENT_REL_PATH}) has no \`## Glossary\` ` +
      "section to lift from yet — nothing derived._"
    );
  }

  const afterHeading = content.slice(headingMatch.index + headingMatch[0].length);
  const nextHeadingMatch = afterHeading.match(NEXT_HEADING_RE);
  const body = (nextHeadingMatch ? afterHeading.slice(0, nextHeadingMatch.index) : afterHeading).trim();

  if (body.length === 0 || body.includes(INTENT_PLACEHOLDER_MARKER)) {
    return (
      `_Domain glossary — seeded from the \`## Glossary\` section of [\`${INTENT_REL_PATH}\`](../${INTENT_REL_PATH}) ` +
      "once the genesis intent interview fills it in; empty on a fresh scaffold._"
    );
  }

  return (
    `_Lifted verbatim from the \`## Glossary\` section of [\`${INTENT_REL_PATH}\`](../${INTENT_REL_PATH}) — edit it ` +
    `there, not here; this block is regenerated from it.\n\n${body}`
  );
}

// ── the marker grammar + regeneration ───────────────────────────────────────

export const SECTIONS = [
  { id: "expect-actual-table", label: "Expect/actual boundary table", generate: generateExpectActualTable },
  { id: "layer-file-inventory", label: "Layer file inventory", generate: generateLayerFileInventory },
  { id: "adr-index", label: "ADR index", generate: generateAdrIndex },
  { id: "glossary", label: "Domain glossary", generate: generateGlossary },
];
export const SECTION_IDS = SECTIONS.map((s) => s.id);

const MARKER_BLOCK_RE = /<!-- cmp:generated ([a-zA-Z0-9_-]+) -->\n([\s\S]*?)<!-- \/cmp:generated -->/g;

/**
 * Strip every `cmp:generated ID` marker block's BODY from `content`,
 * replacing it with nothing but leaving the marker pair itself (id and all)
 * in place — the doc's STRUCTURE (which sections exist, in what order) still
 * counts toward whatever a caller does with the result, only the mechanically
 * regenerated CONTENT is removed.
 *
 * This is the ONE definition of "generated" for this doc — reused (never
 * forked) by qa/lib/approvals.mjs's `architecture` artifact hash basis
 * (docs/proposals/architecture-document-standard.md §4.4): regenerating a
 * section (`node qa/arch-doc.mjs`) must never invalidate that human approval,
 * only an authored-prose edit may.
 * @param {string} content
 * @returns {string}
 */
export function stripGeneratedSections(content) {
  return content.replace(MARKER_BLOCK_RE, (_whole, id) => `<!-- cmp:generated ${id} -->\n<!-- /cmp:generated -->`);
}

/**
 * Regenerate every `cmp:generated` marker section in `docRelPath` (default
 * docs/ARCHITECTURE.md) from a real walk of the tree at `root` right now.
 * Rewrites ONLY the bytes between a recognized marker pair — everything else
 * in the file (prose, headings, unrecognized markers) passes through
 * byte-for-byte untouched.
 * @param {string} root
 * @param {{ docRelPath?: string }} [options]
 * @returns {{ ok: true, content: string, changed: boolean, changedSections: string[], missingSections: string[], unknownSections: string[] } | { ok: false, reason: string }}
 */
export function regenerateArchDoc(root, options = {}) {
  const docRelPath = options.docRelPath ?? ARCH_DOC_REL_PATH;
  const docPath = path.join(root, docRelPath);
  if (!fs.existsSync(docPath)) {
    return { ok: false, reason: `${docRelPath} not found` };
  }
  const original = fs.readFileSync(docPath, "utf8");
  const byId = new Map(SECTIONS.map((s) => [s.id, s]));
  const found = new Set();
  const changedSections = [];
  const unknownSections = [];

  const rewritten = original.replace(MARKER_BLOCK_RE, (whole, id, body) => {
    found.add(id);
    const section = byId.get(id);
    if (!section) {
      unknownSections.push(id);
      return whole; // no generator registered for this id — never fabricate one
    }
    const generated = `${section.generate(root).replace(/\s+$/, "")}\n`;
    if (generated !== body) changedSections.push(id);
    return `<!-- cmp:generated ${id} -->\n${generated}<!-- /cmp:generated -->`;
  });

  const missingSections = SECTION_IDS.filter((id) => !found.has(id));

  return {
    ok: true,
    content: rewritten,
    changed: rewritten !== original,
    changedSections,
    missingSections,
    unknownSections,
  };
}

/**
 * Regenerate and write `docRelPath` in place. No-op (returns `wrote: false`)
 * when nothing would change.
 * @param {string} root
 * @param {{ docRelPath?: string }} [options]
 */
export function writeArchDoc(root, options = {}) {
  const result = regenerateArchDoc(root, options);
  if (!result.ok) return result;
  if (!result.changed) return { ...result, wrote: false };
  const docPath = path.join(root, options.docRelPath ?? ARCH_DOC_REL_PATH);
  fs.writeFileSync(docPath, result.content);
  return { ...result, wrote: true };
}
