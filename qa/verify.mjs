#!/usr/bin/env node
// The verify lane — this project's single verification gate.
//
//   node qa/verify.mjs [--profile scaffold|local|ci] [--json]
//
// Runs every verification step this project carries, aggregates a typed
// PASS/FAIL verdict, and writes the evidence receipt to qa/evidence/latest.json.
// The receipt is COMMITTED with your change (see CLAUDE.md — a change is not
// done without it). Binary artifacts under qa-artifacts/ are never committed;
// the receipt references them by path + sha256.
//
// Verdicts per step: PASS | FAIL | SKIP. The lane verdict is PASS iff no step
// FAILed. SKIPs are recorded with reasons — green-with-gaps is visible, never
// silent. Exit code: 0 = PASS, 1 = FAIL.
//
// Profiles:
//   scaffold — spec coverage + build + unit tests (what `create-cmp --verify` proves at stamp time)
//   local    — everything; device-dependent steps SKIP when no device is attached
//   ci       — everything; SKIPs are recorded so the pipeline stays honest

import { execSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { computeInputsHash } from "./lib/inputs-hash.mjs";
import { compareTokenDrift } from "./lib/token-drift.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const EVIDENCE_DIR = path.join(ROOT, "qa", "evidence");
const ARTIFACTS_DIR = path.join(ROOT, "qa-artifacts");

const args = process.argv.slice(2);
const profile = args.includes("--profile") ? args[args.indexOf("--profile") + 1] : "local";
const asJson = args.includes("--json");

const GRADLEW = process.platform === "win32" ? "gradlew.bat" : "./gradlew";

function sh(cmd, opts = {}) {
  const started = Date.now();
  // maxBuffer: first-run Gradle output easily exceeds spawnSync's 1MB default,
  // which would surface as a bogus FAIL (status null / ENOBUFS).
  const res = spawnSync(cmd, { shell: true, cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024, ...opts });
  const ok = res.status === 0 && !res.error;
  return { ok, status: res.status, error: res.error?.message, out: `${res.stdout ?? ""}${res.stderr ?? ""}`, durationMs: Date.now() - started };
}

function tryGit(cmd) {
  try {
    return execSync(`git ${cmd}`, { cwd: ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  } catch {
    return null;
  }
}

function junitSummary(dir) {
  if (!fs.existsSync(dir)) return null;
  let tests = 0, failures = 0, errors = 0, skipped = 0;
  for (const f of fs.readdirSync(dir).filter((f) => f.startsWith("TEST-") && f.endsWith(".xml"))) {
    const xml = fs.readFileSync(path.join(dir, f), "utf8");
    const m = xml.match(/<testsuite[^>]*tests="(\d+)"[^>]*skipped="(\d+)"[^>]*failures="(\d+)"[^>]*errors="(\d+)"/);
    if (m) {
      tests += Number(m[1]);
      skipped += Number(m[2]);
      failures += Number(m[3]);
      errors += Number(m[4]);
    }
  }
  return { tests, failures, errors, skipped };
}

function deviceAttached() {
  const res = sh("adb devices", { timeout: 10_000 });
  if (!res.ok) return false;
  return res.out.split("\n").slice(1).some((l) => /\tdevice$/.test(l.trim().replace(/\s+/g, "\t")));
}

// Recursive directory walker (no glob dependency) — returns files under `dir`
// whose name ends with one of `exts`.
function walkFiles(dir, exts) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walkFiles(p, exts));
    else if (exts.some((ext) => entry.name.endsWith(ext))) out.push(p);
  }
  return out;
}

// ── Steps ──────────────────────────────────────────────────────────────────
// Each returns { name, verdict, reason?, durationMs, details? }. Failure
// reasons are worded for an AI collaborator to act on.

// Spec ↔ test drift gate — pure Node, no Gradle. Parses clause ids out of
// specs/*.spec.md (live: `- **ID**`, withdrawn: `- ~~**ID**~~`, exempt from
// coverage) and `// SPEC:` / `# SPEC:` citation tags out of composeApp/src and
// qa/e2e, then fails on orphans in either direction.
function stepSpecCoverage() {
  const started = Date.now();
  const specsDir = path.join(ROOT, "specs");
  if (!fs.existsSync(specsDir)) {
    return { name: "specCoverage", verdict: "SKIP", reason: "no specs/ directory in this project", durationMs: Date.now() - started };
  }

  const CLAUSE_LINE_RE = /^-\s+(~~)?\*\*([A-Z][A-Z0-9]*-\d{2,})\*\*/;
  const clauses = new Map(); // id -> { file, withdrawn }
  const specFiles = fs.readdirSync(specsDir).filter((f) => f.endsWith(".spec.md")).map((f) => path.join(specsDir, f));
  for (const f of specFiles) {
    for (const line of fs.readFileSync(f, "utf8").split("\n")) {
      const m = line.match(CLAUSE_LINE_RE);
      if (!m) continue;
      clauses.set(m[2], { file: path.relative(ROOT, f), withdrawn: Boolean(m[1]) });
    }
  }

  const TAG_LINE_RE = /^(?:\/\/|#)\s*SPEC:/;
  const TAG_IDS_RE = /SPEC:\s*([A-Z0-9,\s-]+)/;
  const searchDirs = [path.join(ROOT, "composeApp/src"), path.join(ROOT, "qa/e2e")];
  const files = searchDirs.flatMap((d) => walkFiles(d, [".kt", ".kts", ".yaml", ".yml"]));
  const tags = [];
  for (const f of files) {
    fs.readFileSync(f, "utf8").split("\n").forEach((line, i) => {
      const trimmed = line.trim();
      if (!TAG_LINE_RE.test(trimmed)) return;
      const m = trimmed.match(TAG_IDS_RE);
      if (!m) return;
      const ids = m[1].split(/[,\s]+/).map((s) => s.trim()).filter((s) => /^[A-Z][A-Z0-9]*-\d{2,}$/.test(s));
      for (const id of ids) tags.push({ id, file: path.relative(ROOT, f), line: i + 1 });
    });
  }

  const citedIds = new Set(tags.map((t) => t.id));
  const orphanClauses = [...clauses.entries()].filter(([, c]) => !c.withdrawn).filter(([id]) => !citedIds.has(id));
  const orphanTags = tags.filter((t) => !clauses.has(t.id) || clauses.get(t.id).withdrawn);

  if (orphanClauses.length === 0 && orphanTags.length === 0) {
    return {
      name: "specCoverage",
      verdict: "PASS",
      durationMs: Date.now() - started,
      details: {
        clauses: [...clauses.values()].filter((c) => !c.withdrawn).length,
        withdrawn: [...clauses.values()].filter((c) => c.withdrawn).length,
        tags: tags.length,
        files: files.length,
      },
    };
  }

  const lines = ["Spec coverage broken — the spec and the tests have drifted apart:"];
  for (const [id, c] of orphanClauses) {
    lines.push(`  [${id}] ${c.file} — no durable test cites this clause. Write the test (tag it '// SPEC: ${id}') or withdraw the clause (strike it through).`);
  }
  for (const t of orphanTags) {
    const known = clauses.get(t.id);
    if (known?.withdrawn) {
      lines.push(`  // SPEC: ${t.id} at ${t.file}:${t.line} — the test verifies withdrawn behavior (clause ${t.id} in ${known.file} is struck through). Remove the test or un-withdraw the clause.`);
    } else {
      lines.push(`  // SPEC: ${t.id} at ${t.file}:${t.line} — no such clause in specs/. Add the clause (AI proposes, human confirms) or fix the id.`);
    }
  }

  return {
    name: "specCoverage",
    verdict: "FAIL",
    reason: lines.join("\n"),
    durationMs: Date.now() - started,
    details: {
      clauses: [...clauses.values()].filter((c) => !c.withdrawn).length,
      withdrawn: [...clauses.values()].filter((c) => c.withdrawn).length,
      tags: tags.length,
      files: files.length,
    },
  };
}

function stepBuild() {
  const res = sh(`${GRADLEW} :composeApp:assembleDebug --console=plain`);
  return {
    name: "build",
    verdict: res.ok ? "PASS" : "FAIL",
    reason: res.ok ? undefined : `assembleDebug failed — fix the build before anything else:\n${res.out.split("\n").filter((l) => /error|FAILURE/i.test(l)).slice(0, 12).join("\n")}`,
    durationMs: res.durationMs,
  };
}

// Runs a filtered slice of the JVM test tier and names the verdict after the gate it proves.
// The full suite already ran in unitTests; the filtered slices stay cheap (compilation is
// cached) while `--rerun` forces the tests themselves to EXECUTE — see stepUnitTests.
function gradleTestStep(name, testsFilter, failHint) {
  return () => {
    const res = sh(`${GRADLEW} :composeApp:desktopTest --rerun --tests "${testsFilter}" --console=plain`);
    return {
      name,
      verdict: res.ok ? "PASS" : "FAIL",
      reason: res.ok
        ? undefined
        : `${failHint}\n${res.out.split("\n").filter((l) => /FAILED|\[(ARCH|SHELL|HOME)-\d+\]|error:/i.test(l)).slice(0, 15).join("\n")}`,
      durationMs: res.durationMs,
    };
  };
}

function stepUnitTests() {
  // `--rerun` is EVIDENCE INTEGRITY, not pedantry: without it, Gradle's build cache can
  // restore a PASS recorded against a *different* tree state (deterministic re-scaffolds
  // produce byte-identical sources, and golden baselines aren't compile inputs), so the
  // receipt would attest tests that never executed. Compilation stays cached — only the
  // test execution is forced.
  const res = sh(`${GRADLEW} :composeApp:desktopTest --rerun --console=plain`);
  const summary = junitSummary(path.join(ROOT, "composeApp/build/test-results/desktopTest"));
  return {
    name: "unitTests",
    verdict: res.ok ? "PASS" : "FAIL",
    reason: res.ok
      ? undefined
      : `desktopTest failed (${summary ? `${summary.failures + summary.errors} of ${summary.tests} tests` : "see output"}). Fix the failing behavior — do not delete or weaken tests to pass:\n${res.out.split("\n").filter((l) => /FAILED|error:/i.test(l)).slice(0, 12).join("\n")}`,
    durationMs: res.durationMs,
    details: summary ?? undefined,
  };
}

const stepConformance = gradleTestStep(
  "conformance",
  "*ArchitectureConformanceTest",
  "Architecture conformance violated (specs/app-base.spec.md ARCH clauses). The failing rule names the clause, files, and fix:",
);
const stepGoldenTrees = gradleTestStep(
  "goldenTrees",
  "*GoldenTreeTest",
  "Golden-tree drift: a screen's rendered STRUCTURE no longer matches qa/golden/. Unintended → fix your change; intended → regenerate with UPDATE_GOLDEN=1 and declare it:",
);
const stepA11y = gradleTestStep(
  "a11y",
  "*A11yConformanceTest",
  "A11y gate failed (SHELL-04): interactive nodes must expose a testTag, text, or contentDescription:",
);

// Live tokenDrift tier (harness M4-D): when a debug app + device are available,
// fetches the declared catalog and the live semantics tree off the debug-only
// inspector server (127.0.0.1:9500, see composeApp/src/androidDebug/.../
// InspectorHttpServer.kt) and runs compareTokenDrift() over them — real runtime
// drift detection, embedded in the evidence receipt.
//
// Infrastructure absence (no device, app not running) is NEVER a FAIL — only
// actual drift is. curl (via the existing synchronous sh() helper) stands in for
// an HTTP client here because every step in this lane runs synchronously; a
// couple of short retries cover the debug app's cold start.
const INSPECTOR_PORT = 9500;

function curlJson(url, timeoutSec = 5) {
  const res = sh(`curl -s -m ${timeoutSec} -w "\\n%{http_code}" "${url}"`);
  if (!res.ok) return { ok: false };
  const out = res.out;
  const idx = out.lastIndexOf("\n");
  const code = (idx >= 0 ? out.slice(idx + 1) : "").trim();
  const bodyText = idx >= 0 ? out.slice(0, idx) : "";
  if (code !== "200") return { ok: false };
  try {
    return { ok: true, body: JSON.parse(bodyText) };
  } catch {
    return { ok: false };
  }
}

function pollHealth(port, attempts, delaySec) {
  let health = curlJson(`http://127.0.0.1:${port}/inspect/health`);
  for (let tries = 1; !health.ok && tries < attempts; tries += 1) {
    sh(`sleep ${delaySec}`);
    health = curlJson(`http://127.0.0.1:${port}/inspect/health`);
  }
  return health;
}

function stepTokenDrift() {
  const started = Date.now();
  const elapsed = () => Date.now() - started;

  if (!deviceAttached()) {
    return {
      name: "tokenDrift",
      verdict: "SKIP",
      reason: "no Android device/emulator attached (adb) — runtime token drift needs the live inspector tier",
      durationMs: elapsed(),
    };
  }

  const unreachable = () => ({
    name: "tokenDrift",
    verdict: "SKIP",
    reason: "inspector endpoint not reachable on :9500 (debug app not running?) — launch the debug build to enable the live tier",
    durationMs: elapsed(),
  });

  sh(`adb forward tcp:${INSPECTOR_PORT} tcp:${INSPECTOR_PORT}`);
  try {
    let health = curlJson(`http://127.0.0.1:${INSPECTOR_PORT}/inspect/health`);
    if (!health.ok) {
      // Debug app may not be running — try to launch it (best-effort: parse the
      // applicationId out of the Android build config), then give it a moment
      // to cold-start before giving up.
      let applicationId = null;
      try {
        const gradle = fs.readFileSync(path.join(ROOT, "composeApp/build.gradle.kts"), "utf8");
        applicationId = gradle.match(/applicationId\s*=\s*"([^"]+)"/)?.[1] ?? null;
      } catch {
        applicationId = null;
      }
      if (applicationId) {
        sh(`adb shell am start -n ${applicationId}/.MainActivity`);
      }
      health = pollHealth(INSPECTOR_PORT, 5, 2);
    }
    if (!health.ok) return unreachable();

    const designSystem = curlJson(`http://127.0.0.1:${INSPECTOR_PORT}/inspect/design-system`);
    const tree = curlJson(`http://127.0.0.1:${INSPECTOR_PORT}/inspect/tree`);
    if (!designSystem.ok || !tree.ok) return unreachable();

    const { checked, drifted } = compareTokenDrift(designSystem.body, tree.body);

    if (drifted.length === 0) {
      return {
        name: "tokenDrift",
        verdict: "PASS",
        durationMs: elapsed(),
        details: { checked, drifted: 0 },
      };
    }

    const lines = ["Runtime token drift — a component's resolved value contradicts the declared design-system catalog:"];
    for (const d of drifted) {
      lines.push(
        `  [${d.node}] token '${d.token}' (${d.facet}) — expected ${d.expected}, resolved ${d.actual}. Update the component to use the token, or update the catalog if the token itself changed.`,
      );
    }
    return {
      name: "tokenDrift",
      verdict: "FAIL",
      reason: lines.join("\n"),
      durationMs: elapsed(),
      details: { checked, drifted },
    };
  } finally {
    sh(`adb forward --remove tcp:${INSPECTOR_PORT}`);
  }
}

function maestroAvailable() {
  return sh("maestro --version", { timeout: 15_000 }).ok;
}

function stepE2eSmoke() {
  if (!fs.existsSync(path.join(ROOT, "qa/e2e"))) {
    return { name: "e2eSmoke", verdict: "SKIP", reason: "e2e harness not included in this project (--no-e2e)", durationMs: 0 };
  }
  if (!deviceAttached()) {
    return { name: "e2eSmoke", verdict: "SKIP", reason: "no Android device/emulator attached (adb)", durationMs: 0 };
  }
  if (!maestroAvailable()) {
    return { name: "e2eSmoke", verdict: "SKIP", reason: "maestro CLI not installed — curl -fsSL https://get.maestro.mobile.dev | bash", durationMs: 0 };
  }
  const install = sh(`${GRADLEW} :composeApp:installDebug --console=plain`);
  if (!install.ok) {
    return { name: "e2eSmoke", verdict: "FAIL", reason: "installDebug failed — the APK could not be installed on the attached device", durationMs: install.durationMs };
  }
  // Harden the device for headless/CI automation before driving it. Without this, a slow or
  // loaded emulator produces false reds that have nothing to do with the app:
  //  - hide_error_dialogs=1 stops Android popping ANR/crash dialogs (e.g. SystemUI under load)
  //    that steal focus over the app — a Maestro assert would then see only the dialog;
  //  - MAESTRO_DRIVER_STARTUP_TIMEOUT gives the UiAutomator2 driver a generous budget to come
  //    up on a slow emulator (the built-in default gives up too early under load).
  // Both are benign, reversible, and only touch the device while the lane is driving it.
  sh("adb shell settings put global hide_error_dialogs 1");
  const res = sh("maestro test qa/e2e/smoke.yaml", { env: { ...process.env, MAESTRO_DRIVER_STARTUP_TIMEOUT: "120000" } });
  return {
    name: "e2eSmoke",
    verdict: res.ok ? "PASS" : "FAIL",
    reason: res.ok ? undefined : `Maestro smoke failed (flow cites the SHELL spec clauses it proves):\n${res.out.split("\n").slice(-15).join("\n")}`,
    durationMs: install.durationMs + res.durationMs,
  };
}

// ── Lane ───────────────────────────────────────────────────────────────────

const stepsForProfile = {
  // scaffold: what `create-cmp --verify` proves at stamp time — specCoverage,
  // the full JVM tier (unit + conformance + golden + UI tests) plus the Android build.
  scaffold: [stepSpecCoverage, stepBuild, stepUnitTests],
  local: [
    stepSpecCoverage,
    stepBuild,
    stepUnitTests,
    stepConformance,
    stepGoldenTrees,
    stepTokenDrift,
    stepA11y,
    stepE2eSmoke,
  ],
};
stepsForProfile.ci = stepsForProfile.local;

if (!stepsForProfile[profile]) {
  console.error(`Unknown profile "${profile}" — use scaffold | local | ci.`);
  process.exit(2);
}

const steps = [];
for (const step of stepsForProfile[profile]) {
  const result = step();
  steps.push(result);
  if (!asJson) {
    const mark = result.verdict === "PASS" ? "✓" : result.verdict === "SKIP" ? "→" : "✗";
    console.log(`${mark} ${result.name}: ${result.verdict}${result.reason ? ` — ${result.reason.split("\n")[0]}` : ""}`);
  }
  if (result.name === "build" && result.verdict === "FAIL") break; // nothing downstream is meaningful
}

const verdict = steps.some((s) => s.verdict === "FAIL") ? "FAIL" : "PASS";

// Artifacts: hash whatever the run left under qa-artifacts/ (never committed).
const artifacts = [];
if (fs.existsSync(ARTIFACTS_DIR)) {
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(p);
      else artifacts.push({ path: path.relative(ROOT, p), sha256: createHash("sha256").update(fs.readFileSync(p)).digest("hex") });
    }
  };
  walk(ARTIFACTS_DIR);
  artifacts.sort((a, b) => a.path.localeCompare(b.path));
}

// Bind the receipt to the content of the verified surface (ADR-0005), NOT the
// parent SHA (rebase/merge-fragile). Must be computed before latest.json is
// written — the receipt is an output and must never hash itself.
const inputs = computeInputsHash(ROOT);

// The receipt. Deterministic key order; ONE volatile timestamp field.
// commit.sha is the parent HEAD at run time (you cannot know the sha of the
// commit the receipt will be part of); commit.dirty lists what was uncommitted.
const receipt = {
  schema: "cmp-evidence/1",
  profile,
  verdict,
  commit: {
    sha: tryGit("rev-parse HEAD"),
    dirty: (tryGit("status --porcelain") ?? "").split("\n").filter(Boolean).map((l) => l.slice(3)).sort(),
  },
  inputs: {
    hash: inputs.hash,
    fileCount: inputs.fileCount,
  },
  steps,
  artifacts,
  toolVersions: {
    node: process.version,
    platform: `${process.platform}-${process.arch}`,
  },
  generatedAt: new Date().toISOString(),
};

fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
fs.writeFileSync(path.join(EVIDENCE_DIR, "latest.json"), `${JSON.stringify(receipt, null, 2)}\n`);

if (asJson) console.log(JSON.stringify(receipt, null, 2));
else console.log(`\n${verdict === "PASS" ? "✅" : "❌"} verify lane: ${verdict} — receipt written to qa/evidence/latest.json (commit it with your change)`);

process.exit(verdict === "PASS" ? 0 : 1);
