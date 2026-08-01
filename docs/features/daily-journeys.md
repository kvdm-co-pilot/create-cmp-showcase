# Feature brief: daily-journeys — the app judged as days lived, not screens visited

## What and why

The usability-pass brief fixed the **verbs** (quantity, undo, catalog-first logging,
honest affordances). This brief goes up one altitude: **temporal journeys** — a person
moving through a working day and a training week, with the app in their pocket. Each
journey below was *walked*, screen by rendered screen (the console's own renders, not
source), asking one question: does the app meet this person at the moment they need it?

Two journeys fail at moments the current design never modelled — the half hour **before**
a meal, and the look **back** at how the week actually went. Those two are built in this
pass. The rest are named, honestly graded, and ordered.

## The journeys — walked end to end

**J1 — The fresh morning (plan-then-eat).** 06:30, day empty. Today shows the full-target
ring and Breakfast focused with its add control in the body; tray opens aimed; ticking
rolls focus forward. **Grade: A.** This is the app's spine and it holds.

**J2 — The working prepper.** 11:30, at a desk. Lunch is at 12:00 and needs cooking or
fetching — the useful moment is ~30 minutes *before* the slot, and the app's reminder
fires *at* 12:00: the moment it is already too late to prep. Then LATE at 12:30. The
app's only relationship to a meal is *now/late/missed* — it has no concept of **soon**.
**Grade: D — built in this pass (decision 1).** The plan screen already shows "NEXT at
12:00" before its time (PLAN-25); the notification channel is the one surface that waits
until the moment has passed.

**J3 — The mid-day starter.** Installs (or finally opens) the app at 13:00, wants to "log
my day from lunch on." Walked: Breakfast and Snack read MISSED — muted, back-fillable,
tick-without-food for eaten-off-plan (PLAN-14/19 do real work here) — and Lunch is
focused. The mechanics genuinely support starting mid-day; what stings is *framing*: a
first-ever day greets its user with two MISSED tags for meals eaten before the app
existed. **Grade: B− — mechanics ✅; first-day framing is slice T1** (needs a stored
first-open date; not built now, decision 4).

**J4 — Eat-and-tick through the afternoon.** Reminder → open → tick done → focus
advances; water between meals; supplement bucket on Today. **Grade: A−.** (The reminder
deep-links to Today — right surface.) Minor: after the last tick, "six for six" copy —
good close to the day.

**J5 — The Sunday week-prepper.** Build Monday by hand, copy forward (PLAN-20), adjust a
day or two on the strip; PLANNED entries wait, tray always aimed. **Grade: A.**

**J6 — "How am I actually doing?"** — the journey your question named: log every day,
then get the **holistic view of results**. Walked: it does not exist. Today speaks only
for today; the strip goes back exactly one day; Profile shows *derived claims* — "12 day
streak", "172g avg protein" — with **no surface behind them**: numbers you cannot verify
against any visible day. In a tracker, unverifiable stats are decoration. **Grade: F —
built in this pass (decisions 2, 3): the Week review.**

**J7 — The evening closer.** 21:30, day done, one glance: did I hit protein? Ring +
protein focus answer it if you open Today. Acceptable now; the Week review gives this
glance its context (today's row sits at the bottom of the week). **Grade: B+.**

## Decisions — built in this pass

1. **Meal reminders become prep reminders: they fire 30 minutes before the slot**
   (PLAN-07 amended under reopen). The lead is a named constant (`PREP_LEAD_MINUTES = 30`;
   a user-facing setting belongs to S5), clamped at midnight, **meals only** — water needs
   no prep and keeps its midpoint times. The notification carries both moments: "Lunch at
   12:00 — time to prep." Cancellation on tick (a meal already eaten is never announced)
   and the stale-delivery guard (PLAN-26) apply to the prep moment exactly as they did to
   the slot moment. Rejected: a second reminder at the slot time as well — two
   notifications per meal, six meals a day, is how an app gets muted by Friday.
2. **The Week review — the holistic results surface (route `week`).** The last seven
   logical days, today last and marked: per day — calories consumed vs target, protein vs
   goal, slots completed of 6, water litres, veg count. Read through the SAME plan-day
   derivation and observed streams every other surface uses (RS-01; no second read path,
   no new tables — the data was always there, only the surface was missing). Read-only:
   fixing a past day stays the plan strip's job (PLAN-11's yesterday chip).
3. **Profile's stats row becomes the door to the Week review** (`profile_week_link`).
   UX-04's rule is honored in the other direction: the row gets its tap *because* it now
   has a real destination — and the streak/avg-protein claims become verifiable by the
   surface they open. (PROF-03 untouched: the stats still show; they now also lead
   somewhere.)
4. **First-day framing is T1, not built now.** Softening MISSED on the first-ever day
   ("before you started" instead of MISSED) needs a stored first-open date — new state,
   its own small contract. Named, scoped, deferred.

## Next slices (extending the usability-pass order)

- **T1 First-day framing** — pre-first-open slots render neutral, not MISSED (J3).
- **T2 Reminder actions** — "Tick done" / "Snooze 10 min" actions on the notification
  itself; eat-without-unlocking (J4).
- **T3 Week trends** — the Week review's second page: 4-week protein/kcal trend lines,
  weight over time (extends J6 once S4 history lands).
- (S1–S6 from the usability-pass brief keep their order; S1 personalization remains
  first among equals.)

## Blast radius and contracts

- `feature-brief:daily-journeys` — this document (NEW).
- `feature-spec:daily-journeys` — `specs/daily-journeys.spec.md` (NEW): JRN-01..03
  (week review content, entry, structure), each cited by durable tests.
- `feature-spec:meal-plan` — REOPENED: PLAN-07 amended (prep lead).
- `feature-spec:profile` — already reopened (usability-pass); stats row gains its
  destination under that same reopen.
- Golden trees: `profile` (stats row becomes a link), NEW `week`. Declared,
  regenerated with `UPDATE_GOLDEN=1`.
- Not touched: `components` (screen-local composables only), `design-system`, `intent`,
  `architecture` (prose; the generated inventory regenerates mechanically).

```json cmp:feature
{ "touches": ["feature-spec:meal-plan", "feature-spec:profile"] }
```

## Open decisions

- **Prep lead as a setting** (S5): 30 min is Body-for-LIFE-realistic; some users want 60
  (meal prep from raw). Setting ships with the reminders settings surface.
- **Week starts where?** The review shows a rolling last-7 window, not a calendar week —
  simplest truthful view. If users think in Mon–Sun blocks (the Body-for-LIFE free-day
  rhythm suggests they might), revisit with T3.
- **Targets are today's targets** for all seven rows: goals are not yet dated (S1 will
  decide whether goal changes re-write history or version by date). Stated on the
  surface's contract so nobody mistakes it for a bug.
