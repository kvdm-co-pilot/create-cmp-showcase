# Meal logging, scheduling, and history — feature brief

Governed artifact: `feature-brief:meal` (this file's location, `docs/features/`, is the
governance opt-in) · Author: agent · Date: 2026-07-24 · Signature state: see the console's
Features card or `node qa/approve.mjs --status`

Karel's ask, in his words: *"I need to be able to actually add meals … how do we determine
breakfast etc. … add multiple items to a meal … schedule meals for tomorrow or today …
today must go into tomorrow after twelve … keep a history … use best practices from
existing apps, keep the design and motion already used in the app."*

This document is the recommendation that precedes any code. It states what the app is today
(honestly), what the industry does (with sources), what I recommend (with reasons), and the
two decisions only Karel can make.

---

## 0. The structural fact that governs everything

Fuelled today is a **read-only, single-day, dateless snapshot**:

- `today_goal` (`data/local/TodayEntity.kt`) has a `dateLabel: String` — a *label*, not a
  date. `"WEDNESDAY, JUL 23"` is display text; nothing can be queried by it.
- `today_log` rows have **no date column at all**. `meal` is a free `String`.
- There is no write path anywhere: the use cases are `GetTodaySummaryUseCase`,
  `GetFoodsUseCase`, `GetFoodUseCase`, `SearchFoodsUseCase`, `GetProfileUseCase`,
  `GetSupplementStackUseCase`, `SetSupplementTakenUseCase`. Nothing adds a log entry.

So "add a meal", "schedule for tomorrow", "roll over at midnight", and "keep a history" are
not four features on top of the current model — they are **one data-model change plus four
features**. The change: every log row gets a real date and a status. Everything else follows
from it. Any plan that doesn't do this first will need redoing.

## 1. How do we determine breakfast / lunch / dinner?

### What the industry actually does

| App | Behaviour |
|---|---|
| MyFitnessPal | Fixed named meals (Breakfast/Lunch/Dinner/Snacks), renameable, up to 6. **No** time-of-day auto-assignment — logging at 2pm commonly defaults to Breakfast, and users report forgetting to change it. Auto-assign-by-time is a standing community request. |
| Cronometer | Free tier is one flat **chronological** list; meal categories (incl. "Uncategorized") exist, and "can we break meals into breakfast/lunch/dinner" is a forum ask. |
| Carb Manager | **Auto-selects the meal closest to the current time of day** when logging. |
| Common workaround | MFP users rename meals to encode windows by hand: "Breakfast 7am-10am", "Snack 10am-12pm", "Lunch 12pm-3pm"… |

The pattern in that table: fixed slots are the familiar model, and the *missing* piece users
keep asking for is a time-aware default. The failure mode to avoid is equally clear —
auto-assignment that is hard to override lands a 2pm meal in Breakfast.

### Recommendation

**Four fixed slots, time-window preselect, always one tap to override.**

- Slots: `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` — a Kotlin enum, not the current free
  `String`. Fuelled's `MealGroup` already renders exactly this shape, so the UI is unchanged
  in structure.
- Default windows (local time): Breakfast 04:00–10:30 · Lunch 10:30–15:00 ·
  Dinner 15:00–21:00 · Snack any other time.
- The window picks the **preselected** slot on the add screen. It is a segmented control at
  the top of the tray, pre-set, never locked. This is precisely the gap MFP's users describe.
- Slot renaming and custom windows: deliberately **out of v1**. They're a settings surface
  and a migration; the win is the default, not the configurability.

Why not chronological (Cronometer's free model): Fuelled's Today screen is built around
`MealGroup` totals ("Breakfast 535 kcal"), and its whole visual identity is grouped cards.
A flat list would be a redesign, not a feature.

## 2. Adding multiple items to one meal

### What the industry does

MyFitnessPal's **Multi-add** is the canonical pattern: tap Multi-add on a food list,
checkboxes appear, tick several, adjust servings, tap "Add Checked" — all land in the diary
at once. Its removal from the mobile app produced a sustained run of complaint threads
("Bring back multi add", "What happened to multi-add on the iPhone app?"), and Cronometer
carries the same request. When a feature's *absence* generates that much noise, the pattern
is validated.

### Recommendation

**A tray (basket) with a running total.**

1. From a meal card's `+`, or the Foods tab, enter selection mode.
2. Tap items to add to a tray; each row gets a serving stepper.
3. The tray is a bottom sheet showing **live running kcal and protein/carbs/fat** as items
   go in — this is the piece MFP does *not* have, and it fits Fuelled's macro-first design
   better than a bare count.
4. Confirm writes every item to the chosen `(date, slot)` in one transaction.

The tray also solves scheduling with no extra UI: its header is where the target
`date + slot` lives, so "add to Dinner tomorrow" is the same flow as "add to Lunch today".

## 3. Day rollover — the one place I'd push back

Karel's ask: *"today must go into tomorrow after twelve."*

A literal midnight boundary means a 00:30 snack is filed under tomorrow, splitting one
evening across two days. This is a known, specific complaint: Cronometer's forum carries
night-shift users saying they *"haven't found a tracker that doesn't reset everything at
midnight."*

### Recommendation

**A logical day boundary, default 04:00, stored as a setting — not a hard midnight.**

- `dayStartHour` (default 4) in profile settings.
- `logicalDate(instant) = (instant - dayStartHour).toLocalDate()`.
- Everything — "Today", rollover, history buckets, the date strip — reads that one function.

Midnight is then just `dayStartHour = 0`: the setting *contains* Karel's stated behaviour
as a special case, so choosing configurable costs nothing and keeps the door open. This is
the recommendation, but the default is his call (see Decisions).

Rollover itself must be **computed, never scheduled**: derive the logical date on every
read, and re-derive when the app returns to foreground. Do not run a midnight job that
mutates rows — a device asleep at 00:00, or in a different timezone, silently corrupts the
ledger.

## 4. Scheduling and history — one mechanism, not two

Both fall out of the same change. Give every log row a real date plus a status:

```
LogEntryEntity(
  … existing fields …,
  logDate: Long,        // epochDay of the LOGICAL day
  slot: MealSlot,       // enum, replaces the free `meal: String`
  status: EntryStatus,  // PLANNED | LOGGED
  loggedAtMs: Long,     // real instant, for ordering and window inference
)
```

- **Today** = rows where `logDate == logicalToday`.
- **Scheduling** = rows written with a future `logDate` and `status = PLANNED`. Same tray,
  different target date.
- **History** = rows with a past `logDate`. Nothing extra to build; it is a query.

**`PLANNED` vs `LOGGED` is not optional.** Without it, scheduling tomorrow's dinner either
pollutes a "consumed" total or is indistinguishable from something eaten. The Today ring
must count `LOGGED` only, and show planned as a separate, quieter figure ("1,240 logged ·
860 planned"). A planned entry becomes logged with one tap — the check affordance on the
row.

`today_goal` also has to stop being a single row: either one goal row per date, or a default
goal on the profile with per-date overrides. **Recommend the latter** — a date with no
override inherits the profile goal, so history doesn't need a goal row written per day
forever.

### Date navigation

The pattern common to the meal-planning apps surveyed (MealLog, Food Diary, Meal Planner
App): a **horizontal week strip** under the header + **swipe left/right between days**, with
a tap-to-jump calendar for distant dates. Today's date is the anchor; future days are
reachable but visually distinct from past ones (past = actual, future = plan).

## 5. Design and motion continuity

Honest statement of the baseline: **Fuelled has essentially no motion today.** The only
animation in the whole app is `Shimmer.kt` — an `infiniteRepeatable` `tween(1200ms,
LinearEasing)` for loading skeletons. There are no screen transitions, no list animations,
no state crossfades.

So "keep the motion already used" means: stay as restrained as that. Proposed minimum, added
as tokens rather than ad-hoc numbers:

| Token | Value | Used for |
|---|---|---|
| `MotionFast` | 150ms, standard easing | Selection state, checkbox, tray item in/out |
| `MotionStandard` | 250ms | Sheet open/close, day-to-day crossfade |
| `MotionRing` | 400ms | ProgressRing sweep when totals change |

The ring animating to its new value on add is the one flourish worth having: it is the app's
signature element, and it makes "this food changed my day" legible without a toast.

Visual continuity: every new surface is built from the existing 19-component registry
(`ScreenColumn`, `AppHeader`, `ListItemCard`, `StatBar`, `ProgressRing`, `Tag`,
`ContentStateContainer`, …). The only genuinely new components this work needs:

- `MealSlotSelector` — the segmented slot control (4 options)
- `DateStrip` — the week strip
- `TrayBar` / tray sheet — running-total basket
- `QuantityStepper` — serving adjust

Four additions to a 19-component registry, each justified by a screen that needs it — which
is what `docs/ARCHITECTURE.md` §7's inclusion rubric asks for. They go through the Components
approval before they are law.

## 6. What this becomes, as work

| # | Slice | Depends on |
|---|---|---|
| M1 | Data model: `logDate` + `slot` enum + `status` + `dayStartHour`, Room migration, `logicalDate()` in core | — |
| M2 | Write path: `AddLogEntriesUseCase`, `DeleteLogEntryUseCase`, `MarkEntryLoggedUseCase`, repository writes | M1 |
| M3 | Add-to-meal tray: search + multi-select + steppers + running total + slot/date target | M2 |
| M4 | Day navigation: date strip, swipe, planned-vs-logged rendering on Today | M2 |
| M5 | History: past-day browsing, per-day totals | M4 |
| M6 | Motion tokens + ring animation | M3 |

M1 and M2 are invisible to the eye and unavoidable. M3 is the first thing that will show up
in the console as a new screen.

Each slice lands through the harness the normal way: spec clauses (`MEAL-nn`, `HIST-nn`)
before code, `add-feature` for the vertical slice shape, console review of the rendered
screen, then the verify lane.

## 7. Decisions

Both settled by Karel on 2026-07-24, in the console conversation that produced this brief.

1. **Day boundary — 04:00, configurable.** A `dayStartHour` setting on the profile,
   defaulting to 4. Chosen over a hard midnight because midnight files a 00:30 snack under
   tomorrow, and because `dayStartHour = 0` *is* the midnight behaviour — the setting
   contains the simpler option at no extra cost. Rollover is **computed on read**, never a
   scheduled job that mutates rows.
2. **Build order — vertical slice first.** The tray lands against today only, then
   generalises to dates/scheduling/history. One exception, decided with it: the Room
   migration ships **once**, carrying `logDate` + `slot` + `status` + `loggedAtMs` even
   though slice 1 only uses today — running that migration twice is worse than running it
   once.

## 8. Declared blast radius

This document lives in `docs/features/`, which makes it the governed
`feature-brief:meal` artifact — signed before code, hash-bound from the signature on.
Doneness is **derived, never claimed**: the meal feature is provably done when every live
clause in `specs/meal.spec.md` is cited by a test, the latest receipt is PASS, and that
receipt attests the tree as it stands. There is nothing to arm and no delivery claim —
the Features card and `node qa/approve.mjs --status` print the same derived one-liner.

`touches` declares the blast radius — the governed artifacts this feature expects to
invalidate. It does not enforce (the artifact hashes already do); it lets the console tell
"re-approval, as planned" apart from "undeclared blast". `screens` declares the UI
surface: this feature adds its own screens (the add-to-meal tray), so the walk holds a
design gate — `feature-design:meal`, signed on rendered output — between this brief and
the build.

```json cmp:feature
{ "touches": ["components", "design-system", "feature-spec:today"], "screens": true }
```

## Sources

- [Automatically Determine Meal to Log to Based Off Time of Day — MyFitnessPal community](https://community.myfitnesspal.com/en/discussion/10874503/automatically-determine-meal-to-log-to-based-off-time-of-day)
- [Can I change my meal names, or add more meals? — MyFitnessPal Help](https://support.myfitnesspal.com/hc/en-us/articles/360032622311-Can-I-change-my-meal-names-or-add-more-meals)
- [What is Multi-add and how does it work? — MyFitnessPal](http://myfitnesspal.desk.com/customer/portal/articles/852172-what-is-multi-add-and-how-does-it-work)
- [Bring back multi add — MyFitnessPal community](https://community.myfitnesspal.com/en/discussion/10861611/bring-back-multi-add)
- [Feature Request: Multi-Add Option — Cronometer forums](https://forums.cronometer.com/discussion/comment/19213)
- [Can we break meals like into breakfast, lunch, dinner etc? — Cronometer forums](https://forums.cronometer.com/discussion/6097/can-we-break-meals-like-into-breakfast-lunch-dinner-etc)
- [Log foods by scanning barcodes — Carb Manager help (auto-selects meal closest to current time)](https://help.carbmanager.com/docs/log-foods-by-scanning-barcodes-and-snapping-pictures)
- [MealLog: Meal Planner Tracker — App Store](https://apps.apple.com/us/app/meallog-meal-planner-tracker/id6651852043)
- [Food Diary — Google Play (swipe between days, jump-to calendar, future weeks)](https://play.google.com/store/apps/details?id=com.food.diary&hl=en_US)
- [Meal Planner App — features (drag-and-drop calendar planning)](https://www.mealplanner.app/features)
