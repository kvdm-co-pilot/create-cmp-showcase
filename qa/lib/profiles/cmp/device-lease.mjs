// The machine-global device lease — mutual exclusion for the ONE Android
// device/emulator a machine typically has.
//
// WHY THIS EXISTS: the lane marker (composeApp/build/.cmp-lane-in-progress) is
// per-PROJECT, but the device is machine-GLOBAL. A scratch app stamped in /tmp
// and the real app are different roots sharing one emulator: each stamps its
// own marker, neither sees the other, and two concurrent device drivers produce
// exactly the observed failure class — wedged adbd, `device offline` while
// `adb devices` looks fine, crossed app state between sessions, false-red runs.
// The lease is keyed by the DEVICE (its adb serial), not the project, so the
// primitive finally matches the scarce resource it protects.
//
// ── ON-DISK CONTRACT ────────────────────────────────────────────────────────
// This exact contract is implemented independently by the create-cmp
// inspector MCP (inspector/mcp/src/lib/device-lease.mjs in the create-cmp
// repo — a check-only reader for connect_live / navigate_and_inspect). The two
// codebases ship separately and cannot import each other, so the contract
// lives verbatim in BOTH file headers, each pointing at the other. Changing
// anything below means changing it there too.
//
//   Location    <os.tmpdir()>/create-cmp/device-leases/<sanitized-serial>.json
//               tmpdir on purpose: a lease must never survive a reboot.
//   Sanitizing  serial chars outside [A-Za-z0-9._-] become "_"
//               ("emulator-5554"    → emulator-5554.json,
//                "192.168.1.5:5555" → 192.168.1.5_5555.json).
//   Shape       { "pid": number, "holder": string, "root": string,
//                 "serial": string, "acquiredAt": ISO-8601 string }
//               `holder` is a human/agent-readable label naming WHO is driving
//               ("verify lane e2eSmoke", "connect_live", "fleet-check scratch
//               lane"); `root` is the holder's project root.
//   Staleness   a lease is DEAD (silently reclaimable) when EITHER
//                 - its pid is not alive — process.kill(pid, 0) throws ESRCH.
//                   EPERM means the process EXISTS under another user: ALIVE.
//                 - OR acquiredAt is older than MAX_LEASE_AGE_MS.
//               An unparseable lease file (torn write from a crashed holder)
//               counts as dead. Readers treat dead as free; only acquirers
//               delete/overwrite.
//   Writes      atomic — temp file in the same directory + rename. After the
//               rename the acquirer re-reads and confirms its OWN pid is in
//               the file: two simultaneous renames resolve last-writer-wins,
//               and the loser reports contention instead of believing it holds
//               the device.
// ────────────────────────────────────────────────────────────────────────────
//
// Dependency-free Node. Every function takes an optional { dir } override so
// tests exercise real files in a temp dir without touching the machine's
// actual leases, and an optional { killImpl } so the EPERM-means-alive branch
// is testable without a foreign-user process.

import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

// 30 minutes: the longest legitimate single holder is a full release-profile
// device phase on a cold emulator (installDebug + Maestro smoke +
// connectedDebugAndroidTest + installRelease + release smoke), observed in the
// low tens of minutes — a live holder is protected by the pid check anyway, so
// this cap only decides how long a crashed holder whose pid was RECYCLED by an
// unrelated long-lived process can wedge the device. 30 min bounds that to
// roughly one lane-length: long enough never to reclaim under a healthy run,
// short enough that the machine heals itself within the hour.
export const MAX_LEASE_AGE_MS = 30 * 60 * 1000;

/** Serial → safe file stem: anything outside [A-Za-z0-9._-] becomes "_". */
export function sanitizeSerial(serial) {
  return String(serial).replace(/[^A-Za-z0-9._-]/g, "_");
}

/** The machine-global lease directory (override with { dir } in tests only). */
export function leaseDir(dir) {
  return dir || path.join(os.tmpdir(), "create-cmp", "device-leases");
}

/** Absolute path of the lease file for one serial. */
export function leasePath(serial, { dir } = {}) {
  return path.join(leaseDir(dir), `${sanitizeSerial(serial)}.json`);
}

/**
 * Is a pid alive? ESRCH = no such process → dead. EPERM = the process exists
 * but belongs to another user → ALIVE (killing rights are not liveness).
 * Any other error is treated as alive — when in doubt, never steal a lease.
 */
export function pidAlive(pid, { killImpl = process.kill.bind(process) } = {}) {
  if (!Number.isInteger(pid) || pid <= 0) return false;
  try {
    killImpl(pid, 0);
    return true;
  } catch (err) {
    return !(err && err.code === "ESRCH");
  }
}

function readLeaseFile(file) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return null; // missing OR unparseable — both mean "no live lease here"
  }
}

function describeLease(lease, nowMs) {
  const acquiredMs = Date.parse(lease.acquiredAt);
  return {
    holder: lease.holder ?? "unknown",
    pid: lease.pid ?? null,
    root: lease.root ?? null,
    acquiredAt: lease.acquiredAt ?? null,
    ageMs: Number.isFinite(acquiredMs) ? Math.max(0, nowMs - acquiredMs) : Number.POSITIVE_INFINITY,
  };
}

function leaseDead(lease, nowMs, { killImpl } = {}) {
  if (!pidAlive(lease.pid, { ...(killImpl ? { killImpl } : {}) })) return true;
  const acquiredMs = Date.parse(lease.acquiredAt);
  if (!Number.isFinite(acquiredMs)) return true; // no readable birth time — unverifiable, dead
  return nowMs - acquiredMs >= MAX_LEASE_AGE_MS;
}

/** `"verify lane e2eSmoke" (pid 4711, /tmp/scratch-x, 2m ago)` — for reasons/errors. */
export function formatHolder(heldBy) {
  if (!heldBy) return "an unknown holder";
  const age =
    !Number.isFinite(heldBy.ageMs) ? "age unknown"
    : heldBy.ageMs < 60_000 ? `${Math.max(1, Math.round(heldBy.ageMs / 1000))}s ago`
    : `${Math.round(heldBy.ageMs / 60_000)}m ago`;
  return `"${heldBy.holder}" (pid ${heldBy.pid ?? "?"}, ${heldBy.root ?? "unknown root"}, ${age})`;
}

/**
 * Read the live lease on a serial, if any. Dead/stale leases read as null
 * (free) — reading never deletes; reclaim-by-overwrite is the acquirer's job.
 *
 * @returns {{holder,pid,root,acquiredAt,ageMs}|null}
 */
export function readDeviceLease(serial, { dir, killImpl, now = Date.now } = {}) {
  const lease = readLeaseFile(leasePath(serial, { dir }));
  if (!lease) return null;
  const nowMs = now();
  if (leaseDead(lease, nowMs, { killImpl })) return null;
  return describeLease(lease, nowMs);
}

/**
 * Acquire the machine-global lease on one device serial.
 *
 * @param {{serial: string, holder: string, root: string, dir?: string,
 *   killImpl?: Function, now?: () => number}} opts
 * @returns {{ok: true, handle: {file, serial, pid, acquiredAt}, reclaimed: object|null}
 *   | {ok: false, heldBy: {holder, pid, root, acquiredAt, ageMs}}}
 *   `reclaimed` names the dead lease this acquire silently replaced (a crashed
 *   run must never wedge the machine forever) so the acquiring run can note it
 *   in its own output.
 */
export function acquireDeviceLease({ serial, holder, root, dir, killImpl, now = Date.now } = {}) {
  if (!serial) throw new Error("acquireDeviceLease: serial is required");
  const d = leaseDir(dir);
  fs.mkdirSync(d, { recursive: true });
  const file = leasePath(serial, { dir });
  const nowMs = now();

  let reclaimed = null;
  const existing = readLeaseFile(file);
  if (existing) {
    if (!leaseDead(existing, nowMs, { killImpl })) {
      return { ok: false, heldBy: describeLease(existing, nowMs) };
    }
    reclaimed = describeLease(existing, nowMs); // dead — reclaim silently
  } else if (fs.existsSync(file)) {
    reclaimed = { holder: "unreadable lease (torn write)", pid: null, root: null, acquiredAt: null, ageMs: Number.POSITIVE_INFINITY };
  }

  const lease = {
    pid: process.pid,
    holder: String(holder || "unknown"),
    root: String(root || process.cwd()),
    serial: String(serial),
    acquiredAt: new Date(nowMs).toISOString(),
  };
  // Atomic claim: temp file + rename means no reader ever sees a half-written
  // lease, and two simultaneous acquirers cannot interleave bytes.
  const tmp = path.join(d, `.${sanitizeSerial(serial)}.${process.pid}.${crypto.randomBytes(4).toString("hex")}.tmp`);
  fs.writeFileSync(tmp, `${JSON.stringify(lease, null, 2)}\n`);
  fs.renameSync(tmp, file);

  // Last-writer-wins detection: both racers reached the rename; whoever's bytes
  // survived owns the device. Re-read and confirm it is US — the loser reports
  // contention instead of driving a device someone else holds.
  const confirm = readLeaseFile(file);
  if (!confirm || confirm.pid !== lease.pid || confirm.acquiredAt !== lease.acquiredAt || confirm.holder !== lease.holder) {
    return {
      ok: false,
      heldBy: confirm
        ? describeLease(confirm, now())
        : { holder: "unknown (lease vanished mid-acquire)", pid: null, root: null, acquiredAt: null, ageMs: Number.POSITIVE_INFINITY },
    };
  }
  return { ok: true, handle: { file, serial: lease.serial, pid: lease.pid, acquiredAt: lease.acquiredAt }, reclaimed };
}

/**
 * Release a held lease. Idempotent and safe: missing file is fine, and a file
 * that no longer carries OUR pid+acquiredAt belongs to a newer holder (they
 * reclaimed us as stale, or won a race) — another holder's lease is NEVER
 * deleted.
 */
export function releaseDeviceLease(handle) {
  if (!handle || !handle.file) return;
  const current = readLeaseFile(handle.file);
  if (!current) return; // already gone (or unreadable — not provably ours, leave it)
  if (current.pid !== handle.pid || current.acquiredAt !== handle.acquiredAt) return; // someone else's now
  try {
    fs.rmSync(handle.file, { force: true });
  } catch {
    /* releasing is best-effort; staleness reclaim is the backstop */
  }
}

/**
 * Run `fn(handle)` under the lease, releasing in a finally. Sync or async fn.
 * A refused acquire is returned as-is ({ok:false, heldBy}) — the caller decides
 * what contention means (the verify lane turns it into a SKIP, never a FAIL).
 */
export function withDeviceLease(opts, fn) {
  const res = acquireDeviceLease(opts);
  if (!res.ok) return res;
  let out;
  try {
    out = fn(res.handle);
  } catch (err) {
    releaseDeviceLease(res.handle);
    throw err;
  }
  if (out && typeof out.then === "function") {
    return out.then(
      (value) => {
        releaseDeviceLease(res.handle);
        return { ok: true, result: value, reclaimed: res.reclaimed };
      },
      (err) => {
        releaseDeviceLease(res.handle);
        throw err;
      },
    );
  }
  releaseDeviceLease(res.handle);
  return { ok: true, result: out, reclaimed: res.reclaimed };
}
