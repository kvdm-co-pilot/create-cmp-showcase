# Feature brief — The Body-for-LIFE catalog and the week builder

```json cmp:feature
{ "touches": ["feature-spec:foods", "feature-spec:meal", "feature-spec:catalog", "exemplar-feature", "feature-design:meal"] }
```

> **Status:** proposed. Contract: [`specs/bfl-catalog.spec.md`](../../specs/bfl-catalog.spec.md).

## What this is for

Two things, and the second is the point:

1. **Numbers you can trust.** The seeded catalog was eight invented foods with round-number
   macros. Every total the app has ever shown was arithmetic on fiction.
2. **A week planned in a few taps.** Body-for-LIFE is six meals a day, seven days — 42 meals.
   Nobody builds that food-by-food. The method itself says how: *one portion of protein, one
   portion of carbohydrate, and vegetables with two of them.* If the app knows that grammar,
   planning a week is picking pairs, not filling 42 slots.

## The data

**59 foods, every one from USDA SR Legacy, each carrying its FoodData Central ID.** Values are
per 100 g — protein, carbohydrate, fat and energy — taken from the published dataset rather
than typed from a website.

**How they were obtained.** The USDA API rate-limited at 8 pages, so the full SR Legacy CSV
release was downloaded and indexed locally, and the seed is **generated from that index by a
script** — no human retyped a number, which is the only way 59 × 4 values are right. The
generator refuses to emit a food whose FDC record is missing a macro, so a silent zero cannot
reach the catalog.

**Verification.** Chicken breast checked against the live USDA API (165 kcal, 31.02 P, 0 C,
3.57 F — exact match to the local index, confirming the extraction). Banana cross-checked
against an independent USDA-derived site (89 / 1.1 / 22.8 / 0.3 — exact). Values are the
published dataset's own, so the accuracy bar is USDA's, not an estimate of ours.

**One honest discrepancy.** USDA's two datasets disagree about oats: SR Legacy (analysed
protein) says 16.9 g per 100 g; the newer Foundation Foods (protein *calculated* from
nitrogen) says ~13.2 g. That is a 28% gap on a staple. **The whole catalog uses SR Legacy** —
one dataset means the catalog cannot disagree with itself, and mixing per item is how a food
list starts contradicting its own totals. Documented so the next person meets a decision, not
a bug.

**Where the data did NOT come from.** bodybuilding.com and similar sites carry
user-contributed macro tables that neither cite a lab nor agree with each other. They are
excellent for *what lifters actually eat* — and the meal presets below are drawn from exactly
that — but no number in this catalog comes from one.

## Decisions

**D1 — Per 100 g is the stored truth; a portion is a convenience.** Every food stores
per-100 g macros and a default portion in grams. An entry's macros are always
`per100g × grams ÷ 100`. *Rejected:* storing per-portion macros, which is what made the old
catalog unscaleable — "half a chicken breast" had no answer that wasn't a second row.

**D2 — Portions are grounded, not invented.** Where USDA publishes a portion weight
(`food_portion.csv`) the catalog uses it — 1 medium banana is 118 g because USDA says so, not
because it seemed about right. Body-for-LIFE's palm/fist heuristic is rendered as the LABEL
("1 palm (120 g)"), so the method's language survives while the arithmetic stays real.

**D3 — Foods carry their Body-for-LIFE role.** `PROTEIN · CARB · VEGETABLE · FAT`. This is
the method's own vocabulary and it is what makes a builder possible: "a meal" is a protein
plus a carb, and the app can only offer that if it knows which is which. *Rejected:* free-text
tags — the builder would have to guess, and a guess that puts rice in the protein column is
worse than no builder.

**D4 — The builder composes, it does not decide.** Pick a protein, pick a carb, optionally a
vegetable; the app shows the resulting macros as you pick and writes them through the SAME
`AddLogEntriesUseCase` every other surface writes through. It never auto-selects foods to hit
a macro target — a tracker that plans your food for you is a different product, and one whose
suggestions you cannot audit.

**D5 — Presets are the fast path, not a separate mechanism.** The classic combinations
(chicken + brown rice + broccoli; oats + whey; salmon + sweet potato + asparagus) are just
pre-filled builder selections. Tapping one lands you in the builder with the picks made, free
to change any of them — so a preset can never do something the builder cannot.

**D6 — Apply to many days at once.** The builder writes to a chosen slot across a chosen set
of days. This is the whole reason the feature exists: 42 meals is not a planning exercise
anyone completes by hand, and copy-forward (PLAN-20) only repeats a day you already built.

**D7 — The seed ships IN the app.** All 59 foods are seeded into Room on first run — offline,
no account, no download, no network at any point. The catalog is part of the binary.

## Rejected outright

- **A remote food API.** It makes the app require a network to log breakfast, and it puts
  someone else's uptime between a user and their own diary.
- **Barcode scanning.** Genuinely useful and an entirely separate slice (camera permission,
  a branded-foods dataset, an offline story for when it fails).
- **Auto-generating a week to hit a calorie target.** See D4. The app states what your
  choices add up to; it does not choose for you.

## Open decisions

None.
