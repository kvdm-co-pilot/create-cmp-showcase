---
name: walk
description: >-
  Narrate a governed change as the six-stage walk — Decide · Design · Contract · Build · Prove ·
  Sign-off — so the human always knows where the work is and whose turn it is. Use this at the
  kickoff of ANY change request (new feature, edit, bug fix, copy tweak, redesign), whenever the
  per-prompt inject carries a walk state, a `[chat header]` or a `▲ ARRIVED, UNPLANNED` line,
  and at every point the human must approve or sign. Carries the itinerary, the declared chain
  (`node qa/plan.mjs`), the running protocol and the YOUR TURN card. Works with NO create-cmp
  plugin installed — `qa/walk-status.mjs` and `qa/plan.mjs` ship inside the generated project.
---

## The walk — the user always knows where we are and whose turn it is

Every governed change is a **walk** through six stages, spoken ONLY in this vocabulary
wherever the human reads (chat, cards, commit prose): **Decide · Design · Contract ·
Build · Prove · Sign-off**. The mapping is mechanical — Decide=the brief, Design=the
rendered screens, Contract=the spec, Build=code+citing tests, Prove=the lane's receipt,
Sign-off=acceptance — and spec clauses are spoken as **promises** ("Contract: 7
promises agreed" · "Build: keeping promise 5 of 7" · "Prove: all promises kept,
evidence attached"). `node qa/walk-status.mjs` derives the live position; a
UserPromptSubmit hook injects it every prompt. **Render the injected state — never
your memory of it.**

**At kickoff** (with the triage restatement): print the itinerary — and DECLARE it as
the live chain, so the studio's Drive strip and the statusline's readers see the same
steps you just printed:

    Navigation redesign — the journey (brief lane)
    Decide → Design → Contract → Build → Prove → Sign-off
    Stops for you: 3 (Decide — now · Contract · Sign-off). Build and Prove never stop for you.
    First stop is now: 2 open decisions below.

```bash
node qa/plan.mjs --set "sign the brief | draft screens | agree the promises | build | full check | your sign-off" --title "navigation redesign"
```

**The chain is an offer, not an announcement** (drive-narration N6): show the declared
steps in your first reply and invite the reshape in one breath — "say the word and I'll
reorder" — then start work immediately; the chain gates nothing, so the offer never
blocks. If the human redirects, re-declare (`--set` again) without ceremony: their
reshape IS the new chain.

**The chain stays current** — this is part of the contract, not a nicety: advance it
with `node qa/plan.mjs --step N` as each step lands and `--done` when the request
lands (closing writes the request's line into the local trail the studio's Recent
requests fold shows). The current request itself is recorded mechanically (the
per-prompt hook), the steps are yours to declare, and every surface shows the
declaration's age — a stale chain reads as stale to the human watching the studio,
which is worse than no chain. While the full check runs, the chain's observed line
narrates the lane's own position (step, elapsed, usual cost) — quote THAT, never an
estimate. The chain gates nothing; the walk stays the truth for doneness.

**While a walk is open, the per-prompt inject carries the running protocol** — the `[studio: …]`
line (restore it if DOWN: the cmp-inspector `preview { projectDir }` tool, or tell the human once),
the `[chat header]` to open every reply with, verbatim, and the `▲ ARRIVED, UNPLANNED` line for work
that belongs to no open walk (default: after the current walk lands; one walk at a time). Render the
injected lines, never your memory of them; with no walk open it delivers nothing and you write none
of it. After the header: one line per stage transition, nothing per-file.

**At every human gate — loud:** a full stop card, never a bare question — and the
easiest act leads:

    ■ YOUR TURN — <feature> · stage 3 of 6: Contract — agreeing what it promises
    <what it is, in plain words — two lines maximum>
    → Easiest: the studio console at <url from the injected card> — the row carries the button.
    → CLI fallback: <the command>   (or "reply approve" when no console is up)
    After this: <the remaining stages, and which ones stop for the human>
