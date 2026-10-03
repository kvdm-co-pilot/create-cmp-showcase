// profiles/cmp/maestro.mjs — the Maestro directory run's verdict, and the
// device-log sweep scoped to the app under test. Mobile's, by definition.
//
// These three lived in qa/lib/step-outcomes.mjs beside the neutral verdict
// helpers (StepTimeout, stepErrorResult, spawnTimedOut). They are not neutral:
// a JUnit report from `maestro test <dir>` and an `adb logcat` crash buffer are
// facts about an Android device, and a backend profile has neither. Stage 0
// PR 3 moves them here so step-outcomes.mjs is the spine's again. Bodies are
// verbatim — the tests that pinned them moved with them.

// ── Maestro directory run → per-flow outcome ────────────────────────────────
/**
 * Parse Maestro's JUnit report (`maestro test <dir> --format junit --output f`)
 * into one row per flow. Tolerant: a report that is missing or unparsable
 * returns null and the caller falls back to the exit code — never a fabricated
 * per-flow list.
 * @param {string|null} xml
 * @returns {Array<{flow: string, ok: boolean, message?: string}>|null}
 */
export function parseMaestroJunit(xml) {
  if (typeof xml !== "string" || !/<testcase\b/.test(xml)) return null;
  const rows = [];
  const caseRe = /<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
  let m;
  while ((m = caseRe.exec(xml))) {
    const attrs = m[1] || "";
    const body = m[2] || "";
    const name = (attrs.match(/\bname="([^"]*)"/) || [])[1] ?? (attrs.match(/\bid="([^"]*)"/) || [])[1] ?? "?";
    const status = (attrs.match(/\bstatus="([^"]*)"/) || [])[1];
    const failed = /<(failure|error)\b/.test(body) || (status && !/^(SUCCESS|PASSED?|OK)$/i.test(status));
    const message = failed ? ((body.match(/<(?:failure|error)\b[^>]*message="([^"]*)"/) || [])[1] ?? body.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim().slice(0, 300)) : undefined;
    rows.push(failed ? { flow: name, ok: false, message } : { flow: name, ok: true });
  }
  return rows.length ? rows : null;
}

/**
 * The e2e step's verdict from the Maestro run: exit code + per-flow report +
 * the list of flows the directory held. FAIL names every failing flow; a run
 * whose report lists fewer flows than the directory holds is ERROR — the lane
 * cannot claim flows it has no row for.
 * @param {{ok: boolean, out: string}} res
 * @param {Array<{flow: string, ok: boolean, message?: string}>|null} perFlow
 * @param {string[]} flows root-relative flow files the directory holds
 * @returns {{verdict: "PASS"|"FAIL"|"ERROR", reason?: string, details: object}}
 */
export function maestroOutcome(res, perFlow, flows) {
  const details = { flows, results: perFlow ?? undefined };
  if (perFlow) {
    const failed = perFlow.filter((r) => !r.ok);
    if (failed.length) {
      return {
        verdict: "FAIL",
        reason: `Maestro: ${failed.length} of ${perFlow.length} flow${perFlow.length === 1 ? "" : "s"} failed — ${failed.map((r) => `${r.flow}${r.message ? ` (${r.message.split("\n")[0].slice(0, 120)})` : ""}`).join("; ")}`,
        details,
      };
    }
    if (perFlow.length < flows.length) {
      return {
        verdict: "ERROR",
        reason: `Maestro reported ${perFlow.length} flow${perFlow.length === 1 ? "" : "s"} but ${E2E_DIR_LABEL} holds ${flows.length} — the run did not cover every flow, so no verdict can be claimed for the rest`,
        details,
      };
    }
    if (!res.ok) {
      return { verdict: "FAIL", reason: `Maestro exited non-zero with every flow reported green — treat as a run failure:\n${String(res.out).split("\n").slice(-10).join("\n")}`, details };
    }
    return { verdict: "PASS", details };
  }
  if (!res.ok) {
    return { verdict: "FAIL", reason: `Maestro failed (no per-flow report was written):\n${String(res.out).split("\n").slice(-15).join("\n")}`, details };
  }
  return { verdict: "PASS", reason: "Maestro exited 0 but wrote no per-flow report — verdict from the exit code only", details };
}
const E2E_DIR_LABEL = "qa/e2e";

// ── Device-log incidents, scoped to the app under test ──────────────────────
/**
 * ANR / fatal-exception lines from `adb logcat -d -b system,crash,main` that
 * belong to one of `appIds` (an app's process may be `pkg` or `pkg:remote`).
 * An emulator carries other apps — on 2026-09-03 the first self-booted lane
 * went red on `ANR in com.karel.bratometer` while driving com.fleet.check —
 * so an incident in another package is NOT this lane's failure. With no
 * appIds known the sweep stays unscoped (every incident counts) and says so.
 * @param {string} log
 * @param {string[]} appIds
 * @returns {{lines: string[], scoped: boolean}}
 */
export function deviceLogIncidents(log, appIds = []) {
  const lines = String(log ?? "").split("\n");
  const ids = appIds.filter((id) => typeof id === "string" && id.trim() && id !== "__PACKAGE__");
  const scoped = ids.length > 0;
  const ours = (proc) => !scoped || ids.some((id) => proc === id || proc.startsWith(`${id}:`));
  const out = [];
  lines.forEach((line, i) => {
    const anr = line.match(/ANR in (\S+?)(?:\s|,|$)/);
    if (anr) {
      if (ours(anr[1].replace(/[,)]$/, ""))) out.push(line);
      return;
    }
    if (/FATAL EXCEPTION/i.test(line)) {
      if (!scoped) {
        out.push(line);
        return;
      }
      // The crash buffer names the process on one of the next few lines.
      const window = lines.slice(i, i + 8).join("\n");
      const proc = window.match(/Process:\s*(\S+?)(?:,|\s|$)/);
      if (proc && ours(proc[1])) out.push(line);
    }
  });
  return { lines: out, scoped };
}
