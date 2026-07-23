# Intent brief

> The root artifact everything else traces to (GENESIS-FLOW-DESIGN.md §0, conversation 0 of
> the genesis walk). This is prose, not a clause spec — no `// SPEC:` tags apply here, and
> `specCoverage` never scans this file (it only scans `specs/*.spec.md`). The `cmp-new`
> interview fills in the sections below before the design-language, architecture, components,
> and exemplar-feature conversations begin; each of those is expressed in the vocabulary this
> brief establishes.

## Purpose

Fuelled helps serious gym-goers hit their daily nutrition targets — calories and, above all,
protein — by planning and logging meals and supplements against a personalized goal. The food
catalog and its nutritional values live in a local, on-device database so logging is instant
and works offline; the day's intake is always shown as **consumed vs. recommended**, with
protein-vs-goal kept front and centre.

## Audience

Committed lifters who train seriously and already think in macros — the people for whom
"did I hit my protein today?" is a daily question, not a passing curiosity. Fuelled assumes
that literacy: it is a fast, precise tracker for people who know what a gram of protein costs
them, not a hand-holding calorie app for casual dieters.

## Platforms

Both Android and iOS from one Compose Multiplatform codebase. No platform-exclusive features
at launch; the local food database and daily tracking behave identically on both. Supplement
reminders use the platform notification path (FCM) on each.

## Brand feel

**Strong · precise · energizing.** Dark-first, high-contrast, data-forward, with a single
energetic accent used sparingly for progress and calls to action. It should read as confident
and athletic — closer to a performance instrument than a clinical health chart — without
tipping into loud or gimmicky. This seeds conversation 1 (design language); the token
candidates react to these three words.

## Reference apps

- **MacroFactor** — the bar for data-forward macro tracking and clean, legible progress UI;
  the app Fuelled is most judged against.
- **MyFitnessPal** — the reference for food-diary structure and database-backed logging.
- **Cronometer** — for nutritional rigor in the food/detail view.
- **Zero** — for minimal, dark, ring-based progress presentation.

## First screens

1. **Today** — the hero. Calories consumed vs. recommended (ring), protein vs. goal
   (prominent bar), remaining carbs/fat, and today's logged entries. Answers "am I on track
   right now?" at a glance.
2. **Foods** — the searchable local food catalog → **Food detail** with per-serving macros;
   tap to log. This is the app's first real feature and the exemplar every later feature
   clones from.
3. **Supplements** — today's supplement schedule with reminders.
4. **Profile** — daily goals (calorie target, protein goal), account, and settings.

## Glossary

- **Food** — a catalog entry with nutritional values per serving (kcal, protein, carbs, fat).
- **Serving** — a portion of a Food with its own gram weight and scaled macros.
- **Entry** — a Food logged at a quantity, at a time, on a given day; entries sum into the
  day's totals.
- **Meal** — a grouping of entries within a day (Breakfast · Lunch · Dinner · Snack).
- **Macro** — a tracked macronutrient: protein, carbs, or fat.
- **Goal** — the user's daily targets: a calorie target and a protein goal.
- **Supplement** — a planned intake (e.g. creatine, whey) with a schedule and reminder.
