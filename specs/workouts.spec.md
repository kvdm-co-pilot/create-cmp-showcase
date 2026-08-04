# Spec: workouts (the training week, marked off and remembered)

> Brief: [`docs/features/workouts.md`](../docs/features/workouts.md). Training is the day's
> sixth pillar, not a new place — the day verdict already carries five (JRN-01) and this adds
> the one Body-for-LIFE discipline the app asked about nowhere. Deliberately the SMALLEST
> thing that closes the gap: a labelled week, a per-day reminder time, and a done-mark per
> logical day. No exercises, sets, reps, loads or progression. Every clause id is cited by the
> durable test(s) that verify it (`// SPEC: WORK-NN`).

**Scope of this contract.** The week as a plan, the day as a fact, and the three surfaces that
already exist. The reminder LADDER those times hang off is supplement-plan contract
(SUPP-12) — one policy, two consumers — and WORK-06 only claims what training adds to it.

- **WORK-01** — Given the app, Then training is stored as two things: a `WorkoutWeek` (the
  plan you edit) and a done-mark per logical day (the fact you did it), in separate tables.
  Editing Wednesday from Lower body to Rest therefore leaves every Wednesday already trained
  untouched — a plan and a history are different nouns, and folding them into one row would
  make changing the plan rewrite the past.
- **WORK-02** — Given the training week, Then it always has all seven days, and a day with no
  label IS the rest day. The grid comes from `DayOfWeek`, never from stored rows (PLAN-02's
  discipline), so a day absent from storage is indistinguishable from rest and both read as
  rest — there is no fourth state to keep honest.
- **WORK-03** — Given the Today tab on a TRAINING day, Then a workout card is shown
  (`today_workout`) carrying the session's label and, when one is set, its reminder time;
  given a REST day — or a week that could not be read — Then no card is rendered at all. Rest
  is the plan working, not a zero to stare at (HIST-05's no-data discipline, applied to a
  day), and an unreadable week must not take the dashboard down with it (TODAY-11's stance on
  the supplement bucket).
- **WORK-04** — Given the workout card, When its done control (`today_workout_done`) is
  activated, Then the session is marked done **for the current logical day**, the state
  persists across a reload, and the mark reads undone again when the day rolls over — a
  fact about a day, stored per day, exactly as a supplement dose is (SUPP-07).
- **WORK-05** — Given the Progress surface, Then a training section (`week_training`) shows
  sessions kept of sessions planned for the same seven days the verdict covers, with one dot
  per day (`week_training_<iso>`) in four states — done, missed, pending, rest — and each day
  card gains a training tag only on a training day. Four states, not two: a rest day asked
  nothing, today has not happened yet, and only a PAST training day with no mark is a miss.
  The window is derived from the same logical day the history is (HIST-01), so the strip and
  the day cards can never describe different weeks.
- **WORK-06** — Given a training day with a reminder time and at least one rung, Then the
  armed set carries that day's rungs at the SAME leads the supplement ladder uses (SUPP-12),
  fired on the training day's own date; and a session already marked done arms nothing
  further for that date. Times are PER DAY: a weekday session after work and a Saturday
  morning session are the normal shape of a week.
- **WORK-07** — Given the Settings surface, Then a workout-week card (`settings_week`) lists
  all seven days, each opening an editor (`settings_workout_editor`) for its label, its own
  time, and its rungs; clearing the label makes that day a rest day and drops its time and
  rungs with it, so no alarm survives a day that no longer trains. Every save re-arms, so a
  changed time is live at once rather than at the next app open (SET-08's discipline).
- **WORK-08** — Given a fresh install, Then the week is seeded to the classic Body-for-LIFE
  split (upper/lower alternating, cardio between, Sunday rest) with NO reminder times set. A
  seeded week is a real starting point; a seeded alarm is one nobody asked for, and that is
  how an app's notifications get switched off for good.
- **WORK-09** — Given the Today and Progress surfaces render, When their structure is
  inspected, Then each matches its committed golden tree (`qa/golden/today.json`,
  `qa/golden/progress.json`) — training changed both, so both baselines move deliberately.
