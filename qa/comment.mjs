#!/usr/bin/env node
// The comments CLI — thin shell over qa/lib/comments.mjs (mirrors qa/approve.mjs).
//
//   node qa/comment.mjs --list [--open]              readable table of the ledger,
//                                                     including resolution notes
//   node qa/comment.mjs --resolve <id> --note "..."  resolves a comment as "agent-cli",
//                                                     recording the note explaining
//                                                     what changed as a result
//
// This file has NO logic of its own — every decision (validation, ids, the ledger)
// lives in qa/lib/comments.mjs. Adding a comment is a console/human action (the
// console's `POST /api/comment` calls the same library through a bridge); this CLI
// covers the agent's side of the loop of record: observe, act, resolve.

import path from "node:path";
import { fileURLToPath } from "node:url";

import { listComments, resolveComment } from "./lib/comments.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);

function argValue(flag) {
  const idx = args.indexOf(flag);
  if (idx === -1 || idx === args.length - 1) return undefined;
  return args[idx + 1];
}

function printList(openOnly) {
  let result;
  try {
    result = listComments(ROOT, openOnly ? { status: "open" } : {});
  } catch (err) {
    console.error(`error: ${err.message}`);
    process.exit(1);
  }
  if (result.comments.length === 0) {
    console.log(openOnly ? "No open comments." : "No comments recorded yet.");
    return;
  }
  console.log(`Comments${openOnly ? " (open only)" : ""}:\n`);
  for (const c of result.comments) {
    const mark = c.status === "resolved" ? "✓" : "→";
    console.log(`${mark} ${c.id} [${c.target?.type ?? "unknown"}] ${c.status} — by ${c.author} at ${c.createdAt}`);
    console.log(`    ${c.text}`);
    if (c.status === "resolved") {
      const note = c.resolutionNote ? `: ${c.resolutionNote}` : " (no note)";
      console.log(`    resolved by ${c.resolvedBy} at ${c.resolvedAt}${note}`);
    }
  }
}

if (args.includes("--list")) {
  printList(args.includes("--open"));
  process.exit(0);
}

if (args.includes("--resolve")) {
  const id = argValue("--resolve");
  if (!id) {
    console.error('usage: node qa/comment.mjs --resolve <id> --note "..."');
    process.exit(1);
  }
  const note = argValue("--note");
  const result = resolveComment(ROOT, id, { note, author: "agent-cli" });
  if (!result.ok) {
    console.error(`error: ${result.reason}`);
    process.exit(1);
  }
  const noteSuffix = result.comment.resolutionNote ? ` — ${result.comment.resolutionNote}` : "";
  console.log(`✓ resolved ${result.comment.id}${noteSuffix}`);
  process.exit(0);
}

console.error('usage: node qa/comment.mjs --list [--open] | --resolve <id> --note "..."');
process.exit(1);
