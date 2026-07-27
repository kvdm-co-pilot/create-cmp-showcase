# Spec: meal-plan (the structured day)

> The Body-for-LIFE day as a fixed structure: six meal containers with a 500 ml water
> container between each, always present, planned ahead and ticked off as they are eaten.
> This spec owns the **plan screen** (`plan/{date}`) and the structure behind it — slots,
> times, water derivation, focus and lateness, done-ticks, notifications. Today shows a
> projection of that structure (TODAY-09..TODAY-13); the tray fills it (MEAL-*). The
> decisions behind these clauses are the signed brief, `docs/features/meal-plan.md`.
> Every clause id is cited by the durable test(s) that verify it (`// SPEC: PLAN-NN`).

**Scope of this contract.** These clauses promise the structured day and its plan screen:
the six-slot grid, per-slot times, derived water, week-ahead planning, focus/lateness,
done-ticks, and Android notifications. A free day (brief decision 11) and iOS notifications
(decision 9) are deliberately **not promised here** — they get their own clauses if and when
those slices are built.

## The six containers (brief decisions 1, 2, 3)

- **PLAN-01** — Given any log entry, Then its meal slot is one of `BREAKFAST`,
  `MORNING_SNACK`, `LUNCH`, `AFTERNOON_SNACK`, `DINNER`, `EVENING_SNACK` (MEAL-03) — three
  distinct snack containers, never a generic one — and declaration order is the day order.
- **PLAN-02** — Given the plan screen renders any day, Then all six meal containers are
  shown in slot order, each with its label and its slot time, whether or not it holds
  entries — a day is never a blank page; and a water container is shown after each meal
  container, six in all, interleaved.
- **PLAN-03** — Given a logical day that has never been planned or logged, When it is
  opened, Then every container is empty — no starter entries are seeded — and the day's
  consumed total is zero.
- **PLAN-04** — Given a meal container holds no entries, Then its body is its own add
  control (`plan_add_<slot>`), and tapping it opens the add-to-meal tray already targeted at
  **that container's logical day and slot** (MEAL-10) — the tap carries the target, so the
  tray never asks which meal this was for.

## Slot times and the times sheet (brief decisions 4, 12)

- **PLAN-05** — Given a profile that has never set meal times, Then the six slot times are
  07:00, 09:30, 12:00, 14:30, 17:00, 19:30; and those times are stored per slot, so the app
  asks for them once and never prompts for them again.
- **PLAN-06** — Given the meal-times sheet, When a slot's time is changed, Then that slot's
  stored time is updated, its container shows the new time, its notification is re-armed at
  the new time, and no other slot's time changes. A slot's time is constrained to run
  **strictly between its neighbouring slots' times** — the sheet offers no value outside
  that window and the domain coerces one — so the six times are always strictly ascending
  and every derived surface (water midpoints, focus order) stays monotone (decision 15).
- **PLAN-07** — Given a slot time is set, Then one daily reminder is armed for that slot:
  **exact** when the platform permits exact alarms, otherwise **windowed-inexact** — never
  silently nothing; armed reminders are re-registered on device boot and on app open, so a
  reboot does not eat the day's reminders. When the slot is ticked done before it fires,
  that day's reminder for that slot is cancelled — a meal already eaten is never announced;
  water reminders are unaffected by a meal tick. And when notification permission is
  denied, Then the meal-times sheet states plainly that reminders are off — the surface
  never pretends reminders exist that the platform will not deliver (decision 16).

## Water (brief decision 5)

- **PLAN-08** — Given the six slot times, Then each of the six water containers holds 500 ml
  and is reminded at the **midpoint** between its meal's time and the next meal's time —
  the sixth at 75 minutes after the evening snack, **clamped to 23:59** so the day's last
  reminder never wraps past midnight and the list stays time-ordered — and the day's water
  goal is 3.0 L, shown in litres to one decimal (`plan_water_total`, e.g. "1.5 / 3.0 L").
- **PLAN-09** — Given a meal time is changed, When the water reminders are re-derived, Then
  the neighbouring water times move with it — water times are never asked for, stored as
  their own setting, or edited directly.
- **PLAN-10** — Given a water container is ticked, Then the day's completed litres increase
  by 0.5 L and the total re-renders; and the tick is per day, so a new logical day starts at
  0.0 L.

## Planning ahead and ticking off (brief decisions 6, 8)

- **PLAN-11** — Given the plan screen, Then its day strip (`plan_days`) offers the previous
  logical day, the current logical day, and the next seven — nine days, the current day
  selected on open — and selecting a day renders that day's containers; the strip is the
  only date selector in the feature.
- **PLAN-12** — Given a meal container on a future logical day, When food is added to it,
  Then the entries are written `PLANNED` (MEAL-08) and are not counted in any day's consumed
  total until they are logged (TODAY-03).
- **PLAN-13** — Given a meal container holding `PLANNED` entries, When it is ticked
  (`plan_done_<slot>`), Then those entries become `LOGGED` (MEAL-07), the slot is recorded
  done for that day, and the day's consumed total and macro progress recompute with them.
- **PLAN-14** — Given a meal container holding no entries, When it is ticked, Then the slot
  is recorded done for that day and **no entry is fabricated** — eaten off-plan or skipped
  is a completion, not a food.

## Focus and lateness (brief decision 7)

- **PLAN-15** — Given the current logical day, Then the focused container is the earliest
  slot that is neither done **nor missed** (PLAN-19); it is marked as next, and no container
  on any other day is focused — focus, lateness, and missed-ness are derived on read from
  the slot times and done-state, never stored.
- **PLAN-16** — Given the focused container, When the current time is more than 30 minutes
  past its slot time, Then it is marked late, showing the time it was due; at or within the
  grace it is marked next, not late. Lateness is naturally bounded: at the next slot's time
  the container becomes missed (PLAN-19) and focus moves on — a skipped 09:30 snack cannot
  still be "late" at dinner time.
- **PLAN-17** — Given every slot of the current logical day is done **or missed**, Then no
  container of that day is focused and focus is the next day's breakfast — a finished (or
  abandoned, or free — decision 11) day advances rather than pointing at nothing, and the
  next day derives fresh from its own slots.
- **PLAN-19** — Given a slot that is not done, When its **successor slot's time arrives**,
  Then the slot is **missed**: rendered muted (never with alarm styling), excluded from
  focus, and still fully back-fillable — its add control and its done-tick stay live, and
  ticking it is the ordinary completion (PLAN-13/PLAN-14). Missing a meal is routine in
  this method, not an error state. The last slot of the day has no successor and therefore
  never reads missed — the day's end simply rolls focus forward (PLAN-17, MEAL-02).

## Planning at Body-for-LIFE pace (decisions 17, 18, 19)

- **PLAN-20** — Given a day holding planned meals, When it is **copied forward**, Then its
  planned entries are duplicated onto the chosen following day(s) as `PLANNED` entries
  (MEAL-08) with fresh identities — a prepped week is one day built by hand and one copy,
  never 42 taps — and the source day is unchanged.
- **PLAN-21** — Given a `PLANNED` entry on a logical day that has ended without being
  logged, Then it renders as *planned, not eaten* — muted, never counted in any total
  (TODAY-03) — and remains back-fillable from the day strip: ticking that slot logs it like
  any other (PLAN-13). A stale plan is history, not an unresolved promise.
- **PLAN-22** — Given the day's containers, Then the day derives how many meal containers
  hold at least one vegetable (`veg`-flagged catalog food) and shows it as **"Veg n of 2"**
  beside the water total on both the plan screen (`plan_veg_total`) and Today — vegetables
  with at least two meals is the method's rule, surfaced, never enforced.
- **PLAN-23** — Given any day is rendered, Then slot times and water times shown are the
  **current** stored times — times are settings, not history (decision 15) — and lateness,
  missed-ness, and reminders are derived **only for the current logical day**: a past day
  shows its entries and its ticks and makes no punctuality claims.

## Structure

- **PLAN-18** — Given the plan screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/meal-plan.json`) — structural change must be
  intentional and declared.
