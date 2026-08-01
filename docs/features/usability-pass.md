# Feature brief: usability-pass — every flow, re-judged as a human's day

## What and why

Fuelled's intent (`specs/intent.md`) promises a **fast, precise tracker** for lifters:
plan and log meals and supplements against a personalized goal. The meal-plan work built
the strongest half of that promise — the structured day, aim-then-fill logging, derived
focus — and the rest of the app **drifted**: screens that shipped as scaffold exemplars
(Foods, Profile, Supplements) still behave like exemplars, and the seams between them and
the real flows are where a human gets stranded. The symptom reported in review: *"it does
not support all my needs and flows like a human would have created."*

This pass walks every flow as a person, judges each against industry UI/UX practice, and
repairs the gaps in tiers: **affordance honesty and core verbs now**, personalization and
catalog ownership as named next slices. It is deliberately one governed brief: the defect
is systemic (dead affordances + missing verbs), so the fix must be judged as one system.

## The method — what "industry best practice" means here, concretely

Findings below cite these, by shorthand:

- **N1 Visibility of system status** · **N2 Match to the real world** · **N3 User control
  & freedom (undo/exit)** · **N4 Consistency & standards** · **N5 Error prevention** ·
  **N6 Recognition over recall** · **N7 Flexibility & efficiency** · **N8 Minimalist
  design** · **N9 Error recovery** · **N10 Help** — Nielsen's ten heuristics.
- **AFF** — an affordance is a promise: everything that looks tappable must do what it
  suggests; nothing consequential is hidden behind invisible gestures only.
- **CAP** — capability parity with the category (MacroFactor, MyFitnessPal, Cronometer are
  the intent's own reference apps): log at a quantity, fix a mistake, set your goal, own
  your food list, see your history.
- **A11Y** — 48dp targets, labeled controls, no information carried by color alone.

## The human walkthrough — one user, one honest day

**Install → first open.** The app greets me as *Karel, Cutting, 2400 kcal / 180 g* — a
stranger's name and a stranger's targets (seeded, `ProfileRepositoryImpl.SEED`). There is
no way to make them mine: the goal rows on Profile accept the tap and do nothing (N1, N3,
AFF — and the intent's own word, *personalized*, is unmet). **Worse, structurally: the ring
on Today reads its targets from a second, separate goal row** (`TodayRepositoryImpl`'s
seeded DailyGoal) that merely happens to agree with Profile's numbers — the day a goal
editor lands on either source, the app disagrees with itself (single source of truth).

**Breakfast.** Today shows the focused Breakfast container; its body is the add control;
the tray opens already aimed; I tick foods; the total bar keeps a running macro count; Add
writes and returns me to the container. **This flow is excellent** — better than the
category norm (MFP's meal-picker modal). But I ate *two* scoops of oats — and the tray has
no quantity control at all. `onServingsChanged` exists, tested, in the ViewModel
(MEAL-09 promises "a serving is adjusted"); **no UI calls it** (CAP, N7 — the single most
consequential missing verb in a macro tracker).

**Mid-morning.** I mis-tapped yesterday: yogurt landed in Breakfast twice. There is no way
to remove an entry, anywhere. `DeleteLogEntryUseCase` is wired in DI, tested — reachable
from **zero screens** (N3 undo, N9, CAP: MEAL-06 is a promise with no door). A mis-tap is
permanent, in an app whose whole value is the accuracy of one number.

**Lunch — browsing Foods.** Food detail is genuinely good rigor (per-serving macros, the
proportion bar — Cronometer's bar, matched). Then its one primary button, "Log this food",
does nothing: the nav graph never passes `onLog`, so the `= {}` default ships (AFF at its
worst: the *biggest* control on the screen is a lie). The Foods tab as a whole has lost its
job: browse, tap, read, dead end.

**Supplements.** Tap-to-take works, persists, resets daily. Read-only stack is acceptable
for now (the stack is seeded sensibly) — but it must be *named* a next slice, not left
implicit.

**Evening.** The plan screen holds up: strip keeps my place (PLAN-24), missed slots mute
rather than alarm, ticks are reversible, copy-forward is where it should be. The
structured-day half of this app needed no findings — it is the exemplar of what the rest
must become.

## Findings — each mapped to the practice it violates

| id | Finding | Practice | Severity |
|----|---------|----------|----------|
| F1 | "Log this food" renders but is a no-op (`onLog` never wired) | AFF, N1 | Critical |
| F2 | No quantity control in the tray; every line is exactly 1 serving | CAP, N7, MEAL-09 gap | Critical |
| F3 | No entry deletion anywhere; mis-logs are permanent | N3, N9, CAP, MEAL-06 gap | Critical |
| F4 | Profile goal rows + settings rows tappable no-ops | AFF, N1 | High |
| F5 | Goals live in **two** unconnected sources (Profile row, DailyGoal row) | single source of truth | High (latent) |
| F6 | No first-run: seeded identity presents as the user's | N2, intent "personalized" | High |
| F7 | Catalog closed: no custom food, no favourites/recents | CAP, N6, N7 | Medium |
| F8 | No history: strip is −1..+7; streak/avg stats reference data you can't view | CAP, N1 | Medium |
| F9 | Supplements stack not editable | CAP | Medium |
| F10 | Foods tab lacks a job once tray owns logging (F1 is its last verb) | N8 | Medium |

## Decisions — built in this pass

1. **Every affordance is honest, from this commit on.** Anything rendered as tappable
   acts; anything that cannot act yet does not render as tappable. This is the pass's
   standing rule, and the one a future contributor could most plausibly "simplify" away —
   which is why it is a signed decision, not a code comment.
2. **The tray gets its quantity stepper (F2 → UX-01).** A −/count/+ stepper on each
   *checked* row, calling the existing `onServingsChanged`; below one serving is removal
   (already the VM's contract). Steppers only on checked rows: an unchecked row's job is
   selection, and eight steppers on a search list is noise (N8). Whole-serving multiples
   only for now — fractional servings (0.5×) is an open decision, deferred, because the
   line model multiplies `Int`s and half-portions deserve their own thought, not a cast.
3. **Entries get their delete, where entries render (F3 → UX-02).** Each entry row in a
   meal container (plan screen and Today's focused container — same composable) carries a
   remove control; it calls `DeleteLogEntryUseCase`; observed reads re-derive the day
   (RS-01), so totals and veg counts follow with no reload. No confirmation dialog: the
   category norm for a single list row is immediate removal (N7); an undo snackbar is the
   better guard and is deferred with the motion slice (M6) rather than blocking deletion
   on it — a wrong tap can re-add in two taps today, it cannot un-log at all.
4. **Food detail's CTA becomes real: aim here, then write (F1, F10 → UX-03).** Tapping
   "Log this food" reveals the six containers of the **current logical day** as a
   slot-picker; choosing one writes the food at one serving, `LOGGED`, through
   `AddLogEntriesUseCase` — the same single write path (TODAY-13's discipline), the button
   confirming ("Added to Lunch"). This deliberately extends the aim-then-fill model rather
   than breaking it: the picker **is** the aim, made explicit at the one surface where no
   container tap preceded the food. (Rejected: routing to the tray with the food
   preselected — a second, heavier path to the same write; rejected: removing the CTA —
   catalog-first logging is a core category flow, CAP.) Today only, one serving only:
   back-fill and planning stay the plan screen's aimed flows; quantity beyond one serving
   is the tray's job. The slot-picker states the day it writes to.
5. **Profile stops pretending (F4 → UX-04).** Goal and settings rows render as values,
   not buttons — no `clickable`, no ripple — until their editors exist. The rows stay
   (the values are true and useful); only the false promise goes. PROF-02/PROF-04 are
   amended to match (this is the one signed-spec text change in the pass).
6. **The next slices are named, ordered, and scoped here** — so "later" is a plan, not a
   shrug (see below). In particular **goals-editing is explicitly blocked on F5**: the
   first goal editor must land together with the single-goal-source unification, else the
   ring and the profile disagree the moment either is edited.

## Use-case inventory — exhaustive, per screen

Legend: ✅ works today · 🔧 built in this pass · 🗺 named next slice (ordered below) ·
✂ deliberately rejected (with why).

### Cross-cutting
| Use case | Status |
|---|---|
| See day roll over at `dayStartHour` without reload | ✅ RS-02 |
| State survives background/foreground, Doze | ✅ RS-03 |
| Write failure surfaces without destroying the rendered day | ✅ RS-04 |
| Transient read failure heals on next emission | ✅ RS-01 |
| First run: enter name, calorie target, protein goal | 🗺 S1 |
| Edit goals later; ring/macros re-target immediately | 🗺 S1 (blocked on F5 unification) |
| Choose units (metric/imperial) | 🗺 S5 |
| Dark/light preference | ✂ — dark-first is the signed brand (intent); revisit only on user demand |

### Today (dashboard)
| Use case | Status |
|---|---|
| Am I on track — ring, macros, protein focus, at a glance | ✅ TODAY-01/02 |
| Empty day is a plannable day, not an error | ✅ TODAY-04 |
| Add to the focused meal from the dashboard | ✅ TODAY-07/09 |
| Tick meal/water/supplement highlight from the dashboard | ✅ TODAY-09/10/11 |
| Remove a wrong entry from the focused container | 🔧 UX-02 |
| Adjust an entry's quantity in place | 🗺 S2 (edit = delete + re-add until then) |
| Jump into the week | ✅ TODAY-12 |
| See yesterday's summary | 🗺 S4 history |

### Plan (structured day)
| Use case | Status |
|---|---|
| See the whole day's structure, always, empty or not | ✅ PLAN-02/03 |
| Plan up to a week ahead; back-fill yesterday | ✅ PLAN-11/12 |
| Copy a built day forward | ✅ PLAN-20 |
| Tick done / undo a tick; tick an off-plan meal without fabricating food | ✅ PLAN-13/14 |
| Focus, lateness, missed — derived, honest about the clock | ✅ PLAN-15/16/19/25 |
| Adjust meal times; water and reminders follow | ✅ PLAN-05..09 |
| Remove a planned or logged entry from any container | 🔧 UX-02 |
| Reminders that survive reboot and refuse to fire stale | ✅ PLAN-07/26 |
| A free day (Body-for-LIFE's seventh day) | 🗺 S6 (decision 11's slice) |
| Reorder / rename slots | ✂ — the six-slot structure IS the method (signed meal-plan decision 1) |

### Meal tray (add-to-meal)
| Use case | Status |
|---|---|
| Arrive aimed; target stated, never asked | ✅ MEAL-10 |
| Search the catalog; multi-select; live macro total | ✅ MEAL-09, FOODS-02 idiom |
| Log more or less than one serving | 🔧 UX-01 |
| Fractional servings (0.5×) | 🗺 S2 (open decision: line model is Int-multiplied) |
| Confirm writes transactionally; close on success; stay open on failure | ✅ MEAL-05/13 |
| Empty tray cannot write | ✅ MEAL-11 |
| Favourites / recents surfaced before search | 🗺 S3 |
| Barcode scan | ✂ for now — no camera pipeline in scope; revisit with S3 |

### Foods (catalog) + Food detail
| Use case | Status |
|---|---|
| Browse / search the catalog; rigor view of one food | ✅ FOODS-01..06 |
| Log a food from its detail — the catalog-first flow | 🔧 UX-03 |
| Add a custom food; edit; per-100g vs per-serving entry | 🗺 S3 |
| Favourite a food | 🗺 S3 |
| Missing id → mapped not-found, never a crash | ✅ FOODS-07 |

### Supplements
| Use case | Status |
|---|---|
| Today's stack by timing bucket; tap-to-take; daily reset | ✅ SUPP-01..03/07 |
| Add / edit / remove a supplement; schedule its reminder | 🗺 S5 |
| See adherence over time | 🗺 S4 |

### Profile
| Use case | Status |
|---|---|
| See identity, goals, weekly stats | ✅ PROF-01..03 |
| Rows are honest about being read-only (until editors land) | 🔧 UX-04 |
| Edit goals (with single goal source) | 🗺 S1 |
| Log / track weight | 🗺 S4 |
| Account, connected apps, reminders settings | 🗺 S5 |

## Next slices — ordered, each its own brief

- **S1 Personalization** — first-run (name, targets) + goal editor + **the F5
  unification** (one goal source feeding ring, macros, and profile). First, because the
  intent's word "personalized" is still unmet and F5 is a latent divergence.
- **S2 Entry & serving editing** — edit quantity in place (tray line and logged entry),
  fractional servings decision, undo snackbar (with M6 motion).
- **S3 Catalog ownership** — custom foods, edit, favourites/recents in tray and Foods;
  re-judge the Foods tab's job (it may become "My foods").
- **S4 History** — browse past days beyond −1, weekly trends behind the stats Profile
  already shows, weight log.
- **S5 Settings that exist** — units, reminders surface, supplement stack management,
  account.
- **S6 Free day** — meal-plan decision 11's deferred slice.

## Blast radius and contracts

- `feature-brief:usability-pass` — this document (NEW).
- `feature-spec:usability-pass` — `specs/usability-pass.spec.md` (NEW): UX-01..UX-04,
  each cited by durable tests. Cross-feature clauses in their own namespace, the
  `reactive-state` precedent — the behaviors span tray, plan, Today, foods, profile.
- `feature-spec:profile` — REOPENED: PROF-02/PROF-04 amended ("tappable" → honest
  read-only rows).
- `exemplar-feature` — REOPENED: the foods file set changes (detail's CTA becomes real).
- `feature-design:meal` / `feature-design:meal-plan` — REOPENED: tray rows gain the
  stepper; container entry rows gain the remove control. Re-sign on rendered output.
- Golden trees: `meal`, `meal-plan`, `today`, `foods`, `profile` — declared drift,
  regenerated with `UPDATE_GOLDEN=1`.
- Not touched: `components` (reuses `AppIconButton`/existing vocabulary; no new common
  component), `design-system`, `architecture`, `intent`, supplements/today/meal-plan/meal
  spec texts.

```json cmp:feature
{ "touches": ["feature-spec:profile", "exemplar-feature", "feature-design:meal", "feature-design:meal-plan"] }
```

## Open decisions

- **Fractional servings** (S2): change `TrayLine.servings` to a quarter-step Int (×0.25)
  or Double? Affects stored `serving` labels. Needs its own look at the write model.
- **Undo vs confirm for delete** (S2): this pass ships immediate delete (N7); the undo
  snackbar belongs with the motion tokens slice. If real-world mis-deletes hurt sooner,
  pull it forward.
- **Foods tab's long-term job** (S3): catalog-first logging (UX-03) may be enough of a
  job; or the tab becomes "My foods" once custom foods exist. Decide on usage, not now.
