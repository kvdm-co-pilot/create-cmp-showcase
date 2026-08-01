# Changelog

All notable changes to Fuelled. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Added
- **Personalization — the app becomes yours** (`docs/features/personalization.md`,
  `specs/personalization.spec.md` PERS-01..03; usability-pass S1, first half). The
  calorie target and protein goal are now **editable from Profile** (tap the row), your
  **name is editable** (tap the identity header), and — the structural fix behind it —
  the two goal stores the usability pass flagged (F5) are **one**: the profile table lost
  its duplicate goal columns (schema v9, destructive dev migration) and every surface —
  ring, macros, week review, Profile — reads and writes the single goal row. Editing a
  goal re-targets every observed surface with no reload and never touches what was eaten.
  Non-positive and blank inputs are refused before any write.
- **The daily-journeys pass — the app judged as days lived, not screens visited**
  (`docs/features/daily-journeys.md`, `specs/daily-journeys.spec.md` JRN-01..03; PLAN-07
  amended). Seven journeys walked on the rendered screens; the two that failed are built:
  - **Prep reminders** (J2, PLAN-07): meal reminders now fire **30 minutes before** the
    slot — the moment a working user can still cook or fetch the meal — carrying the slot
    time in the copy ("Lunch at 12:00 — time to prep"). Water keeps its midpoints; a
    ticked meal still cancels; the times sheet states the lead.
  - **Week in review** (J6, route `week`): the holistic results surface — headline verdict
    first (protein days hit at a ≥95%-of-goal tolerance, meals kept, avg kcal), then the
    last seven logical days with kcal vs target, protein bar, meals /6, water, veg. All
    derived through the same observed plan-day and summary reads (no second read path).
    Profile's stats row is now the door (`profile_week_link`) — its streak/avg claims
    finally open the surface that backs them.
- **The usability pass — every flow re-judged as a human's day**
  (`docs/features/usability-pass.md`, `specs/usability-pass.spec.md` UX-01..UX-04). The
  walkthrough found the app's real flows sound but three core verbs missing and several
  controls wired to nothing; this pass ships the verbs and the honesty rule:
  - **Serving quantities in the tray** (UX-01): a checked row grows a −/count/+ stepper
    (`meal_tray_minus/servings/plus_<id>`); the running total follows; minus at 1× removes
    the line. `MealTrayViewModel.onServingsChanged` now removes below one serving — its doc
    always said removal; the code held the line at 1×.
  - **Entry deletion** (UX-02): every entry row in a meal container — plan screen and
    Today's focused container — carries a remove control (`plan/today_entry_delete_<id>`)
    writing through `DeleteLogEntryUseCase` (MEAL-06's one path); observed reads re-derive
    the day (RS-01). `PlanEntryUi` now carries the entry id.
  - **Catalog-first logging** (UX-03): Food detail's "Log this food" — previously rendered
    but wired to nothing — now opens a six-slot picker (`food_log_slot_<slot>`) for the
    current logical day and writes one `LOGGED` serving through `AddLogEntriesUseCase`, the
    same write path as the tray. New preview variant `food-detail@log`.
  - **Affordance honesty on Profile** (UX-04, PROF-02/PROF-04 amended): goal and settings
    rows are read-only values — no `clickable`, no chevron — until their editors exist.
  The brief also documents the exhaustive per-screen use-case inventory, the findings
  against industry heuristics (including the latent two-goal-source divergence, F5), and
  the ordered next slices (personalization S1 → settings S5).

### Fixed
- **Screens outside the shell are now visible to on-device automation.**
  `Modifier.exposeTestTagsForAutomation()` — the modifier that surfaces Compose testTags as
  Android resource-ids / iOS accessibilityIdentifiers — was applied inside `AppShell`. The
  property is inherited by descendants, so it covered the tabs and **nothing else**: every
  destination registered directly on the NavHost (`food/{foodId}`, and now
  `meal/{date}/{slot}`) had testTags that no id-selector could see, which made those screens
  untestable end-to-end — `meal_tray_screen` simply did not exist as a resource-id. Moved to
  the NavHost itself in `AppNavHost.kt`, the actual graph root, so every destination inherits
  it and one added later does so without anyone remembering to. The smoke flow now taps
  `today_add_breakfast` and asserts arrival on `meal_tray_screen` with the header aimed at
  Breakfast (TODAY-07), which is also the standing guard against this regressing.

### Added
- **The add-to-meal tray is reachable from Today** (`specs/today.spec.md` TODAY-07, TODAY-08).
  Every meal card carries an add control (`today_add_<slot>`) and the empty state carries one
  too (`today_empty_add`) — a day with nothing logged is exactly the day food must be addable
  to. The tap's target travels in the route (`meal/{date}/{slot}`, ISO logical date + the
  `MealSlot` enum name) and reaches `MealTrayViewModel` as a **constructor** input
  (`MealTrayInitialTarget`), so the tray's first frame is already aimed at that day and slot —
  never defaulted and then corrected (MEAL-10). The empty state, having no slot of its own,
  takes the one the current time suggests (`slotForLocalTime`, MEAL-04). An absent or malformed
  route argument falls back to the tray's existing clock-derived target instead of throwing.
  New golden baseline for `qa/golden/today.json` (TODAY-06): two add-control nodes, declared.
- **Add-to-meal tray, wired to the real path** (`specs/meal.spec.md` MEAL-09…12).
  `MealTrayViewModel` holds the target (logical date + slot), the Room-backed catalog and its
  search (through the existing `GetFoodsUseCase`/`SearchFoodsUseCase`), the tray's lines with
  their servings, and the derived running total; `MealTrayRoute` renders it and confirms
  through `AddLogEntriesUseCase`. The total now carries **protein, carbs and fat alongside
  calories**, recomputed on every add/remove/serving change (MEAL-09); the header states the
  target **date and slot**, and either can be retargeted without emptying the tray — "add to
  Dinner tomorrow" is the same flow as "add to Lunch today" (MEAL-10). An empty tray disables
  the confirm control *and* is refused inside the ViewModel, so no write is attempted from any
  caller (MEAL-11). New golden baseline `qa/golden/meal.json` (MEAL-12).
- Meal-log **write path** (`specs/meal.spec.md` MEAL-05…08): `AddLogEntriesUseCase`,
  `DeleteLogEntryUseCase`, `MarkEntryLoggedUseCase`, and the matching `TodayRepository`
  writes. A tray confirm writes every item to one `(logical date, slot)` in a single
  transaction; the same write is a *log* on the current logical day and a *plan* on a future
  one (MEAL-08).

### Changed
- **Today is now a dated, status-aware ledger** (amended TODAY-01/TODAY-03). `TodayModel`
  carries the logical day's `LocalDate` instead of a stored `dateLabel`, `MealGroup` carries
  the closed `MealSlot` enum instead of a free-text meal name, and the day's consumed total
  and macro progress sum `LOGGED` entries only — a `PLANNED` entry shows in its meal group but
  is never counted as consumed. Presentation formats the date and the slot label.
- Room schema **v6**: `today_log` gains `logicalDate`/`slot`/`status` and drops `mealOrder`
  (slot order derives from `MealSlot.ordinal`); `today_goal` drops `dateLabel`. Destructive
  fallback re-seeds the sample day, dated with the logical day the seed runs on.
- `qa/golden/today.json` regenerated: the header renders the formatted logical date
  ("WEDNESDAY, JUL 22") in place of the old fixture's `"GOLDEN DAY"` label — one line.
- The meal tray's signed design draft (`feature-design:meal`) drifts **as declared**: the total
  bar gained the P/C/F macro row MEAL-09 requires, and the header gained the target-date line
  plus a Yesterday/Today/Tomorrow pill row MEAL-10 requires — both built from the draft's own
  vocabulary (macro `Tag`s, the existing slot pill). Nothing else was restyled; a human
  re-approves on the re-rendered screens. The draft's private `MealSlot` enum is gone: the
  screen now uses `domain.model.MealSlot`, so the app has exactly one.

## [0.1.0] — scaffold

### Added
- Initial scaffold generated by [create-cmp](https://github.com/kvdm-co-pilot/create-cmp):
  Compose Multiplatform app with Clean Architecture, the `home` exemplar feature (with tests),
  theme token catalog, and the verification harness (`qa/verify.mjs` + evidence receipts +
  [`CLAUDE.md`](./CLAUDE.md) contract).
