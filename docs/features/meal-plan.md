# Feature brief: meal-plan — the structured day (Body-for-LIFE, modernized)

## What and why

Fuelled's day stops being a freeform log and becomes a **fixed, planned structure**:
every day carries the same six meal containers — **Breakfast · Snack · Lunch · Snack ·
Dinner · Snack** — with a **500 ml water container between each**. You plan meals up to a
week ahead, then tick them off as you eat; the screen keeps itself aimed at the next meal
and tells you when you're late. The UI stays what it is today — same ring, same macros,
same card language — with the minimum edits that carry the new structure.

This is the Body-for-LIFE eating method (Bill Phillips) as a modern surface: the book has
you eat six small meals a day, two to three hours apart, portioning protein by palm and
carbs by fist, vegetables with at least two meals, water constantly through the day, and
one free day a week. The book's paper log — a fixed grid of meal rows you fill in ahead
and tick through — is exactly what this feature turns into a screen. We take the six-meal
rhythm, the plan-then-tick loop, and the always-present structure; we modernize the
capture (food picker, macros, notifications) rather than the philosophy.

## Decisions

1. **Six fixed slots, three distinct snacks.** `MealSlot` becomes
   `BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER, EVENING_SNACK` —
   declaration order stays the day order (MEAL-03's rule, unchanged). The generic `SNACK`
   dies: with three snack containers on screen, "a snack" is not an identity. This is a
   breaking change to stored rows (`slot` strings); at this stage the fix is a destructive
   dev migration, not a mapping.
2. **The containers always render — a day is never a blank page.** All six meal containers
   and all six water containers appear for every day, empty or not. An empty meal container's
   body IS its add affordance (`today_add_<slot>` moves inside the container). The whole-day
   `today_empty` state and its `today_empty_add` control become meaningless and are removed.
3. **Days start empty.** The seeded starter day (`TodayRepositoryImpl.seedEntries`) is
   removed. The foods catalog seed stays — the tray needs foods to pick.
4. **Every slot has an alarm time; set once, never re-asked.** Defaults (Body-for-LIFE's
   2–3 h rhythm): 07:00 · 09:30 · 12:00 · 14:30 · 17:00 · 19:30. Times are stored
   per-slot in Room and editable from a small times sheet; the app never prompts for them
   again unless the user opens that sheet. Each slot time drives one daily notification.
5. **Water derives from meals — one configuration surface, not two.** Six water containers
   of 500 ml (day goal 3.0 L, shown as litres: "1.5 / 3.0 L"). Each water container sits
   after its meal and reminds at the **midpoint** between that meal's time and the next
   (the last: 75 min after the evening snack). Move a meal time and its neighbouring water
   reminders move with it — water times are never asked at all.
6. **Plan ahead one week, tick off as you eat.** The tray already writes `PLANNED` for
   future dates and `LOGGED` for today (MEAL-08); the horizon becomes today + 7 days,
   navigated by a compact day strip on Today. Ticking a meal container marks its entries
   `LOGGED` and the slot done. Consumed totals still count only `LOGGED` (TODAY-03).
7. **Focus and lateness are derived, never stored.** The focused container is the earliest
   not-done slot of the current logical day. A focused slot whose alarm time is more than
   30 min past is flagged **late**. When all six slots are done — or the logical day ends —
   focus moves to tomorrow's breakfast, and the day strip advances with it.
8. **A slot can be ticked with no entries.** Eaten off-plan, or skipped — the tick records
   slot completion; it fabricates no food. (This is also what keeps the free-day idea
   representable later without new state.)
9. **Notifications are Android-first.** AlarmManager + POST_NOTIFICATIONS (runtime prompt
   on first arm), fired at slot times and water midpoints; tapping opens Today. iOS
   (UNUserNotificationCenter) follows as its own slice — the verify lane's device tier is
   Android, so Android is what this feature can prove.

## Usability audit — the tray, re-judged against the new flow

The tray was designed when it was the *only* way in: it had to carry its own targeting.
The six-container grid inverts that — **the tap now carries the target** — and two of the
tray's controls flip from helpful to harmful:

- **The slot pill row goes.** Tapping Breakfast's add and then flipping the slot pill
  mid-tray is exactly how food lands in the wrong meal; and four generic pills cannot even
  say *which* snack. The target line ("Breakfast · Wednesday, Jul 29") stays as a
  statement; to retarget you go back and tap the right container. One mental model:
  containers aim, the tray fills.
- **The yesterday/today/tomorrow date row goes.** It conflicts with week planning; the day
  strip on Today is the one date selector. (Back-filling yesterday = day strip, same as
  planning forward.)
- **What stays, verified as good:** search, multi-select with per-food ticks, the running
  total bar with macros (MEAL-09), confirm disabled on an empty tray (MEAL-11), the
  aimed-constructor target (MEAL-10). The tray becomes strictly simpler.

## Blast radius and contracts

- `feature-spec:meal-plan` — NEW; the plan screen owns the structure clauses (fixed grid,
  day strip + yesterday chip, water derivation, focus/late, done-ticks, times sheet).
- `feature-spec:today` — reopen; amendments shrink to the highlights (focus container,
  next-water row + litres total, supplements bucket row, plan link; whole-day empty state
  removed — the focus container's empty body is the add). The ring/macros clauses stand.
- `feature-spec:meal` — reopen; MEAL-03 slot set change and MEAL-10 amended to match the
  simplified tray (it states the target, it no longer retargets), MEAL-04's time-of-day
  preselect withdrawn. Slot times and done-ticks are **not** here: decision 13 gives them to
  `feature-spec:meal-plan` with the rest of the structure, leaving this spec the ledger.
- `feature-design:meal` — reopen; the tray redesign is signed on rendered output.
- `components` — likely touched (a slim water-row card if no existing component composes it).
- Golden trees (`qa/golden/today.json`, meal tray), the smoke flow's tray step, and the
  Room schema all move with it.

10. **Default times confirmed** (Karel, 2026-07-26): 07:00 · 09:30 · 12:00 · 14:30 ·
    17:00 · 19:30, with the 30 min late grace. These ship as the defaults.
11. **Free day is out of v1** (Karel, 2026-07-26). Decision 8 (a slot can be ticked with
    no entries) keeps it representable if it ever comes in.
12. **A done slot's alarm is suppressed** (Karel, 2026-07-26): ticking a meal early
    cancels that slot's notification for the day; water reminders are unaffected.
13. **Today is the highlights dashboard; the plan is its own screen** (Karel, 2026-07-27).
    Fuelled has three time-structured features — meals, water, supplements — and the Today
    tab belongs to none of them: it is the derived "now" surface across all three. Top to
    bottom: the existing header + hero ring + macros + protein focus, **untouched**
    (consumed = LOGGED only, TODAY-03 unamended); then the focused meal container (the
    plan's own card, rendered once — NEXT/LATE tag, entries or the add button as its empty
    body, tick to complete, self-advancing); the next water container with the running
    litres total; a supplements highlight (**bucket-based** — `timing` is a free string
    plus a `taken` flag, so "Morning stack · 2 of 4 taken" linking to the Supplements tab;
    clock-time supplement reminders are OUT of this feature's scope); and one "This week"
    link. The full grid — day strip, all six meal containers, all six water containers —
    lives on a routed **plan screen** (`plan/{date}`), whose day strip gains a leading
    *yesterday* chip for back-fill (this is where the tray's dead date row truly went) and
    whose header carries the way into the meal-times sheet. Both surfaces render the same
    derived state through the same components and tick through the same use case —
    highlight = projection of the plan, never a second write path. This refines decisions
    2 and 6: the always-rendered grid and the day strip are the plan screen's; Today
    carries the focused container only.

14. **A slot can be MISSED — the third outcome** (edge-case audit, 2026-07-27). Skipping a
    meal is ROUTINE in Body-for-LIFE (the 09:30 snack at work, the weekly free day), and
    the original focus rule made one skipped meal poison the rest of the day: focus stuck
    on "Snack, late since 09:30" at 19:00 and dinner never became next. So: a not-done slot
    whose **successor's time has arrived** stops competing for focus and reads **missed** —
    muted, never alarming, still back-fillable (its add and tick stay live). Focus is the
    earliest not-done, not-missed slot; lateness is therefore naturally bounded by the next
    meal's time. When everything left is missed, the day is behind you and focus advances
    to tomorrow's breakfast — which is also what keeps a free day from corrupting the
    surface: ignore all six containers on Sunday and Sunday just goes quiet; Monday derives
    fresh from its own slots.
15. **Times run forwards, and they are settings, not history** (2026-07-27). Slot times
    must stay strictly between their neighbours' times — the sheet constrains the picker
    and the domain coerces, so "dinner at 05:00" cannot exist and every derived surface
    (water midpoints, focus order) stays monotone. The last water reminder clamps at 23:59
    rather than wrapping past midnight. And because times are stored ONCE, not per day,
    changing one re-renders every day at the new times — so **past days are never
    re-judged**: lateness and reminders are derived only for the current logical day;
    history shows what was eaten and what was ticked, and makes no claims about
    punctuality it can no longer honestly compute.
16. **Notifications survive the platform** (2026-07-27). PLAN-07 as first written assumed
    alarms that fire exactly, forever. Android disagrees: exact alarms need
    `SCHEDULE_EXACT_ALARM` (deniable), Doze defers inexact ones by up to ~15 min, reboot
    clears everything, and Android 13+ lets the user refuse notifications entirely. The
    contract now says what is actually promised: exact when permitted, windowed-inexact
    otherwise, re-armed on boot and on app open, and a visible settings-level statement
    when notifications are off — the plan surface never silently pretends reminders exist.
17. **Portions speak Body-for-LIFE by default** (2026-07-27). The method portions by palm
    (protein) and fist (carbs) precisely so nothing is ever weighed; our tray speaks grams.
    The bridge is the catalog: every seeded food's DEFAULT serving approximates one BFL
    portion of that food, so the default one-tap add is method-compliant and the grams
    remain the honest data model underneath. No schema change; the palm lives in the
    serving strings.
18. **A week is planned by copying, not by 42 taps** (2026-07-27). BFL eaters eat nearly
    the same day repeatedly and prep in batches; the plan screen therefore offers **copy
    day forward** — duplicate a day's planned meals onto chosen following days as
    `PLANNED` entries. Planning Sunday's prep is one day built by hand and one copy.
19. **The day tracks vegetables, and stale plans read as history** (2026-07-27). "Veg with
    at least two meals" is a real BFL rule: the day derives how many meal containers hold a
    vegetable and shows "Veg n of 2" beside the water total (needs a `veg` flag on catalog
    foods — lands with the Room slice). And a `PLANNED` entry on a day that ended un-eaten
    stays visible as *planned, not eaten* — muted, never counted, still back-fillable via
    the day strip — instead of lingering as an unresolved promise.

## Edge cases

The adversarial pass over the design (2026-07-27), against how Body-for-LIFE is
actually eaten rather than how it reads on paper. Each case names how it resolves.

- **A meal is skipped** — routine here (the 09:30 snack at work), and the first
  focus rule left it "late" all day while dinner never became next → MISSED as a
  third outcome, decision 14 / PLAN-19.
- **A free day is taken** — six skipped slots at once, the same bug at maximum →
  falls out of PLAN-19; the day goes quiet and focus advances (PLAN-17).
- **Meals are eaten out of order** — snack before lunch → done takes precedence
  over missed; focus is the earliest slot that is neither (PLAN-15).
- **Slot times are rearranged past each other** — a shift worker setting dinner
  at 05:00 broke water midpoints and focus order → times are coerced strictly
  between their neighbours, decision 15 / PLAN-06.
- **The evening snack sits near midnight** — its derived water wrapped to 00:45
  and sorted above breakfast → clamped to 23:59, PLAN-08.
- **Meal times change after history exists** — global times re-judged past days
  for punctuality they never had → times are settings, not history; lateness is
  derived only for the current day, decision 15 / PLAN-23.
- **The platform refuses the reminder** — exact alarms need permission, Doze
  defers inexact ones, reboot clears them, Android 13+ can deny notifications
  outright → exact-when-permitted / windowed otherwise, re-armed on boot, and
  said plainly when off, decision 16 / PLAN-07.
- **A week is planned one food at a time** — 42 slots by hand is why someone
  stops in week two; BFL eaters repeat and batch-prep → copy day forward,
  decision 18 / PLAN-20.
- **A planned meal is never eaten** — PLANNED rows lingered as unresolved
  promises → they read as *planned, not eaten*: muted, uncounted, still
  back-fillable, decision 19 / PLAN-21.
- **Portions are palms and fists, not grams** — the method deliberately never
  weighs → seeded default servings approximate one BFL portion, decision 17.
- **Vegetables with at least two meals** — a real rule nothing tracked → derived
  "Veg n of 2", decision 19 / PLAN-22 / TODAY-14.

## Open decisions

None — all closed 2026-07-27 (decisions 1–9 and 10–12 on 2026-07-26, decision 13 and the
edge-case decisions 14–19 on 2026-07-27).

```json cmp:feature
{ "touches": ["feature-spec:today", "feature-spec:meal", "feature-design:meal", "components"], "screens": true, "unrouted": true }
```

*`unrouted` is the design-phase declaration: the `presentation/meal-plan/` drafts are
deliberately not in the nav graph yet. The build step registers `plan/{date}` and the
meal-times route and REMOVES this flag — from then on the reachability gate enforces it.*
