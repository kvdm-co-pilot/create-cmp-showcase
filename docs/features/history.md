# Feature brief — History & trends (usability-pass S4)

```json cmp:feature
{ "touches": ["feature-spec:meal-plan", "feature-design:meal-plan"] }
```

> **Status:** proposed — decisions below are signed BEFORE code (`node qa/approve.mjs
> feature-brief:history`). Contract: [`specs/history.spec.md`](../../specs/history.spec.md).

## The journey this exists for

Three real moments, none of which the app can currently serve:

1. **"I've been at this a month. Am I actually getting anywhere?"** The week review answers
   for *seven days*. Seven days is a snapshot, not a direction — and this method runs in
   12-week blocks. A tracker that can only ever show you the current week cannot tell you
   the one thing you started tracking to find out.
2. **"Sunday was terrible — what did I actually eat?"** The week review renders Sun 19 at
   1580 kcal and 92 g protein, and then offers nothing. You can see the verdict on a day and
   you cannot open it. The plan strip starts at *yesterday*, so no surface in the app can
   reach a day older than that. **The data is all there; there is no door.**
3. **"I weighed in this morning."** The app tracks intake against a calorie target and has
   no idea what that intake is *doing*. Weight is the outcome variable the whole plan exists
   to move, and the app does not hold it. Body-for-LIFE's own method schedules a weekly
   progress check; ours has nowhere to write one.

## Findings against the rendered surfaces

| # | Finding | Where | Heuristic |
|---|---|---|---|
| H1 | Day cards render a verdict per day and are inert — no tap, no route, nothing | `week` | AFF (an affordance is a promise) |
| H2 | No surface reaches a logical day older than *yesterday* (`stripDays = -1..+7`) | `plan/{date}` | N7 flexibility |
| H3 | Opening a past day would render a **lying strip**: `selectedDay = indexOf(date).coerceAtLeast(0)` silently highlights *yesterday* when the date is outside the window | `MealPlanRoute.toUi` | N1 visibility of status |
| H4 | No trend of any kind — every number in the app describes today or this week | all | N6 recognition |
| H5 | No weight, no measurements, no outcome variable at all | — | — |
| H6 | Copy-forward would happily overwrite **days that have already happened** if a past day were ever reachable | `plan_copy_forward` | destructive-by-default |

H3 and H6 are latent today only because H2 makes past days unreachable. **Building the door
turns both into live defects**, so this slice fixes them in the same change.

## Decisions

**D1 — One results surface, grown, not a second one.** The `week` route becomes
**Progress**: verdict → trend → weight → the day list. *Rejected:* a separate `history`
route beside `week`. Two surfaces that both answer "how am I doing?" is precisely the
"no surface owns this moment" failure the journeys pass was written to fix — a user should
never have to know whether the answer lives under Week or under History.

**D2 — A day card is a door.** Tapping one opens `plan/{date}` for that day. *Why the plan
screen and not a read-only viewer:* the reason you open Sunday is usually to fix it —
back-fill the meal you forgot to log. A read-only past day would answer the question and
then refuse the follow-up. The plan screen already edits any date; it just could not be
*aimed* at one.

**D3 — The strip anchors on the day you are looking at.** `stripDays` becomes
`[selected−1 … selected+7]` instead of `[today−1 … today+7]`. Opening Jul 15 shows Jul 14
through Jul 22 — its own neighbourhood, with Today still labelled wherever it falls. This
kills H3 at the source rather than patching the index: there is no window the selected day
can fall outside of.

**D4 — Copy-forward disappears on a past day.** "Copy this day to the rest of the week"
aimed at a day three weeks back would write over three weeks of real logged history.
Silently. The control is hidden when the selected day is before today. *Rejected:* a confirm
dialog — the operation has no legitimate meaning pointing backwards, and a dialog on a
meaningless action is a worse answer than not offering it.

**D5 — Trend window: 4 weeks, one read path.** `GetWeekReviewUseCase` generalises to
`GetHistoryUseCase(days)`, and **both** the 7-day verdict and the 4-week trend become
projections of that one composed stream. A second aggregate read — a SQL `GROUP BY` over a
range — would be faster and would be a second source of truth for numbers the day cards
already state. When the two disagree, and they always eventually do, there is no way to
tell which one is lying. 28 composed day-flows is the honest cost of one derivation.

**D6 — An unstarted week is absent, not zero.** A week with nothing logged renders as a gap
in the trend, never as a bar at zero. Same discipline as `avgConsumedKcal` averaging only
started days: the app must not report "you ate 0 kcal" for a week you had not installed it.

**D7 — Weight is stored, not derived, and it is the one thing here that is.** One entry per
logical day (upsert — a second weigh-in on the same day corrects the first, it does not
append). The window shows latest, and the change across it. *Kilograms for now*, with the
display deferring to S5's unit preference the moment that lands — this brief deliberately
does **not** invent a unit setting so the two slices cannot ship two of them.

**D8 — Weight is optional and its absence is not a hole.** No weight logged means the
section offers the affordance and states nothing else. An app that renders an empty chart
with axes and no line is telling you that you have failed at something you never opted into.

## Rejected outright

- **A charting library.** The design system has `StatBar`; four weekly rows built from it
  read better at this size than a line chart, and adding a dependency to draw four bars is
  how a token catalog stops being the source of truth for how things look.
- **Body-fat %, measurements, photos.** Body-for-LIFE tracks all of them. Every one is a new
  input, a new storage shape and a new empty state, and none is the outcome variable people
  actually check. If weight earns its keep, they can follow.
- **Editing a past day's *goals*.** Goals are undated (stated in `WeekDay`'s contract). A day
  card showing "2350 / 2400" where 2400 is today's target is a known, documented simplification;
  dating goals is its own slice and pretending otherwise here would bury it.

## Open decisions

None. All eight are closed; the human's signature on this brief closes them formally.
