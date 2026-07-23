// render.mjs — deterministic SVG wireframe of a CMP inspector tree.
//
// The "structural twin" of a pixel preview: every node with a non-zero visual
// footprint becomes a rect; token-annotated nodes are visually distinct and carry
// a small chip with their resolved values ("radius 16 · pad 16"); clickable nodes
// get a distinct outline; testTags render as small mono labels; text nodes show
// their text. An optional a11y audit result overlays violations in a danger style.
//
// SVG is structured TEXT, not pixels — safe for model context, works for ANY
// source (file / live / uiautomator). Output is fully deterministic: no dates,
// no randomness; the same tree + opts always yields byte-identical SVG.
//
// Pure logic only — no fs, no MCP imports; the server wires file I/O around it.

import { walk } from "./tree.mjs";

const FIT_WIDTH = 740; // target drawing width when no explicit scale is given
const MARGIN = 16;
const LEGEND_H = 30;
const FOOTER_H = 24;

const STYLE = {
  plain: { fill: "none", stroke: "#9CA3AF", strokeWidth: 1, dash: null },
  tokenized: { fill: "rgba(0,185,107,0.10)", stroke: "#00B96B", strokeWidth: 1.5, dash: null },
  clickableStroke: "#2563EB",
  dangerStroke: "#DC2626",
  chipFill: "#0A2540",
  chipText: "#FFFFFF",
  tagText: "#6B7280",
  nodeText: "#1A1A1A",
  footerText: "#6B7280",
};

/**
 * Render a tree (full {root} document or bare node) as an SVG wireframe string.
 *
 * @param {object} tree
 * @param {object} [opts]
 * @param {object} [opts.a11y]     an auditA11y() result — its violations are overlaid
 *                                 in the danger style (matched to nodes by path).
 * @param {number} [opts.maxDepth] only draw nodes up to this depth (root = 0).
 * @param {number} [opts.scale]    explicit px scale; default fits root width to ~740.
 * @returns {string} the SVG document.
 */
export function renderTreeSvg(tree, opts = {}) {
  const root = tree && tree.root ? tree.root : tree;
  if (!root || typeof root !== "object") {
    throw new Error("renderTreeSvg: tree has no root node.");
  }
  const schemaVersion = (tree && tree.schemaVersion) ?? 1;
  const source = (tree && tree.source) ?? "unknown";

  const rootW = boundsDim(root.bounds, "width") || 360;
  const rootH = boundsDim(root.bounds, "height") || 640;
  const scale = typeof opts.scale === "number" && opts.scale > 0 ? opts.scale : FIT_WIDTH / rootW;
  const maxDepth =
    typeof opts.maxDepth === "number" && opts.maxDepth >= 0 ? opts.maxDepth : Infinity;

  // Violations by node path (danger overlay).
  const violationsByPath = new Map();
  if (opts.a11y && Array.isArray(opts.a11y.violations)) {
    for (const v of opts.a11y.violations) {
      if (!violationsByPath.has(v.path)) violationsByPath.set(v.path, []);
      violationsByPath.get(v.path).push(v.rule);
    }
  }

  const drawW = rootW * scale;
  const drawH = rootH * scale;
  const svgW = Math.ceil(drawW + MARGIN * 2);
  const svgH = Math.ceil(LEGEND_H + drawH + FOOTER_H + MARGIN * 2);
  const originX = MARGIN;
  const originY = LEGEND_H + MARGIN / 2;

  let nodeCount = 0;
  const body = [];

  for (const { node, path } of walk(root)) {
    nodeCount++;
    if (depthOf(path) > maxDepth) continue;
    const b = node.bounds;
    const w = boundsDim(b, "width");
    const h = boundsDim(b, "height");
    if (!(w > 0 && h > 0)) continue; // zero-footprint nodes have nothing to draw

    const x = originX + (b.x || 0) * scale;
    const y = originY + (b.y || 0) * scale;
    const sw = w * scale;
    const sh = h * scale;
    const tokenized = node.designToken != null;
    const clickable = node.clickable === true;
    const rules = violationsByPath.get(path);

    const base = tokenized ? STYLE.tokenized : STYLE.plain;
    body.push(
      `<rect x="${fmt(x)}" y="${fmt(y)}" width="${fmt(sw)}" height="${fmt(sh)}" ` +
        `fill="${base.fill}" stroke="${base.stroke}" stroke-width="${base.strokeWidth}"` +
        `${tokenized ? ` class="tokenized"` : ""} data-path="${esc(path)}"/>`
    );
    if (clickable) {
      // Distinct clickable outline, drawn just inside the node rect.
      body.push(
        `<rect x="${fmt(x + 1.5)}" y="${fmt(y + 1.5)}" width="${fmt(Math.max(sw - 3, 1))}" ` +
          `height="${fmt(Math.max(sh - 3, 1))}" fill="none" stroke="${STYLE.clickableStroke}" ` +
          `stroke-width="2" stroke-dasharray="5 3" class="clickable"/>`
      );
    }
    if (rules && rules.length > 0) {
      // Danger overlay + rule label for a11y violations.
      body.push(
        `<rect x="${fmt(x - 2)}" y="${fmt(y - 2)}" width="${fmt(sw + 4)}" height="${fmt(sh + 4)}" ` +
          `fill="rgba(220,38,38,0.08)" stroke="${STYLE.dangerStroke}" stroke-width="2" class="a11y-violation"/>`
      );
      body.push(
        `<text x="${fmt(x)}" y="${fmt(y - 4)}" font-family="monospace" font-size="8" ` +
          `fill="${STYLE.dangerStroke}" class="a11y-label">! ${esc([...rules].sort().join(", "))}</text>`
      );
    }
    if (node.testTag) {
      body.push(
        `<text x="${fmt(x + 3)}" y="${fmt(y + 9)}" font-family="monospace" font-size="8" ` +
          `fill="${STYLE.tagText}" class="test-tag">${esc(node.testTag)}</text>`
      );
    }
    if (node.text) {
      body.push(
        `<text x="${fmt(x + 3)}" y="${fmt(y + sh / 2 + 3)}" font-family="sans-serif" font-size="10" ` +
          `fill="${STYLE.nodeText}" class="node-text">${esc(truncate(node.text, 48))}</text>`
      );
    }
    if (tokenized) {
      const chip = tokenChip(node.designToken);
      if (chip) {
        const chipW = chip.length * 4.6 + 8;
        const chipY = y + sh - 12;
        body.push(
          `<rect x="${fmt(x + 2)}" y="${fmt(chipY)}" width="${fmt(chipW)}" height="11" rx="5" ` +
            `fill="${STYLE.chipFill}" opacity="0.85" class="token-chip"/>`
        );
        body.push(
          `<text x="${fmt(x + 6)}" y="${fmt(chipY + 8.5)}" font-family="monospace" font-size="7.5" ` +
            `fill="${STYLE.chipText}" class="token-chip-text">${esc(chip)}</text>`
        );
      }
    }
  }

  const legend = legendRow(opts.a11y != null);
  const footer =
    `<text x="${MARGIN}" y="${svgH - 8}" font-family="monospace" font-size="10" ` +
    `fill="${STYLE.footerText}" class="footer">${nodeCount} nodes · ${esc(source)} · schemaVersion ${schemaVersion}</text>`;

  return [
    `<svg xmlns="http://www.w3.org/2000/svg" width="${svgW}" height="${svgH}" viewBox="0 0 ${svgW} ${svgH}">`,
    `<rect x="0" y="0" width="${svgW}" height="${svgH}" fill="#F7F9FC"/>`,
    legend,
    ...body,
    footer,
    `</svg>`,
    ``,
  ].join("\n");
}

// --- helpers ----------------------------------------------------------------

// Legend row across the top: what each visual style means.
function legendRow(withA11y) {
  const items = [];
  let x = MARGIN;
  const y = 8;
  const swatch = (fill, stroke, dash, label, cls) => {
    const parts = [
      `<rect x="${fmt(x)}" y="${y}" width="14" height="10" fill="${fill}" stroke="${stroke}" ` +
        `stroke-width="1.5"${dash ? ` stroke-dasharray="${dash}"` : ""} class="legend-${cls}"/>`,
      `<text x="${fmt(x + 18)}" y="${y + 9}" font-family="sans-serif" font-size="9" fill="#1A1A1A">${label}</text>`,
    ];
    x += 18 + label.length * 5.2 + 14;
    items.push(...parts);
  };
  swatch("none", STYLE.plain.stroke, null, "node", "node");
  swatch(STYLE.tokenized.fill, STYLE.tokenized.stroke, null, "tokenized", "tokenized");
  swatch("none", STYLE.clickableStroke, "5 3", "clickable", "clickable");
  if (withA11y) swatch("rgba(220,38,38,0.08)", STYLE.dangerStroke, null, "a11y violation", "a11y");
  return `<g class="legend">${items.join("")}</g>`;
}

// "radius 16 · pad 16" — compact resolved-values chip, sorted keys for determinism.
function tokenChip(dt) {
  if (!dt || !dt.resolved || typeof dt.resolved !== "object") return null;
  const keys = Object.keys(dt.resolved).sort();
  if (keys.length === 0) return null;
  const parts = keys.map((k) => {
    const v = String(dt.resolved[k]).replace(/(dp|sp)$/i, "");
    return `${abbrev(k)} ${v}`.trim();
  });
  return truncate(parts.join(" · "), 64);
}

const ABBREV = {
  padding: "pad",
  elevation: "elev",
  fontSize: "font",
  height: "h",
  width: "w",
  statusBarPadding: "statusBar",
  navBarPadding: "navBar",
};
function abbrev(key) {
  return ABBREV[key] ?? key;
}

function depthOf(path) {
  return (path.match(/\.children\[/g) || []).length;
}

function boundsDim(b, key) {
  return b && typeof b[key] === "number" ? b[key] : 0;
}

function fmt(n) {
  // Fixed one-decimal formatting: deterministic and diff-friendly.
  return (Math.round(n * 10) / 10).toString();
}

function truncate(s, max) {
  const str = String(s);
  return str.length <= max ? str : str.slice(0, max - 1) + "…";
}

function esc(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/**
 * Count the nodes renderTreeSvg would draw as rects (non-zero footprint within
 * maxDepth) plus the total node count — used by the tool result.
 */
export function countRenderable(tree, opts = {}) {
  const root = tree && tree.root ? tree.root : tree;
  const maxDepth =
    typeof opts.maxDepth === "number" && opts.maxDepth >= 0 ? opts.maxDepth : Infinity;
  let total = 0;
  let drawn = 0;
  for (const { node, path } of walk(root)) {
    total++;
    if (depthOf(path) > maxDepth) continue;
    if (boundsDim(node.bounds, "width") > 0 && boundsDim(node.bounds, "height") > 0) drawn++;
  }
  return { total, drawn };
}
