# Spec: usability-pass (affordance honesty and the missing verbs)

> Cross-feature contract, the `reactive-state` precedent: these clauses span the tray,
> the meal containers (plan screen and Today), the food detail, and Profile. Born from the
> usability walkthrough (`docs/features/usability-pass.md`): the app's real flows were
> sound, but three core verbs — *how much*, *undo*, *log from the catalog* — had no door,
> and several controls rendered as tappable while wired to nothing. The standing rule
> these clauses enforce: **an affordance is a promise**. Every clause id is cited by the
> durable test(s) that verify it (`// SPEC: UX-NN`).

**Scope of this contract.** Slices built in this pass only. The named next slices —
personalization + single goal source (S1), in-place quantity editing and fractional
servings (S2), catalog ownership (S3), history (S4), settings (S5), free day (S6) — get
their own clauses when their briefs are signed; nothing here claims them.

## The quantity verb (brief decision 2)

- **UX-01** — Given a food is checked in the add-to-meal tray, Then its row shows a
  serving stepper (`meal_tray_minus_<id>`, `meal_tray_servings_<id>`,
  `meal_tray_plus_<id>`) and the running total recomputes on every change (MEAL-09);
  stepping below one serving removes the line — a zero-serving line that contributes
  nothing may not linger; an **unchecked** row shows no stepper — selection first, quantity
  second. A multi-serving line is written with its multiple stated in its serving label
  (`n × <serving>`), so the logged day reads back what was actually eaten.

## The undo verb (brief decision 3)

- **UX-02** — Given a meal container renders an entry — on the plan screen or as Today's
  focused container — Then the entry row carries a remove control
  (`plan_entry_delete_<id>` / `today_entry_delete_<id>`), and activating it deletes that
  entry through the one delete path (MEAL-06); the day's totals, macro progress, and veg
  count re-derive through the observed read (RS-01) with no reload and no confirmation
  dialog — removal of one mis-tapped row is immediate (the undo snackbar is S2's slice);
  a failed delete surfaces on the write channel (RS-04), never by destroying the rendered
  day.

## The catalog-first verb (brief decision 4)

- **UX-03** — Given a food's detail screen, When "Log this food" is activated, Then the
  six meal containers of the **current logical day** are offered as a slot-picker
  (`food_log_slot_<slot>`), naming the day it writes to; choosing a slot writes that food
  at one serving, `LOGGED`, through the same write path as the tray (`AddLogEntriesUseCase`
  — TODAY-13's one-write-path discipline), and the surface confirms the slot it wrote to;
  a failed write surfaces its mapped error on the same surface, never a raw exception.
  The picker offers no other day — back-fill and planning remain the plan screen's aimed
  flows.

## The honesty rule (brief decisions 1, 5)

- **UX-04** — Given the Profile screen's goal and settings rows have no live editor
  destination, Then they render as read-only values — not clickable, no tap affordance —
  and no screen in the app renders a control wired to a no-op; a control appears when its
  behavior exists (PROF-02/PROF-04 as amended).
