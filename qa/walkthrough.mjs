#!/usr/bin/env node
// walkthrough.mjs — the generated, committable walkthrough report (A2/C8/C9),
// and the run-to-run diff (A3).
//
//   node qa/walkthrough.mjs [--port 9500] [--settle 1200] [--out <dir>]
//   node qa/walkthrough.mjs --compare <runDirA> <runDirB> [--out <dir>]
//
// WHAT THIS IS. Evidence, not decoration: one run walks the live app and emits
// `qa/evidence/walkthrough/<stamp>/` containing per-screen pixels + tree +
// a11y — captured from the SAME frame (pixels are read before and after the
// tree; a capture only counts when both reads hash identically) — plus a DB
// appendix read at capture time, and a self-contained report.html styled from
// the app's own design-system catalog (that is why every app's report arrives
// auto-branded in its own tokens). `manifest.json` is the machine half: the
// console's Walkthrough section and `--compare` both consume it, never the HTML.
//
// COVERAGE MODEL — route-jumps for coverage, taps only where the shell demands
// them, honesty about the rest:
//   • shell tabs: discovered live (descendants of `app_bottom_nav` tagged
//     `nav_<slug>`), visited by tapping — tabs are in-shell state, not routes.
//   • parameterless routes from Routes (Screen.kt): visited via the debug
//     inspector's `/inspect/navigate` — mechanical, no guessed tap coordinates.
//   • parameterized routes (`detail/{itemId}`): NOT walked, listed in
//     `notWalked` with the reason. Entity-bearing routes need a behaviour flow
//     (a real tap on a real row), which is e2e's job, not coverage's.
//   • per-screen `@state` variants (home@empty…): stitched from tier-0 renders
//     under composeApp/build/previews, labelled `tier-0` — full four-arm
//     coverage, honestly sourced (C8): the live walk shows the app's real
//     state; contrived arms come from the renderer and say so.
//
// Requires: the debug app running with its inspector reachable (default
// http://127.0.0.1:9500 — `adb forward tcp:9500 tcp:9500`), adb on PATH
// (BACK key between route visits), Node 18+.

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { auditA11y } from "./lib/a11y.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const PREVIEWS_DIR = path.join(ROOT, "composeApp", "build", "previews");
const SPECS_DIR = path.join(ROOT, "specs");
const EVIDENCE_ROOT = path.join(ROOT, "qa", "evidence", "walkthrough");

const args = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] != null ? args[i + 1] : fallback;
};
const PORT = Number(flag("port", 9500));
const SETTLE_MS = Number(flag("settle", 1200));
const BASE = `http://127.0.0.1:${PORT}`;

const sha256 = (buf) => createHash("sha256").update(buf).digest("hex");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getJson(p) {
  const res = await fetch(`${BASE}${p}`);
  const body = await res.text();
  if (!res.ok) throw new Error(`GET ${p} -> ${res.status}: ${body.slice(0, 200)}`);
  return JSON.parse(body);
}
async function getBytes(p) {
  const res = await fetch(`${BASE}${p}`);
  if (!res.ok) throw new Error(`GET ${p} -> ${res.status}`);
  return Buffer.from(await res.arrayBuffer());
}
async function postTap(x, y) {
  const res = await fetch(`${BASE}/inspect/tap`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ x, y }),
  });
  if (!res.ok) throw new Error(`POST /inspect/tap -> ${res.status}: ${await res.text()}`);
}

/** Same-frame capture: pixels → tree → pixels, accepted only when both pixel reads hash alike. */
async function captureStable({ maxAttempts = 4, settleMs = 400 } = {}) {
  let last = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const a = await getBytes("/inspect/screenshot");
    const tree = await getJson("/inspect/tree");
    const b = await getBytes("/inspect/screenshot");
    const ha = sha256(a);
    if (ha === sha256(b)) {
      let route = null;
      try {
        route = (await getJson("/inspect/nav"))?.currentRoute ?? null;
      } catch {
        /* older app without /inspect/nav — capture stands without the label */
      }
      return { png: a, sha256: ha, tree, route, attempts: attempt };
    }
    last = ha;
    if (attempt < maxAttempts) await sleep(settleMs);
  }
  throw new Error(`frame never stabilised (${maxAttempts} attempts, last ${last?.slice(0, 12)}…) — UI still animating?`);
}

const walkTree = function* (node, p = "root") {
  yield { node, path: p };
  let i = 0;
  for (const c of node.children || []) yield* walkTree(c, `${p}.${i++}`);
};

/**
 * The settle rule, applied to the walk itself (the ledger's e2e lesson): a
 * stable FRAME is not a settled SCREEN — a loading skeleton is perfectly
 * stable. Poll the tree until it stops changing between polls AND no
 * registry loading vocabulary (`*_loading`, skeleton) is on screen. On
 * timeout the capture still happens — with `settled:false` recorded, because
 * an honest "captured mid-load" beats a silent one.
 */
async function waitForSettled({ timeoutMs = 8_000, pollMs = 500 } = {}) {
  let prev = null;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const t = await getJson("/inspect/tree");
    const root = t.root ?? t;
    const h = sha256(Buffer.from(JSON.stringify(t)));
    const loading = tagsOf(root).some((tag) => tag.endsWith("_loading") || tag.includes("skeleton"));
    if (!loading && h === prev) return true;
    prev = h;
    await sleep(pollMs);
  }
  return false;
}
const countNodes = (root) => [...walkTree(root)].length;
const tagsOf = (root) => [...walkTree(root)].map(({ node }) => node.testTag).filter(Boolean);
const findTag = (root, tag) => [...walkTree(root)].find(({ node }) => node.testTag === tag)?.node ?? null;

/** Parameterless routes from Screen.kt's Routes object; parameterized ones reported, not walked. */
function discoverRoutes() {
  const navDir = readdirSync(path.join(ROOT, "composeApp", "src", "commonMain", "kotlin"), { recursive: true })
    .map(String)
    .find((f) => f.endsWith(path.join("presentation", "navigation", "Screen.kt")));
  if (!navDir) return { jumpable: [], parameterized: [] };
  const src = readFileSync(path.join(ROOT, "composeApp", "src", "commonMain", "kotlin", navDir), "utf8");
  const routes = [...src.matchAll(/const\s+val\s+[A-Z_]+\s*=\s*"([^"]+)"/g)].map((m) => m[1]);
  return {
    jumpable: routes.filter((r) => r !== "shell" && !r.includes("{")),
    parameterized: routes.filter((r) => r.includes("{")),
  };
}

/** Spec deep-links: `<slug>_screen` root tag -> specs/<slug>.spec.md + its clause ids. */
function specFor(slug) {
  const file = path.join(SPECS_DIR, `${slug}.spec.md`);
  if (!existsSync(file)) return null;
  const clauses = [...readFileSync(file, "utf8").matchAll(/\*\*([A-Z]+-\d+)\*\*/g)].map((m) => m[1]);
  return { file: path.relative(ROOT, file), clauses: [...new Set(clauses)] };
}

/** C8 — tier-0 rendered `@state` variants for a screen, honestly labelled by source. */
function variantsFor(slug, outDir) {
  if (!existsSync(PREVIEWS_DIR)) return [];
  return readdirSync(PREVIEWS_DIR)
    .filter((d) => d.startsWith(`${slug}@`))
    .flatMap((d) => {
      const png = path.join(PREVIEWS_DIR, d, "screen.png");
      if (!existsSync(png)) return [];
      const state = d.slice(slug.length + 1);
      const dest = `variants-${slug}@${state}.png`;
      writeFileSync(path.join(outDir, dest), readFileSync(png));
      return [{ state, png: dest, source: "tier-0" }];
    });
}

function designSystemColors(ds) {
  const c = ds?.colors || {};
  return {
    bg: c.Background || "#0d0f0d",
    surface: c.Surface || "#151815",
    onSurface: c.OnSurface || "#f2f4f2",
    onSurfaceVariant: c.OnSurfaceVariant || "#a9b0a9",
    primary: c.Primary || "#b4f04a",
    outline: c.OutlineVariant || c.Outline || "#2a2e2a",
    error: c.Error || "#ff6b6b",
  };
}

// ---------------------------------------------------------------------------
// The walk
// ---------------------------------------------------------------------------

async function runWalk() {
  let health;
  try {
    health = await getJson("/inspect/health");
  } catch (err) {
    console.error(
      `✗ inspector unreachable at ${BASE} — is the DEBUG app running and forwarded ` +
        `(adb forward tcp:${PORT} tcp:${PORT})? (${err.message})`
    );
    process.exit(1);
  }

  let ds = null;
  let dsSource = "none";
  try {
    ds = await getJson("/inspect/design-system");
    dsSource = "live";
  } catch {
    const f = path.join(PREVIEWS_DIR, "design-system.json");
    if (existsSync(f)) {
      ds = JSON.parse(readFileSync(f, "utf8"));
      dsSource = "tier-0";
    }
  }

  const stamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
  const outDir = path.resolve(flag("out", path.join(EVIDENCE_ROOT, stamp)));
  mkdirSync(outDir, { recursive: true });

  const screens = [];
  const notWalked = [];

  const record = async (id, kind, extra = {}) => {
    const settled = await waitForSettled();
    const cap = await captureStable();
    const root = cap.tree.root ?? cap.tree;
    writeFileSync(path.join(outDir, `${id}.png`), cap.png);
    writeFileSync(path.join(outDir, `${id}.tree.json`), JSON.stringify(cap.tree, null, 2));
    const slug = tagsOf(root).find((t) => t.endsWith("_screen"))?.replace(/_screen$/, "") ?? id;
    const a11y = auditA11y(root);
    screens.push({
      id,
      kind,
      slug,
      route: cap.route,
      png: `${id}.png`,
      treeJson: `${id}.tree.json`,
      sha256: cap.sha256,
      captureAttempts: cap.attempts,
      settled,
      nodes: countNodes(root),
      tags: tagsOf(root),
      a11y: { violations: a11y.violations, warnings: a11y.warnings, passCount: a11y.passCount },
      spec: specFor(slug),
      variants: variantsFor(slug, outDir),
      ...extra,
    });
    console.log(`  ✓ ${id} (${cap.route ?? "route unknown"}, ${countNodes(root)} nodes, a11y ${a11y.violations.length} violations)`);
    return cap;
  };

  // 1. Shell tabs — discovered live, visited by tap (they are state, not routes).
  console.log("walking shell tabs…");
  const first = await captureStable();
  const firstRoot = first.tree.root ?? first.tree;
  const navTags = tagsOf(firstRoot).filter((t) => t.startsWith("nav_"));
  if (navTags.length === 0) console.log("  (no nav_* tags found — single-screen app?)");
  for (const tag of navTags) {
    // Re-read the tree each round: bounds may shift with selection state.
    const tree = await getJson("/inspect/tree");
    const node = findTag(tree.root ?? tree, tag);
    if (!node?.bounds) {
      notWalked.push({ target: tag, reason: "nav tag present but no bounds — not tappable from here" });
      continue;
    }
    await postTap(
      Math.round(node.bounds.x + node.bounds.width / 2),
      Math.round(node.bounds.y + node.bounds.height / 2)
    );
    await sleep(SETTLE_MS);
    await record(tag.replace(/^nav_/, ""), "tab", { visitedVia: `tap ${tag}` });
  }

  // 2. Parameterless routes — mechanical coverage via /inspect/navigate.
  const { jumpable, parameterized } = discoverRoutes();
  if (jumpable.length) console.log("walking routes…");
  for (const route of jumpable) {
    try {
      const res = await fetch(`${BASE}/inspect/navigate?route=${encodeURIComponent(route)}`);
      if (!res.ok) {
        notWalked.push({ target: route, reason: `navigate -> ${res.status}: ${(await res.text()).slice(0, 120)}` });
        continue;
      }
      await sleep(SETTLE_MS);
      await record(route.replace(/[^a-z0-9]+/gi, "-"), "route", { visitedVia: `/inspect/navigate?route=${route}` });
      execFileSync("adb", ["shell", "input", "keyevent", "4"]); // BACK — return to shell for the next visit
      await sleep(SETTLE_MS);
    } catch (err) {
      notWalked.push({ target: route, reason: err.message.slice(0, 160) });
    }
  }
  for (const route of parameterized) {
    notWalked.push({ target: route, reason: "parameterized — needs a behaviour flow (e2e), not blind coverage" });
  }

  // 3. C9 — the DB appendix, read AT CAPTURE TIME: rows are the persistence receipt.
  let db = null;
  try {
    const schema = await getJson("/inspect/db");
    const tables = [];
    for (const t of schema.tables ?? []) {
      const name = typeof t === "string" ? t : t.name;
      try {
        const q = await getJson(`/inspect/db?table=${encodeURIComponent(name)}&limit=5`);
        tables.push({ name, rowCount: q.rowCount ?? (q.rows ? q.rows.length : null), sample: q.rows ?? [] });
      } catch (err) {
        tables.push({ name, error: err.message.slice(0, 120) });
      }
    }
    db = { source: "GET /inspect/db at capture time", tables };
  } catch {
    db = null; // no Room / endpoint absent — the report states the absence honestly
  }

  const manifest = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    appId: health.appId,
    processStartedAtMs: health.processStartedAtMs ?? null,
    inspector: BASE,
    designSystemSource: dsSource,
    screens,
    notWalked,
    db,
  };
  writeFileSync(path.join(outDir, "manifest.json"), JSON.stringify(manifest, null, 2));
  writeFileSync(path.join(outDir, "report.html"), reportHtml(manifest, ds));
  console.log(`\n✅ walkthrough -> ${path.relative(ROOT, outDir)} (${screens.length} screens, ${notWalked.length} not walked)`);
  console.log(`   report: ${path.join(path.relative(ROOT, outDir), "report.html")}`);
}

// ---------------------------------------------------------------------------
// report.html — styled from the app's own tokens (that's the auto-branding)
// ---------------------------------------------------------------------------

const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

function reportHtml(m, ds) {
  const c = designSystemColors(ds);
  const cards = m.screens
    .map((s) => {
      const a11yLine =
        s.a11y.violations.length === 0
          ? `<span class="ok">a11y: 0 violations</span>`
          : `<span class="bad">a11y: ${s.a11y.violations.length} violations</span>`;
      const spec = s.spec
        ? `<div class="meta">spec: ${esc(s.spec.file)} — ${s.spec.clauses.map(esc).join(", ") || "no clauses"}</div>`
        : `<div class="meta dim">no per-feature spec file</div>`;
      const variants = s.variants.length
        ? `<div class="variants">${s.variants
            .map((v) => `<figure><img src="${esc(v.png)}" loading="lazy"><figcaption>@${esc(v.state)} · ${esc(v.source)}</figcaption></figure>`)
            .join("")}</div>`
        : "";
      return `<section class="card">
  <h2>${esc(s.id)} <span class="chip">${esc(s.kind)}</span></h2>
  <div class="meta">route: <code>${esc(s.route ?? "—")}</code> · via ${esc(s.visitedVia ?? "—")} · ${s.nodes} nodes · frame ${esc(s.sha256.slice(0, 12))}… · ${a11yLine}</div>
  ${spec}
  <img class="shot" src="${esc(s.png)}" loading="lazy">
  ${variants}
</section>`;
    })
    .join("\n");

  const notWalked = m.notWalked.length
    ? `<section class="card"><h2>Not walked</h2><ul>${m.notWalked
        .map((n) => `<li><code>${esc(n.target)}</code> — ${esc(n.reason)}</li>`)
        .join("")}</ul></section>`
    : "";

  const db = m.db
    ? `<section class="card"><h2>DB appendix <span class="chip">persistence receipt</span></h2>
<div class="meta">${esc(m.db.source)}</div>
${m.db.tables
  .map((t) =>
    t.error
      ? `<h3>${esc(t.name)}</h3><div class="meta bad">${esc(t.error)}</div>`
      : `<h3>${esc(t.name)} <span class="dim">(${t.rowCount ?? "?"} rows)</span></h3><pre>${esc(
          JSON.stringify(t.sample, null, 1).slice(0, 2000)
        )}</pre>`
  )
  .join("")}</section>`
    : `<section class="card"><h2>DB appendix</h2><div class="meta dim">no DB endpoint (Room off, or app predates /inspect/db)</div></section>`;

  return `<!doctype html><meta charset="utf-8">
<title>${esc(m.appId)} — walkthrough ${esc(m.generatedAt)}</title>
<style>
  :root { color-scheme: dark; }
  body { background:${c.bg}; color:${c.onSurface}; font: 15px/1.5 system-ui, sans-serif; margin: 0 auto; max-width: 900px; padding: 24px; }
  h1 { font-size: 22px; } h2 { font-size: 17px; margin: 0 0 6px; } h3 { font-size: 14px; margin: 14px 0 4px; }
  .card { background:${c.surface}; border: 1px solid ${c.outline}; border-radius: 12px; padding: 16px 18px; margin: 14px 0; }
  .meta { color:${c.onSurfaceVariant}; font-size: 13px; margin: 2px 0; }
  .dim { opacity:.7 } .ok { color:${c.primary} } .bad { color:${c.error} }
  .chip { background:${c.bg}; border:1px solid ${c.outline}; border-radius:999px; padding:1px 9px; font-size:11px; vertical-align:2px; color:${c.onSurfaceVariant} }
  img.shot { width: 260px; border-radius: 10px; border:1px solid ${c.outline}; margin-top: 8px; }
  .variants { display:flex; gap:10px; margin-top:10px; flex-wrap:wrap }
  .variants img { width: 150px; border-radius:8px; border:1px solid ${c.outline} }
  .variants figcaption { font-size:11px; color:${c.onSurfaceVariant}; text-align:center }
  pre { background:${c.bg}; border-radius:8px; padding:10px; overflow-x:auto; font-size:12px }
  code { color:${c.primary} }
</style>
<h1>${esc(m.appId)} — walkthrough</h1>
<div class="meta">${esc(m.generatedAt)} · inspector ${esc(m.inspector)} · process started ${esc(
    m.processStartedAtMs ? new Date(m.processStartedAtMs).toISOString() : "unknown"
  )} · design tokens: ${esc(m.designSystemSource)}</div>
<div class="meta">Evidence, not decoration — every card is pixels + tree + a11y from one proven frame; variants are tier-0 renders and say so; the DB appendix was read at capture time.</div>
${cards}
${notWalked}
${db}`;
}

// ---------------------------------------------------------------------------
// --compare — A3: two runs, side by side, screen by screen
// ---------------------------------------------------------------------------

function runCompare(dirA, dirB) {
  const load = (d) => {
    const f = path.join(path.resolve(d), "manifest.json");
    if (!existsSync(f)) {
      console.error(`✗ no manifest.json in ${d} — is this a walkthrough run directory?`);
      process.exit(1);
    }
    return JSON.parse(readFileSync(f, "utf8"));
  };
  const A = load(dirA);
  const B = load(dirB);
  const outDir = path.resolve(flag("out", path.join(EVIDENCE_ROOT, `diff-${Date.now()}`)));
  mkdirSync(outDir, { recursive: true });
  const relA = (p) => path.join(path.relative(outDir, path.resolve(dirA)), p);
  const relB = (p) => path.join(path.relative(outDir, path.resolve(dirB)), p);

  const ids = [...new Set([...A.screens.map((s) => s.id), ...B.screens.map((s) => s.id)])];
  const rows = ids.map((id) => {
    const a = A.screens.find((s) => s.id === id) ?? null;
    const b = B.screens.find((s) => s.id === id) ?? null;
    return {
      id,
      inA: !!a,
      inB: !!b,
      pixelsChanged: a && b ? a.sha256 !== b.sha256 : null,
      nodesDelta: a && b ? b.nodes - a.nodes : null,
      a11yDelta: a && b ? b.a11y.violations.length - a.a11y.violations.length : null,
      tagsAdded: a && b ? b.tags.filter((t) => !a.tags.includes(t)) : [],
      tagsRemoved: a && b ? a.tags.filter((t) => !b.tags.includes(t)) : [],
    };
  });

  const diff = { schemaVersion: 1, runA: { dir: dirA, generatedAt: A.generatedAt }, runB: { dir: dirB, generatedAt: B.generatedAt }, rows };
  writeFileSync(path.join(outDir, "diff.json"), JSON.stringify(diff, null, 2));

  const cards = rows
    .map((r) => {
      const a = A.screens.find((s) => s.id === r.id);
      const b = B.screens.find((s) => s.id === r.id);
      const verdict = !r.inA
        ? `<span class="chip">new in B</span>`
        : !r.inB
          ? `<span class="chip">removed in B</span>`
          : r.pixelsChanged
            ? `<span class="bad">pixels changed</span> · nodes ${r.nodesDelta >= 0 ? "+" : ""}${r.nodesDelta} · a11y ${r.a11yDelta >= 0 ? "+" : ""}${r.a11yDelta}`
            : `<span class="ok">identical pixels</span>`;
      const tagNotes =
        r.tagsAdded.length || r.tagsRemoved.length
          ? `<div class="meta">tags: ${r.tagsAdded.map((t) => `+${esc(t)}`).join(" ")} ${r.tagsRemoved.map((t) => `−${esc(t)}`).join(" ")}</div>`
          : "";
      return `<section class="card"><h2>${esc(r.id)}</h2><div class="meta">${verdict}</div>${tagNotes}
<div class="pair">${a ? `<figure><img src="${esc(relA(a.png))}" loading="lazy"><figcaption>A · ${esc(A.generatedAt)}</figcaption></figure>` : ""}
${b ? `<figure><img src="${esc(relB(b.png))}" loading="lazy"><figcaption>B · ${esc(B.generatedAt)}</figcaption></figure>` : ""}</div></section>`;
    })
    .join("\n");

  writeFileSync(
    path.join(outDir, "diff.html"),
    `<!doctype html><meta charset="utf-8"><title>walkthrough diff</title>
<style>
  :root{color-scheme:dark} body{background:#0d0f0d;color:#f2f4f2;font:15px/1.5 system-ui;margin:0 auto;max-width:960px;padding:24px}
  .card{background:#151815;border:1px solid #2a2e2a;border-radius:12px;padding:16px 18px;margin:14px 0}
  .meta{color:#a9b0a9;font-size:13px}.ok{color:#b4f04a}.bad{color:#ff6b6b}
  .chip{border:1px solid #2a2e2a;border-radius:999px;padding:1px 9px;font-size:11px;color:#a9b0a9}
  .pair{display:flex;gap:14px;margin-top:10px}.pair img{width:240px;border-radius:10px;border:1px solid #2a2e2a}
  figcaption{font-size:11px;color:#a9b0a9;text-align:center}h2{font-size:17px;margin:0 0 6px}
</style>
<h1>Walkthrough diff</h1><div class="meta">A: ${esc(dirA)} (${esc(A.generatedAt)})<br>B: ${esc(dirB)} (${esc(B.generatedAt)})</div>
${cards}`
  );
  const changed = rows.filter((r) => r.pixelsChanged).length;
  console.log(`✅ diff -> ${path.relative(ROOT, outDir)} (${rows.length} screens, ${changed} with pixel changes)`);
}

// ---------------------------------------------------------------------------

const compareIdx = args.indexOf("--compare");
if (compareIdx >= 0) {
  const [a, b] = [args[compareIdx + 1], args[compareIdx + 2]];
  if (!a || !b) {
    console.error("usage: node qa/walkthrough.mjs --compare <runDirA> <runDirB> [--out <dir>]");
    process.exit(1);
  }
  runCompare(a, b);
} else {
  runWalk();
}
