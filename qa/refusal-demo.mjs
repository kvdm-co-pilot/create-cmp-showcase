#!/usr/bin/env node
// The refusal demo (C7) — proves the gates REFUSE real violations, each named
// and blocking, rather than merely asserting it in prose.
//
//   node qa/refusal-demo.mjs
//
// Operates on a throwaway scaffolded app in a temp dir (never this template,
// never the calling repo). For each of the four canonical violations
// (docs/M4-ENFORCEMENT-DESIGN.md §B):
//
//   1. inject the violation into the scaffold
//   2. run the narrowest gate that should catch it
//   3. assert the gate verdict is FAIL and the expected clause id appears in
//      the failure output
//   4. assert `qa/receipt-check.mjs` goes INVALID (violation blocks "done")
//   5. `git checkout -- .` to revert before the next violation
//
// Emits a summary table and exits non-zero if ANY assertion fails — i.e. if a
// gate did NOT catch its violation. That is the actual finding this script
// exists to surface; it must never be papered over.

import { execFileSync, spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const TEMPLATE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const REPO_ROOT = path.resolve(TEMPLATE_ROOT, "..");
// Scaffolding the throwaway app: inside the create-cmp dev tree the engine sits at
// ../bin relative to the template — use it directly (fast, offline, tests the local
// code). In a real generated repo that path doesn't exist, so fall back to the
// published CLI via npx (needs network on first run).
const LOCAL_ENGINE = path.join(REPO_ROOT, "bin", "create-cmp.mjs");
const SCAFFOLD_CMD = fs.existsSync(LOCAL_ENGINE)
  ? `node "${LOCAL_ENGINE}"`
  : "npx --yes create-cmp-cli@latest";
const GRADLEW = process.platform === "win32" ? "gradlew.bat" : "./gradlew";

function log(msg) {
  process.stdout.write(`${msg}\n`);
}

function sh(cmd, cwd, opts = {}) {
  const res = spawnSync(cmd, {
    shell: true,
    cwd,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    ...opts,
  });
  return { ok: res.status === 0 && !res.error, status: res.status, out: `${res.stdout ?? ""}${res.stderr ?? ""}` };
}

function git(cwd, args) {
  return execFileSync("git", args, { cwd, encoding: "utf8" }).trim();
}

// ── Step 0: scaffold a throwaway app ────────────────────────────────────────

function scaffold() {
  const tmpBase = fs.mkdtempSync(path.join(os.tmpdir(), "cmp-refusal-demo-"));
  const projectDir = path.join(tmpBase, "RefusalDemo");
  log(`Scaffolding a throwaway app at ${projectDir} (never touching ${TEMPLATE_ROOT})…`);

  const res = sh(
    `${SCAFFOLD_CMD} "${projectDir}" --name RefusalDemo --package com.example.refusaldemo --no-ios --yes --no-verify`,
    REPO_ROOT,
    { timeout: 5 * 60_000 },
  );
  if (!res.ok || !fs.existsSync(projectDir)) {
    throw new Error(`Scaffold failed — cannot run the refusal demo without a scaffolded app:\n${res.out}`);
  }

  git(projectDir, ["init", "-q"]);
  git(projectDir, ["add", "-A"]);
  git(projectDir, ["-c", "user.email=refusal-demo@example.com", "-c", "user.name=Refusal Demo", "commit", "-q", "-m", "init"]);

  log("Establishing a green baseline (node qa/verify.mjs --profile scaffold)…");
  const verify = sh("node qa/verify.mjs --profile scaffold", projectDir, { timeout: 10 * 60_000 });
  if (!verify.ok) {
    throw new Error(`Baseline scaffold did not PASS verify — cannot demonstrate refusal against a broken baseline:\n${verify.out}`);
  }
  git(projectDir, ["add", "-A"]);
  git(projectDir, ["-c", "user.email=refusal-demo@example.com", "-c", "user.name=Refusal Demo", "commit", "-q", "-m", "green baseline"]);

  return projectDir;
}

// ── Selectors — find targets by content, never by hardcoded line numbers ───

// Any *Screen.kt under presentation/ that is not a shared shell piece —
// "the first screen composable" the spec calls for.
function findScreenComposable(projectDir) {
  const presoDir = path.join(projectDir, "composeApp/src/commonMain/kotlin");
  const candidates = walk(presoDir).filter(
    (p) =>
      p.replace(/\\/g, "/").includes("/presentation/") &&
      /Screen\.kt$/.test(p) &&
      !p.endsWith(`${path.sep}Screen.kt`) &&
      !p.replace(/\\/g, "/").includes("/components/") &&
      !p.replace(/\\/g, "/").includes("/navigation/"),
  );
  if (candidates.length === 0) {
    throw new Error(`No screen composable found under ${presoDir} — cannot inject the color-literal violation.`);
  }
  candidates.sort();
  return candidates[0];
}

// A real class under data/ so the injected import resolves to something that exists.
function findDataLayerImport(projectDir) {
  const dataDir = path.join(projectDir, "composeApp/src/commonMain/kotlin");
  const candidates = walk(dataDir).filter((p) => p.replace(/\\/g, "/").includes("/data/") && p.endsWith(".kt"));
  if (candidates.length === 0) {
    throw new Error(`No file found under a data/ package in ${dataDir} — cannot inject the UI-to-data-import violation.`);
  }
  candidates.sort();
  const target = candidates[0];
  const text = fs.readFileSync(target, "utf8");
  const pkgMatch = text.match(/^package\s+([\w.]+)/m);
  const classMatch = text.match(/\bclass\s+(\w+)/);
  if (!pkgMatch || !classMatch) {
    throw new Error(`Could not determine package/class name from ${target} to build a realistic data-layer import.`);
  }
  return `import ${pkgMatch[1]}.${classMatch[1]}`;
}

// A `// SPEC: <ID>`-tagged test in commonTest bound to exactly one clause, so
// removing it orphans that clause cleanly (no cascading orphans).
function findOrphanableSpecTest(projectDir) {
  const testDir = path.join(projectDir, "composeApp/src/commonTest/kotlin");
  const files = walk(testDir).filter((p) => p.endsWith(".kt"));
  const tagRe = /^(\s*)\/\/\s*SPEC:\s*([A-Z][A-Z0-9]*-\d{2,})\s*$/;
  const funcRe = /fun\s+`([^`]+)`\s*\(/;

  const counts = new Map();
  const found = [];
  for (const file of files.sort()) {
    const lines = fs.readFileSync(file, "utf8").split("\n");
    for (let i = 0; i < lines.length; i++) {
      const m = lines[i].match(tagRe);
      if (!m) continue;
      const id = m[2];
      counts.set(id, (counts.get(id) ?? 0) + 1);
      // Find the @Test fun signature within the next few lines.
      let funcLine = -1;
      let funcName = null;
      for (let j = i + 1; j < Math.min(i + 5, lines.length); j++) {
        const fm = lines[j].match(funcRe);
        if (fm) {
          funcLine = j;
          funcName = fm[1];
          break;
        }
      }
      if (funcLine === -1) continue;
      found.push({ file, id, tagLine: i, funcName, indent: m[1] });
    }
  }

  const singlyBound = found.filter((f) => counts.get(f.id) === 1);
  if (singlyBound.length === 0) {
    throw new Error("No singly-bound `// SPEC: <ID>` test found in commonTest — cannot demonstrate a clean orphan without cascading.");
  }
  singlyBound.sort((a, b) => (a.file === b.file ? a.id.localeCompare(b.id) : a.file.localeCompare(b.file)));
  return singlyBound[0];
}

function walk(dir) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(p));
    else out.push(p);
  }
  return out;
}

// ── The four violations ─────────────────────────────────────────────────────

// Finds the byte offset of the opening `{` of the first @Composable function
// body — robust to multi-line parameter lists (unlike a single-line regex).
function firstComposableBodyBraceEnd(text) {
  const composableIdx = text.indexOf("@Composable");
  if (composableIdx === -1) return -1;
  const funIdx = text.indexOf("fun ", composableIdx);
  if (funIdx === -1) return -1;
  const braceIdx = text.indexOf("{", funIdx);
  if (braceIdx === -1) return -1;
  return braceIdx + 1;
}

function injectColorLiteral(projectDir) {
  const target = findScreenComposable(projectDir);
  let text = fs.readFileSync(target, "utf8");
  if (!/import androidx\.compose\.ui\.graphics\.Color/.test(text)) {
    text = text.replace(/^(package .+\n)/, `$1\nimport androidx.compose.ui.graphics.Color`);
  }
  // Insert a literal Color(0x...) use into the composable body — anywhere in
  // a non-theme file trips ARCH-05's source scan.
  const insertAt = firstComposableBodyBraceEnd(text);
  if (insertAt === -1) {
    throw new Error(`Could not locate a @Composable function body in ${target} to inject the color literal.`);
  }
  text = `${text.slice(0, insertAt)}\n    val refusalDemoColor = Color(0xFF123456) // injected violation${text.slice(insertAt)}`;
  fs.writeFileSync(target, text);
  return { file: path.relative(projectDir, target) };
}

function injectUiToDataImport(projectDir) {
  const target = findScreenComposable(projectDir);
  const importLine = findDataLayerImport(projectDir);
  let text = fs.readFileSync(target, "utf8");
  text = text.replace(/^(package .+\n)/, `$1\n${importLine} // injected violation`);
  fs.writeFileSync(target, text);
  return { file: path.relative(projectDir, target), importLine };
}

function injectDeletedSpecTest(projectDir) {
  const found = findOrphanableSpecTest(projectDir);
  const lines = fs.readFileSync(found.file, "utf8").split("\n");

  // Remove the `// SPEC:` tag line and the following @Test + fun signature +
  // its body (up to the matching closing brace at the function's own indent).
  const startLine = found.tagLine;
  let braceDepth = 0;
  let endLine = -1;
  let sawOpenBrace = false;
  for (let i = startLine; i < lines.length; i++) {
    for (const ch of lines[i]) {
      if (ch === "{") {
        braceDepth++;
        sawOpenBrace = true;
      } else if (ch === "}") {
        braceDepth--;
      }
    }
    if (sawOpenBrace && braceDepth === 0) {
      endLine = i;
      break;
    }
  }
  if (endLine === -1) {
    throw new Error(`Could not find the end of test '${found.funcName}' in ${found.file} to remove it cleanly.`);
  }

  const newLines = [...lines.slice(0, startLine), ...lines.slice(endLine + 1)];
  fs.writeFileSync(found.file, newLines.join("\n"));
  return { file: path.relative(projectDir, found.file), clause: found.id, test: found.funcName };
}

function findHomeGoldenTest(projectDir) {
  const testDir = path.join(projectDir, "composeApp/src/desktopTest/kotlin");
  const candidates = walk(testDir).filter((p) => /GoldenTreeTest\.kt$/.test(p));
  if (candidates.length === 0) {
    throw new Error(`No *GoldenTreeTest.kt found under ${testDir} — cannot inject the structural-regression violation.`);
  }
  candidates.sort();
  return candidates[0];
}

function injectStructuralRegression(projectDir) {
  const goldenTest = findHomeGoldenTest(projectDir);
  const testText = fs.readFileSync(goldenTest, "utf8");
  const screenMatch = testText.match(/setContent\s*\{\s*MaterialTheme\s*\{\s*(\w+)\(/);
  if (!screenMatch) {
    throw new Error(`Could not locate the screen composable rendered by ${goldenTest} to mutate its structure.`);
  }
  const screenName = screenMatch[1];
  const screenDir = path.join(projectDir, "composeApp/src/commonMain/kotlin");
  const screenFile = walk(screenDir).find((p) => p.endsWith(`${path.sep}${screenName}.kt`));
  if (!screenFile) {
    throw new Error(`Could not find source file for composable '${screenName}' referenced by ${goldenTest}.`);
  }

  let text = fs.readFileSync(screenFile, "utf8");
  // Mutate the structure without touching UPDATE_GOLDEN: add an extra text
  // node right after the title's Text(...) call closes, so the semantics
  // tree gains a sibling child the committed baseline does not have. Find
  // the *matching* closing paren of that call (brace/paren-depth walk) —
  // a lazy regex would splice mid-call and produce invalid Kotlin.
  const titleCallStart = text.indexOf('text = "Home"');
  if (titleCallStart === -1) {
    throw new Error(`Could not find the Home title Text() node in ${screenFile} to mutate structurally.`);
  }
  const callOpenParen = text.lastIndexOf("Text(", titleCallStart);
  if (callOpenParen === -1) {
    throw new Error(`Could not find the opening Text( for the Home title in ${screenFile}.`);
  }
  let depth = 0;
  let callCloseParen = -1;
  for (let i = callOpenParen; i < text.length; i++) {
    if (text[i] === "(") depth++;
    else if (text[i] === ")") {
      depth--;
      if (depth === 0) {
        callCloseParen = i;
        break;
      }
    }
  }
  if (callCloseParen === -1) {
    throw new Error(`Could not find the matching close paren for the Home title Text() in ${screenFile}.`);
  }
  const insertAt = callCloseParen + 1;
  text = `${text.slice(0, insertAt)}\n        Text(text = "Injected structural regression")${text.slice(insertAt)}`;
  fs.writeFileSync(screenFile, text);
  return { file: path.relative(projectDir, screenFile), screen: screenName };
}

// ── Gate runners ─────────────────────────────────────────────────────────────

// Gradle's `--console=plain` prints only the test-class/method + a
// `java.lang.AssertionError at X.kt:NN` pointer to the console — never the
// actual assertion message (the clause-citing text lives in the JUnit XML
// report's <failure message="..."> attribute). Read it back and fold it into
// the output the assertion checks so the demo — and its evidence doc — quote
// the real gate-authored failure text, not a stack-trace stub.
function junitFailureMessages(projectDir) {
  const dir = path.join(projectDir, "composeApp/build/test-results/desktopTest");
  if (!fs.existsSync(dir)) return "";
  const messages = [];
  for (const f of fs.readdirSync(dir).filter((f) => f.startsWith("TEST-") && f.endsWith(".xml"))) {
    const xml = fs.readFileSync(path.join(dir, f), "utf8");
    for (const m of xml.matchAll(/<failure message="([^"]*)"/g)) {
      messages.push(
        m[1]
          .replace(/&#10;/g, "\n")
          .replace(/&quot;/g, '"')
          .replace(/&lt;/g, "<")
          .replace(/&gt;/g, ">")
          .replace(/&amp;/g, "&"),
      );
    }
  }
  return messages.join("\n---\n");
}

function runGradleTestGate(projectDir, testsFilter) {
  const res = sh(`${GRADLEW} :composeApp:desktopTest --tests "${testsFilter}" --console=plain`, projectDir, { timeout: 10 * 60_000 });
  const junit = junitFailureMessages(projectDir);
  return { ...res, out: junit ? `${res.out}\n\n--- JUnit failure messages ---\n${junit}` : res.out };
}

function runConformance(projectDir) {
  return runGradleTestGate(projectDir, "*ArchitectureConformanceTest");
}

function runGoldenTrees(projectDir) {
  return runGradleTestGate(projectDir, "*GoldenTreeTest");
}

function runSpecCoverage(projectDir) {
  return sh("node qa/verify.mjs --profile scaffold --json", projectDir, { timeout: 10 * 60_000 });
}

function runReceiptCheck(projectDir) {
  return sh("node qa/receipt-check.mjs", projectDir);
}

function revert(projectDir) {
  git(projectDir, ["checkout", "--", "."]);
  git(projectDir, ["clean", "-fd", "--", "composeApp", "qa"]);
}

// ── The four violation cases ─────────────────────────────────────────────────

const VIOLATIONS = [
  {
    id: 1,
    name: "Hardcoded color literal",
    expectedClause: "ARCH-05",
    gate: "conformance",
    inject: injectColorLiteral,
    run: runConformance,
  },
  {
    id: 2,
    name: "UI→data import",
    expectedClause: "ARCH-01",
    gate: "conformance",
    inject: injectUiToDataImport,
    run: runConformance,
  },
  {
    id: 3,
    name: "Deleted / weakened spec test",
    expectedClause: null, // determined at inject time (the clause id the removed test cited)
    gate: "specCoverage",
    inject: injectDeletedSpecTest,
    run: runSpecCoverage,
  },
  {
    id: 4,
    name: "Undeclared structural regression",
    expectedClause: "HOME-06",
    gate: "goldenTrees",
    inject: injectStructuralRegression,
    run: runGoldenTrees,
  },
];

function main() {
  const projectDir = scaffold();
  const results = [];

  for (const v of VIOLATIONS) {
    log(`\n── Violation ${v.id}: ${v.name} ──`);
    let injectInfo;
    try {
      injectInfo = v.inject(projectDir);
    } catch (err) {
      results.push({ ...v, expectedClause: v.expectedClause ?? "?", verdict: "ERROR", assertionPass: false, detail: `injection failed: ${err.message}` });
      log(`  INJECTION FAILED: ${err.message}`);
      revert(projectDir);
      continue;
    }

    const expectedClause = v.expectedClause ?? injectInfo.clause;
    log(`  Injected into ${injectInfo.file}${injectInfo.test ? ` (removed test: ${injectInfo.test}, clause ${injectInfo.clause})` : ""}`);
    log(`  Running gate: ${v.gate}…`);

    const gateRes = v.run(projectDir);
    const gateFailed = !gateRes.ok;
    const clauseNamed = gateRes.out.includes(`[${expectedClause}]`) || gateRes.out.includes(expectedClause);

    const receiptRes = runReceiptCheck(projectDir);
    const receiptBlocked = !receiptRes.ok;

    const assertionPass = gateFailed && clauseNamed && receiptBlocked;

    results.push({
      ...v,
      expectedClause,
      verdict: gateFailed ? "FAIL" : "PASS",
      clauseNamed,
      receiptBlocked,
      assertionPass,
      gateOutputExcerpt: extractRelevantLines(gateRes.out, expectedClause),
    });

    log(`  Gate verdict: ${gateFailed ? "FAIL" : "PASS (did not catch it!)"}`);
    log(`  Clause "${expectedClause}" named in output: ${clauseNamed ? "yes" : "NO"}`);
    log(`  receipt-check blocked (non-zero exit): ${receiptBlocked ? "yes" : "NO"}`);
    log(`  Assertion: ${assertionPass ? "PASS" : "FAIL"}`);

    revert(projectDir);
  }

  // ── Summary ────────────────────────────────────────────────────────────────
  log("\n\n=== Refusal demo summary (C7) ===\n");
  const header = "# | Violation                          | Expected clause | Observed verdict | Assertion";
  log(header);
  log("-".repeat(header.length));
  for (const r of results) {
    log(
      `${String(r.id).padEnd(1)} | ${r.name.padEnd(34)} | ${String(r.expectedClause).padEnd(15)} | ${String(r.verdict).padEnd(17)} | ${r.assertionPass ? "PASS" : "FAIL"}`,
    );
  }

  const allPass = results.every((r) => r.assertionPass);
  log(`\n${allPass ? results.length : results.filter((r) => r.assertionPass).length}/${results.length} assertions PASS`);

  if (!allPass) {
    log("\nFAILING ASSERTIONS — a gate did NOT catch its violation as expected:");
    for (const r of results.filter((r) => !r.assertionPass)) {
      log(`  #${r.id} ${r.name}: ${r.detail ?? `verdict=${r.verdict} clauseNamed=${r.clauseNamed} receiptBlocked=${r.receiptBlocked}`}`);
    }
  }

  log("\n--- Real gate failure messages observed ---");
  for (const r of results) {
    if (r.gateOutputExcerpt) {
      log(`\n[#${r.id} ${r.name} — ${r.gate}]`);
      log(r.gateOutputExcerpt);
    }
  }

  fs.rmSync(path.dirname(projectDir), { recursive: true, force: true });

  process.exit(allPass ? 0 : 1);
}

function extractRelevantLines(out, clause) {
  const lines = out.split("\n");
  const idx = lines.findIndex((l) => l.includes(clause));
  if (idx === -1) return lines.filter((l) => /FAIL|error/i.test(l)).slice(0, 8).join("\n");
  return lines.slice(Math.max(0, idx - 2), idx + 10).join("\n");
}

try {
  main();
} catch (err) {
  process.stderr.write(`\nrefusal-demo aborted: ${err.message}\n`);
  process.exit(1);
}
