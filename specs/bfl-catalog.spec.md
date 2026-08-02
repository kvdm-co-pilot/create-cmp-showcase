# Spec: bfl-catalog (real numbers, and a week you can actually plan)

> [`docs/features/bfl-catalog.md`](../docs/features/bfl-catalog.md). Every clause id is cited
> by the durable test(s) that verify it (`// SPEC: BFL-NN`).

- **BFL-01** — Given a food in the seeded catalog, Then it carries per-100 g protein,
  carbohydrate, fat and energy taken from USDA SR Legacy, its source `fdcId`, its
  Body-for-LIFE category (`PROTEIN`/`CARB`/`VEGETABLE`/`FAT`), and a default portion in grams
  with a human label; no seeded food has a zero or missing macro where USDA publishes one.
- **BFL-02** — Given any quantity of a food in grams, Then its macros are
  `per100g × grams ÷ 100`, rounded once for display — so a portion, a half portion and a
  logged entry are the same arithmetic and can never disagree.
- **BFL-03** — Given the catalog is seeded, Then it happens on first run from data compiled
  into the app: no network call, no account, and the full catalog is searchable offline.
- **BFL-04** — Given the Foods surface, Then foods are grouped by their Body-for-LIFE category
  in the method's own order (protein, carb, vegetable, fat), and each row states its portion
  and that portion's calories and protein.
- **BFL-05** — Given the meal builder, Then choosing a protein and a carb (and optionally a
  vegetable) composes one meal, its running total updates as each choice is made, and
  confirming writes every chosen food as separate entries through the one write path
  (MEAL-05) at the chosen portion.
- **BFL-06** — Given a builder selection, When it is applied to a slot across several days,
  Then the same entries are written to that slot on each chosen day — a future day receives
  them as `PLANNED` (MEAL-08) and the current day as `LOGGED`.
- **BFL-07** — Given a meal preset, Then activating it opens the builder with its foods
  already chosen and every one of them still changeable — a preset sets the selection and
  nothing else.
- **BFL-08** — Given a composed meal, Then the surface states whether it satisfies the
  method's shape — a protein and a carb present, and whether a vegetable is included — as
  information, never as a block on confirming.
