# Spec: meal (logging, slots, and the add-to-meal tray)

> Meal logging turns Fuelled from a read-only daily snapshot into a food ledger: every entry
> carries a logical date, a meal slot, and a status, and food reaches the ledger through a
> multi-select tray with a live running total. The decisions behind these clauses — and the
> reasoning rejected along the way — are the signed brief, `docs/features/meal.md`.
> Every clause id is cited by the durable test(s) that verify it (`// SPEC: MEAL-NN`).

**Scope of this contract.** These clauses promise slices M1–M3 of the brief's plan: the data
model (logical day, slot enum, status), the write path, and the add-to-meal tray. History
browsing (M5) is promised in `specs/history.spec.md`, and the motion layer (M6) in
`specs/motion.spec.md` — each landed under its own contract, so this one never claimed
behavior nobody was building yet.

**What moved out.** Day navigation (M4) is now the meal-plan feature's day strip (PLAN-11),
and the structure this ledger records into — the six containers, their times, water, focus
and lateness, done-ticks — is `specs/meal-plan.spec.md` (PLAN-*). This spec stayed the
**ledger**: what an entry is, how it is written, and how food gets picked. The plan aims;
the tray fills; these clauses store.

## The logical day (M1)

- **MEAL-01** — Given the profile's `dayStartHour` (default 4), When an instant is mapped to
  a logical date, Then every instant from `dayStartHour`:00 through 03:59 of the following
  calendar day maps to the same logical date; and with `dayStartHour = 0` the logical date
  equals the calendar date — midnight is the special case of the setting, not a separate rule.
- **MEAL-02** — Given the logical day has rolled over while the app was backgrounded, When
  the app returns to the foreground, Then the day in view is re-derived from the current
  instant, and no stored entry is mutated by the rollover — the boundary is computed on read,
  never a scheduled job that rewrites rows.

## Meal slots (M1, M3)

- **MEAL-03** — Given any log entry, Then its meal slot is one of `BREAKFAST`,
  `MORNING_SNACK`, `LUNCH`, `AFTERNOON_SNACK`, `DINNER`, `EVENING_SNACK` (PLAN-01) — a closed
  enum, never a free-text meal name — and a day's entries are grouped and ordered by that
  slot's declaration order.
- ~~**MEAL-04**~~ — *withdrawn (meal-plan brief, decisions 2 and 13).* Promised a
  time-of-day slot preselect for a tray opened with no target. Every way into the tray now
  starts from a specific container, so the tray is always opened already aimed (MEAL-10,
  PLAN-04) — there is no untargeted open left to guess a slot for, and guessing from the
  clock was only ever a fallback for one.

## The write path (M2)

- **MEAL-05** — Given a tray holding one or more items with their servings and a target
  (logical date, slot), When the tray is confirmed, Then every item is written to that date
  and slot in a single transaction; and when the write fails, no entry is persisted and a
  mapped `DomainError` is surfaced, never a raw exception message.
- **MEAL-06** — Given a logged entry, When it is deleted, Then it is removed from its day and
  that day's consumed total and macro progress recompute without it.
- **MEAL-07** — Given a `PLANNED` entry, When it is marked logged, Then its status becomes
  `LOGGED`, it begins counting toward that day's consumed total, and no other entry changes.
- **MEAL-08** — Given the tray is confirmed, When its target date is the current logical day,
  Then the entries are written `LOGGED`; and when the target date is a future logical day,
  Then they are written `PLANNED` — scheduling and logging are the same write with a
  different target.

## The add-to-meal tray (M3)

- **MEAL-09** — Given items are added to or removed from the tray, or a serving is adjusted,
  Then the tray shows the running total of its contents — calories plus protein, carbs, and
  fat — recomputed on every change.
- **MEAL-10** — Given the tray is open, Then its header **states** the target logical date and
  slot it was opened with (`meal_tray_target`), and the tray offers no control to change
  either — the target is carried from the tap that opened it (TODAY-07, PLAN-04), so
  retargeting is done by going back and tapping the right container. "Add to Dinner tomorrow"
  is the identical flow to "add to Lunch today": aim, then fill.
- **MEAL-11** — Given the tray holds no items, Then the confirm control is disabled and no
  write can be attempted.
- **MEAL-13** — Given the tray's contents are confirmed, When the write succeeds, Then the
  tray **closes**, returning to the container that opened it. The confirmation the user needs
  is the food sitting in that container; a tray that stays open makes planning six meals six
  manual dismissals, and leaves the just-added food out of sight while you do it. A FAILED
  confirm keeps the tray open with its contents and its error — there is nothing to go back to.
- **MEAL-12** — Given the add-to-meal screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/meal.json`) — structural change must be
  intentional and declared.
