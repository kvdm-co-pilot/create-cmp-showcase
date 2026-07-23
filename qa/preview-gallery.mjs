#!/usr/bin/env node
// preview-gallery.mjs — build a self-contained HTML gallery from renderScreens output.
//
//   node qa/preview-gallery.mjs [previewsDir]
//
// Reads <previewsDir>/manifest.json (default composeApp/build/previews — the output of
// `./gradlew :composeApp:renderScreens`), renders each screen's tree.json to a wireframe
// SVG with the vendored inspector render lib (structure for the AI), and embeds the
// harness PNGs (pixels for the human) into ONE index.html — no server, no device, open
// the file. Also drops <id>/wireframe.svg next to each tree.
//
// Zero dependencies beyond qa/lib (vendored, pure logic) — works without the create-cmp
// plugin installed, like every other qa/ script.
import { readFileSync, writeFileSync } from "node:fs";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const previewsDir = resolve(
  process.argv[2] || join(HERE, "..", "composeApp", "build", "previews"),
);

const { renderTreeSvg } = await import(new URL("./lib/render.mjs", import.meta.url));
const { auditA11y } = await import(new URL("./lib/a11y.mjs", import.meta.url));

const manifest = JSON.parse(readFileSync(join(previewsDir, "manifest.json"), "utf8"));
const { width, height, pngScale } = manifest.viewport;

const cards = [];
for (const screen of manifest.screens) {
  const tree = JSON.parse(readFileSync(join(previewsDir, screen.tree), "utf8"));
  const audit = auditA11y(tree);
  const svg = renderTreeSvg(tree, { a11y: audit });
  writeFileSync(join(previewsDir, screen.id, "wireframe.svg"), svg);

  const png = readFileSync(join(previewsDir, screen.png));
  const summary = summarize(tree);
  cards.push({ screen, svg, pngB64: png.toString("base64"), audit, summary });
  console.log(
    `${screen.id}: ${summary.nodes} nodes, ${summary.tokenized} tokenized, ` +
      `${summary.tagged} tagged, a11y ${audit.pass ? "PASS" : audit.violations.length + " violation(s)"}`,
  );
}

function summarize(tree) {
  let nodes = 0, tokenized = 0, tagged = 0;
  (function walk(n) {
    nodes++;
    if (n.designToken) tokenized++;
    if (n.testTag) tagged++;
    (n.children || []).forEach(walk);
  })(tree.root);
  return { nodes, tokenized, tagged };
}

const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

const html = `<!doctype html>
<meta charset="utf-8">
<title>Fuelled — screen previews (headless)</title>
<style>
  :root { color-scheme: light; }
  body { font-family: -apple-system, system-ui, sans-serif; margin: 0; background: #F7F9FC; color: #1A1A1A; }
  header { padding: 20px 28px 8px; }
  header h1 { margin: 0 0 4px; font-size: 20px; }
  header p { margin: 0; color: #6B7280; font-size: 13px; }
  .grid { display: flex; flex-wrap: wrap; gap: 24px; padding: 20px 28px 40px; }
  .card { background: #fff; border: 1px solid #E5E7EB; border-radius: 16px; padding: 16px; }
  .card h2 { margin: 0 0 2px; font-size: 15px; }
  .meta { color: #6B7280; font-size: 12px; margin: 0 0 10px; }
  .meta .fail { color: #DC2626; font-weight: 600; }
  .meta .pass { color: #16A34A; font-weight: 600; }
  .panes { display: flex; gap: 12px; align-items: flex-start; }
  .panes img { width: ${Math.round(width * 0.62)}px; border: 1px solid #E5E7EB; border-radius: 12px; display: block; }
  .panes .wire svg { width: ${Math.round(width * 0.78)}px; height: auto; display: block; }
  .wire { border: 1px dashed #C8D0DA; border-radius: 12px; overflow: hidden; }
  .lbl { font-size: 10px; letter-spacing: .06em; text-transform: uppercase; color: #9CA3AF; margin: 0 0 4px; }
</style>
<header>
  <h1>Fuelled — screen previews</h1>
  <p>Rendered headlessly (no device/emulator) by <code>:composeApp:renderScreens</code> —
     ${width}×${height}dp, PNG @${pngScale}x · pixels for humans, wireframe+tree for the AI ·
     regenerate: <code>./gradlew :composeApp:renderScreens && node qa/preview-gallery.mjs</code></p>
</header>
<div class="grid">
${cards
  .map(
    ({ screen, svg, pngB64, audit, summary }) => `  <div class="card">
    <h2>${esc(screen.title)}</h2>
    <p class="meta">id <code>${esc(screen.id)}</code> · ${summary.nodes} nodes ·
       ${summary.tokenized} tokenized · ${summary.tagged} tagged ·
       a11y <span class="${audit.pass ? "pass" : "fail"}">${
      audit.pass ? "PASS" : esc(audit.violations.length + " violation(s)")
    }</span></p>
    <div class="panes">
      <div><p class="lbl">pixels</p><img alt="${esc(screen.id)} pixels" src="data:image/png;base64,${pngB64}"></div>
      <div><p class="lbl">structure</p><div class="wire">${svg}</div></div>
    </div>
  </div>`,
  )
  .join("\n")}
</div>
`;

const outFile = join(previewsDir, "index.html");
writeFileSync(outFile, html);
console.log(`gallery -> ${outFile}`);
