// tree.mjs — pure helpers for loading and walking a CMP inspector tree.
// No MCP imports here: everything is unit-testable in isolation.
//
// The JSON tree contract (schemaVersion 1):
//   { schemaVersion, source, root: <Node> }
//   Node = { testTag, text, contentDescription, bounds:{x,y,width,height},
//            designToken: { tokens:string[], resolved:{[k]:string} } | null,
//            children: Node[] }
//
// Additive optional fields (still schemaVersion 1 — absent on old trees, so every
// consumer must treat them as optional):
//   role:      string|null  — semantics Role (e.g. "Button", "Checkbox")
//   clickable: boolean      — presence of the OnClick semantics action
//   disabled:  boolean      — presence of the Disabled semantics property

import { readFileSync } from "node:fs";

/**
 * Load a tree from a filesystem path, a JSON string, or an already-parsed object.
 * Validates the minimal shape (schemaVersion + root) and throws a clear,
 * caller-facing Error (never a raw fs/JSON stack) on failure.
 *
 * @param {string|object} pathOrObj
 * @returns {object} the parsed tree ({ schemaVersion, source, root })
 */
export function loadTree(pathOrObj) {
  if (pathOrObj == null) {
    throw new Error("loadTree: no tree provided (path or object is null/undefined).");
  }

  let tree;
  if (typeof pathOrObj === "object") {
    tree = pathOrObj;
  } else if (typeof pathOrObj === "string") {
    const raw = readOrParse(pathOrObj);
    tree = raw;
  } else {
    throw new Error(`loadTree: unsupported input type '${typeof pathOrObj}'.`);
  }

  if (!tree || typeof tree !== "object") {
    throw new Error("loadTree: tree is not an object.");
  }
  if (!tree.root || typeof tree.root !== "object") {
    throw new Error("loadTree: tree has no 'root' node (expected { schemaVersion, source, root }).");
  }
  return tree;
}

// If the string looks like a JSON document, parse it directly; otherwise treat
// it as a filesystem path and read+parse. This lets callers pass either.
function readOrParse(str) {
  const trimmed = str.trim();
  if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
    try {
      return JSON.parse(trimmed);
    } catch (err) {
      throw new Error(`loadTree: input looked like JSON but failed to parse: ${err.message}`);
    }
  }
  let contents;
  try {
    contents = readFileSync(str, "utf8");
  } catch (err) {
    if (err.code === "ENOENT") {
      throw new Error(`loadTree: tree file not found: ${str}`);
    }
    throw new Error(`loadTree: could not read tree file '${str}': ${err.message}`);
  }
  try {
    return JSON.parse(contents);
  } catch (err) {
    throw new Error(`loadTree: tree file '${str}' is not valid JSON: ${err.message}`);
  }
}

/**
 * Depth-first walk yielding every node with a stable, dotted path.
 * Root's path is "root"; children are "root.children[0]", etc.
 *
 * @param {object} tree  a full tree ({root}) OR a bare node.
 * @yields {{ node: object, path: string }}
 */
export function* walk(tree) {
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

/**
 * Find the first node with the given testTag. Returns { node, path } or null.
 * @param {object} tree
 * @param {string} tag
 */
export function findByTestTag(tree, tag) {
  for (const entry of walk(tree)) {
    if (entry.node.testTag === tag) return entry;
  }
  return null;
}
