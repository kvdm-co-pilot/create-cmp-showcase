# Spec: supplements (today's stack, tap to take)

> Supplements is the day's supplement stack grouped by timing, with tap-to-take tracking that
> persists. Data-backed. Every clause id is cited by a durable test (`// SPEC: SUPP-NN`).

- **SUPP-01** — Given the Supplements screen opens, When the stack is loading, Then a loading
  indicator is shown; and when it completes the supplements are grouped by timing (the closed
  `SupplementTiming` set — Morning, Pre-workout, Post-workout, Evening — SET-06) in a stable
  order derived from that same value, each showing name and dose, with no error. The stack is
  the USER's (SET-04/05): seeded on a fresh install, editable from Settings thereafter.
- **SUPP-02** — Given the stack is loaded, Then a summary shows the count taken of the total
  and a progress bar of taken ÷ total.
- **SUPP-03** — Given a supplement is shown, When the user taps its take control, Then its
  taken state toggles **for the current logical day**, the summary count and progress bar
  update accordingly, and the new state persists across a reload of the screen.
- **SUPP-07** — Given a supplement was taken, When the logical day rolls over, Then it reads
  untaken again and the summary count starts from zero — a supplement stack is a **daily**
  routine, so "taken" is a fact about a day and is stored per day, exactly as water ticks are
  (PLAN-10). It follows that a **fresh install claims nothing taken**: seed data seeds the
  stack, never a dose the user did not swallow.
- **SUPP-04** — Given the stack is empty, When it loads, Then the empty state
  (`supplements_empty`) is shown and no error.
- **SUPP-05** — Given the source fails, When loading completes, Then a mapped error
  (`supplements_error`) is shown from the failure's `DomainError` kind, **and no retry
  control** — the stack is observed, so when the source recovers the next emission renders it
  with nothing tapped. The retry this clause used to promise was wired to a no-op: a button
  that looked like the way out of an error and did nothing (TODAY-05 dropped its own for the
  same reason).
- **SUPP-06** — Given the Supplements screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/supplements.json`).

## Not every day (brief: [`docs/features/supplement-schedules.md`](../docs/features/supplement-schedules.md))

> SUPP-01's stack was daily by construction. Injection protocols are not: a testosterone dose
> on Mondays and Thursdays, a pen every other day. These clauses make the stack non-daily
> without making the common case cost anything — a daily vitamin's row is unchanged.

- **SUPP-08** — Given a supplement, Then it carries a schedule from a CLOSED set — every day,
  fixed weekdays, or every N days from an anchor — and whether it is due on a date is
  **derived** from that schedule, never stored. There is no due-today column and no nightly
  job, so nothing can be stale when the app is opened after a week away (PLAN-02/MEAL-02's
  discipline). A missed dose does **not** move an every-N-days anchor: the cadence is a
  property of the protocol, not of compliance with it, and a schedule that cannot be
  re-derived from its own definition is one that drifts.
- **SUPP-09** — Given the Supplements screen on a logical day, Then the stack splits: what is
  due today is grouped by timing as before and is the ONLY thing the summary counts ("N of M
  **due today** taken"), and what is not due is listed separately (`supplements_resting`) with
  its schedule and the date it next comes round — visible, dated, and with no take control.
  An off-day dose must never read as one that was missed; and "did I take it, or is it not a
  dose day?" is the exact question a Mon/Thu injection creates, which a date answers and an
  absence does not. Taking a dose off-schedule is a decision made in the editor, not a mis-tap
  on the screen opened every morning.
- **SUPP-10** — Given a supplement whose schedule is every day, Then its row renders exactly as
  it did before schedules existed — no schedule caption, no extra control. The feature costs
  the common case nothing.
- **SUPP-11** — Given a stored schedule that cannot be read — a kind this build does not know,
  a malformed anchor — Then it reads as daily rather than throwing, on the same principle as
  SET-06's timing fallback. Daily is the safe fallback specifically because it OVER-shows: a
  dose appearing on a day it is not due is visible and correctable, where silently never
  appearing again is neither.
- **SUPP-12** — Given a supplement with a reminder time and at least one armed rung, Then the
  armed set carries that rung on the schedule's own due DATES — night before (at the same
  evening moment the plan-tomorrow nudge uses, NOTIF-04, so there is no second evening setting
  to keep in step), thirty minutes before, and at the time. The night-before rung is neither
  offered nor armed for a daily schedule: "tomorrow is creatine day" is noise, and the rung's
  whole value is that it names an exception. A dose already taken today arms nothing further
  for today; saving or deleting a supplement re-arms immediately, since a deleted row leaves
  nothing behind to derive a cancellation from.
- **SUPP-13** — Given a supplement is saved with a time but no rungs, or rungs but no time,
  Then it is stored with neither. Half a reminder is one that never fires while the row still
  claims it will.

## The stack's door (motion D16)

- **SUPP-14** — Given the Supplements screen, Then its header (`supplements_title`, an
  `AppHeader` like every pushed screen's) carries an "Edit stack" action
  (`supplements_edit_stack`) that opens the Settings surface, where the stack editor lives
  (SET-01's card) — the editor does not move; it gains a second door from the screen that
  shows the doses it defines.
