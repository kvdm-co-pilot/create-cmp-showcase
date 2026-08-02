# Feature brief — Dated goals

```json cmp:feature
{ "touches": ["feature-spec:daily-journeys", "feature-spec:history", "feature-spec:personalization"] }
```

> **Status:** proposed. Contract: [`specs/dated-goals.spec.md`](../../specs/dated-goals.spec.md).

## The journey this exists for

You cut your calorie target from 2600 to 2400 in week three. Open Progress today and **every
day of the last four weeks is judged against 2400** — including the fortnight you were
deliberately eating 2600 and hitting it. A month you executed well reads as a month you
overshot, and the trend that was supposed to tell you whether the change worked is drawn
against a target that has quietly been retconned onto days it never applied to.

`WeekDay`'s own contract has said so since the week review shipped: *"[targetKcal] and
[proteinGoalG] are the CURRENT goals: goals are not yet dated."* It was an honest caveat while
history was one week long. **Four weeks of trend made it a defect** — a longer memory is
exactly what turns "the target moved" from a footnote into the thing you are looking at.

## Decisions

**D1 — A goal is a row with a start date, not a value that gets overwritten.** `goal_history`
holds `(effectiveFrom, targetKcal, proteinGoalG, …)`. The goal for any day is the latest row
whose `effectiveFrom` is on or before it. *Rejected:* a `changedAt` column on the single goal
row — it records that something changed and still cannot tell you what the target was in week
two, which is the entire question.

**D2 — Editing rewrites TODAY, never yesterday.** Saving a goal upserts the row effective from
the current logical day. Change your target twice this morning and you get one row, corrected —
not two, and not a target that changed at 09:14. Past days keep whatever they were judged
against, because they were.

**D3 — Goals apply forward, and the first row reaches back to the beginning of time.** The
earliest goal row is treated as effective from `LocalDate.MIN`, so a day before you ever
touched the goal editor is judged against the seeded default rather than against nothing. A
day with no applicable goal would otherwise render `0 / 0 kcal`, which is a lie of a
different kind.

**D4 — This is a read-side change only.** Nothing else moves: the ring, the macros and the
goal editors keep reading and writing "the goal", which is now defined as *the goal for the
current logical day*. One store, one write path (PERS-01 intact) — the store simply gained a
date column.

**D5 — Progress states the target it used, per day.** The day cards already render
`2350 / 2400 kcal`; that 2400 now belongs to that day. The trend's `vs 2400` becomes the
target that week was actually judged against.

## Rejected outright

- **Back-dating a goal change.** "I actually cut in week two" is a plausible ask and a bad
  idea: it silently re-scores days you already read, so the app's verdict on a week changes
  after you have seen it. Corrections belong to the day you make them.
- **Per-day goal overrides.** A different target for one Tuesday is a different feature
  (refeeds, training days) and needs its own vocabulary, not a hole in this one.

## Open decisions

None.
