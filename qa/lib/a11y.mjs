// a11y.mjs — accessibility audit over the tree contract.
// Pure logic only — no fs, no MCP imports; unit-testable.
//
// Rules (violations):
//   touch-target-too-small — a clickable node whose width or height is below the
//                            minimum touch target (default 48px; the harness dumps
//                            at density 1 so px == dp there — pass a different
//                            minTouchTargetPx for device-density trees).
//   missing-label          — a clickable node with no text, no contentDescription,
//                            and no descendant text: nothing for a screen reader.
// Rules (warnings):
//   empty-content-description — contentDescription === "" (redundant/empty; either
//                               label it or drop the attribute).
//
// Trees produced before the role/clickable/disabled contract extension are handled
// gracefully: nodes without `clickable` are simply skipped, never crashed on.

import { walk } from "./tree.mjs";

/**
 * Audit a tree for accessibility faults.
 *
 * @param {object} tree  a full tree ({root}) or bare node.
 * @param {{minTouchTargetPx?: number}} [opts]
 * @returns {{
 *   violations: Array<{path:string, testTag:string|null, rule:string, detail:string, bounds:object|null}>,
 *   warnings:   Array<{path:string, testTag:string|null, rule:string, detail:string, bounds:object|null}>,
 *   warningCount: number,
 *   passCount: number
 * }}  passCount = clickable nodes that passed every check.
 */
export function auditA11y(tree, opts = {}) {
  const minTouchTargetPx =
    typeof opts.minTouchTargetPx === "number" && opts.minTouchTargetPx > 0
      ? opts.minTouchTargetPx
      : 48;

  const violations = [];
  const warnings = [];
  let passCount = 0;

  for (const { node, path } of walk(tree)) {
    const entryBase = {
      path,
      testTag: node.testTag ?? null,
      bounds: node.bounds ?? null,
    };

    // Warn on redundant/empty contentDescription regardless of clickability.
    if (node.contentDescription === "") {
      warnings.push({
        ...entryBase,
        rule: "empty-content-description",
        detail: 'contentDescription is an empty string ("") — either label the node or drop the attribute',
      });
    }

    // Interactive checks only apply to nodes that self-report clickable:true.
    // Old trees without the optional field are skipped gracefully.
    if (node.clickable !== true) continue;

    let violated = false;

    const b = node.bounds;
    if (
      b &&
      typeof b.width === "number" &&
      typeof b.height === "number" &&
      (b.width < minTouchTargetPx || b.height < minTouchTargetPx)
    ) {
      violations.push({
        ...entryBase,
        rule: "touch-target-too-small",
        detail: `clickable node is ${b.width}x${b.height}px; minimum touch target is ${minTouchTargetPx}x${minTouchTargetPx}px`,
      });
      violated = true;
    }

    const hasOwnLabel =
      (node.text != null && node.text !== "") ||
      (node.contentDescription != null && node.contentDescription !== "");
    if (!hasOwnLabel && !hasDescendantText(node)) {
      violations.push({
        ...entryBase,
        rule: "missing-label",
        detail: "clickable node has no text, no contentDescription, and no descendant text — invisible to screen readers",
      });
      violated = true;
    }

    if (!violated) passCount++;
  }

  return { violations, warnings, warningCount: warnings.length, passCount };
}

function hasDescendantText(node) {
  for (const child of node.children || []) {
    if (child.text != null && child.text !== "") return true;
    if (child.contentDescription != null && child.contentDescription !== "") return true;
    if (hasDescendantText(child)) return true;
  }
  return false;
}
