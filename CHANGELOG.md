# Changelog

All notable changes to Fuelled. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/).

## [Unreleased]

## [0.5.0] — the reminders arrive

### Added
- **Notifications actually reach the device now** (`docs/features/notifications.md`,
  `specs/notifications.spec.md` NOTIF-01..07). The meal and water reminders had shipped
  (PLAN-07/08) but nothing ever *asked* for the Android 13+ notification permission, so every
  alarm was armed undeliverable. Now: the system permission is requested once — on the first
  Today open after onboarding — and never again (denied means denied); the times sheet's
  "reminders are OFF" notice gains an "Open notification settings" tap-through as the one
  sanctioned second chance (schema v14 records the ask).
- **End-of-day plan-tomorrow nudge** (NOTIF-04..07). When the next logical day has zero
  planned entries, one `plan_tomorrow` notification fires 45 minutes after the evening snack
  (never later than 22:00 — the time derives from your own meal times, like water). Planning
  anything for tomorrow silences it instantly via the existing re-arm seams, and delivery
  re-checks emptiness so a nudge about a meanwhile-planned day is never posted. It fires
  every empty evening — the OS channel is the off switch.

### Fixed
- **The E2E smoke flow could not have passed on a clean device** — and nothing revealed it,
  because `e2eSmoke` SKIPs whenever no device is attached, so release after release stepped
  over it. Driven on a device for this release, it failed at step two and kept failing for
  three more reasons, each a stale selector nobody had run:
  - It asserted the shell on a fresh install, but `clearState` IS a fresh install, so the app
    shows the first-run interview (START-01). The flow now proves that gate instead of
    tripping over it: the interview is shown, skipping keeps the seeded defaults, and the
    shell replaces it **in place** with no relaunch.
  - Food ids became slugs when 0.4.0 replaced the eight invented foods with 59 USDA rows —
    `foods_item_1` had been `foods_item_almonds` for a whole release. Selectors are now real
    ids, never positions.
  - The tray's picks are a LazyColumn over 59 foods, so only composed rows exist in the
    semantics tree at all; a selector for a food below the fold is simply absent.
  - `pressKey: Enter` did not dismiss the IME on Gboard/API 35 as the flow assumed, so the
    keyboard covered the bottom bar and every assert against it failed on a working app.
- **The first-run interview was invisible to every id-based automation tool.** START-01 is a
  gate ABOVE the nav graph, and `exposeTestTagsForAutomation` was applied only inside
  `AppNavHost` — so uiautomator saw `android:id/content` and nothing else on the one screen a
  fresh install always shows. Now exposed at the interview's own root, adding no layout node.

This release's receipt is the first to carry **`on-device: tokenDrift+e2eSmoke`**: 0.2.0
through 0.4.0 all shipped with both of those SKIPped.

## [0.4.0] — real food, and a week you can actually plan

### Added
- **The catalog is 59 USDA foods, and every number is real** (`docs/features/bfl-catalog.md`,
  `specs/bfl-catalog.spec.md` BFL-01..04). The seed was eight invented foods with round-number
  macros — every total the app had ever shown was arithmetic on fiction. Now: protein,
  carbohydrate, fat and energy **per 100 g** from **USDA SR Legacy**, each row carrying the
  FoodData Central id it came from.
  - **Generated, not typed.** The USDA API rate-limited, so the full SR Legacy CSV release was
    downloaded, indexed locally, and the seed rows were emitted by a script — nobody retyped
    59 × 4 values. The generator refuses to emit a food whose FDC record is missing a macro,
    so a silent zero cannot reach the catalog.
  - **Verified.** Chicken breast checked against the live USDA API (165 kcal, 31.02 P, 0 C,
    3.57 F — exact match to the local index); banana cross-checked against an independent
    USDA-derived source (89 / 1.1 / 22.8 / 0.3 — exact).
  - **One documented disagreement.** USDA's own datasets differ on oats: SR Legacy *analyses*
    protein (16.9 g/100 g), the newer Foundation Foods *calculates* it from nitrogen
    (~13.2 g). The whole catalog uses SR Legacy — one dataset means it cannot contradict
    itself, and mixing per food is how a catalog starts disagreeing with its own totals.
  - **Portions are grounded.** Where USDA publishes a portion weight, the catalog uses it —
    1 medium banana is 118 g because USDA says so. Body-for-LIFE's palm/fist heuristic is the
    LABEL ("1 palm (120 g)"), so the method's language survives and the arithmetic stays real.
  - **It ships in the app.** Seeded into Room on first run: offline, no account, no network.
- **The meal builder — 42 meals without 42 decisions** (BFL-05..08). Body-for-LIFE is six
  meals a day for seven days, and copy-forward only repeats a day you already built by hand.
  Pick a protein, pick a carb, optionally a vegetable; the total updates as you pick; choose a
  slot and the days, and it writes through the same one path everything else does. Eight
  presets (chicken/rice/broccoli, oats/egg whites, salmon/sweet potato/asparagus …) fill the
  selection and change nothing else. The method's shape — protein, carb, veg — is **reported,
  never enforced**: a mid-morning snack is a protein and a piece of fruit, and an app that
  refuses to compose it in order to be correct is one people stop opening.
- Fixed before shipping, caught by LOOKING at the render rather than by a test: the builder's
  total read **0 kcal** under three chosen foods. The cards showed each food's portion macros
  while the total recomputed from per-100 g — two sources for one number, the same divergence
  class the two goal stores were collapsed to fix. The total now sums exactly what the cards
  display.

## [0.3.0] — dated goals

### Added
- **Dated goals — a target belongs to the days it applied to** (`docs/features/dated-goals.md`,
  `specs/dated-goals.spec.md` GOAL-01..04). `WeekDay`'s contract had carried the caveat since
  the week review shipped: *targets are the CURRENT goals, goals are not yet dated*. Honest
  while history was seven days long; a defect the moment the trend reached four weeks. Cut
  your target from 2600 to 2400 and the fortnight you spent deliberately eating 2600 — and
  hitting it — re-scored overnight as a fortnight you overshot, on the very surface built to
  tell you whether the change worked.
  - `today_goal` becomes a **history**, keyed by the logical date a goal takes effect
    (schema **v12**). The goal for any day is the latest row starting on or before it; the
    earliest row reaches back to the beginning of time, so no day is ever judged against
    nothing (`0 / 0 kcal` is its own kind of lie).
  - Editing writes **today's** row and replaces it on a second edit — a target changes on a
    day, not at 09:14 — and never rewrites a day already lived. Back-dating is deliberately
    not offered: it would silently re-score days you had already read.
  - The trend resolves each day against its own goal from **one** goal-history stream, not a
    query per day: four weeks costs one read and a pure fold, not twenty-eight reads.
  - Read-side only. The ring, the macros and the goal editors still read and write "the goal",
    now defined as the goal for the current logical day — one store, one write path (PERS-01).

## [0.2.0] — the usable app

First build published as an installable APK. Everything below landed in this release: the
usability pass, the daily-journeys pass, personalization, first run, entry editing, catalog
ownership, Progress, and Settings.

### Added
- **Progress — the app grows a memory longer than a week** (`docs/features/history.md`,
  `specs/history.spec.md` HIST-01..08; usability-pass S4). The `week` route becomes
  **`progress`**: verdict → four-week trend → weight → the seven day cards. One surface, not
  two — a user should never have to know whether "how am I doing?" lives under Week or under
  History.
  - **Day cards are doors** (HIST-02). The week review could tell you Sunday was 1580 kcal and
    92 g protein and then offered *nothing*. Tapping a card opens that logical day's plan —
    the plan screen, not a read-only viewer, because the reason you open Sunday is usually to
    back-fill the meal you forgot to log.
  - **The day strip anchors on the day you are looking at** (HIST-03, PLAN-11 amended). It was
    a fixed today-relative window, and `selectedDay` derives as
    `indexOf(date).coerceAtLeast(0)` — so any day outside it silently highlighted the *first*
    chip. Building the door turned that latent bug live: 1 July's meals under a chip reading
    "Tue 21". Anchoring on the selection kills it at the source.
  - **Copy-forward disappears on a past day** (HIST-04, PLAN-20 amended). Pointed backwards it
    would duplicate a day over days already lived, overwriting real logged history, from a
    control whose label promises a planning convenience. No confirm dialog: the operation has
    no legitimate backwards meaning to confirm.
  - **A four-week trend** (HIST-05), and `GetWeekReviewUseCase` generalised to
    **`GetHistoryUseCase`** — the seven-day verdict and the trend are now two projections of
    ONE composed stream. A separate aggregate query would have been faster and would have been
    a second source of truth for numbers the day cards already state. A week with nothing
    logged reads *no data*, never a bar at zero: "you averaged 0 kcal" is a false statement
    about a week the app was not installed for.
  - **Weight** (HIST-06..08) — the outcome variable, and the only stored thing on the surface.
    One row per logical day (the primary key, so weighing twice corrects rather than appends),
    stored in kilograms whatever the display unit. With nothing recorded it says so and offers
    the control: no chart, no zero, no empty axes.
- **Settings that settle something** (`docs/features/settings.md`, `specs/settings.spec.md`
  SET-01..08; usability-pass S5). UX-04 took the tap OFF Profile's settings rows because none
  had a destination; three of them now have one, and the tap comes back **with** it.
  - **Units** (SET-02/SET-03): one metric/imperial choice, applied to weight and water. It
    deliberately does **not** touch a food's serving — `"1 bowl"`, `"200 g · 150 g"` is text
    the user chose, not a measurement, and a gram→ounce pass over it would mangle the compound
    ones and turn a label into a number the app guessed. Stated in `Units.kt`, in the spec, and
    on the screen itself, because it is the rule most likely to be "fixed" later.
  - **The supplement stack is yours** (SET-04..06): add, edit, remove, re-time. `timing` became
    the closed `SupplementTiming` set whose ordinal drives the existing `timingOrder` column —
    safe as free text only while nobody could type it, and two groups named `Morning` and
    `morning` the moment they could. Removing a supplement leaves past doses alone.
  - **The prep lead is a choice** (SET-07/SET-08), which PLAN-07's own text always said it
    would become. 0–120 minutes from a closed set (a minutes field invites `-15` and `9999`);
    zero means *at the meal time*. Changing it **re-arms every reminder at once** — a setting
    that waits for tomorrow looks broken tonight.
  - Settings ride the existing `app_state` row as typed columns (schema **v11**), not a table
    each and not a key-value bag: one observed stream, so a unit change re-renders every
    surface with no reload.
- Fixed a defect shipped in the previous slice: Today's focused container passed
  `onDeleteEntry` to its meal card but **not** `onEntryServings` — the ViewModel wired the
  stepper and the card defaulted it to a no-op, so stepping servings from Today did nothing.
  Exactly the affordance-is-a-promise class the usability pass exists to catch.

### Changed
- **The entry editor is disclosed, not always-on** (`specs/entry-editing.spec.md` ENTRY-01
  amended, **ENTRY-03** added). Six containers × two entries × (stepper + remove) turned the
  plan into a wall of controls for a screen people mostly READ. The row is now a labelled tap
  target that reveals its stepper and remove together — one row at a time, accordion-style.
  What the collapsed row never hides is the FACT: the serving label still states the multiple
  ("2 x 100 g"). The reveal is a tap on the row, never a swipe, and the expanded controls stay
  labelled 48 dp targets. New preview variant `meal-plan@editing` — without it the editor
  would be a control no gallery, golden tree or human review ever sees.
- Declared golden drift, regenerated: `meal-plan` (entry rows now nest under
  `plan_entry_<id>`), `profile` (settings rows gained chevrons and taps), and `week.json` →
  **`progress.json`** (the surface's new sections).
- **First run, entry editing, and catalog ownership** — the last three named slices
  (`docs/features/first-run.md`, `docs/features/catalog-and-editing.md`;
  `specs/first-run.spec.md` START-01/02, `specs/entry-editing.spec.md` ENTRY-01/02,
  `specs/catalog.spec.md` CAT-01..03). Schema **v10** carries all three.
  - **T1 First run** (START-01): a fresh install opens on a three-answer interview — name,
    calorie target, protein goal — writing through the same stores Profile's editors use;
    skippable, shown once, and gated ABOVE the nav graph so it can't be reached by going
    back. **First-day framing** (START-02): the app now records when it was first opened
    (`app_state`), and slots earlier than that read *before you started* — muted, excluded
    from focus, still back-fillable — instead of MISSED. A tracker shouldn't tell you off
    for meals it never saw.
  - **S2 Entry editing** (ENTRY-01/02): every logged entry row carries an in-place serving
    stepper, and removals now offer **undo** — the delete returns what it removed, so the
    restore is exact (same id, day, slot, order, servings). Log rows now store per-serving
    macros plus the multiple, which is what makes an after-the-fact serving edit possible
    at all.
  - **S3 Catalog ownership** (CAT-01..03): create, edit and delete your **own foods**
    (seeded catalog stays read-only reference data); **favourites** pin a food and lead
    every list from one SQL ordering; **recents** derive from the log's own `foodId`
    provenance. The Foods tab finally has a job: it's where your foods live.
  - Fixed two a11y defects found by `audit_a11y` on the new screens before they shipped:
    text fields labelled only by a sibling Text (invisible to screen readers → moved into
    M3's own `label` slot), and a bare 52×32 Switch (→ a toggleable row at the 48 dp floor,
    the tray's mirror pattern).
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
