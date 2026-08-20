#!/usr/bin/env node
// The verify lane — this project's single verification gate.
//
//   node qa/verify.mjs [--profile scaffold|local|ci|release] [--fast] [--json]
//
// Runs every verification step this project carries, aggregates a typed
// PASS/FAIL verdict, and writes the evidence receipt to qa/evidence/latest.json.
// `--fast` is the INNER LOOP: the resolved profile minus the device/release
// tier, with unchanged pure-Node steps reused from the step cache (CACHED)
// and unit tests scoped to the working-tree change — its receipt records
// mode "fast" and can never satisfy the done-gate.
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
//   release  — everything ci proves PLUS the release-APK smoke (releaseSmoke): the
//              ship-time profile, run before cutting a release, never per-change

import { execSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { computeInputsHash } from "./lib/inputs-hash.mjs";
import { compareTokenDrift } from "./lib/token-drift.mjs";
import { evaluateApprovalsGate } from "./lib/approvals.mjs";
import { clauseTierCoverage, scanCitations, scanSpecClauses, walkFiles } from "./lib/spec-coverage.mjs";
import { evaluateComponentStoryParity } from "./lib/component-stories.mjs";
import { evaluateReachability } from "./lib/reachability.mjs";
import { evidenceLevel } from "./lib/evidence-level.mjs";
import { updateReadmeBadge, README_REL_PATH } from "./lib/evidence-badge.mjs";
import { memoizeStep } from "./lib/step-cache.mjs";
import { changedWorkingTreePaths, deriveAffectedFilter } from "./lib/affected-tests.mjs";
import { acquireDeviceLease, releaseDeviceLease, formatHolder } from "./lib/device-lease.mjs";
import { ARCH_DOC_REL_PATH, SECTION_IDS, regenerateArchDoc } from "./lib/arch-doc.mjs";
import { DETERMINISM_TIMEZONES, compareOutcomes, parseJUnitOutcomes } from "./lib/determinism.mjs";
import { evaluateAuditCadence } from "./lib/audit-cadence.mjs";
import { appendFlightRecord, buildFlightEntry } from "./lib/flight-recorder.mjs";
import { checkHarnessIntegrity, describeIntegrity, LOCK_PATH } from "./lib/harness-lock.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const EVIDENCE_DIR = path.join(ROOT, "qa", "evidence");
const ARTIFACTS_DIR = path.join(ROOT, "qa-artifacts");

// ── Argument parsing — strict, and first thing this file does ──────────────
// An unrecognized flag used to fall through silently and start the full
// multi-minute lane (`--help` ran the whole lane for ~2 minutes before being
// killed). Same refusal-over-fabrication stance as qa/approve.mjs, which
// refuses an unknown artifact by name rather than guessing: an unknown
// argument here is refused by name, not swallowed into "run everything".
const USAGE = `node qa/verify.mjs [--profile scaffold|local|ci|release] [--fast] [--json] [--help]

The verify lane — this project's single verification gate. Runs every
verification step this project carries, aggregates a typed PASS/FAIL
verdict, and writes the evidence receipt to qa/evidence/latest.json (commit
it with your change — see CLAUDE.md). Exit code: 0 = PASS, 1 = FAIL.

Flags:
  --profile <scaffold|local|ci|release>
                                 which step set to run (default: local)
  --fast                         INNER LOOP ONLY — run the resolved profile
                                  minus the device/release tier (releaseBuild,
                                  tokenDrift, e2eSmoke, androidChecks,
                                  releaseSmoke), unconditionally, device
                                  attached or not. Also reuses the pure-Node
                                  steps' last PASS when their inputs are
                                  unchanged (verdict CACHED), lets Gradle's
                                  up-to-date checks stand (no --rerun), and
                                  scopes unit tests to the working-tree change
                                  (broad-impact changes run everything). The
                                  receipt records mode "fast", derives no
                                  evidence rung, and can NEVER satisfy the
                                  done-gate — run the full lane once before
                                  you call it done
  --determinism                  run the timezone determinism probe: the JVM
                                  test tier (unit + golden + the other
                                  desktop suites) executes TWICE, under
                                  TZ=Etc/GMT+12 (UTC-12) and TZ=Etc/GMT-14
                                  (UTC+14), and the probe FAILs naming every
                                  test whose verdict or failure output
                                  differs — a nondeterminism leak ARCH-13's
                                  static net missed. Bare (no --profile) it
                                  runs JUST the probe and writes no receipt;
                                  with --profile ci (or release) it runs
                                  inside the lane and lands on the receipt.
                                  Never combinable with --fast
  --json                         print the receipt as JSON instead of the
                                  human-readable step-by-step log
  --help, -h                     print this usage and exit 0 without
                                  running anything

Profiles:
  scaffold  spec coverage + build + unit tests (what \`create-cmp --verify\`
            proves at stamp time)
  local     everything; device-dependent steps SKIP when no device is
            attached
  ci        everything; SKIPs are recorded so the pipeline stays honest
  release   everything ci proves PLUS the release-APK smoke (releaseSmoke) —
            the ship-time profile; run it before cutting a release, never
            per-change
`;

const rawArgs = process.argv.slice(2);

if (rawArgs.includes("--help") || rawArgs.includes("-h")) {
  console.log(USAGE);
  process.exit(0);
}

const RECOGNIZED_FLAGS = new Set(["--profile", "--json", "--fast", "--determinism"]);
for (let i = 0; i < rawArgs.length; i += 1) {
  const arg = rawArgs[i];
  if (arg === "--profile") {
    i += 1; // consume its value (missing/invalid value keeps the existing exit-2 behavior below)
    continue;
  }
  if (RECOGNIZED_FLAGS.has(arg)) continue;
  console.error(`unknown argument "${arg}" — run node qa/verify.mjs --help`);
  process.exit(2);
}

const args = rawArgs;
const profile = args.includes("--profile") ? args[args.indexOf("--profile") + 1] : "local";
const asJson = args.includes("--json");
const fast = args.includes("--fast");
// --no-journal suppresses the flight-recorder append (qa/watch.mjs passes it).
// See the append site below for why the inner loop must not write here.
const noJournal = args.includes("--no-journal");
const mode = fast ? "fast" : "full";

// ── --determinism: the timezone double-run probe (roadmap §10 item 8) ───────
// Refusals up front, by name (same stance as unknown arguments above):
//  - never with --fast: the probe deliberately runs the JVM test tier twice,
//    and --fast is the inner loop that exists to not pay such costs — the
//    combination is a contradiction, so it is refused rather than silently
//    resolved either way.
//  - only the ci profile (and release, which inherits ci) carries the probe's
//    lane row; asking for it in local/scaffold is refused with the two ways
//    that DO work, instead of silently running a step the requested profile
//    does not own.
const determinism = args.includes("--determinism");
const profileExplicit = args.includes("--profile");
if (determinism && fast) {
  console.error(
    "--determinism cannot be combined with --fast: the probe runs the JVM test tier twice by design, and --fast is the inner loop. Run it alone (node qa/verify.mjs --determinism) or inside a full ci/release lane (--profile ci --determinism).",
  );
  process.exit(2);
}
if (determinism && profileExplicit && profile !== "ci" && profile !== "release") {
  console.error(
    `--determinism belongs to the ci profile (release inherits it), not "${profile}" — run --profile ci --determinism, or bare --determinism to run the probe alone.`,
  );
  process.exit(2);
}

const GRADLEW = process.platform === "win32" ? "gradlew.bat" : "./gradlew";

// ── `--rerun` is scoped to FULL mode ────────────────────────────────────────
// `--rerun` exists for evidence integrity (see stepUnitTests's comment): it
// stops Gradle's build cache replaying a PASS recorded against a different
// tree into a receipt that claims tests executed. That mechanism belongs to
// the runs that produce integrity-bearing artifacts — and a --fast run does
// not: its receipt already declares itself non-evidence (mode "fast", no
// evidence rung, refused by qa/receipt-check.mjs), so forcing execution there
// paid an integrity tax to protect an artifact with nothing to protect. Fast
// mode therefore omits the flag and lets Gradle's up-to-date/cache machinery
// do its job; full mode keeps it, byte-identical to before.
const RERUN = fast ? "" : " --rerun";

function sh(cmd, opts = {}) {
  const started = Date.now();
  // maxBuffer: first-run Gradle output easily exceeds spawnSync's 1MB default,
  // which would surface as a bogus FAIL (status null / ENOBUFS).
  const res = spawnSync(cmd, { shell: true, cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024, ...opts });
  const ok = res.status === 0 && !res.error;
  return { ok, status: res.status, error: res.error?.message, out: `${res.stdout ?? ""}${res.stderr ?? ""}`, durationMs: Date.now() - started };
}

// ── Preview-daemon coexistence ──────────────────────────────────────────────
// The preview daemon (the eyes) and this lane both spawn Gradle against this
// project and share composeApp/build/kspCaches, whose KSP incremental storage
// is single-owner — two concurrent builds throw "Storage for [...] is already
// registered" and one side dies. Three defenses, all automatic:
//   1. COORDINATE (this lane -> the daemon): this lane stamps a marker file
//      for its duration; the preview service defers renders while it exists
//      (mtime-bounded, so a crashed lane never wedges the eyes for long).
//   2. COORDINATE (the daemon -> this lane), the symmetric half: the daemon
//      stamps its OWN marker for the duration of a render's Gradle build;
//      shGradle waits for it to clear (or go stale) before launching this
//      lane's own Gradle command — same mtime-bounded shape, so a crashed
//      daemon never wedges the lane for long either.
//   3. SELF-HEAL: a Gradle step that still hits the collision clears kspCaches
//      and retries once — the manual recovery that always worked, automated.
const LANE_MARKER = path.join(ROOT, "composeApp", "build", ".cmp-lane-in-progress");
const KSP_COLLISION_RE = /Storage for \[[^\]]*\] is already registered/;

// Degraded-path activations observed during this run — self-heals and
// fallbacks that kept the lane moving without failing it. Collected for the
// flight recorder (qa/lib/flight-recorder.mjs): a degradation that fires
// once is a shrug, one that fires every run for a month is the tooling
// quietly rotting under a green lane — and only a journal can tell those
// two apart.
const DEGRADED_PATHS = [];

// The daemon's half of defense 2 above — pid + ISO timestamp, mirroring
// LANE_MARKER's own content shape (see where LANE_MARKER is stamped, below).
const RENDER_MARKER = path.join(ROOT, "composeApp", "build", ".cmp-render-in-progress");
const RENDER_MARKER_FRESH_MS = 5 * 60 * 1000; // older than this = a crashed daemon's stale marker, ignore it
const RENDER_WAIT_TIMEOUT_MS = 3 * 60 * 1000; // give up waiting after this long regardless
const RENDER_WAIT_POLL_MS = 2000;

/**
 * Defer this lane's next Gradle command while the preview daemon's render
 * marker is present AND fresh (mtime younger than RENDER_MARKER_FRESH_MS).
 * Polls every RENDER_WAIT_POLL_MS; gives up and proceeds anyway after
 * RENDER_WAIT_TIMEOUT_MS, or the moment the marker disappears or goes stale —
 * whichever comes first. A missing/unreadable marker returns immediately:
 * this is a coexistence courtesy, never a hard dependency on the daemon.
 */
function waitForRenderMarker() {
  const deadline = Date.now() + RENDER_WAIT_TIMEOUT_MS;
  for (;;) {
    let stat;
    try {
      stat = fs.statSync(RENDER_MARKER);
    } catch {
      return; // no render in flight
    }
    if (Date.now() - stat.mtimeMs >= RENDER_MARKER_FRESH_MS) return; // gone stale
    if (Date.now() >= deadline) return; // waited long enough — proceed regardless
    sh(`sleep ${RENDER_WAIT_POLL_MS / 1000}`);
  }
}

function shGradle(cmd, opts = {}) {
  waitForRenderMarker();
  const first = sh(cmd, opts);
  if (first.ok || !KSP_COLLISION_RE.test(first.out)) return first;
  console.error("· KSP cache collision (concurrent Gradle — the preview daemon?) — clearing kspCaches, retrying once");
  fs.rmSync(path.join(ROOT, "composeApp", "build", "kspCaches"), { recursive: true, force: true });
  const retry = sh(cmd, opts);
  retry.durationMs += first.durationMs;
  retry.selfHealed = "ksp-cache-collision";
  DEGRADED_PATHS.push("ksp-cache-collision: cleared kspCaches and retried the Gradle step");
  return retry;
}

function tryGit(cmd) {
  try {
    return execSync(`git ${cmd}`, { cwd: ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  } catch {
    return null;
  }
}

/**
 * Line-oriented git output, WITHOUT [tryGit]'s trim. `git status --porcelain`
 * has significant leading whitespace: an unstaged modification is `" M path"`,
 * so trimming the whole blob eats the first line's leading space — and a fixed
 * `slice(3)` then swallows that path's first character. The receipt would name
 * a file that does not exist. Only trailing newlines are dropped here.
 */
function tryGitLines(cmd) {
  try {
    const out = execSync(`git ${cmd}`, { cwd: ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
    return out.replace(/\n+$/, "").split("\n").filter(Boolean);
  } catch {
    return [];
  }
}

// Recursive: desktopTest writes TEST-*.xml flat, but connected (instrumented) results
// land one directory level down per device (build/outputs/androidTest-results/connected/
// debug/<device>/TEST-*.xml) — both shapes are summarized by the same walk.
function junitSummary(dir) {
  if (!fs.existsSync(dir)) return null;
  let tests = 0, failures = 0, errors = 0, skipped = 0;
  const walk = (d) => {
    for (const entry of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, entry.name);
      if (entry.isDirectory()) {
        walk(p);
        continue;
      }
      if (!entry.name.startsWith("TEST-") || !entry.name.endsWith(".xml")) continue;
      const xml = fs.readFileSync(p, "utf8");
      const m = xml.match(/<testsuite[^>]*tests="(\d+)"[^>]*skipped="(\d+)"[^>]*failures="(\d+)"[^>]*errors="(\d+)"/);
      if (m) {
        tests += Number(m[1]);
        skipped += Number(m[2]);
        failures += Number(m[3]);
        errors += Number(m[4]);
      }
    }
  };
  walk(dir);
  return { tests, failures, errors, skipped };
}

function deviceAttached() {
  const res = sh("adb devices", { timeout: 10_000 });
  if (!res.ok) return false;
  return res.out.split("\n").slice(1).some((l) => /\tdevice$/.test(l.trim().replace(/\s+/g, "\t")));
}

// ── The machine-global device lease (qa/lib/device-lease.mjs) ───────────────
// LANE_MARKER above is per-PROJECT; the device is machine-GLOBAL. A scratch app
// in /tmp and the real app each stamp their own marker and still share the one
// emulator — nothing stopped two lanes (or a lane and a live console session)
// driving it at once, which is the observed wedged-adbd / `device offline` /
// crossed-app-state failure class. Every device-touching step below takes the
// lease before touching the device.
//
// SCOPE DECISION — once per run, not per step: the lease is acquired lazily by
// the FIRST device step that actually reaches the device and held until the
// lane exits (released in the same `finally` as LANE_MARKER). A single run must
// not thrash acquire/release between adjacent device steps, and holding through
// the desktop steps interleaved among them (a11y sits between tokenDrift and
// e2eSmoke) costs nothing — nothing else should drive the device mid-lane
// anyway, which is the whole point.
//
// ON CONTENTION THE STEP RETURNS SKIP — NEVER FAIL: nothing is broken; another
// run legitimately holds the device. This composes with the evidence ladder
// (qa/lib/evidence-level.mjs): a SKIPped device step simply does not buy its
// rung, so contention visibly DEGRADES the evidence level (L2 falls back to L1)
// instead of corrupting the run with a false red — that degradation being
// honest and visible is exactly why SKIP is the right verdict.
let laneDeviceLease = null;

/** Serials of devices currently in `device` state (same parse as deviceAttached). */
function attachedDeviceSerials() {
  const res = sh("adb devices", { timeout: 10_000 });
  if (!res.ok) return [];
  return res.out
    .split("\n")
    .slice(1)
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => l.split(/\s+/))
    .filter(([, state]) => state === "device")
    .map(([serial]) => serial);
}

/**
 * Acquire (or confirm) the lane's device lease for a device step.
 * Returns null when the lane holds the device; otherwise the SKIP result the
 * step should return verbatim. The serial leased is the one the lane will
 * actually drive: the single attached device, or ANDROID_SERIAL when several
 * are attached (adb/Gradle/Maestro honor the same variable). Ambiguity is
 * SKIPped by name — leasing a guess would protect the wrong device.
 */
function leaseDeviceForStep(stepName) {
  if (laneDeviceLease) return null; // already held for this run
  const serials = attachedDeviceSerials();
  if (serials.length === 0) return null; // each step's own guard SKIPs "no device" with its precise reason
  let serial = serials[0];
  if (serials.length > 1) {
    const chosen = process.env.ANDROID_SERIAL;
    if (chosen && serials.includes(chosen)) {
      serial = chosen;
    } else {
      return {
        name: stepName,
        verdict: "SKIP",
        reason: `${serials.length} devices attached (${serials.join(", ")}) — the lane cannot tell which one it would drive, so it leases none rather than guessing. Set ANDROID_SERIAL to the device this lane should own, or detach the extras.`,
        durationMs: 0,
      };
    }
  }
  const res = acquireDeviceLease({ serial, holder: `verify lane ${stepName}`, root: ROOT });
  if (!res.ok) {
    return {
      name: stepName,
      verdict: "SKIP",
      reason: `device ${serial} is held by ${formatHolder(res.heldBy)} — device evidence is batched, not concurrent; wait for it or run once when it finishes`,
      durationMs: 0,
    };
  }
  if (res.reclaimed) {
    console.error(`· reclaimed a dead device lease on ${serial} (was ${formatHolder(res.reclaimed)})`);
  }
  laneDeviceLease = res.handle;
  return null;
}

// Settle adb before handing the device to whatever drives it next (Maestro, the
// instrumented runner). An install task returning 0 means the package manager accepted
// the APK — NOT that the device is ready to be driven: a reinstall over a running app
// briefly drops the emulator's adb transport. `adb devices` still says `device`, but a
// fresh adb client (Maestro's dadb, Gradle's ddmlib) gets `device offline` and dies
// before the first assertion (observed 4/4 when the live-inspector tier ran earlier in
// the lane — its port-forward traffic widens the window — and 0/4 when it was skipped).
// wait-for-device blocks only while the transport is actually down; the kill/start pair
// ahead of it clears a stale server-side transport entry that survives the device coming
// back. Neither weakens any assertion — every downstream check still passes on its own
// merits.
function settleAdb() {
  sh("adb kill-server");
  sh("adb start-server");
  sh("adb wait-for-device");
}

// ── Steps ──────────────────────────────────────────────────────────────────
// Each returns { name, verdict, reason?, durationMs, details? }. Failure
// reasons are worded for an AI collaborator to act on.

// Spec ↔ test drift gate — pure Node, no Gradle. The clause/citation scan
// itself lives in qa/lib/spec-coverage.mjs — the SAME scan feature-brief.mjs
// derives doneness from, so this gate and the Features view can never disagree
// about a clause. This step owns only the orphan decision + bookkeeping.
// The first question any verdict depends on: is the lane that is about to
// issue it the lane this app was given?
//
// Without this the receipt is unfalsifiable in one specific way — edit
// qa/verify.mjs to force every step PASS and the receipt still validates,
// because the edited file is simply part of the hashed input surface. Hashing
// the machine-owned region against qa/harness.lock.json closes that: the lane
// cannot vouch for itself while modified.
//
// DELIBERATELY NOT MEMOIZED. Every other pure-Node step can serve a cached
// PASS when its inputs are unchanged; a cached PASS on an integrity check is
// precisely the failure it exists to prevent, and 34 file reads are too cheap
// to be worth the risk.
//
// Three states, three verdicts:
//   intact    PASS
//   modified  FAIL — named files, with the command that restores them
//   unlocked  SKIP — an app stamped before locks existed. Nothing is known to
//             be wrong, but nothing is proven either; recording the gap keeps
//             the pipeline honest instead of quietly passing.
function stepHarnessIntegrity() {
  const started = Date.now();
  const r = checkHarnessIntegrity(ROOT);
  const base = { name: "harnessIntegrity", durationMs: Date.now() - started, harness: r };

  if (r.status === "intact") {
    return { ...base, verdict: "PASS", note: describeIntegrity(r) };
  }
  if (r.status === "unlocked") {
    return {
      ...base,
      verdict: "SKIP",
      reason: `no ${LOCK_PATH} — this app was stamped before harness locks existed. ` +
        "`npx create-cmp-cli upgrade --harness` records one.",
    };
  }

  const named = [
    ...r.modified.map((f) => `modified  ${f}`),
    ...r.missing.map((f) => `missing   ${f}`),
    ...r.extra.map((f) => `unrecorded ${f}`),
  ];
  return {
    ...base,
    verdict: "FAIL",
    reason:
      `the verify lane has been modified since it was installed — ${describeIntegrity(r)}. ` +
      "Lane code is machine-owned: it is byte-identical in every create-cmp app and carries " +
      "no app content, so a local edit is either an accident, a half-applied upgrade, or an " +
      "attempt to make this receipt say something the lane would not. Restore it with " +
      "`npx create-cmp-cli upgrade --harness`, which also reports any genuine local patch " +
      "instead of discarding it.",
    files: named,
  };
}

function stepSpecCoverage() {
  const started = Date.now();
  const specsDir = path.join(ROOT, "specs");
  if (!fs.existsSync(specsDir)) {
    return { name: "specCoverage", verdict: "SKIP", reason: "no specs/ directory in this project", durationMs: Date.now() - started };
  }

  const clauses = scanSpecClauses(ROOT);
  const tags = scanCitations(ROOT);
  const searchDirs = [path.join(ROOT, "composeApp/src"), path.join(ROOT, "qa/e2e")];
  const files = searchDirs.flatMap((d) => walkFiles(d, [".kt", ".kts", ".yaml", ".yml"]));

  const citedIds = new Set(tags.map((t) => t.id));
  const orphanClauses = [...clauses.entries()].filter(([, c]) => !c.withdrawn).filter(([id]) => !citedIds.has(id));
  const orphanTags = tags.filter((t) => !clauses.has(t.id) || clauses.get(t.id).withdrawn);

  if (orphanClauses.length === 0 && orphanTags.length === 0) {
    // Tier visibility, not a gate (industry rule: instrument before you police). A clause
    // cited only from desktop-tier tests can still hide a platform-behavior bug — both
    // production apps shipped alarm/notification defects behind clauses that were
    // "covered" by JVM tests androidMain never ran under. The line names them; the
    // instrumented seam (androidChecks) is where such clauses earn a citation.
    const tiers = clauseTierCoverage(clauses, tags);
    return {
      name: "specCoverage",
      verdict: "PASS",
      durationMs: Date.now() - started,
      details: {
        clauses: [...clauses.values()].filter((c) => !c.withdrawn).length,
        withdrawn: [...clauses.values()].filter((c) => c.withdrawn).length,
        tags: tags.length,
        files: files.length,
        tierNote: tiers.summaryLine,
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

// Human-approval gate (VERIFICATION-LAYER-DESIGN.md §2) — pure Node, no Gradle,
// same grouping as specCoverage. The decision itself lives in
// qa/lib/approvals.mjs (evaluateApprovalsGate); this step only adds the
// name/duration bookkeeping every step in this file carries.
function stepApprovals() {
  const started = Date.now();
  const { verdict, reason, statuses } = evaluateApprovalsGate(ROOT);
  return {
    name: "approvals",
    verdict,
    reason,
    durationMs: Date.now() - started,
    details: { artifacts: statuses.map((s) => ({ id: s.id, status: s.status, hash: s.hash })) },
  };
}

// There is deliberately NO feature-doneness step here (CHANGE-FLOW-DESIGN.md
// §7): a feature's doneness is DERIVED from gates this lane already runs —
// specCoverage fails an uncited clause, the test steps fail a broken promise,
// and the receipt's inputs.hash attests the tree. A second mechanism would be
// a second truth.

// Component ↔ story parity gate (STUDIO-REDESIGN.md §3.3) — pure Node, no
// Gradle, same grouping as specCoverage/approvals. The decision itself lives
// in qa/lib/component-stories.mjs (evaluateComponentStoryParity); this step
// only adds the name/duration bookkeeping every step in this file carries.
function stepComponentStories() {
  const started = Date.now();
  const { verdict, reason, details } = evaluateComponentStoryParity(ROOT);
  return { name: "componentStories", verdict, reason, durationMs: Date.now() - started, details };
}

// Navigation-reachability gate (task FI-7, docs/AUTONOMY-GAPS.md §3) — pure
// Node, no Gradle, same grouping as specCoverage/approvals/componentStories.
// The decision itself lives in qa/lib/reachability.mjs (evaluateReachability);
// this step only adds the name/duration bookkeeping every step in this file
// carries. Closes the exact hole a real feature slipped through: every other
// gate PASSed while its screen was wired into nothing.
function stepReachability() {
  const started = Date.now();
  const { verdict, reason, details } = evaluateReachability(ROOT);
  return { name: "reachability", verdict, reason, durationMs: Date.now() - started, details };
}

// Architecture-doc freshness gate (Wave B, docs/proposals/architecture-document-
// standard.md §6) — pure Node, no Gradle, same grouping as specCoverage/
// approvals. The decision itself lives in qa/lib/arch-doc.mjs
// (regenerateArchDoc); this step only adds the name/duration bookkeeping every
// step in this file carries, plus wording the FAIL reason for an AI
// collaborator (name the stale/missing section, name the fix command).
function stepArchDoc() {
  const started = Date.now();
  const elapsed = () => Date.now() - started;

  const result = regenerateArchDoc(ROOT);
  if (!result.ok) {
    return { name: "archDoc", verdict: "SKIP", reason: `${result.reason} — nothing to check`, durationMs: elapsed() };
  }
  if (result.unknownSections.length > 0) {
    return {
      name: "archDoc",
      verdict: "FAIL",
      reason: `${ARCH_DOC_REL_PATH} has cmp:generated marker(s) with no registered generator: ${result.unknownSections.join(", ")} — add a generator in qa/lib/arch-doc.mjs or remove the marker.`,
      durationMs: elapsed(),
    };
  }

  const stale = result.changed || result.missingSections.length > 0;
  if (!stale) {
    return { name: "archDoc", verdict: "PASS", durationMs: elapsed(), details: { sectionsChecked: SECTION_IDS.length } };
  }

  const lines = [`${ARCH_DOC_REL_PATH} is stale — a generated section no longer matches the tree:`];
  for (const id of result.changedSections) {
    lines.push(`  [${id}] regenerating would change this section.`);
  }
  for (const id of result.missingSections) {
    lines.push(`  [${id}] marker missing from the doc entirely — never generated.`);
  }
  lines.push("Run: node qa/arch-doc.mjs");
  return {
    name: "archDoc",
    verdict: "FAIL",
    reason: lines.join("\n"),
    durationMs: elapsed(),
    details: { changedSections: result.changedSections, missingSections: result.missingSections },
  };
}

// Schema-history gate — pure Node + git, no Gradle, same grouping as the other
// evidence checks. Room's exportSchema writes one <version>.json per database per
// target under composeApp/schemas/. Every version EXCEPT the current highest is a
// frozen historical record of a database that shipped: migrations are written and
// validated against those exact bytes, so a regeneration that rewrites them
// silently corrupts the baseline every future migration is proven against. Only
// the highest version is the live, in-progress schema — free to change or appear
// (that IS the current change). This gate exists because schema regeneration
// looks like harmless build output right up until a shipped user's upgrade fails.
function stepSchemaHistory() {
  const started = Date.now();
  const elapsed = () => Date.now() - started;
  const schemasRel = path.join("composeApp", "schemas");
  const schemasRoot = path.join(ROOT, schemasRel);

  if (!fs.existsSync(schemasRoot)) {
    return { name: "schemaHistory", verdict: "SKIP", reason: "no exported Room schemas (composeApp/schemas/ absent) — nothing frozen to guard", durationMs: elapsed() };
  }
  const gitTop = tryGit("rev-parse --show-toplevel");
  if (!gitTop || !tryGit("rev-parse HEAD")) {
    return { name: "schemaHistory", verdict: "SKIP", reason: "no git history yet — schema versions have no committed baseline to be frozen against", durationMs: elapsed() };
  }

  // Every directory holding versioned schema JSONs, with its highest version on disk.
  const versionFile = /^(\d+)\.json$/;
  const maxVersionByDir = new Map(); // absolute dir path -> highest N among its N.json files
  const walkSchemas = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, entry.name);
      if (entry.isDirectory()) walkSchemas(p);
      else {
        const m = entry.name.match(versionFile);
        if (m) maxVersionByDir.set(dir, Math.max(maxVersionByDir.get(dir) ?? 0, Number(m[1])));
      }
    }
  };
  walkSchemas(schemasRoot);

  // Tracked schema files whose committed bytes no longer match the tree (staged or
  // unstaged; deletions included). Paths come back relative to the git toplevel.
  // Untracked files never appear here — a brand-new version file is by definition
  // not yet frozen history.
  const dirtyFiles = tryGitLines(`diff --name-only HEAD -- "${schemasRel.replace(/\\/g, "/")}"`);

  const violations = [];
  for (const rel of dirtyFiles) {
    const abs = path.resolve(gitTop, rel);
    const m = path.basename(abs).match(versionFile);
    if (!m) continue; // not a versioned schema JSON
    const version = Number(m[1]);
    const dirMax = maxVersionByDir.get(path.dirname(abs));
    // The highest version currently on disk is the live schema — dirty is fine.
    // Anything else (a lower version, or a file whose whole directory is gone)
    // is rewritten/deleted history.
    if (dirMax !== undefined && version === dirMax) continue;
    violations.push(rel);
  }

  if (violations.length === 0) {
    return { name: "schemaHistory", verdict: "PASS", durationMs: elapsed(), details: { schemaDirs: maxVersionByDir.size } };
  }

  const lines = [
    "Historical Room schema files were modified or deleted — these are frozen records of shipped databases, and regeneration must never rewrite them (migrations are validated against these exact bytes). Only the current highest version may change. Restore each file:",
  ];
  for (const rel of violations) lines.push(`  git checkout -- ${rel}`);
  lines.push("If you intended a schema change, bump the database version so a NEW <version>.json is exported instead of overwriting history.");
  return {
    name: "schemaHistory",
    verdict: "FAIL",
    reason: lines.join("\n"),
    durationMs: elapsed(),
    details: { schemaDirs: maxVersionByDir.size, violations },
  };
}

function stepBuild() {
  const res = shGradle(`${GRADLEW} :composeApp:assembleDebug --console=plain`);
  return {
    name: "build",
    verdict: res.ok ? "PASS" : "FAIL",
    reason: res.ok ? undefined : `assembleDebug failed — fix the build before anything else:\n${res.out.split("\n").filter((l) => /error|FAILURE/i.test(l)).slice(0, 12).join("\n")}`,
    durationMs: res.durationMs,
  };
}

// The build nobody runs until the day they need it.
//
// assembleDebug passing says nothing about assembleRelease: R8 and `lintVital` only run on
// the release variant, and BuildConfig is generated PER BUILD TYPE, so a constant declared
// in one and not the other is a compile error that only release ever sees. All three of
// those bit this template at once, and none of them were visible from a green debug lane —
// the first release build ever attempted (2026-07-29) failed three times over.
//
// So release is proven at the checkpoint, not discovered at launch. Unsigned: signing needs
// a keystore, which belongs to whoever ships the app, and this step is about the shrinker
// and the build graph rather than the signature.
function stepReleaseBuild() {
  const res = shGradle(`${GRADLEW} :composeApp:assembleRelease --console=plain`);
  return {
    name: "releaseBuild",
    verdict: res.ok ? "PASS" : "FAIL",
    reason: res.ok
      ? undefined
      : `assembleRelease failed — the shippable build is broken even though the debug one is fine:\n${res.out
          .split("\n")
          .filter((l) => /error|FAILURE|Missing class|Unresolved/i.test(l))
          .slice(0, 12)
          .join("\n")}`,
    durationMs: res.durationMs,
  };
}

// Runs a filtered slice of the JVM test tier and names the verdict after the gate it proves.
// The full suite already ran in unitTests; the filtered slices stay cheap (compilation is
// cached) while `--rerun` forces the tests themselves to EXECUTE — see stepUnitTests.
// In fast mode the flag is omitted (RERUN, defined with the mode flags above): the
// integrity mechanism belongs to the runs that produce integrity-bearing artifacts, and a
// fast receipt has already declared itself non-evidence.
function gradleTestStep(name, testsFilter, failHint) {
  return () => {
    const res = shGradle(`${GRADLEW} :composeApp:desktopTest${RERUN} --tests "${testsFilter}" --console=plain`);
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
  // test execution is forced. Scoped to FULL mode (see RERUN above): the integrity
  // mechanism belongs to the runs that produce integrity-bearing artifacts, and a fast
  // receipt is already declared non-evidence.
  //
  // Fast mode additionally scopes the suite to tests plausibly affected by the
  // working-tree change (qa/lib/affected-tests.mjs): changed .kt files map to
  // `--tests "*<segment>*"` patterns, with a mandatory blast-radius escape hatch (build
  // files, DI, theme, shared components, qa/, anything outside composeApp/src → full
  // suite) and fail-open on every uncertain case (no git, unmappable change). FALSE
  // NEGATIVES ARE ACCEPTABLE HERE AND ONLY HERE: the full, unfiltered suite runs at the
  // checkpoint (the full lane), where done is actually decided. The filter that ran is
  // reported in the step's note and recorded in the (fast-only) receipt, so a filtered
  // run can never be mistaken for the full suite.
  let note;
  let testsArgs = "";
  let affected = null;
  if (fast) {
    const changed = changedWorkingTreePaths(ROOT);
    if (changed === null) {
      note = "full suite — git unavailable, cannot derive the change (fail open)";
    } else {
      const filter = deriveAffectedFilter(changed);
      if (filter.mode === "filtered") {
        testsArgs = filter.patterns.map((p) => ` --tests "${p}"`).join("");
        note = `affected: ${filter.patterns.join(", ")} — ${filter.sourcePaths.length} changed source file(s)`;
        affected = { patterns: filter.patterns, changedFiles: filter.sourcePaths.length };
      } else {
        note = `full suite — ${filter.reason}`;
      }
    }
  }
  let res = shGradle(`${GRADLEW} :composeApp:desktopTest${RERUN}${testsArgs} --console=plain`);
  if (fast && testsArgs && !res.ok && /No tests found for given includes/.test(res.out)) {
    // The heuristic filter matched no test class at all (e.g. a feature with no tests
    // yet). That is the harness's guess being wrong, not the app — fall back to the
    // full suite in-lane rather than false-redding on our own filter. (RERUN is empty
    // here by construction — this branch only exists in fast mode.)
    const retry = shGradle(`${GRADLEW} :composeApp:desktopTest${RERUN} --console=plain`);
    retry.durationMs += res.durationMs;
    res = retry;
    note = "full suite — the affected-test filter matched no tests (fell back)";
    affected = null;
    DEGRADED_PATHS.push("affected-test filter matched no tests — fell back to the full desktopTest suite");
  }
  const summary = junitSummary(path.join(ROOT, "composeApp/build/test-results/desktopTest"));
  let details = summary ?? undefined;
  if (affected) details = { ...(summary ?? {}), affected };
  return {
    name: "unitTests",
    verdict: res.ok ? "PASS" : "FAIL",
    reason: res.ok
      ? undefined
      : `desktopTest failed (${summary ? `${summary.failures + summary.errors} of ${summary.tests} tests` : "see output"}). Fix the failing behavior — do not delete or weaken tests to pass:\n${res.out.split("\n").filter((l) => /FAILED|error:/i.test(l)).slice(0, 12).join("\n")}`,
    note,
    durationMs: res.durationMs,
    details,
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

// ── Determinism probe (roadmap §10 item 8) — opt-in, ci-profile ────────────
// ARCH-13 statically bans ambient time reads — in APP code. A library the
// app calls can still read the wall clock, and a golden can still depend on
// the machine's timezone through a seam the static net cannot see (a
// ViewModel constructed without its injected clock already caused one
// overnight golden-tree drift). This probe closes that gap DYNAMICALLY: it
// runs the JVM test tier twice, under two timezones whose local calendar
// dates never agree — the offsets are 26 hours apart, so any date-derived
// value differs between the legs at every instant (see DETERMINISM_TIMEZONES
// in qa/lib/determinism.mjs for why UTC-12/UTC+14 and not UTC/UTC+14) — and
// FAILs naming every test whose outcome differs between the legs.
//
// Mechanics that carry the honesty:
//  - TZ reaches the test JVM through the environment: Gradle forwards the
//    client's environment to the daemon on every build, and test workers
//    fork from the daemon — so the child env below is inherited all the way
//    down to the JVM whose default timezone the tests see.
//  - BOTH legs force --rerun. Without it Gradle would mark the second leg
//    up-to-date (TZ is not a declared build input) and replay the first
//    leg's results — the probe would then compare a run against its own
//    echo and certify a determinism it never tested (the build-cache-replay
//    lesson, again). The legs use the mode-scoped RERUN like every other
//    desktopTest invocation — and because --determinism is refused alongside
//    --fast up front, RERUN is always " --rerun" by the time a leg runs.
//  - Only verdicts and failure output are compared; durations are never even
//    parsed (qa/lib/determinism.mjs), so a timing wobble is structurally
//    unable to trip the probe.
function stepDeterminism() {
  const started = Date.now();
  const elapsed = () => Date.now() - started;
  if (!determinism) {
    return {
      name: "determinism",
      verdict: "SKIP",
      reason: "determinism probe is opt-in (it runs the JVM test tier twice) — add --determinism to this lane, or run the probe alone: node qa/verify.mjs --determinism",
      durationMs: elapsed(),
    };
  }

  const resultsDir = path.join(ROOT, "composeApp/build/test-results/desktopTest");
  const legs = [];
  for (const { tz, label } of DETERMINISM_TIMEZONES) {
    const res = shGradle(`${GRADLEW} :composeApp:desktopTest${RERUN} --console=plain`, { env: { ...process.env, TZ: tz } });
    // Parsed NOW, before the next leg overwrites the same results directory.
    const outcomes = parseJUnitOutcomes(resultsDir);
    legs.push({ tz, label, ok: res.ok, outcomes, tail: res.out.split("\n").slice(-8).join("\n") });
  }
  const [a, b] = legs;
  const labelA = `TZ=${a.tz} (${a.label})`;
  const labelB = `TZ=${b.tz} (${b.label})`;
  const countA = Object.keys(a.outcomes).length;
  const countB = Object.keys(b.outcomes).length;

  if (countA === 0 && countB === 0) {
    // Neither leg produced a single test result: the suite failed before
    // running anything (build error). The probe measured nothing — that is
    // a FAIL that says so, never a PASS by absence of differences.
    return {
      name: "determinism",
      verdict: "FAIL",
      reason: `determinism probe could not execute: desktopTest produced no test results under either timezone — the suite fails before running (fix the build first; this is not a timezone difference):\n${a.tail}`,
      durationMs: elapsed(),
    };
  }
  if (countA === 0 || countB === 0) {
    const ran = countA > 0 ? { label: labelA, n: countA } : { label: labelB, n: countB };
    const empty = countA > 0 ? b : a;
    return {
      name: "determinism",
      verdict: "FAIL",
      reason: `Nondeterminism under timezone shift: desktopTest ran ${ran.n} test(s) under ${ran.label} but produced no results at all under TZ=${empty.tz} (${empty.label}) — the suite itself dies under that zone:\n${empty.tail}`,
      durationMs: elapsed(),
      details: { timezones: DETERMINISM_TIMEZONES.map((t) => t.tz) },
    };
  }

  const diffs = compareOutcomes(a.outcomes, b.outcomes, labelA, labelB);
  if (diffs.length > 0) {
    const lines = [
      `Nondeterminism under timezone shift — the same tree produced different outcomes under ${labelA} vs ${labelB}. Something reads ambient time or zone past the ARCH-13 net (a library default, an uninjected clock, a golden that captures "today"):`,
    ];
    for (const d of diffs.slice(0, 20)) lines.push(`  [${d.step}] ${d.test} — ${d.detail}`);
    if (diffs.length > 20) lines.push(`  … and ${diffs.length - 20} more differing test(s)`);
    return {
      name: "determinism",
      verdict: "FAIL",
      reason: lines.join("\n"),
      durationMs: elapsed(),
      details: { timezones: DETERMINISM_TIMEZONES.map((t) => t.tz), diffs: diffs.slice(0, 50) },
    };
  }

  const failedIdentically = Object.values(a.outcomes).filter((o) => o.status !== "pass" && o.status !== "skip").length;
  return {
    name: "determinism",
    verdict: "PASS",
    // Identical red is DETERMINISTIC red: the probe's claim ("no timezone
    // dependence") holds, and the failing tests already belong to
    // unitTests/goldenTrees, which fail the lane on their own merits — a
    // second FAIL here would report the same defect twice under a wrong name.
    note:
      failedIdentically > 0
        ? `${failedIdentically} test(s) failed identically under both timezones — deterministic, but red (the owning test steps report it)`
        : undefined,
    durationMs: elapsed(),
    details: { timezones: DETERMINISM_TIMEZONES.map((t) => t.tz), testsCompared: countA },
  };
}

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

  // Machine-global lease before the first device touch (contention = SKIP).
  const leaseSkip = leaseDeviceForStep("tokenDrift");
  if (leaseSkip) return { ...leaseSkip, durationMs: elapsed() };

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

// The e2e guard trio, shared by every step that drives the smoke flow on a device.
// Returns null when the harness is fully available, else the SKIP result for [name].
function maestroGuards(name) {
  if (!fs.existsSync(path.join(ROOT, "qa/e2e"))) {
    return { name, verdict: "SKIP", reason: "e2e harness not included in this project (--no-e2e)", durationMs: 0 };
  }
  if (!deviceAttached()) {
    return { name, verdict: "SKIP", reason: "no Android device/emulator attached (adb)", durationMs: 0 };
  }
  if (!maestroAvailable()) {
    return { name, verdict: "SKIP", reason: "maestro CLI not installed — curl -fsSL https://get.maestro.mobile.dev | bash", durationMs: 0 };
  }
  return null;
}

// Drives qa/e2e/smoke.yaml against whatever build is installed, with the device hardened
// for headless/CI automation. Shared by e2eSmoke (debug APK) and releaseSmoke (release
// APK) so the hardening and the honesty sweep can never drift apart between variants.
// Without the hardening, a slow or loaded emulator produces false reds that have nothing
// to do with the app:
//  - hide_error_dialogs=1 stops Android popping ANR/crash dialogs (e.g. SystemUI under load)
//    that steal focus over the app — a Maestro assert would then see only the dialog;
//  - MAESTRO_DRIVER_STARTUP_TIMEOUT gives the UiAutomator2 driver a generous budget to come
//    up on a slow emulator (the built-in default gives up too early under load).
// Both are benign, reversible, and only touch the device while the lane is driving it —
// hide_error_dialogs is restored to its pre-run value (or deleted, returning the device
// to its default) in the finally below, on every exit path.
// hide_error_dialogs suppresses the OS dialog, NEVER the underlying event — so after the
// run we grep the device log for ANR/crash lines the dialog would have shown, and FAIL on
// them. The eyes must report what automation stability had to hide.
function runMaestroSmoke(name, priorDurationMs) {
  const prevHideErrorDialogs = sh("adb shell settings get global hide_error_dialogs").out.trim();
  sh("adb shell settings put global hide_error_dialogs 1");
  sh("adb logcat -c"); // clear so the post-run dump only reflects this run
  try {
    const res = sh("maestro test qa/e2e/smoke.yaml", { env: { ...process.env, MAESTRO_DRIVER_STARTUP_TIMEOUT: "120000" } });
    if (!res.ok) {
      return {
        name,
        verdict: "FAIL",
        reason: `Maestro smoke failed (flow cites the SHELL spec clauses it proves):\n${res.out.split("\n").slice(-15).join("\n")}`,
        durationMs: priorDurationMs + res.durationMs,
      };
    }
    const anrDump = sh("adb logcat -d -b system,crash,main");
    const anrRe = /ANR in |FATAL EXCEPTION/i;
    if (anrDump.ok && anrRe.test(anrDump.out)) {
      const anrLines = anrDump.out.split("\n").filter((l) => anrRe.test(l)).slice(0, 10).join("\n");
      return {
        name,
        verdict: "FAIL",
        reason: `Maestro smoke passed, but the device log shows an ANR/crash during the run (hide_error_dialogs only suppresses the OS dialog, never the underlying event):\n${anrLines}`,
        durationMs: priorDurationMs + res.durationMs,
      };
    }
    return { name, verdict: "PASS", durationMs: priorDurationMs + res.durationMs };
  } finally {
    if (prevHideErrorDialogs && prevHideErrorDialogs !== "null") {
      sh(`adb shell settings put global hide_error_dialogs ${prevHideErrorDialogs}`);
    } else {
      sh("adb shell settings delete global hide_error_dialogs");
    }
  }
}

function stepE2eSmoke() {
  const guard = maestroGuards("e2eSmoke");
  if (guard) return guard;
  // Machine-global lease before the first device touch (contention = SKIP).
  const leaseSkip = leaseDeviceForStep("e2eSmoke");
  if (leaseSkip) return leaseSkip;
  const install = shGradle(`${GRADLEW} :composeApp:installDebug --console=plain`);
  if (!install.ok) {
    return { name: "e2eSmoke", verdict: "FAIL", reason: "installDebug failed — the APK could not be installed on the attached device", durationMs: install.durationMs };
  }
  settleAdb();
  return runMaestroSmoke("e2eSmoke", install.durationMs);
}

// Instrumented behavior tier (composeApp/src/androidInstrumentedTest) — the one step
// whose evidence crosses the process boundary. Alarms, notification channels,
// full-screen intents, PendingIntent identity, and audio routing are OS facts:
// desktopTest is a JVM, golden trees are structure, the conformance suite is static,
// and the Maestro smoke taps UI without asserting anything about the shade or the
// alarm table. Nine escaped platform-semantics defects across two real apps trace to
// exactly this blind spot; the hand-built precursor of this step caught two bugs the
// week it landed. `connectedDebugAndroidTest` builds, installs, and runs the
// instrumented suite in the app's real process on the attached device.
//
// SKIP (never FAIL) on missing infrastructure — no device, or no instrumented sources
// yet — mirroring e2eSmoke's stance: absence of the tier is recorded honestly, only
// broken behavior fails.
function stepAndroidChecks() {
  const started = Date.now();
  const instrumentedDir = path.join(ROOT, "composeApp/src/androidInstrumentedTest");
  const hasSources = fs.existsSync(instrumentedDir) &&
    walkFiles(instrumentedDir, [".kt"]).length > 0;
  if (!hasSources) {
    return {
      name: "androidChecks",
      verdict: "SKIP",
      reason: "no instrumented tests (composeApp/src/androidInstrumentedTest has no Kotlin sources)",
      durationMs: Date.now() - started,
    };
  }
  if (!deviceAttached()) {
    return {
      name: "androidChecks",
      verdict: "SKIP",
      reason: "no Android device/emulator attached (adb) — instrumented behavior needs the real process boundary",
      durationMs: Date.now() - started,
    };
  }
  // Machine-global lease before the first device touch (contention = SKIP).
  const leaseSkip = leaseDeviceForStep("androidChecks");
  if (leaseSkip) return { ...leaseSkip, durationMs: Date.now() - started };
  // Settle before Gradle's own install+drive: earlier lane steps (tokenDrift's
  // port-forwards, e2eSmoke's reinstall) can leave the transport stale — see settleAdb.
  settleAdb();
  // `--rerun` for the same evidence-integrity reason as stepUnitTests: the receipt must
  // attest tests that EXECUTED on this tree, never a replayed up-to-date verdict.
  const res = shGradle(`${GRADLEW} :composeApp:connectedDebugAndroidTest --rerun --console=plain`);
  const summary = junitSummary(path.join(ROOT, "composeApp/build/outputs/androidTest-results/connected"));
  return {
    name: "androidChecks",
    verdict: res.ok ? "PASS" : "FAIL",
    reason: res.ok
      ? undefined
      : `connectedDebugAndroidTest failed (${summary ? `${summary.failures + summary.errors} of ${summary.tests} tests` : "see output"}) — an on-device behavior claim is broken. Fix the behavior, not the test:\n${res.out.split("\n").filter((l) => /FAILED|error:|failed/i.test(l)).slice(0, 12).join("\n")}`,
    durationMs: Date.now() - started,
    details: summary ?? undefined,
  };
}

// Release-APK smoke — the behavior half of stepReleaseBuild. assembleRelease proves R8
// and the build graph COMPILE; two real bugs were only findable by *running* the release
// variant (R8 behavior differs from debug). Installs the release APK and drives the same
// Maestro smoke flow against it. Ship-time cost by design: this step exists only in the
// `release` profile, never per-change.
//
// Honesty notes, both deliberate:
//  - A template-fresh app has NO release signingConfig (the keystore belongs to whoever
//    ships), and an unsigned APK cannot be installed. That is a SKIP naming what to
//    configure, never a FAIL — a fresh scaffold must not red-bar on a keystore it was
//    never given.
//  - This step reinstalls NOTHING afterwards: the release build stays on the device,
//    which is the honest state ("what is installed is what was last proven"). The next
//    debug install over it will hit INSTALL_FAILED_UPDATE_INCOMPATIBLE (release and debug
//    signatures differ) — run `adb uninstall <applicationId>` first; the same applies in
//    reverse here, so that raw Gradle error is translated into the actionable message.
function stepReleaseSmoke() {
  const guard = maestroGuards("releaseSmoke");
  if (guard) return guard;

  let gradleText = "";
  try {
    gradleText = fs.readFileSync(path.join(ROOT, "composeApp/build.gradle.kts"), "utf8");
  } catch {
    gradleText = "";
  }
  const applicationId = gradleText.match(/applicationId\s*=\s*"([^"]+)"/)?.[1] ?? "<applicationId>";
  if (!/signingConfig/.test(gradleText)) {
    return {
      name: "releaseSmoke",
      verdict: "SKIP",
      reason:
        "release APK is unsigned — no signingConfig in composeApp/build.gradle.kts. To enable the release smoke: create a keystore (keytool -genkeypair), declare android.signingConfigs { create(\"release\") { … } } from a gitignored keystore.properties, and set buildTypes.release.signingConfig. The keystore is yours to keep out of the repo.",
      durationMs: 0,
    };
  }

  // Machine-global lease before the first device touch (contention = SKIP).
  // After the signing check on purpose: an unsigned template SKIPs on the
  // keystore without ever needing the device.
  const leaseSkip = leaseDeviceForStep("releaseSmoke");
  if (leaseSkip) return leaseSkip;

  const install = shGradle(`${GRADLEW} :composeApp:installRelease --console=plain`);
  if (!install.ok) {
    if (/INSTALL_FAILED_UPDATE_INCOMPATIBLE/.test(install.out)) {
      return {
        name: "releaseSmoke",
        verdict: "FAIL",
        reason: `installRelease refused: the device holds a build with a different signature (usually the debug build from an earlier lane step). Android never installs across signatures — run \`adb uninstall ${applicationId}\` and re-run the release profile. This is a device-state conflict, not a build defect.`,
        durationMs: install.durationMs,
      };
    }
    if (/SigningConfig|not signed|INSTALL_PARSE_FAILED_NO_CERTIFICATES/i.test(install.out)) {
      return {
        name: "releaseSmoke",
        verdict: "SKIP",
        reason: "release APK is not installable — signing is not fully configured (see composeApp/build.gradle.kts signingConfigs). Configure a release keystore to enable the release smoke.",
        durationMs: install.durationMs,
      };
    }
    return {
      name: "releaseSmoke",
      verdict: "FAIL",
      reason: `installRelease failed — the shippable APK could not be installed:\n${install.out.split("\n").filter((l) => /error|FAILURE|INSTALL_/i.test(l)).slice(0, 12).join("\n")}`,
      durationMs: install.durationMs,
    };
  }
  settleAdb();
  return runMaestroSmoke("releaseSmoke", install.durationMs);
}

// ── Audit cadence (roadmap §10 item 9) — a REPORT, never a gate ────────────
// cmp-audit (the adversarial platform-semantics audit) found six latent
// defects the first time a human happened to ask for it — which is exactly
// why it must not depend on someone remembering to ask. This step is the
// cheapest honest replacement for that memory: at ship time (release
// profile) the receipt lists which androidMain subsystems changed since
// their last RECORDED audit (qa/audits.jsonl, appended by
// node qa/record-audit.mjs). The derivation lives in
// qa/lib/audit-cadence.mjs; this step adds only the bookkeeping every step
// carries — and by construction it maps every outcome to PASS or SKIP,
// never FAIL: audit debt is a judgment call (a rename is not six latent
// defects), and a gate here would teach people to game the ledger, which
// would destroy the only value it has.
function stepAuditCadence() {
  const started = Date.now();
  const report = evaluateAuditCadence(ROOT);
  if (!report.ok) {
    return { name: "auditCadence", verdict: "SKIP", reason: report.reason, durationMs: Date.now() - started };
  }
  return {
    name: "auditCadence",
    verdict: "PASS",
    note: report.summary,
    durationMs: Date.now() - started,
    details: {
      packageRoot: report.packageRoot,
      subsystems: report.subsystems.map((s) => ({
        name: s.name,
        status: s.status,
        changedFiles: s.changedFiles,
        lastAudit: s.audit ? { sha: s.audit.sha, at: s.audit.at, by: s.audit.by } : null,
      })),
      lines: report.lines,
    },
  };
}

// ── Lane ───────────────────────────────────────────────────────────────────

// Device-dependent steps, in lane order. Used twice: receipt STRENGTH (which
// on-device steps actually PASSed — see below, where the receipt is built) and
// the --fast exclusion (with releaseBuild added), so the "device/slow tier"
// can never mean two different lists.
const DEVICE_STEPS = ["e2eSmoke", "tokenDrift", "androidChecks", "releaseSmoke"];

// ── Fast-mode memoization of the pure-Node steps (qa/lib/step-cache.mjs) ────
// These five steps run no Gradle, shell out to nothing, and are pure functions
// of files on disk — so in FAST mode an unchanged input set reuses the last
// PASS as verdict "CACHED" (rendered distinctly; only a PASS is ever reused,
// a cached FAIL/SKIP always re-runs). THE FULL LANE NEVER CONSULTS THE CACHE —
// deliberately: it keeps the integrity property absolute rather than "absolute
// unless a cache says otherwise". A full run still WRITES entries so the next
// fast run benefits. schemaHistory is NOT here even though it runs no Gradle:
// it shells out to git and its verdict depends on HEAD state, not only file
// bytes — memoizing it on a content hash could go silently stale.
//
// Each input set is the step's ACTUAL read surface, over-declared where cheap
// (a too-broad set only costs cache misses; a too-narrow one is a
// silently-stale gate — the worst possible bug here):
//   specCoverage     reads specs/*.spec.md + citations under composeApp/src
//                    and qa/e2e (qa/lib/spec-coverage.mjs)
//   approvals        reads qa/approvals.json + every governed artifact file:
//                    specs/, docs/features/, docs/ARCHITECTURE.md, and the
//                    exemplar/theme/components Kotlin under composeApp/src
//                    (qa/lib/approvals.mjs listGovernedArtifacts)
//   componentStories reads commonMain presentation/components and desktopMain
//                    inspector sources — both under composeApp/src
//   reachability     reads commonMain Kotlin (composeApp/src) + the unrouted
//                    declarations in docs/features/
//   archDoc          reads docs/ARCHITECTURE.md, docs/adr/, specs/intent.md
//                    (over-declared to all of specs/), and every source-set's
//                    Kotlin under composeApp/src (qa/lib/arch-doc.mjs)
const MEMOIZED_STEP_INPUTS = {
  specCoverage: ["specs", "composeApp/src", "qa/e2e"],
  approvals: ["qa/approvals.json", "specs", "docs/features", "docs/ARCHITECTURE.md", "composeApp/src"],
  componentStories: ["composeApp/src"],
  reachability: ["composeApp/src", "docs/features"],
  archDoc: ["docs/ARCHITECTURE.md", "docs/adr", "specs", "composeApp/src"],
};

const memoized = (stepName, stepFn) => () =>
  memoizeStep({ fast, root: ROOT, stepName, inputs: MEMOIZED_STEP_INPUTS[stepName], run: stepFn });

const stepSpecCoverageMemo = memoized("specCoverage", stepSpecCoverage);
const stepApprovalsMemo = memoized("approvals", stepApprovals);
const stepComponentStoriesMemo = memoized("componentStories", stepComponentStories);
const stepReachabilityMemo = memoized("reachability", stepReachability);
const stepArchDocMemo = memoized("archDoc", stepArchDoc);

const stepsForProfile = {
  // scaffold: what `create-cmp --verify` proves at stamp time — specCoverage,
  // the full JVM tier (unit + conformance + golden + UI tests) plus the Android build.
  scaffold: [stepHarnessIntegrity, stepSpecCoverageMemo, stepApprovalsMemo, stepComponentStoriesMemo, stepReachabilityMemo, stepArchDocMemo, stepSchemaHistory, stepBuild, stepUnitTests],
  local: [
    // First, always: every verdict below is only worth what the lane issuing
    // it is worth.
    stepHarnessIntegrity,
    stepSpecCoverageMemo,
    stepApprovalsMemo,
    stepComponentStoriesMemo,
    stepReachabilityMemo,
    stepArchDocMemo,
    stepSchemaHistory,
    stepBuild,
    // Release stays OUT of `scaffold`: stamp-time --verify promises a green first build, and
    // an R8 pass would add minutes to every scaffold to re-prove what this step proves here.
    // local + ci is where release rot gets caught before it reaches anyone.
    stepReleaseBuild,
    stepUnitTests,
    stepConformance,
    stepGoldenTrees,
    stepTokenDrift,
    stepA11y,
    stepE2eSmoke,
    // androidChecks joins local BY the file's own convention, not despite it: local's
    // contract (see USAGE) is "everything; device-dependent steps SKIP when no device is
    // attached" — device presence is the opt-in, exactly as e2eSmoke and tokenDrift
    // already work. A developer with no device attached pays nothing here; one who
    // attached an emulator has already opted into the device tier's cost. Hiding this
    // step in ci-only would make local's documented contract a lie and re-open the gap
    // this tier closes (androidMain test-invisible in the profile people actually run).
    // Last on purpose: the cheap desktop verdicts and the smoke land first.
    stepAndroidChecks,
  ],
};
// ci = local + the determinism probe's row — the first place ci diverges
// from local. The probe is OPT-IN (the step SKIPs unless --determinism was
// passed: it doubles the JVM test tier's cost), but its row lives in the ci
// profile so a ci receipt always records whether the probe ran — an honest,
// visible gap beats an invisible one ("SKIPs are recorded so the pipeline
// stays honest", per the profile's own contract). local deliberately does
// NOT carry the row: the per-change developer profile is not where a
// deliberate double-run belongs.
stepsForProfile.ci = [...stepsForProfile.local, stepDeterminism];
// release = everything ci proves PLUS the audit-cadence report and the
// release-APK behavior smoke. The expensive proofs are profile-tiered by
// decision: per-change stays fast (local/ci pay for the release COMPILE via
// releaseBuild, already in the set), and the release-variant *behavior* cost
// lands once, at ship time. auditCadence (a report, never a gate) also
// belongs to ship time — "what moved in androidMain since its last
// adversarial audit?" is the question asked before shipping, not per edit.
// releaseSmoke runs last so the device ends the run holding the exact build
// that was proven.
stepsForProfile.release = [...stepsForProfile.ci, stepAuditCadence, stepReleaseSmoke];

if (!stepsForProfile[profile]) {
  console.error(`Unknown profile "${profile}" — use scaffold | local | ci | release.`);
  process.exit(2);
}

// ── Bare --determinism: the probe, nothing else, and NO receipt ─────────────
// "Run it alone" means alone: no other steps, and deliberately no
// qa/evidence/latest.json. The done-gate (qa/receipt-check.mjs) validates a
// receipt by verdict + content hash — a receipt whose steps are one probe
// would satisfy it while attesting almost nothing, so a probe-only run must
// never mint one. The lane marker IS still stamped: the probe runs Gradle
// and owes the preview daemon the same coexistence courtesy as the lane.
if (determinism && !profileExplicit) {
  fs.mkdirSync(path.dirname(LANE_MARKER), { recursive: true });
  fs.writeFileSync(LANE_MARKER, `${process.pid} ${new Date().toISOString()}\n`);
  let probe;
  try {
    probe = stepDeterminism();
  } finally {
    fs.rmSync(LANE_MARKER, { force: true });
  }
  if (asJson) {
    console.log(JSON.stringify(probe, null, 2));
  } else {
    const mark = probe.verdict === "PASS" ? "✓" : "✗";
    console.log(`${mark} determinism: ${probe.verdict}${probe.note ? ` (${probe.note})` : ""}${probe.reason ? ` — ${probe.reason}` : ""}`);
    console.log("\n(probe-only run — no receipt written; the full lane is where evidence is earned)");
  }
  process.exit(probe.verdict === "FAIL" ? 1 : 0);
}

// ── --fast: the inner loop, mechanically unable to claim done ───────────────
// The genuinely slow tier is device/release work — every DEVICE_STEPS entry
// (Gradle install + emulator + Maestro + instrumented runner) plus
// releaseBuild (R8 + lintVital, the slow release COMPILE). --fast filters
// that tier out of whatever profile resolved, UNCONDITIONALLY — device
// attached or not — so a small change gets its did-I-break-anything-obvious
// signal in JVM time. The rest of the profile still runs — but cheaply: the
// pure-Node steps reuse an unchanged PASS from the step cache (CACHED — see
// the memoization block above), the Gradle test steps drop --rerun (see
// RERUN above), and unitTests scopes itself to the working-tree change
// (see stepUnitTests). The loophole is closed at the receipt, not by
// convention: mode "fast" is
// recorded, no evidence rung is derived (qa/lib/evidence-level.mjs), and
// qa/receipt-check.mjs refuses a fast receipt as done evidence.
const FAST_EXCLUDED_NAMES = [...DEVICE_STEPS, "releaseBuild"];
const STEP_FN_BY_NAME = {
  e2eSmoke: stepE2eSmoke,
  tokenDrift: stepTokenDrift,
  androidChecks: stepAndroidChecks,
  releaseSmoke: stepReleaseSmoke,
  releaseBuild: stepReleaseBuild,
};
for (const name of FAST_EXCLUDED_NAMES) {
  if (!STEP_FN_BY_NAME[name]) {
    // Drift guard: a new device-tier step must be mapped here or --fast would silently run it.
    console.error(`internal: fast-excluded step "${name}" has no entry in STEP_FN_BY_NAME — fix qa/verify.mjs`);
    process.exit(2);
  }
}
const FAST_EXCLUDED_FNS = new Set(FAST_EXCLUDED_NAMES.map((name) => STEP_FN_BY_NAME[name]));
const laneSteps = fast
  ? stepsForProfile[profile].filter((fn) => !FAST_EXCLUDED_FNS.has(fn))
  : stepsForProfile[profile];
const fastExcluded = fast
  ? FAST_EXCLUDED_NAMES.filter((name) => stepsForProfile[profile].includes(STEP_FN_BY_NAME[name]))
  : [];

if (fast) {
  console.error(
    [
      "⚡⚡ FAST MODE — INNER LOOP ONLY, NOT THE DONE-GATE ⚡⚡",
      `   skipping the device/release tier: ${fastExcluded.join(", ") || "(none in this profile)"}`,
      '   this run\'s receipt records mode "fast", earns no evidence rung, and can NEVER satisfy "done"',
      "   run the full lane once (node qa/verify.mjs) before you finish",
    ].join("\n"),
  );
}

// Stamp the lane marker for the run's duration (coexistence defense 1 above);
// always removed, even on a failing step, so the eyes only ever defer briefly.
fs.mkdirSync(path.dirname(LANE_MARKER), { recursive: true });
fs.writeFileSync(LANE_MARKER, `${process.pid} ${new Date().toISOString()}\n`);
const laneStartedAt = Date.now(); // for the flight-recorder entry's durationMs
const steps = [];
try {
for (const step of laneSteps) {
  const result = step();
  steps.push(result);
  if (!asJson) {
    // CACHED (fast mode only — see the memoization block above) renders with
    // its own mark and "unchanged since" note so a reused verdict is never
    // mistakable for a fresh execution.
    const mark = result.verdict === "PASS" ? "✓" : result.verdict === "CACHED" ? "⚡" : result.verdict === "SKIP" ? "→" : "✗";
    console.log(`${mark} ${result.name}: ${result.verdict}${result.note ? ` (${result.note})` : ""}${result.reason ? ` — ${result.reason.split("\n")[0]}` : ""}`);
  }
  if (result.name === "build" && result.verdict === "FAIL") break; // nothing downstream is meaningful
}
} finally {
  fs.rmSync(LANE_MARKER, { force: true });
  // The device lease (if a device step took it) is held to the very end of the
  // run — see the scope decision at leaseDeviceForStep. Release is idempotent
  // and never deletes a foreign holder's lease.
  if (laneDeviceLease) releaseDeviceLease(laneDeviceLease);
}

// CACHED counts as PASS for the lane verdict (it IS a prior PASS, reused only
// in fast mode on an unchanged input set) — but it stays CACHED on the
// receipt, visibly distinct, so a fast receipt can never be read as if every
// step freshly executed.
const verdict = steps.some((s) => s.verdict === "FAIL") ? "FAIL" : "PASS";

// Receipt STRENGTH — a desktop-only green and an on-device green are different
// claims, and the difference should never live only in the SKIP lines. Device-
// dependent steps that actually RAN (PASSed) are named on the receipt and in the
// verdict line: "PASS (on-device: e2eSmoke)" vs "PASS (desktop-only)".
// (DEVICE_STEPS itself is defined above the lane — it also drives --fast.)
const onDeviceSteps = steps.filter((s) => DEVICE_STEPS.includes(s.name) && s.verdict === "PASS").map((s) => s.name);
const strengthLabel = onDeviceSteps.length ? `on-device: ${onDeviceSteps.join("+")}` : "desktop-only";

// Receipt RUNG — the evidence ladder (qa/lib/evidence-level.mjs): the coarse,
// named grade (L0 scaffold / L1 desktop / L2 device / L3 release) DERIVED from
// which steps actually ran and PASSed. The strength string above stays as the
// fine print; the rung is added alongside, never in place of it. null on FAIL —
// a failed lane has no rung. null on a --fast run too: the inner loop is a
// signal, never evidence, so a fast receipt derives NO rung at all.
const level = evidenceLevel(steps, profile, { mode });

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
/**
 * The receipt's harness summary — compact by design. The per-file detail lives
 * on the harnessIntegrity step; this is the part a receipt-holder needs to
 * identify the lane, plus the names of any modified files (an auditor told
 * "not intact" and not told which files has been given a rumour, not a fact).
 */
function harnessForReceipt() {
  const row = steps.find((st) => st.name === "harnessIntegrity");
  const r = row?.harness ?? checkHarnessIntegrity(ROOT);
  const summary = {
    name: r.name,
    version: r.version,
    sha256: r.sha256,
    status: r.status,
    intact: r.status === "intact",
  };
  if (r.status === "modified") {
    summary.modified = r.modified;
    summary.missing = r.missing;
    summary.extra = r.extra;
  }
  return summary;
}

const inputs = computeInputsHash(ROOT);

// The receipt. Deterministic key order; ONE volatile timestamp field.
// commit.sha is the parent HEAD at run time (you cannot know the sha of the
// commit the receipt will be part of); commit.dirty lists what was uncommitted.
const receipt = {
  schema: "cmp-evidence/1",
  profile,
  // "full" is the done-gate; "fast" (--fast) excluded the device/release tier
  // and is REFUSED by qa/receipt-check.mjs — a fast run can never end a session
  // as "done". Receipts predating this field are treated as full.
  mode,
  verdict,
  commit: {
    sha: tryGit("rev-parse HEAD"),
    dirty: tryGitLines("status --porcelain").map((l) => l.slice(3)).sort(),
  },
  inputs: {
    hash: inputs.hash,
    fileCount: inputs.fileCount,
  },
  steps,
  // WHICH LANE issued this verdict. A receipt that cannot name its own harness
  // can only be checked against the tree it came from; naming the version and
  // the region digest lets a third party who holds the receipt ask the harder
  // question — was this the real published lane? — without the tree at all.
  //
  // `intact` is the LOCAL claim only: unmodified since installed. It is a
  // checksum, not a signature, and someone who edits the lane can edit this
  // too. What they cannot edit is what the registry published under that
  // version, which is why `version` + `sha256` travel together.
  harness: harnessForReceipt(),
  strength: { onDeviceSteps },
  evidenceLevel: level,
  artifacts,
  toolVersions: {
    node: process.version,
    platform: `${process.platform}-${process.arch}`,
  },
  generatedAt: new Date().toISOString(),
};

fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
fs.writeFileSync(path.join(EVIDENCE_DIR, "latest.json"), `${JSON.stringify(receipt, null, 2)}\n`);
// latest.json is the single receipt-of-record. Commit it with your change: the
// studio console's Evidence audit trail reconstructs the full history from the
// git log of this file — every commit is one verified, attributed state.

// The README's evidence badge is DERIVED from the receipt just written — an
// output, never a gate, so it runs after the verdict and cannot change it. It
// renders the rung together with the commit it was attested against, so the
// sentence stays true as the tree moves on (qa/lib/evidence-badge.mjs).
const badge = updateReadmeBadge(ROOT);

// ── Flight recorder (roadmap §10 item 5) — the lane journals its own run ────
// One JSON line per run into qa/flight-recorder.jsonl (committed, and
// excluded from the receipt's hashed surface — qa/lib/flight-recorder.mjs
// carries the whole rationale). Appended AFTER the receipt so the entry
// records the final verdict and rung. A failed append must never fail the
// lane — a recorder that breaks the thing it observes is worse than no
// recorder — so the failure degrades to a note in the lane's own output,
// which is itself the honest record of the degradation.
//
// --no-journal is the ONE exemption, and qa/watch.mjs passes it on every
// save-triggered run. Same rule the README badge obeys, for the same reason:
// THE INNER LOOP DOES NOT WRITE TO COMMITTED FILES. A watcher journaling every
// save would add hundreds of lines a day to a committed file — turning the
// app's history into keystroke noise and leaving a permanently-dirty tree in
// the loop the recorder exists to observe. What survives is every full lane
// and every DELIBERATE fast run, which is what the retrospective's questions
// actually rest on (SKIP reasons, degraded paths, the longest stretch with no
// full lane). qa/retrospective.mjs discloses the exemption in its own output
// so the fast-vs-full ratio is never read as a complete census.
const flight = noJournal
  ? { ok: true, skipped: true }
  : appendFlightRecord(
      ROOT,
      buildFlightEntry({
        profile,
        mode,
        verdict,
        evidenceLevel: level,
        steps,
        sha: receipt.commit.sha,
        durationMs: Date.now() - laneStartedAt,
        onDeviceSteps,
        degraded: DEGRADED_PATHS,
      }),
    );
if (!flight.ok) {
  console.error(`· flight recorder: journal append failed (${flight.reason}) — the lane verdict is unaffected, but this run is missing from qa/flight-recorder.jsonl`);
}

if (asJson) {
  console.log(JSON.stringify(receipt, null, 2));
  if (fast) {
    console.error(`⚡⚡ FAST MODE verdict: ${verdict} — INNER LOOP ONLY, not done. Skipped: ${fastExcluded.join(", ") || "(none)"}. Run the full lane (node qa/verify.mjs) before you finish.`);
  }
} else if (fast) {
  // Deliberately NOT the full lane's verdict-line shape: fast-green must never
  // be mistakable for done-green.
  console.log(
    `\n${verdict === "PASS" ? "⚡⚡" : "❌"} verify lane [FAST — INNER LOOP ONLY, NOT DONE]: ${verdict} (skipped device/release tier: ${fastExcluded.join(", ") || "none"}) — this fast receipt satisfies no done-gate; run the full lane (node qa/verify.mjs) once before you finish`,
  );
} else {
  console.log(`\n${verdict === "PASS" ? "✅" : "❌"} verify lane: ${verdict}${level ? ` · ${level.rung} ${level.name}` : ""} (${strengthLabel}) — receipt written to qa/evidence/latest.json${badge.changed ? ` and ${README_REL_PATH}'s evidence badge refreshed` : ""} (commit ${badge.changed ? "them" : "it"} with your change)`);
}

// The audit-cadence nudges print in the human path, not only inside the
// receipt JSON — a ship-time report that lives only in a JSON field is a
// report nobody reads at ship time. Nudges only; a gate this is not.
if (!asJson) {
  const auditStep = steps.find((s) => s.name === "auditCadence");
  const auditLines = auditStep?.details?.lines ?? [];
  if (auditLines.length > 0) {
    console.log("\naudit cadence (report, never a gate):");
    for (const l of auditLines) console.log(`  ${l}`);
  }
}

process.exit(verdict === "PASS" ? 0 : 1);
