// e2e-coverage.mjs — every real feature with a screen has a device journey.
//
// The question (Karel, 2026-09-03): "is anything forcing e2e maestro tests to be
// written when we implement a feature?" Before this gate: no. A feature's flow
// was stamped as a skeleton that cites nothing, every clause could be proven by
// JVM tests alone, and a UI feature reached "done" with zero device evidence.
//
// The rule: a feature is REAL when it has a screen (presentation/<f>/*Screen.kt
// — reachability's definition) AND a spec (specs/<f>.spec.md). A real feature
// must have at least one live clause cited from a flow under qa/e2e — a flow
// that the lane RUNS (spec-coverage's listFlowFiles is the one list). A
// placeholder screen without a spec is not yet real (CHANGE-FLOW-DESIGN:
// "placeholder tabs earn a brief only when they become real") — reported, not
// failed. A screen declared `{ "unrouted": true }` in its brief has no journey
// to walk — exempt, by the same declare-not-gate mechanism reachability uses.
//
// Pure Node, milliseconds. FAILs by name with the file to write.

import fs from "node:fs";
import path from "node:path";
import { evaluateReachability } from "./reachability.mjs";
import { scanCitations, scanSpecClauses } from "../../spec-coverage.mjs";
import { specModelFrom } from "../../spec-model.mjs";
import { layout as cmpLayout, tiers as cmpTiers, grammar as cmpGrammar } from "./declarations.mjs";

const SPEC_MODEL = (() => {
  const r = specModelFrom({ id: "cmp", layout: cmpLayout, tiers: cmpTiers, grammar: cmpGrammar });
  if (!r.ok) throw new Error(r.reason);
  return r.model;
})();
const E2E_FLOW_DIR = cmpLayout.flows.dir;
const JOURNEY_TIER = cmpTiers.journey;

/**
 * @param {string} root
 * @returns {{verdict: "PASS"|"FAIL"|"SKIP", reason?: string, details: {features: Array<{name: string, spec: string|null, liveClauses: number, e2eCited: string[], status: "covered"|"uncovered"|"unspecified"|"unrouted"}>}}}
 */
export function evaluateE2eCoverage(root) {
  if (!fs.existsSync(path.join(root, E2E_FLOW_DIR))) {
    return { verdict: "SKIP", reason: `e2e harness not included in this project (no ${E2E_FLOW_DIR}/)`, details: { features: [] } };
  }
  const reach = evaluateReachability(root);
  const screenFeatures = reach.details && Array.isArray(reach.details.features) ? reach.details.features : [];
  if (screenFeatures.length === 0) {
    return { verdict: "SKIP", reason: reach.reason ?? "no presentation/<feature> directory has a *Screen.kt file — nothing to cover", details: { features: [] } };
  }
  const clauses = scanSpecClauses(root, SPEC_MODEL);
  const e2eTags = scanCitations(root, SPEC_MODEL).filter((t) => t.tier === JOURNEY_TIER);
  const features = screenFeatures.map((f) => {
    const specRel = `specs/${f.name}.spec.md`;
    const spec = fs.existsSync(path.join(root, specRel)) ? specRel : null;
    if (f.unrouted) return { name: f.name, spec, liveClauses: 0, e2eCited: [], status: "unrouted" };
    if (!spec) return { name: f.name, spec: null, liveClauses: 0, e2eCited: [], status: "unspecified" };
    const live = [...clauses.entries()].filter(([, c]) => c.file.split(path.sep).join("/") === specRel && !c.withdrawn).map(([id]) => id);
    const e2eCited = [...new Set(e2eTags.filter((t) => live.includes(t.id)).map((t) => t.id))].sort();
    return { name: f.name, spec, liveClauses: live.length, e2eCited, status: e2eCited.length ? "covered" : "uncovered" };
  });
  const uncovered = features.filter((f) => f.status === "uncovered");
  if (uncovered.length === 0) return { verdict: "PASS", details: { features } };
  const lines = [
    `${uncovered.length} feature${uncovered.length === 1 ? " has" : "s have"} a screen and a spec but no device journey — no flow under ${E2E_FLOW_DIR}/ cites any of their clauses:`,
    ...uncovered.map((f) =>
      f.liveClauses === 0
        ? `  [${f.name}] ${f.spec} has no live clauses — promise the behaviour there first, then prove one clause in ${E2E_FLOW_DIR}/${f.name}.yaml (# SPEC: <ID> above the steps)`
        : `  [${f.name}] ${f.spec} has ${f.liveClauses} live clause${f.liveClauses === 1 ? "" : "s"} — write the journey in ${E2E_FLOW_DIR}/${f.name}.yaml and cite the clause(s) it proves (# SPEC: <ID> above the steps)`,
    ),
    "A UI feature is proven on a device, not only on the JVM. Declare { \"unrouted\": true } in its brief only if the screen is intentionally not reachable yet.",
  ];
  return { verdict: "FAIL", reason: lines.join("\n"), details: { features } };
}
