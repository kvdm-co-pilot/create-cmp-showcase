---
paths:
  - "qa/comments.json"
  - "qa/comment.mjs"
  - "qa/lib/comments.mjs"
---

## Comments — review feedback flows back through the agent

Approvals are binding (they gate the verify lane); **comments are advisory** — a human's
running commentary, with a defined path back into your plan, spec, and code.
`qa/comments.json` is the ledger; `qa/lib/comments.mjs` is the library, mirroring
`qa/lib/approvals.mjs`'s shape: state, validation, transitions, nothing fabricated.

**The loop of record:**

1. A human adds a comment from the preview console — on a screen, a spec clause, a
   design-system token or component, or an architecture tree node.
2. You observe it — `review_comments { waitForComment: true }` (plugin) blocks until a new
   one lands; without the plugin, `node qa/comment.mjs --list --open`.
3. You act on it — update the plan, the spec clause, or the code it points at.
4. You resolve it **after** acting, with a note saying what you did —
   `resolve_comment { id, note }` (plugin) or `node qa/comment.mjs --resolve <id> --note
   "..."` (CLI, records author `agent-cli`). The console then shows `resolved` plus your
   note. The console never edits code: humans comment, agents resolve.

| Command | What |
|---|---|
| `node qa/comment.mjs --list` | Every comment, open and resolved, with resolution notes |
| `node qa/comment.mjs --list --open` | Only open comments |
| `node qa/comment.mjs --resolve <id> --note "..."` | Resolve a comment, recording what changed |

A comment targets one of: a **screen**, an **element** (screen + testTag), a **spec-line**
(file + clause id), a **design-system** token, an **architecture** path, or **general**.
`addComment` refuses empty text and a target missing the fields its type requires — the
same refusal-over-fabrication stance as approvals. A ledger that exists but cannot be
parsed is never treated as empty (that would hide real feedback); reads and writes surface
