# Feature brief: workouts

**Spec:** [`specs/workouts.spec.md`](../../specs/workouts.spec.md) — WORK-01..WORK-09.

## The ask

> "I want to track my workouts and get reminders for them. Not a full workout plan — just an
> in-app reminder and a track record that I did the day, for now. What is the best way to add
> it to the current app holistically? I want to see and track and mark it off and have a
> history."

## The problem, and why it is not a new tab

Body-for-LIFE was always three disciplines: eat clean, take the stack, train. The day's verdict
already carries five derived values — calories, protein, meals kept, water, veg (JRN-01) — and
training is the one conspicuously absent. It is not a missing feature so much as a **missing
pillar**.

That framing decides the shape. A "Workouts" tab would be a fourth place holding one boolean
per day, and a surface you must navigate to in order to tick one box is a surface that goes
unticked. Three surfaces that already exist each grow a little instead.

## Decisions

**D1 — No new tab, no new screen.** *Do it* on Today (a card with its own tick), *see it* on
Progress (a week strip and a line on each day card), *shape it* in Settings (a card beside the
supplement stack). Every one of those is a place the user is already going.

**D2 — The plan and the fact are separate tables.** `WorkoutWeek` is what you intend;
a done-mark keyed by logical date is what happened. Editing Wednesday from Lower body to Rest
must not rewrite the Wednesdays already trained.

**D3 — The done-mark IS the history.** No separate log. Progress reads the rows Today writes,
through the same composed window the verdict uses (HIST-01: never a second aggregate).

**D4 — The row's existence is the fact.** Like a supplement dose (SUPP-07) and a water tick
(PLAN-10): a new day has no row, so it starts undone with nothing to reset and no boundary job.
A `done` boolean column would allow a row saying `false`, indistinguishable from never having
trained.

**D5 — A rest day renders nothing.** Not "no training today" — nothing. Rest is the plan
working, and HIST-05 already established this app does not draw empty axes for days with
nothing in them.

**D6 — The week strip has four states, not two.** Done, missed, pending, rest. "Not done"
collapses three facts that mean very different things; only a PAST training day with no mark is
a miss, and colouring today's un-done session red would be the app scolding someone at 9am.

**D7 — Per-day reminder times.** *Karel's call, asked and answered:* "every one will be at a
different time, you can schedule it per day." A weekday session after work and a Saturday
morning session are the normal shape of a week; one time for all seven would be wrong on most
of them.

**D8 — The same reminder ladder as supplements.** Night before / 30 minutes / at the time,
through one policy with two consumers. A second reminder vocabulary would be one to learn for
no reason, and "night before" would come to mean two different moments.

**D9 — The mark-done gesture is the supplements take ring, one size up.** Same gesture, same
meaning.

**D10 — Training does NOT yet change the day's verdict.** The strip and the tag are display.
Whether a missed session should make a day score worse — "a good day = ate clean AND trained" —
changes what green means everywhere. **Open for Karel:** raised, not yet ruled on; the
conservative default is display-only, and it is reversible.

**D11 — Seed the classic split, seed no alarms.** Upper/lower alternating, cardio between,
Sunday rest — a real starting point rather than seven blanks that look broken. Times are empty:
a reminder nobody asked for is how notifications get switched off for good.

## Not built, on purpose

Exercises, sets, reps, loads, duration, progression, rest timers, plan templates. Those are a
training log — a different product with a different data model, and a hollow version of one is
worse than none. The done-mark is keyed by date, so detail can hang off it later without
changing what a "done day" means.
