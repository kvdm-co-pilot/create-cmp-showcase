# Spec: dated-goals (a target belongs to the days it applied to)

> [`docs/features/dated-goals.md`](../docs/features/dated-goals.md). Every clause id is cited
> by the durable test(s) that verify it (`// SPEC: GOAL-NN`).

- **GOAL-01** — Given goals are stored, Then each is a row carrying the logical date it takes
  effect from, and the goal **for a given day** is the latest row whose effective date is on
  or before that day; the earliest row applies to every day before it, so no day is ever
  judged against no goal.
- **GOAL-02** — Given a goal is edited (PERS-02), Then it is written effective from the
  **current logical day**, and editing again on the same day REPLACES that row rather than
  appending — a target changes on a day, not at a time. Rows for earlier days are never
  rewritten.
- **GOAL-03** — Given the Progress surface (HIST-01), Then each day card and each trend week
  is judged against the goal that applied **on those days**, not the current one — so lowering
  a target today never re-scores the weeks before it.
- **GOAL-04** — Given the current logical day, Then every surface that reads "the goal" — the
  ring, the macros, the goal editors, the week verdict — reads the row effective for that day,
  through the one goal store (PERS-01); dating the store adds no second read path.
