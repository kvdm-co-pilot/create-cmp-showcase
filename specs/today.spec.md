# Spec: today (the daily dashboard)

> Today is the home dashboard: the day's calories and macros against goal, plus the
> **highlights** of the structured day — what to eat next, the next water, the supplement
> stack, and the way into the week's plan. It owns no structure of its own: the meal
> containers and their state are the meal-plan feature's (PLAN-*), projected here.
> Data-backed, stateless-over-a-model. Every clause id is cited by the durable test(s) that
> verify it (`// SPEC: TODAY-NN`).

## The day's numbers

- **TODAY-01** — Given the Today screen opens, When the day's summary is loading, Then a
  loading indicator is shown; and when it completes the screen shows the **logical day's**
  date (derived from `dayStartHour` — MEAL-01, so a 00:30 entry still belongs to the evening
  before), the calorie ring (consumed vs. target), and the calories remaining, with no error.
- **TODAY-02** — Given the day's summary is loaded, Then each tracked macro (protein, carbs,
  fat) is shown as a progress bar of current vs. target, and protein is surfaced as the focus
  with its grams-remaining-to-goal — or a goal-met state when current ≥ target.
- **TODAY-03** — Given the day has entries, Then the day's consumed total equals the sum of
  the calories of its **`LOGGED`** entries — a `PLANNED` entry (MEAL-08) is never counted as
  consumed — and its macro progress is computed from the same set.
- **TODAY-04** — Given the current logical day has no entries in any container, When the
  summary loads, Then the ring reads the full target as remaining, the focused container is
  still shown with its own add control (TODAY-09, PLAN-04), and no error is shown — an empty
  day is a plannable day, not an error or a dead end.
- **TODAY-05** — Given the source fails, When loading completes, Then a mapped error
  (`today_error`) is shown from the failure's `DomainError` kind, never a raw exception
  message, and **no retry control** is offered; When the source next emits after recovering,
  Then the summary renders on its own (RS-01) — the state is observed, so recovery is the
  stream's next emission, not a button.
- **TODAY-06** — Given the Today screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/today.json`) — structural change must be
  intentional and declared.

## The highlights (brief decision 13)

- **TODAY-07** — Given a meal container is shown on the Today screen, Then it carries an add
  control (`today_add_<slot>`), and tapping it opens the add-to-meal tray already targeted at
  **that logical day and that slot** — the target is carried from the tap, never defaulted at
  the tray (MEAL-10), so "add to Dinner" from Dinner's container needs no retargeting.
- ~~**TODAY-08**~~ — *withdrawn (brief decision 13).* Promised a separate whole-day empty
  state control (`today_empty_add`) resolving to the slot for the current time (MEAL-04).
  There is no whole-day empty state any more: an empty day shows its focused container, and
  that container's own body is the add control (TODAY-04, PLAN-04), carrying a real target
  instead of a time-guessed one.
- **TODAY-09** — Given the current logical day, Then Today shows exactly one meal container —
  the focused one (PLAN-15) — with its slot, time, entries, tick, and add control; it is
  marked next, or late with the time it was due when past the grace (PLAN-16). Ticking it
  from Today records the slot done (PLAN-13/PLAN-14) and the shown container becomes the new
  focused slot without leaving the screen.
- **TODAY-10** — Given the current logical day, Then Today shows the next water container not
  yet ticked (PLAN-08) with its 500 ml and its reminder time, and the day's running litres
  against the 3.0 L goal (`today_water_total`); ticking it there is the same completion as
  ticking it on the plan screen (PLAN-10).
- **TODAY-11** — Given the day's supplement stack, Then Today shows a highlight for the
  current timing bucket (`today_supplements`) naming the bucket and how many of its
  supplements are taken (SUPP-02), and opening it goes to the Supplements tab — Today
  summarizes the stack, it never edits it.
- **TODAY-12** — Given the Today screen, Then it offers one control into the full week
  (`today_plan_link`) which opens the plan screen (PLAN-11) at the current logical day —
  planning is one tap from the dashboard, and Today never renders the week itself.
- **TODAY-13** — Given the same logical day, When a meal or water container is ticked from
  Today and when it is ticked from the plan screen, Then the resulting stored state is
  identical — Today renders a projection of the plan and writes through the same use case,
  never a second write path.
- **TODAY-14** — Given the current logical day, Then Today shows the day's vegetable count
  (`today_veg_total`, "Veg n of 2" — PLAN-22) alongside the water total — the method's
  veg-with-two-meals rule at a glance, surfaced, never enforced.
