// token-drift.mjs — pure, dependency-free comparison of a LIVE inspector tree's
// resolved design-token payloads against the declared design-system catalog.
//
// Mirrors the comparison semantics of diffAgainstDesignSystem() in the cmp-inspector
// MCP server (inspector/mcp/src/lib/drift.mjs): for each node in the tree that carries
// a designToken payload ({tokens:[names], resolved:{facet:value}}), and for each
// declared token name on that node, resolve the expected value from the catalog
// (dimens checked before colors — same lookup order as the MCP) and compare it
// against every one of the node's resolved facet values. If NONE of them match the
// declared value, the node has drifted from what it claims to use.
//
// Matching is case/whitespace-normalized string equality (trim + lowercase) — the
// exact normalization the MCP's `normalize()` applies (so "#0A2540" == "#0a2540",
// but "72dp" would NOT equal "72.0dp" — the MCP does not do numeric-format
// normalization, so neither do we, to keep the comparison rule identical).
//
// No imports, no Node built-ins beyond what the runtime provides for free — pure
// object-in/object-out, unit-testable with plain objects.

/**
 * @param {{colors?:Record<string,string>, dimens?:Record<string,string>}} declaredCatalog
 * @param {{root:object}|object} tree  the parsed /inspect/tree document (or a bare node)
 * @returns {{checked:number, drifted:Array<{node:string, token:string, facet:string, expected:string, actual:string}>}}
 */
export function compareTokenDrift(declaredCatalog, tree) {
  const colors = (declaredCatalog && declaredCatalog.colors) || {};
  const dimens = (declaredCatalog && declaredCatalog.dimens) || {};

  let checked = 0;
  const drifted = [];

  for (const { node, path } of walk(tree)) {
    const dt = node && node.designToken;
    if (!dt || !Array.isArray(dt.tokens) || dt.tokens.length === 0) continue;

    const resolved = dt.resolved && typeof dt.resolved === "object" ? dt.resolved : {};
    const resolvedEntries = Object.entries(resolved);

    for (const token of dt.tokens) {
      let declared;
      if (Object.prototype.hasOwnProperty.call(dimens, token)) declared = dimens[token];
      else if (Object.prototype.hasOwnProperty.call(colors, token)) declared = colors[token];
      else continue; // token not in the declared catalog — nothing to diff against

      checked += 1;

      const declaredNorm = normalize(declared);
      const matches = resolvedEntries.some(([, v]) => normalize(v) === declaredNorm);
      if (matches) continue;

      const [facet, actual] = pickFacetForReport(resolvedEntries);
      drifted.push({
        node: node.testTag || path,
        token,
        facet,
        expected: declared,
        actual,
      });
    }
  }

  return { checked, drifted };
}

// Depth-first walk yielding every node with a stable, dotted path. Accepts either a
// full tree ({schemaVersion, source, root}) or a bare node — same contract as the
// MCP's tree.mjs walk().
function* walk(tree) {
  const root = tree && tree.root ? tree.root : tree;
  if (!root || typeof root !== "object") return;
  yield* walkNode(root, "root");
}

function* walkNode(node, path) {
  yield { node, path };
  const children = Array.isArray(node.children) ? node.children : [];
  for (let i = 0; i < children.length; i++) {
    yield* walkNode(children[i], `${path}.children[${i}]`);
  }
}

// Case-insensitive, trimmed comparison — identical to the MCP's normalize().
function normalize(v) {
  return String(v == null ? "" : v).trim().toLowerCase();
}

// Best-effort single (facet, value) to blame in the drift report. If the node
// resolved exactly one facet, name it directly; otherwise join every facet/value
// so the reader sees everything the node actually resolved.
function pickFacetForReport(entries) {
  if (entries.length === 1) return [entries[0][0], String(entries[0][1])];
  if (entries.length === 0) return ["(none)", "(no resolved values)"];
  return [entries.map(([k]) => k).join(","), entries.map(([, v]) => String(v)).join(", ")];
}
