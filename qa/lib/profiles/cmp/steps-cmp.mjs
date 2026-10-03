// steps-cmp.mjs — the Compose Multiplatform STEP PACK (evidence-economics S8b).
//
// Every step the lane runs against a create-cmp app, behind one factory. The
// SPINE — argument parsing, the subprocess helper with its deadline, the
// markers, the runner (qa/lib/lane-runner.mjs), the receipt, the journal —
// lives in qa/verify.mjs and knows nothing about Gradle, adb, Maestro or
// composeApp/. THIS file knows nothing about receipts or argv. A Kotlin
// backend supplies a different pack to the same spine; that is the split
// payment-blueprint had to re-implement by hand (2,769 lines) because the two
// were one file.
//
// `ctx` is everything the steps borrow from the spine, passed explicitly so
// the borrowing is visible: the project root, the Gradle wrapper, the
// `--rerun` suffix, the run's mode flags, the subprocess helpers (sh reads the
// running step's deadline; shGradle adds the KSP self-heal), git helpers, and
// the DEGRADED_PATHS list the receipt reports.
//
// The bodies below are the lane's own steps, moved verbatim from verify.mjs.

import fs from "node:fs";
import path from "node:path";
import { compareTokenDrift } from "./token-drift.mjs";
import { evaluateApprovalsGate } from "../../approvals.mjs";
import { clauseTierCoverage, listFlowFiles, scanCitations, scanSpecClauses, walkFiles } from "../../spec-coverage.mjs";
import { specModelFrom } from "../../spec-model.mjs";
import { layout as cmpLayout, tiers as cmpTiers, reports, grammar as cmpGrammar } from "./declarations.mjs";
import { RENDER_MARKER_FRESH_MS, RENDER_MARKER_NAME } from "../../lane-markers.mjs";

// The scanner's model, built from this profile's own declarations — the pack
// hands its facts to the core rather than round-tripping through the manifest.
const SPEC_MODEL = (() => {
  const r = specModelFrom({ id: "cmp", layout: cmpLayout, tiers: cmpTiers, grammar: cmpGrammar });
  if (!r.ok) throw new Error(r.reason);
  return r.model;
})();
/** Where the flows the lane runs live. */
const E2E_FLOW_DIR = cmpLayout.flows.dir;
import { evaluateComponentStoryParity } from "./component-stories.mjs";
import { evaluateReachability } from "./reachability.mjs";
import { evaluateE2eCoverage } from "./e2e-coverage.mjs";
import { memoizeStep } from "../../step-cache.mjs";
import { changedWorkingTreePaths, deriveAffectedFilter } from "../../affected-tests.mjs";
import { affected as cmpAffected } from "./affected.mjs";
import { acquireDeviceLease, releaseDeviceLease, formatHolder } from "./device-lease.mjs";
import { ARCH_DOC_REL_PATH, SECTION_IDS, regenerateArchDoc } from "../../arch-doc.mjs";
import { DETERMINISM_TIMEZONES, compareOutcomes, parseReportOutcomes, reportFormatProblem } from "../../determinism.mjs";
import { evaluateAuditCadence } from "../../audit-cadence.mjs";
import { androidChecksOutcome } from "./android-checks.mjs";
import { deviceLogIncidents, maestroOutcome, parseMaestroJunit } from "./maestro.mjs";
import { ensureDevice, releaseDevice } from "./device-provider.mjs";
import { checkHarnessIntegrity, describeIntegrity, LOCK_PATH } from "../../harness-lock.mjs";
import { stepDisplayName } from "../../lane-runner.mjs";
import { CMP_LADDER } from "./ladder.mjs";

/**
 * @param {object} ctx
 * @param {string} ctx.ROOT project root
 * @param {string} ctx.HERE the qa/ directory
 * @param {boolean} ctx.fast
 * @param {boolean} ctx.determinism
 * @param {string} ctx.profile
 * @param {"full"|"fast"} ctx.mode
 * @param {Function} ctx.sh subprocess helper (throws StepTimeout past the step's deadline)
 * @param {Function} ctx.tryGit
 * @param {Function} ctx.tryGitLines
 * @param {string[]} ctx.DEGRADED_PATHS degraded-path activations the receipt reports
 * @returns {{stepsForProfile: Record<string, Function[]>, DEVICE_STEPS: string[],
 *   FAST_EXCLUDED_NAMES: string[], STEP_FN_BY_NAME: Record<string, Function>,
 *   stepDeterminism: Function, releaseLease: () => void}}
 */
/**
 * Is the inspector server in this app at all? `--no-inspector` deletes the
 * androidDebug inspector sources (the feature manifest's paths), so the file
 * that answers `/inspect/health` is simply not there. Asked BEFORE the lane
 * touches a device: the 2026-09-08 context audit found this step booting a
 * headless emulator first and only then discovering the endpoint it wanted to
 * read was never shipped — a device paid for by every `--no-inspector` app,
 * for a step that then SKIPped. Structural, so the receipt stays done-evidence.
 */
export function inspectorShipped(root) {
  const base = path.join(root, "composeApp", "src", "androidDebug");
  if (!fs.existsSync(base)) return false;
  const stack = [base];
  while (stack.length) {
    const dir = stack.pop();
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (e.isDirectory()) stack.push(path.join(dir, e.name));
      else if (e.name === "InspectorHttpServer.kt") return true;
    }
  }
  return false;
}


export function createCmpSteps(ctx) {
  const { ROOT, HERE, fast, determinism, profile, mode, sh, tryGit, tryGitLines, DEGRADED_PATHS } = ctx;

  // ── Gradle is THIS profile's build tool ───────────────────────────────────
  // The wrapper invocation, the integrity flag and the coexistence dance used
  // to live in qa/verify.mjs and arrive through ctx — which meant the runner,
  // the spine every stack shares, knew what Gradle is, where composeApp/build
  // sits, and what a KSP cache collision looks like. Stage 0 PR 6a moved them
  // here: the runner hands the pack a plain subprocess helper, and the pack
  // wraps it with whatever its own toolchain needs.
  const GRADLEW = process.platform === "win32" ? "gradlew.bat" : "./gradlew";

  // `--rerun` exists for evidence integrity: it stops Gradle's build cache
  // replaying a PASS recorded against a different tree into a receipt that
  // claims tests executed. That belongs to runs producing integrity-bearing
  // artifacts — and a --fast run does not: its receipt already declares itself
  // non-evidence (mode "fast", no evidence rung, refused by
  // qa/receipt-check.mjs), so forcing execution there paid an integrity tax to
  // protect an artifact with nothing to protect.
  const RERUN = fast ? "" : " --rerun";

  // ── Preview-daemon coexistence ───────────────────────────────────────────
  // The preview daemon (the eyes) and this lane both spawn Gradle against this
  // project and share <buildDir>/kspCaches, whose KSP incremental storage is
  // single-owner — two concurrent builds throw "Storage for [...] is already
  // registered" and one side dies. Three defenses, all automatic:
  //   1. COORDINATE (this lane -> the daemon): the lane stamps its marker for
  //      its duration (qa/.lane-in-progress, core state); the preview service
  //      defers renders while it exists (mtime-bounded, so a crashed lane
  //      never wedges the eyes for long).
  //   2. COORDINATE (the daemon -> this lane), the symmetric half: the daemon
  //      stamps its OWN marker for the duration of a render's Gradle build;
  //      shGradle waits for it to clear (or go stale) before launching this
  //      lane's own Gradle command — same mtime-bounded shape, so a crashed
  //      daemon never wedges the lane for long either.
  //   3. SELF-HEAL: a Gradle step that still hits the collision clears
  //      kspCaches and retries once — the manual recovery that always worked,
  //      automated.
  // The render marker is the PROVIDER's, so it lives under this profile's own
  // buildDir; qa/lib/lane-markers.mjs names the file and the freshness bound
  // so the daemon and the lane can never disagree about either.
  const KSP_COLLISION_RE = /Storage for \[[^\]]*\] is already registered/;
  const BUILD_DIR = cmpLayout.buildDir;
  const RENDER_MARKER = path.join(ROOT, ...BUILD_DIR.split("/"), RENDER_MARKER_NAME);
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
    fs.rmSync(path.join(ROOT, ...BUILD_DIR.split("/"), "kspCaches"), { recursive: true, force: true });
    const retry = sh(cmd, opts);
    retry.durationMs += first.durationMs;
    retry.selfHealed = "ksp-cache-collision";
    DEGRADED_PATHS.push("ksp-cache-collision: cleared kspCaches and retried the Gradle step");
    return retry;
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

// ── The lane's device (qa/lib/device-provider.mjs) ──────────────────────────
// Provisioned ONCE per run by the first device step that needs it: an attached
// device is used as-is; with none attached the lane boots a headless emulator
// (bounded) and shuts it down in the runner's finally. A device that cannot
// be provisioned is an ERROR row — "could not check" — never a SKIP that
// reads as an honest gap and is then ignored forever (2026-09-03: the whole
// tier had SKIPped on every receipt anyone looked at). The one SKIP left is
// the explicit opt-out CMP_DEVICE=none, marked skipKind "environment" so the
// receipt check refuses it as done-evidence.
let laneDevice = null;

function deviceRow(stepName, d) {
  if (d.optOut) return { name: stepName, verdict: "SKIP", skipKind: "environment", reason: d.reason, durationMs: 0 };
  return { name: stepName, verdict: "ERROR", reason: `could not provision a device: ${d.reason}`, durationMs: 0 };
}

/** null when the lane has a device; otherwise the row the step returns verbatim. */
function ensureLaneDevice(stepName) {
  if (!laneDevice) {
    laneDevice = ensureDevice({ sh, log: (line) => console.error(`· ${line}`) });
    if (laneDevice.ok && laneDevice.booted) {
      console.error(`· booted ${laneDevice.avd} headless (${laneDevice.serial}) in ${Math.round(laneDevice.bootMs / 1000)} s — shut down when the lane exits (CMP_KEEP_DEVICE=1 keeps it)`);
    }
  }
  return laneDevice.ok ? null : deviceRow(stepName, laneDevice);
}

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
        skipKind: "environment",
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
      skipKind: "environment",
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

  // ADR-0010 — THE FLOOR, BEFORE THE STATUS. A region with no engine module
  // reads `intact` and is telling the truth: it is unmodified since it was
  // locked. It is simply not a lane, and a lane that is not there cannot vouch
  // for anything.
  //
  // On an ordinary tree this cannot fire — a region with no qa/verify.mjs has
  // nothing to run this check. It fires in the one mode ADR-0008 rejects: a
  // lane running from OUTSIDE the region, over a project whose qa/ holds only
  // declarations. There the lane runs, this step executes, and until now it
  // returned PASS over a project carrying none of the code that produced the
  // verdict — the residue that ADR predicted, in the row that exists to
  // prevent exactly it.
  if (r.vacuous) {
    return {
      ...base,
      verdict: "FAIL",
      reason:
        `the harness region holds ${r.fileCount} file(s) and NONE of them is engine code — this is not a lane, so it cannot vouch for one. ` +
        `A lane running from outside the region (node_modules) over a project whose qa/ carries only declarations produces exactly this: ` +
        `install the lane into the tree (\`prooflane init\`, or \`prooflane upgrade\` to re-vendor it) so the code that issues the verdict is the code the receipt binds`,
    };
  }

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

  const clauses = scanSpecClauses(ROOT, SPEC_MODEL);
  const tags = scanCitations(ROOT, SPEC_MODEL);
  const searchDirs = SPEC_MODEL.citationRoots.map((rel) => path.join(ROOT, ...rel.split("/")));
  const files = searchDirs.flatMap((d) => walkFiles(d, [...SPEC_MODEL.citationExts, ...(SPEC_MODEL.flows ? SPEC_MODEL.flows.exts : [])]));

  const citedIds = new Set(tags.map((t) => t.id));
  const orphanClauses = [...clauses.entries()].filter(([, c]) => !c.withdrawn).filter(([id]) => !citedIds.has(id));
  const orphanTags = tags.filter((t) => !clauses.has(t.id) || clauses.get(t.id).withdrawn);

  const tiers = clauseTierCoverage(clauses, tags, SPEC_MODEL);

  if (orphanClauses.length === 0 && orphanTags.length === 0 && tiers.unmetTier.length === 0) {
    // Tier visibility, still not a gate for UNDECLARED clauses (industry rule:
    // instrument before you police). A clause cited only from desktop-tier tests can
    // still hide a platform-behavior bug — both production apps shipped
    // alarm/notification defects behind clauses that were "covered" by JVM tests
    // androidMain never ran under. The line names them; the instrumented seam
    // (androidChecks) is where such clauses earn a citation.
    //
    // A clause that DECLARED `[tier: …]` is policed above — that is the second move
    // this note's first move was always waiting for.
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
  // The competence check, first: an existing-but-blind citation is a subtler
  // failure than a missing one, and its message has to say WHY the citation it
  // can see does not count.
  for (const u of tiers.unmetTier) {
    if (u.unknown) {
      lines.push(
        `  [${u.id}] ${u.file} — declares [tier: ${u.requiredTier}], a requirement this profile does not define. ` +
          `Declared requirements: ${Object.keys(SPEC_MODEL.tiers.satisfying).join(", ")}. Fix the tag or declare the tier in the profile.`,
      );
      continue;
    }
    const has = u.tiers.length ? `cited only from ${u.tiers.join(", ")}` : "cited by nothing";
    lines.push(
      `  [${u.id}] ${u.file} — declares [tier: ${u.requiredTier}] but is ${has}. ` +
        `A test on those tiers cannot observe this promise (no process lifecycle, no OS facts, no real device). ` +
        `Add a citing test in ${SPEC_MODEL.tiers.satisfying[u.requiredTier].join(" or ")} — and note that tier SKIPPING for want of a device ` +
        `leaves this clause unproven, which is the point: "I could not check this" is a failure, not a quieter rung.`,
    );
  }
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
// Every real feature has a device journey (qa/lib/e2e-coverage.mjs): a screen
// plus a spec means at least one live clause cited from a flow the lane runs.
// Pure Node. This is the gate that makes "write the Maestro flow" a lane
// verdict instead of a habit (Karel, 2026-09-03).
function stepE2eCoverage() {
  const started = Date.now();
  const { verdict, reason, details } = evaluateE2eCoverage(ROOT);
  return { name: "e2eCoverage", verdict, reason, durationMs: Date.now() - started, details };
}

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
  // Named explicitly: a function returned from a factory has no inferred name,
  // so the runner narrated conformance / goldenTrees / a11y as null and gave
  // them the default deadline. Same latent class as the memoized wrappers.
  const step = () => {
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
  Object.defineProperty(step, "name", { value: `step${name.charAt(0).toUpperCase()}${name.slice(1)}` });
  return step;
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
      const filter = deriveAffectedFilter(changed, cmpAffected);
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

  const reportProblem = reportFormatProblem(reports);
  if (reportProblem) return { name: "determinism", verdict: "ERROR", reason: reportProblem, durationMs: elapsed() };
  const resultsDir = path.join(ROOT, reports.dir, "desktopTest");
  const legs = [];
  for (const { tz, label } of DETERMINISM_TIMEZONES) {
    const res = shGradle(`${GRADLEW} :composeApp:desktopTest${RERUN} --console=plain`, { env: { ...process.env, TZ: tz } });
    // Parsed NOW, before the next leg overwrites the same results directory.
    // The DISPATCHER, not one format's parser. A pack that named the JUnit
    // reader directly would silently ignore its own `reports.format` the day a
    // profile declared tap or ctrf — reading zero tests and calling it green.
    const outcomes = parseReportOutcomes(resultsDir, { format: reports.format });
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

  // WHICH STEP owns a test class is this stack's knowledge: Kotlin class-name
  // conventions on the left, this pack's step names on the right. It lived in
  // qa/lib/determinism.mjs until Stage 0 A3, where the core knowing four step
  // names was stamping them onto other stacks' determinism diffs.
  const attribute = (classname) => {
    if (/GoldenTreeTest$/.test(classname)) return "goldenTrees";
    if (/ArchitectureConformanceTest$/.test(classname)) return "conformance";
    if (/A11yConformanceTest$/.test(classname)) return "a11y";
    return "unitTests";
  };
  const diffs = compareOutcomes(a.outcomes, b.outcomes, labelA, labelB, attribute);
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

  if (!inspectorShipped(ROOT)) {
    return { name: "tokenDrift", verdict: "SKIP", skipKind: "structure", reason: "inspector not shipped in this project (--no-inspector) — no endpoint to read, no device needed", durationMs: elapsed() };
  }

  const device = ensureLaneDevice("tokenDrift");
  if (device) return { ...device, durationMs: elapsed() };

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
  if (!fs.existsSync(path.join(ROOT, E2E_FLOW_DIR))) {
    return { name, verdict: "SKIP", skipKind: "structure", reason: "e2e harness not included in this project (--no-e2e)", durationMs: 0 };
  }
  if (listFlowFiles(ROOT, SPEC_MODEL).length === 0) {
    return { name, verdict: "SKIP", skipKind: "structure", reason: `${E2E_FLOW_DIR}/ holds no flows (*.yaml) — nothing to drive`, durationMs: 0 };
  }
  if (!maestroAvailable()) {
    return { name, verdict: "SKIP", skipKind: "environment", reason: "maestro CLI not installed — curl -fsSL https://get.maestro.mobile.dev | bash", durationMs: 0 };
  }
  const device = ensureLaneDevice(name);
  if (device) return device;
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
// EVERY flow runs (2026-09-03). This used to run qa/e2e/smoke.yaml by name
// while spec coverage counted a citation from ANY yaml under qa/e2e — four
// hand-written per-feature flows on the showcase satisfied clauses without
// ever executing. The directory runs in ONE Maestro session (one driver
// start-up, per-flow rows from the JUnit report); listFlowFiles is the same
// list the coverage scan reads, so cited ⊆ executed holds by construction.
/** Driver start-up plus three minutes per flow: the Maestro run's own bound, whatever the journal says. */
const E2E_RUN_BOUND_MS = (flowCount) => 120_000 + 180_000 * Math.max(1, flowCount);

function runMaestroSmoke(name, priorDurationMs) {
  const flows = listFlowFiles(ROOT, SPEC_MODEL);
  const report = path.join(ROOT, "qa-artifacts", `maestro-${name}.xml`);
  fs.mkdirSync(path.dirname(report), { recursive: true });
  fs.rmSync(report, { force: true });
  const prevHideErrorDialogs = sh("adb shell settings get global hide_error_dialogs").out.trim();
  sh("adb shell settings put global hide_error_dialogs 1");
  sh("adb logcat -c"); // clear so the post-run dump only reflects this run
  try {
    // Bounded on its own, not only by the step deadline: a FIRST run has no
    // journal, so its deadline is the 30-minute ceiling — and on 2026-09-03
    // Maestro selected the device and then sat, driver never started, app
    // never in the foreground, under another lane's build load. Two
    // minutes of driver start-up plus three per flow is generous for a
    // healthy device; past it the run is killed and the row reads ERROR
    // (StepTimeout), which is what "did not get to check" should say.
    const runBoundMs = E2E_RUN_BOUND_MS(flows.length);
    const res = sh(`maestro test ${E2E_FLOW_DIR} --format junit --output "${report}"`, { env: { ...process.env, MAESTRO_DRIVER_STARTUP_TIMEOUT: "120000" }, timeout: runBoundMs });
    let xml = null;
    try {
      xml = fs.readFileSync(report, "utf8");
    } catch {
      xml = null;
    }
    const outcome = maestroOutcome(res, parseMaestroJunit(xml), flows);
    if (outcome.verdict !== "PASS") {
      return { name, verdict: outcome.verdict, reason: outcome.reason, durationMs: priorDurationMs + res.durationMs, details: outcome.details };
    }
    // The post-run sweep is scoped to THIS app's process(es): the flows' own
    // appId lines say which. Another app misbehaving on a shared emulator is
    // not this lane's red (deviceLogIncidents).
    const appIds = [...new Set(flows.map((rel) => (fs.readFileSync(path.join(ROOT, rel), "utf8").match(/^appId:\s*(\S+)/m) || [])[1]).filter(Boolean))];
    const anrDump = sh("adb logcat -d -b system,crash,main");
    const incidents = anrDump.ok ? deviceLogIncidents(anrDump.out, appIds) : { lines: [], scoped: appIds.length > 0 };
    if (incidents.lines.length) {
      return {
        name,
        verdict: "FAIL",
        reason: `Maestro flows passed, but the device log shows an ANR/crash in ${incidents.scoped ? appIds.join(", ") : "some process (no appId known to scope by)"} during the run (hide_error_dialogs only suppresses the OS dialog, never the underlying event):\n${incidents.lines.slice(0, 10).join("\n")}`,
        durationMs: priorDurationMs + res.durationMs,
        details: outcome.details,
      };
    }
    return { name, verdict: "PASS", durationMs: priorDurationMs + res.durationMs, note: `${flows.length} flow${flows.length === 1 ? "" : "s"}`, details: outcome.details, ...(outcome.reason ? { reason: outcome.reason } : {}) };
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
      skipKind: "structure",
      reason: "no instrumented tests (composeApp/src/androidInstrumentedTest has no Kotlin sources)",
      durationMs: Date.now() - started,
    };
  }
  const device = ensureLaneDevice("androidChecks");
  if (device) return { ...device, durationMs: Date.now() - started };
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
  // Verdict separated from invocation (qa/lib/step-outcomes.mjs): a run that
  // executed zero tests has observed nothing and must not accuse the change.
  const outcome = androidChecksOutcome(res, summary, { gradlew: GRADLEW });
  return {
    name: "androidChecks",
    verdict: outcome.verdict,
    reason: outcome.reason,
    durationMs: Date.now() - started,
    // `executed` rides on the receipt so a reader can tell a red that measured
    // something from a red that measured nothing. Shape preserved: undefined
    // when there is no summary AND nothing to add, as before.
    details: summary ? { ...summary, executed: outcome.executed } : outcome.executed ? undefined : { executed: false },
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
  e2eCoverage: ["composeApp/src", "docs/features", "specs", "qa/e2e"],
  archDoc: ["docs/ARCHITECTURE.md", "docs/adr", "specs", "composeApp/src"],
};

// The wrapper carries the step's NAME explicitly. An inner arrow returned from
// a factory has no inferred name, so the runner's stepDisplayName() read these
// as null — the marker narrated "specCoverage" as nothing, and its deadline
// fell to the 30-minute default. Latent since drive-narration; surfaced by the
// pack's own test the moment names were asserted at runtime rather than in
// source.
const memoized = (stepName, stepFn) => {
  const wrapped = () => memoizeStep({ fast, root: ROOT, stepName, inputs: MEMOIZED_STEP_INPUTS[stepName], run: stepFn });
  Object.defineProperty(wrapped, "name", { value: `step${stepName.charAt(0).toUpperCase()}${stepName.slice(1)}Memo` });
  return wrapped;
};

const stepSpecCoverageMemo = memoized("specCoverage", stepSpecCoverage);
const stepApprovalsMemo = memoized("approvals", stepApprovals);
const stepComponentStoriesMemo = memoized("componentStories", stepComponentStories);
const stepReachabilityMemo = memoized("reachability", stepReachability);
const stepE2eCoverageMemo = memoized("e2eCoverage", stepE2eCoverage);
const stepArchDocMemo = memoized("archDoc", stepArchDoc);

const stepsForProfile = {
  // scaffold: what `create-cmp --verify` proves at stamp time — specCoverage,
  // the full JVM tier (unit + conformance + golden + UI tests) plus the Android build.
  scaffold: [stepHarnessIntegrity, stepSpecCoverageMemo, stepApprovalsMemo, stepComponentStoriesMemo, stepReachabilityMemo, stepE2eCoverageMemo, stepArchDocMemo, stepSchemaHistory, stepBuild, stepUnitTests],
  // smoke (docs/GATE-RULES.md Rule 0, docs/PRINCIPLES.md #2): the smallest
  // end-to-end lane — every pure-Node step through the REAL runner, marker,
  // receipt and journal, and NO Gradle, no device, no network. Its job is to
  // prove the framework RETURNS, fast, in both directions, before any real
  // work is pointed at it. qa/framework-check.mjs drives it: PASS on a
  // fresh scaffold, then FAIL BY NAME on one planted spec edit, each bounded
  // in seconds. Its receipt is refused as done-evidence (qa/receipt-check.mjs)
  // exactly like --fast: it proves the instrument, never the change.
  smoke: [stepHarnessIntegrity, stepSpecCoverageMemo, stepApprovalsMemo, stepComponentStoriesMemo, stepReachabilityMemo, stepE2eCoverageMemo, stepArchDocMemo, stepSchemaHistory],
  local: [
    // First, always: every verdict below is only worth what the lane issuing
    // it is worth.
    stepHarnessIntegrity,
    stepSpecCoverageMemo,
    stepApprovalsMemo,
    stepComponentStoriesMemo,
    stepReachabilityMemo,
    stepE2eCoverageMemo,
    stepArchDocMemo,
    stepSchemaHistory,
    stepBuild,
    stepUnitTests,
    stepConformance,
    stepGoldenTrees,
    stepTokenDrift,
    stepA11y,
    // Release stays OUT of `scaffold`: stamp-time --verify promises a green first build, and
    // an R8 pass would add minutes to every scaffold to re-prove what this step proves here.
    // local + ci is where release rot gets caught before it reaches anyone.
    //
    // And it sits AFTER the cheap tier, not before it. The lane runs every step
    // regardless of failures, so the order costs nothing on a green run — but
    // ahead of them, a red unit test was reported only once R8 had finished,
    // which on a real change is minutes of waiting to be told something the JVM
    // knew in seconds. Cheap high-signal checks report first.
    stepReleaseBuild,
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
// nightly (evidence-economics S6 / proposal P4): the stage for proofs whose cost
// scales with the SUITE rather than with the change — the determinism probe
// today (forced on above; `--determinism` is implied), and the place any
// future load / chaos step lands, so the placement decision is made
// once instead of per expensive step. It proves the harness and the tree's
// invariants, not a change: qa/receipt-check.mjs refuses its receipt as
// done-evidence, exactly as it refuses --fast. Same step set as ci on purpose —
// what differs is what is forced, and what the receipt is allowed to mean.
stepsForProfile.nightly = [...stepsForProfile.ci];

// Which layer of the stack each step proves — stamped onto the receipt row by
// the runner (lane-runner.mjs) so the Evidence pane can group by it and a
// multi-pack lane (a Compose app over a Kotlin backend) reads as one lane
// with per-layer tallies. Three layers for this pack: `spine` — the harness
// proving itself and the governed record (integrity, spec coverage,
// approvals, the architecture doc, the schema history, the audit cadence);
// `compose` — the JVM tier of the app (build, tests, conformance, goldens,
// a11y, release compile); `device` — anything that needs an emulator or a
// physical device. Layer names are free-form strings on the wire; these are
// this pack's. Derived by NAME after the lists are built so a step listed in
// two profiles is tagged once, and a step nobody listed is never tagged.
const SPINE_STEP_NAMES = new Set(["harnessIntegrity", "specCoverage", "approvals", "componentStories", "reachability", "e2eCoverage", "archDoc", "schemaHistory", "auditCadence", "determinism"]);
function layerForStep(name) {
  if (DEVICE_STEPS.includes(name)) return "device";
  if (SPINE_STEP_NAMES.has(name)) return "spine";
  return "compose";
}
for (const fn of new Set(Object.values(stepsForProfile).flat())) {
  const name = stepDisplayName(fn);
  if (name) fn.layer = layerForStep(name);
}

const FAST_EXCLUDED_NAMES = [...DEVICE_STEPS, "releaseBuild"];
const STEP_FN_BY_NAME = {
  e2eSmoke: stepE2eSmoke,
  tokenDrift: stepTokenDrift,
  androidChecks: stepAndroidChecks,
  releaseSmoke: stepReleaseSmoke,
  releaseBuild: stepReleaseBuild,
};
// Where to look when one of THIS pack's steps blows its deadline. The spine
// prints it verbatim and knows nothing about Gradle or adb (Stage 0 PR 6b.2);
// marked on the step function, like `fn.layer`.
const GRADLE_TIMEOUT_HINT = "A wedged Gradle daemon or a device that stopped answering are the usual causes; check `./gradlew --status` and `adb devices`.";
for (const fn of [stepBuild, stepReleaseBuild, stepUnitTests, stepConformance, stepGoldenTrees, stepA11y, ...Object.values(STEP_FN_BY_NAME)]) {
  if (typeof fn === "function") fn.timeoutHint = GRADLE_TIMEOUT_HINT;
}

for (const name of FAST_EXCLUDED_NAMES) {
  if (!STEP_FN_BY_NAME[name]) {
    // Drift guard: a new device-tier step must be mapped here or --fast would silently run it.
    console.error(`internal: fast-excluded step "${name}" has no entry in STEP_FN_BY_NAME — fix qa/verify.mjs`);
    process.exit(2);
  }
}

  return {
    // WHICH PACK this is. The receipt names it (verify.mjs → receipt.pack) so a
    // cmp L2 and some other pack's L2 can never be mistaken for the same claim
    // on the wire. The pack declares its own identity — the spine writes what it
    // is told, never a name it assumed (docs/proposals/AGNOSTIC-HARNESS-
    // ARCHITECTURE.md §8.1, Stage 0 PR 1). Versioned with the harness until the
    // profile loader lands, so no `version` here yet: the spine pairs `id` with
    // the lock's version.
    id: "cmp",
    stepsForProfile,
    DEVICE_STEPS,
    // The verdict line's strength phrase. The core prints what it is handed and
    // nothing when handed nothing: "desktop-only" is THIS stack's word for the
    // absence of a device, not the harness's word for the absence of anything.
    // It used to be composed in verify.mjs, so a backend service with neither a
    // desktop nor a device read "(desktop-only)" on every verdict it ever printed.
    strengthLabel: (onDeviceSteps) => (onDeviceSteps.length ? `on-device: ${onDeviceSteps.join("+")}` : "desktop-only"),
    FAST_EXCLUDED_NAMES,
    STEP_FN_BY_NAME,
    stepDeterminism,
    // The step whose failure makes every verdict behind it meaningless — for
    // this stack, `./gradlew :composeApp:assembleDebug`. The spine stops the
    // lane there (qa/lib/lane-runner.mjs's compileShortCircuit) and it used to
    // find this step by the literal name "build", which is Gradle's word for
    // the task and Gradle's word for the output directory, and nobody else's.
    // Declared here so the spine reads a name instead of assuming one; the
    // value is unchanged, so this lane stops exactly where it always did.
    compileStepName: "build",
    // The bounds on a step's derived deadline. These are the spine's historical
    // fallback to the millisecond, so this lane does not move — but they are a
    // judgement about how long a cold Gradle daemon plus an Android build can
    // honestly take, and that judgement belongs to whoever owns the toolchain.
    // Declared so the numbers have an owner rather than being inherited by
    // every stack that never chose them.
    stepDeadlines: { floorMs: 5 * 60_000, ceilingMs: 30 * 60_000 },
    // The ladder this pack's steps can earn (evidence-level.mjs). A pack that
    // returns none earns no rung — the spine never grades a pack by another
    // pack's step names.
    evidenceLadder: CMP_LADDER,
    // The device lease is held to the very end of the run (see the scope
    // decision above); the spine releases it in the runner's finally.
    releaseLease: () => {
      if (laneDeviceLease) releaseDeviceLease(laneDeviceLease);
      // The emulator this lane booted goes down with the lane; an attached
      // device is never touched (device-provider.mjs).
      const down = releaseDevice(laneDevice, { sh });
      if (down.shutdown) console.error(`· shut down ${laneDevice.avd} (${laneDevice.serial})`);
    },
  };
}
