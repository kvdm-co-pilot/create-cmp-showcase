// audit-cadence.mjs — the mechanical nudge that keeps cmp-audit from
// depending on someone remembering.
//
// The adversarial platform-semantics audit (the cmp-audit skill) found six
// latent defects on its first real outing — and it only ran because a human
// happened to ask. This module is the cheapest honest replacement for that
// memory: the release profile's receipt lists which androidMain subsystems
// changed since their last RECORDED audit, so the ship-time surface itself
// says "these platform seams moved and nobody has interrogated them since".
//
// It is a REPORT, never a gate. Audit debt is a judgment call (a one-line
// rename is not six latent defects), so this file computes facts and the
// human decides — a FAIL here would train people to game the ledger, which
// would destroy the only thing it has: honesty.
//
// The ledger (qa/audits.jsonl) is append-only, one JSON object per line:
// subsystem, the commit sha the audit ran against, an ISO timestamp, and who
// or what recorded it. Recording is a CLAIM — "this subsystem, as of this
// commit, was audited" — so recordAudit() derives the sha from HEAD itself
// and refuses to record when the subsystem's files differ from HEAD: a
// record claiming a commit the audited bytes did not match would be the
// exact dishonesty the whole harness exists to prevent.
//
// "Subsystem" is DERIVED, never configured: the immediate package directory
// under the app's androidMain Kotlin source root (the root is resolved from
// the android namespace in composeApp/build.gradle.kts). This template is
// stamped into apps whose package names it cannot know; deriving from the
// tree is the only definition that survives that. Kotlin files sitting
// directly at the package root belong to no package directory and are
// reported under the literal name "(root)" rather than invented into one.

import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

export const AUDITS_REL_PATH = "qa/audits.jsonl";
export const AUDIT_RECORD_SCHEMA = "cmp-audit-record/1";

/** The pseudo-subsystem for Kotlin files directly at the androidMain package root. */
export const ROOT_SUBSYSTEM = "(root)";

function tryGit(root, cmd) {
  try {
    return execSync(`git ${cmd}`, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  } catch {
    return null;
  }
}

function tryGitLines(root, cmd) {
  try {
    const out = execSync(`git ${cmd}`, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
    return out.replace(/\n+$/, "").split("\n").filter(Boolean);
  } catch {
    return null;
  }
}

/**
 * Resolve the androidMain Kotlin package root for this app, relative to the
 * project root — derived from the android `namespace` (falling back to
 * `applicationId`) in composeApp/build.gradle.kts, never hardcoded.
 * @param {string} root project root (absolute)
 * @returns {{ok: true, rel: string}|{ok: false, reason: string}}
 */
export function androidMainPackageRoot(root) {
  let gradle;
  try {
    gradle = fs.readFileSync(path.join(root, "composeApp", "build.gradle.kts"), "utf8");
  } catch {
    return { ok: false, reason: "composeApp/build.gradle.kts not readable — cannot derive the app package" };
  }
  const pkg = gradle.match(/namespace\s*=\s*"([^"]+)"/)?.[1] ?? gradle.match(/applicationId\s*=\s*"([^"]+)"/)?.[1];
  if (!pkg) {
    return { ok: false, reason: "no android namespace/applicationId in composeApp/build.gradle.kts — cannot derive the app package" };
  }
  const rel = path.posix.join("composeApp/src/androidMain/kotlin", ...pkg.split("."));
  if (!fs.existsSync(path.join(root, rel))) {
    return { ok: false, reason: `androidMain has no Kotlin sources under the app package (${rel} absent)` };
  }
  return { ok: true, rel };
}

/**
 * List this app's androidMain subsystems: the immediate directories under
 * the package root (sorted), plus ROOT_SUBSYSTEM when Kotlin files sit
 * directly at the root.
 * @param {string} root project root (absolute)
 * @param {string} pkgRootRel from androidMainPackageRoot()
 * @returns {string[]}
 */
export function listSubsystems(root, pkgRootRel) {
  const abs = path.join(root, pkgRootRel);
  let entries;
  try {
    entries = fs.readdirSync(abs, { withFileTypes: true });
  } catch {
    return [];
  }
  const names = entries.filter((e) => e.isDirectory()).map((e) => e.name).sort();
  if (entries.some((e) => e.isFile() && e.name.endsWith(".kt"))) names.push(ROOT_SUBSYSTEM);
  return names;
}

/**
 * Read the audit ledger. Absent is the honest "no audit ever recorded"
 * state; malformed lines are counted, never silently dropped — the report
 * says how many records it could not read instead of under-counting audits.
 * @param {string} root project root (absolute)
 * @returns {{entries: Array<{subsystem: string, sha: string, at: string, by: string}>, malformed: number}}
 */
export function readAuditLedger(root) {
  const p = path.join(root, AUDITS_REL_PATH);
  if (!fs.existsSync(p)) return { entries: [], malformed: 0 };
  let raw;
  try {
    raw = fs.readFileSync(p, "utf8");
  } catch {
    return { entries: [], malformed: 0 };
  }
  const entries = [];
  let malformed = 0;
  for (const line of raw.split("\n")) {
    if (!line.trim()) continue;
    try {
      const e = JSON.parse(line);
      if (e && typeof e === "object" && typeof e.subsystem === "string" && typeof e.sha === "string") entries.push(e);
      else malformed += 1;
    } catch {
      malformed += 1;
    }
  }
  return { entries, malformed };
}

/**
 * Record an audit claim: append {subsystem, sha: HEAD, at, by} to the
 * ledger. Honesty guards, each a refusal rather than a fabrication:
 *   - no git HEAD → refused (a claim about no commit is not a claim);
 *   - unknown subsystem → refused, naming the derived ones;
 *   - the subsystem's files differ from HEAD → refused (the record would
 *     claim HEAD while the audited bytes are something else — commit first).
 * @param {string} root project root (absolute)
 * @param {{subsystem: string, by?: string}} claim
 * @returns {{ok: true, sha: string, entry: object}|{ok: false, reason: string}}
 */
export function recordAudit(root, { subsystem, by }) {
  const sha = tryGit(root, "rev-parse HEAD");
  if (!sha) {
    return { ok: false, reason: "no git history — an audit record is a claim about a specific commit, and there is none to claim against. Commit first." };
  }
  const pkgRoot = androidMainPackageRoot(root);
  if (!pkgRoot.ok) return { ok: false, reason: pkgRoot.reason };
  const known = listSubsystems(root, pkgRoot.rel);
  if (!known.includes(subsystem)) {
    return { ok: false, reason: `unknown subsystem "${subsystem}" — derived subsystems under ${pkgRoot.rel}: ${known.join(", ") || "(none)"}` };
  }
  const scope = subsystem === ROOT_SUBSYSTEM ? pkgRoot.rel : path.posix.join(pkgRoot.rel, subsystem);
  const dirty = tryGitLines(root, `status --porcelain -- "${scope}"`) ?? [];
  // For "(root)" the porcelain scope is the whole package root; narrow to
  // files directly at the root so a dirty subsystem dir doesn't block a
  // root-level record it has nothing to do with.
  const relevantDirty =
    subsystem === ROOT_SUBSYSTEM
      ? dirty.filter((l) => {
          const rel = l.slice(3).trim();
          return path.posix.dirname(rel) === pkgRoot.rel;
        })
      : dirty;
  if (relevantDirty.length > 0) {
    return {
      ok: false,
      reason: `uncommitted changes under ${scope} — the record would claim commit ${sha.slice(0, 7)} but the audited files are not that commit. Commit (or revert) first, then record.`,
    };
  }
  const entry = {
    schema: AUDIT_RECORD_SCHEMA,
    subsystem,
    sha,
    at: new Date().toISOString(),
    by: by || tryGit(root, "config user.name") || "unknown",
  };
  const p = path.join(root, AUDITS_REL_PATH);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.appendFileSync(p, `${JSON.stringify(entry)}\n`);
  return { ok: true, sha, entry };
}

/**
 * The report itself: for every derived subsystem, what the ledger claims
 * and what git says moved since that claim.
 *
 * Statuses, each phrased so the receipt can print the line verbatim:
 *   never-audited   no ledger entry — says exactly that, implies no staleness
 *   changed         androidMain files under it changed between the audited
 *                   sha and HEAD (committed changes only — sha vs HEAD is
 *                   the honest comparison; the working tree is not history)
 *   unchanged       no committed change since the audited sha
 *   unknown-commit  the ledger names a sha this repo's history does not
 *                   contain — drift cannot be measured, and the report says
 *                   so instead of guessing
 *
 * @param {string} root project root (absolute)
 * @returns {{ok: false, reason: string}|{ok: true, packageRoot: string,
 *   subsystems: Array<{name: string, status: string, audit: object|null, changedFiles: number}>,
 *   lines: string[], summary: string, malformed: number}}
 */
export function evaluateAuditCadence(root) {
  if (!tryGit(root, "rev-parse HEAD")) {
    // No git history: "changed since the last audit" has no meaning yet.
    // Report NOTHING rather than guessing — an invented staleness signal
    // would be worse than none.
    return { ok: false, reason: "no git history — changed-since-audit cannot be measured" };
  }
  const pkgRoot = androidMainPackageRoot(root);
  if (!pkgRoot.ok) return { ok: false, reason: pkgRoot.reason };
  const subsystems = listSubsystems(root, pkgRoot.rel);
  if (subsystems.length === 0) {
    return { ok: false, reason: `no subsystems under ${pkgRoot.rel} — nothing to report` };
  }

  const { entries, malformed } = readAuditLedger(root);
  // Last entry per subsystem wins: the ledger is append-only, so file order
  // IS chronological order — trusted over the `at` timestamps, which are
  // claims a machine's clock made, not facts git can vouch for.
  const latest = new Map();
  for (const e of entries) latest.set(e.subsystem, e);

  const gitTop = tryGit(root, "rev-parse --show-toplevel");
  // Realpath both sides before re-anchoring diff paths: git reports the
  // toplevel with symlinks resolved (macOS: /var/… vs /private/var/…), and a
  // mismatch here would silently mis-attribute every changed file.
  let rootReal = root;
  try {
    rootReal = fs.realpathSync(root);
  } catch {
    rootReal = root;
  }
  const results = [];
  const lines = [];
  for (const name of subsystems) {
    const audit = latest.get(name) ?? null;
    if (!audit) {
      results.push({ name, status: "never-audited", audit: null, changedFiles: 0 });
      lines.push(`no audit recorded for ${name} — when it gets one (cmp-audit ${name}), record it: node qa/record-audit.mjs ${JSON.stringify(name)}`);
      continue;
    }
    // A ledger sha is a CLAIM read from a file — validate its shape before it
    // touches a shell, and resolve it against history before trusting it.
    const shaShapeOk = typeof audit.sha === "string" && /^[0-9a-f]{4,40}$/i.test(audit.sha);
    const shaKnown = shaShapeOk && Boolean(tryGit(root, `rev-parse --verify --quiet "${audit.sha}^{commit}"`));
    if (!shaKnown) {
      results.push({ name, status: "unknown-commit", audit, changedFiles: 0 });
      lines.push(`${name}: last audit (${fmtWhen(audit)}) was recorded against ${audit.sha.slice(0, 12)}, which is not in this repo's history — drift since it cannot be measured`);
      continue;
    }
    const scope = name === ROOT_SUBSYSTEM ? pkgRoot.rel : path.posix.join(pkgRoot.rel, name);
    const changedRaw = tryGitLines(root, `diff --name-only ${audit.sha} HEAD -- "${scope}"`) ?? [];
    // Diff paths come back relative to the git toplevel, which may sit above
    // the project root; re-anchor before subsystem attribution.
    const changed = changedRaw
      .map((rel) => (gitTop ? path.relative(rootReal, path.resolve(gitTop, rel)).split(path.sep).join("/") : rel))
      .filter((rel) => (name === ROOT_SUBSYSTEM ? path.posix.dirname(rel) === pkgRoot.rel : true));
    if (changed.length > 0) {
      results.push({ name, status: "changed", audit, changedFiles: changed.length });
      lines.push(
        `${name}: ${changed.length} androidMain file(s) changed since its last recorded audit (${audit.sha.slice(0, 7)}, ${fmtWhen(audit)}) — audit it (cmp-audit ${name}), then record: node qa/record-audit.mjs ${JSON.stringify(name)}`,
      );
    } else {
      results.push({ name, status: "unchanged", audit, changedFiles: 0 });
    }
  }

  const changedCount = results.filter((r) => r.status === "changed").length;
  const neverCount = results.filter((r) => r.status === "never-audited").length;
  const unchangedCount = results.filter((r) => r.status === "unchanged").length;
  if (unchangedCount > 0) {
    lines.push(`${unchangedCount} subsystem(s) unchanged since their last recorded audit: ${results.filter((r) => r.status === "unchanged").map((r) => r.name).join(", ")}`);
  }
  if (malformed > 0) {
    lines.push(`${malformed} ledger line(s) in ${AUDITS_REL_PATH} could not be parsed and are not counted`);
  }
  const summary = `${changedCount} changed since audit · ${neverCount} never audited · ${unchangedCount} unchanged`;

  return { ok: true, packageRoot: pkgRoot.rel, subsystems: results, lines, summary, malformed };
}

function fmtWhen(audit) {
  return typeof audit.at === "string" ? audit.at.slice(0, 10) : "undated";
}
