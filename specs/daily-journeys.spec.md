# Spec: daily-journeys (the prep moment and the week in review)

> Cross-feature contract born from walking the app as days lived
> (`docs/features/daily-journeys.md`): the half hour before a meal and the look back at
> the week were the two moments no surface owned. The prep-lead promise itself lives in
> the meal-plan contract (PLAN-07, amended under this brief) — reminders are that spec's
> domain; these clauses own the NEW surface. Every clause id is cited by the durable
> test(s) that verify it (`// SPEC: JRN-NN`).

**Scope of this contract.** The Week review surface and its door. First-day framing (T1),
notification actions (T2), and trends (T3) are named slices in the brief — no clause here
claims them.

## The week in review (brief decision 2)

- **JRN-01** — Given the Week review opens, Then it shows the last seven **logical** days
  ending with the current day (marked as today, `week_day_<iso>` rows in ascending date
  order), and each day shows: calories consumed vs target, protein grams vs goal, meal
  slots completed of six, water litres, and veg count — every value derived through the
  same plan-day derivation and observed reads the other surfaces use (RS-01, TODAY-13's
  no-second-path discipline), so a tick or delete made anywhere re-renders the week
  without a reload; consumed totals count only `LOGGED` entries (TODAY-03), and a day
  with nothing logged shows zeros, never an error. Targets are the CURRENT goals for all
  seven rows — goals are not yet dated (usability-pass S1 owns that decision).
- **JRN-02** — Given the Profile screen, Then its weekly-stats row is a real control
  (`profile_week_link`) whose activation opens the Week review — the stats' claims become
  verifiable by the surface they open, and the row's tap exists BECAUSE the destination
  does (UX-04's rule, satisfied in the other direction).
- **JRN-03** — Given the Week review renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/week.json`) — structural change must be
  intentional and declared.
