// evidence-html.mjs — THE RUN, AS ONE FILE YOU CAN SEND SOMEONE.
//
// docs/proposals/LIVE-CONSOLE.md D3a: "`node qa/verify.mjs --html` writes
// qa/evidence/latest.html alongside the receipt, opt-in, not committed by
// default. One file, no server, attaches to a PR." D3b — write it on every run
// — was rejected for churn, and D3c (Gatekeeper hosts it) is deferred by a
// standing decision. So this is a renderer and nothing else: no server, no
// endpoint, no upload, no dependency.
//
// STANDALONE MEANS STANDALONE. The file opens from a file:// URL on a machine
// with no network: the CSS is inline, there is no <script>, no <img>, no
// <link>, no font URL and no shields.io badge — a snapshot that fetched
// anything would leak WHO OPENED IT to whoever serves that thing, and would
// render as a broken page in the one place it is most likely to be read (an
// air-gapped review). test/evidence-html.test.mjs refuses any absolute URL in
// the output, so the day someone adds a logo is the day the suite says so.
//
// WHY THE LANE RENDERS IT AND NOT THE CONSOLE. The console's modules live in
// `src/console/`, which is NOT vendored into an app's qa/ — a stamped project's
// lane may import only from `./lib/`, and that dependency-free vendoring is
// what makes `node qa/verify.mjs` work offline, in CI, air-gapped
// (scripts/sync-harness.mjs's own header). So the snapshot is rendered here,
// from the same DERIVATIONS the console uses — qa/lib/framework-record.mjs for
// what Rule 0 said, qa/lib/evidence-level.mjs for what the next rung wants — so
// the two surfaces cannot come to disagree about either fact. What each one
// does not share is markup, which is the same split console-evidence.mjs
// describes: every caller owns its own chrome.
//
// A RUNG NEVER APPEARS WITHOUT ITS PACK (NORTH-STAR §6.5, §8.9). This is a
// surface that TRAVELS — attached to a PR, mailed, kept — so it is exactly the
// surface where a bare `L2` does the most damage, and the clause it renders is
// the one qa/verify.mjs already prints on the line every agent reads:
// `L2 device · pack cmp`. Only the pack's ID (ADR-0008: `pack.version` on a
// receipt is the harness lock's number, not the profile's).
//
// It states the age of nothing. A snapshot is a statement about a COMMIT at an
// INSTANT, and both are printed; "4 minutes ago" would be a lie the moment the
// file is opened tomorrow, which is the property evidence-badge.mjs already
// keeps for the README badge and the reason that badge names a sha and a date.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/evidence-html.mjs in the
// create-cmp repo. Vendored byte-identical into qa/lib/ — edit the package
// source, then run `node scripts/sync-harness.mjs` in the create-cmp repo.

import { ladderStanding } from "./evidence-level.mjs";
import { FRAMEWORK_RECORD_COMMAND, trustLine, trustState } from "./framework-record.mjs";

/** Where the snapshot lands — beside the receipt it is about (D3a). */
export const SNAPSHOT_REL = "qa/evidence/latest.html";

const esc = (s) =>
  String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const escAttr = (s) => esc(s).replace(/"/g, "&quot;");

/** "412ms" / "12.4s" / "3m 02s" — the same shape the console's rows use. */
function duration(ms) {
  if (typeof ms !== "number" || !Number.isFinite(ms) || ms < 0) return "";
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  const m = Math.floor(ms / 60_000);
  return `${m}m ${String(Math.round((ms % 60_000) / 1000)).padStart(2, "0")}s`;
}

/**
 * The rung and the pack that graded it, or null when the receipt records no
 * rung — the three honest absences (a FAILed lane, a fast run, a profile with
 * no ladder or no plants) all render as no rung rather than as a weaker one.
 */
function rungClause(level, pack) {
  const rung = level && typeof level.rung === "string" ? level.rung : null;
  if (!rung) return null;
  const name = level && typeof level.name === "string" && level.name ? ` ${level.name}` : "";
  const id = pack && typeof pack.id === "string" && pack.id.trim() ? pack.id.trim() : null;
  // "pack unnamed" is the wording the README badge and the console both use for
  // this state. An unattributed rung is comparable to nothing, and the snapshot
  // says so rather than letting the reader assume the question never arose.
  return `${rung}${name} · ${id ? `pack ${id}` : "pack unnamed"}`;
}

/** SKIP is muted, never green (§7). The four verdicts, in the console's colours. */
function verdictClass(v) {
  if (v === "PASS") return "v-pass";
  if (v === "FAIL") return "v-fail";
  if (v === "ERROR") return "v-error";
  return "v-skip";
}

const CSS = `
  :root { color-scheme: light dark;
    --ink: #16181d; --ink-2: #3d434f; --muted: #6b7280; --line: #e4e6eb;
    --bg: #ffffff; --surface: #f6f7f9; --pass: #1f7a4d; --fail: #b3261e; --warn: #8a6100; }
  @media (prefers-color-scheme: dark) { :root {
    --ink: #e7e9ee; --ink-2: #b9bfcc; --muted: #8b93a3; --line: #2a2e37;
    --bg: #14161a; --surface: #1b1e24; --pass: #59c08a; --fail: #ef7a72; --warn: #d9ab4a; } }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px; background: var(--bg); color: var(--ink);
    font: 15px/1.55 ui-sans-serif, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
  main { max-width: 900px; margin: 0 auto; }
  h1 { font-size: 17px; margin: 0 0 4px; letter-spacing: 0.01em; }
  h2 { font-size: 13px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--muted);
       margin: 24px 0 6px; font-weight: 600; }
  code, .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 13px; }
  .strip { padding: 10px 12px; background: var(--surface); border: 1px solid var(--line); border-radius: 8px; }
  .strip .verdict { font-weight: 700; }
  .pass .verdict { color: var(--pass); } .fail .verdict { color: var(--fail); }
  .row { margin: 0; color: var(--ink-2); font-size: 14px; }
  .muted { color: var(--muted); }
  ol.steps { list-style: none; margin: 0; padding: 0; font-size: 13px; }
  ol.steps li { padding: 3px 0; border-bottom: 1px solid var(--line); }
  .name { display: inline-block; min-width: 22ch; }
  .v-pass { color: var(--pass); } .v-fail, .v-error { color: var(--fail); font-weight: 650; }
  .v-skip { color: var(--muted); }
  pre.reason { margin: 6px 0 6px 2ch; padding: 8px 10px; background: var(--surface); border-radius: 6px;
    white-space: pre-wrap; overflow-x: auto; color: var(--fail); }
  .rung-mark { font-family: ui-monospace, monospace; color: var(--muted); margin-right: 6px; }
  .rung-earned { color: var(--ink); }
  footer { margin-top: 28px; padding-top: 10px; border-top: 1px solid var(--line);
    color: var(--muted); font-size: 12px; }
`;

/**
 * The whole snapshot, as one HTML document.
 *
 * @param {object} p
 * @param {object} p.receipt the receipt just written, verbatim
 * @param {object|null} [p.ladder] the profile's resolved ladder declaration
 * @param {{ok: boolean, record?: object, reason?: string, relPath?: string}|null} [p.frameworkRecord]
 *   readFrameworkRecord()'s result, or null when the caller did not look
 * @param {string} [p.appName] what to call the project in the title
 * @returns {string} a complete, self-contained HTML document
 */
export function snapshotHtml({ receipt, ladder = null, frameworkRecord = null, appName = null } = {}) {
  const r = receipt && typeof receipt === "object" ? receipt : {};
  const steps = Array.isArray(r.steps) ? r.steps.filter((s) => s && typeof s.name === "string") : [];
  const verdict = typeof r.verdict === "string" ? r.verdict : "?";
  const sha = r.commit && typeof r.commit.sha === "string" ? r.commit.sha : null;
  const dirty = r.commit && Array.isArray(r.commit.dirty) ? r.commit.dirty.length : 0;
  const rung = rungClause(r.evidenceLevel, r.pack);
  const title = `${appName ? `${appName} — ` : ""}verify ${verdict}${sha ? ` at ${sha.slice(0, 7)}` : ""}`;

  // THE STRIP. Every clause is the receipt's own field; nothing is computed
  // about "now", because this file is read later by definition.
  const stripParts = [
    `<span class="verdict">${esc(verdict)}</span>`,
    rung ? esc(rung) : `<span class="muted">no rung &mdash; ${esc(r.mode === "fast" ? "a --fast run is the inner loop, never evidence" : "this run earned none")}</span>`,
    r.profile ? `profile ${esc(r.profile)}` : null,
    sha ? `commit <code>${esc(sha.slice(0, 7))}</code>` : `<span class="muted">no commit recorded</span>`,
    dirty > 0
      ? `<span class="muted">${dirty} uncommitted file${dirty === 1 ? "" : "s"} at attestation &mdash; this describes that RUN, not that commit</span>`
      : null,
    r.generatedAt ? `<span class="muted">${esc(r.generatedAt)}</span>` : null,
  ].filter(Boolean);

  const stepRows = steps
    .map((s) => {
      const d = duration(s.durationMs);
      const failed = s.verdict === "FAIL" || s.verdict === "ERROR";
      // The tool's own reason, verbatim, and only in red where the verdict is
      // red — the console's rule, for the same reason: a SKIP explaining itself
      // is not a failure (§7 — nothing here rewords what the lane printed).
      const detail = s.reason && failed ? `\n      <pre class="reason">${esc(s.reason)}</pre>` : "";
      const said = s.reason && !failed ? ` <span class="muted">${esc(String(s.reason).split("\n")[0])}</span>` : "";
      const note = s.note ? ` <span class="muted">${esc(s.note)}</span>` : "";
      return `      <li><code class="name">${esc(s.name)}</code> <span class="${verdictClass(s.verdict)}">${esc(s.verdict ?? "")}</span> <span class="muted">${esc(d)}</span>${note}${said}${detail}</li>`;
    })
    .join("\n");

  // THE TRUST ROW — the same derivation the console reads, so the file and the
  // page cannot say two different things about what Rule 0 found.
  const trust = trustState(frameworkRecord);
  const trustHtml = trust.available
    ? `<p class="row">${esc(trustLine(trust))}${trust.generatedAt ? ` <span class="muted">&middot; ${esc(trust.generatedAt)}</span>` : ""}</p>`
    : `<p class="row muted">${esc(trustLine(trust))} &mdash; run <code>${esc(FRAMEWORK_RECORD_COMMAND)}</code></p>`;

  // THE LADDER ROW — told the rung, never deriving one (evidence-level.mjs).
  const stand = ladderStanding(ladder, {
    earned: r.evidenceLevel && typeof r.evidenceLevel.rung === "string" ? r.evidenceLevel.rung : null,
    passed: steps.filter((s) => s.verdict === "PASS").map((s) => s.name),
  });
  let ladderHtml;
  if (!stand.available) {
    ladderHtml = `<p class="row muted">${esc(stand.reason)}</p>`;
  } else {
    const marks = stand.rungs
      .map(
        (x) =>
          `<span class="rung-mark${x.earned ? " rung-earned" : ""}" title="${escAttr(
            `${x.id} ${x.name} — ${x.mode === "any" ? "any one of" : "every one of"}: ${x.requires.join(", ") || "(nothing declared)"}`,
          )}">${esc(x.id)} ${x.earned ? "&#9679;" : "&#9675;"}</span>`,
      )
      .join(" ");
    const needs = stand.orphanRung
      ? `the receipt records ${esc(stand.earned)}, which this profile's ladder does not declare`
      : stand.atTop
        ? `${esc(stand.earned)} is the top rung this pack declares`
        : stand.next
          ? `${esc(stand.next.id)} needs ${(stand.next.unmet.length ? stand.next.unmet : stand.next.requires)
              .map((n) => `<code>${esc(n)}</code>`)
              .join(stand.next.mode === "any" ? " or " : " &middot; ")}`
          : "this ladder declares no rungs";
    // THE PACK IS ON THIS ROW TOO, not only in the strip above it. The marks
    // ARE rungs, so §6.5 binds them, and the strip cannot stand in: on a run
    // that earned no rung the strip has no pack clause to lend, and the ladder
    // would then draw four unattributed marks. The console's own ladder row
    // carries it for the same reason (console-ladder.mjs).
    const id = r.pack && typeof r.pack.id === "string" && r.pack.id.trim() ? r.pack.id.trim() : null;
    ladderHtml = `<p class="row"><span class="muted">${id ? `pack ${esc(id)}` : "pack unnamed"}</span> &middot; ${marks} &middot; ${needs}</p>`;
  }

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<style>${CSS}</style>
</head>
<body>
<main>
  <h1>${esc(title)}</h1>
  <p class="row muted">A snapshot of one lane run, written by <code>node qa/verify.mjs --html</code>. Nothing here was typed by hand and nothing here is live &mdash; it is what the receipt said at the instant above.</p>
  <div class="strip ${verdict === "PASS" ? "pass" : "fail"}">
    <p class="row">${stripParts.join(" &middot; ")}</p>
  </div>

  <h2>Steps</h2>
  ${steps.length ? `<ol class="steps">\n${stepRows}\n  </ol>` : `<p class="row muted">the receipt records no steps</p>`}

  <h2>Trust &mdash; what Rule 0 last said about this lane</h2>
  ${trustHtml}

  <h2>Ladder &mdash; what would earn the next rung</h2>
  ${ladderHtml}

  <footer>
    Derived from <code>${esc(r.schema ?? "the receipt")}</code>${sha ? ` at <code>${esc(sha)}</code>` : ""}.
    Inputs hash <code>${esc((r.inputs && r.inputs.hash) || "not recorded")}</code>${
      r.inputs && typeof r.inputs.fileCount === "number" ? ` over ${r.inputs.fileCount} files` : ""
    }.
    Absence on this page means not derivable, never "fine".
  </footer>
</main>
</body>
</html>
`;
}
