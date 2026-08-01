# Feature brief — Settings that exist (usability-pass S5)

```json cmp:feature
{ "touches": ["feature-spec:meal-plan", "feature-spec:supplements", "feature-spec:profile"] }
```

> **Status:** proposed — decisions below are signed BEFORE code (`node qa/approve.mjs
> feature-brief:settings`). Contract: [`specs/settings.spec.md`](../../specs/settings.spec.md).

## The journey this exists for

The usability pass (UX-04) made Profile's settings rows **stop pretending**: they lost their
chevrons and their `clickable` because tapping them did nothing. That was the honest fix, and
it left the app with a Settings section that visibly settles nothing. This slice is the other
half of that promise.

Three moments:

1. **"I think in pounds."** The app states weight in kilograms because that is what it was
   written in. Nothing about the method requires metric.
2. **"I take magnesium at night now."** The supplement stack is *seed data*. There is no way
   to add one, correct a dose, drop one you stopped taking, or move one to a different time
   of day. A stack you cannot edit is somebody else's stack.
3. **"Thirty minutes isn't enough — I need an hour to cook dinner."** Prep reminders fire at
   a hard-coded `PREP_LEAD_MINUTES = 30`. PLAN-07 already says, in the contract, that the lead
   "is a named constant **until the reminders settings surface makes it a choice**". This is
   that surface.

## Decisions

**D1 — One Settings route, reached from Profile.** `settings`, opened from the Profile rows
that UX-04 made inert. Those rows become live again — and this time the promise is kept.

**D2 — Units are a system (metric/imperial), not a per-value choice.** One control, applied
to every computed measurement. *Rejected:* independent per-unit pickers (weight in lb, water
in litres); it is four times the surface to serve a preference nobody holds that way.

**D3 — Units apply to what the app COMPUTES, never to what you typed.** Weight (kg/lb) and
water (L/fl oz) convert. A food's serving is free text — `"80 g"`, `"1 bowl"`, `"1 scoop"` —
and the app **must not** attempt to convert it. This is the decision most likely to be
"simplified" later by someone adding a gram→ounce pass over serving strings: it would render
`"1 bowl"` untouched beside `"2.8 oz"`, silently mangle `"200 g · 150 g"`, and turn a label
the user wrote into a number the app guessed. Serving text is **data, not a measurement**.

**D4 — Energy stays kcal, deliberately.** Not an oversight. The seeded catalog, the calorie
target, the goal editor, the ring and every day card are kcal; converting *display* to kJ
while the goal editor still accepts kcal would put two units on one screen, and converting
*everything* is a slice of its own with a stored-goal migration in it. Stated here so the
omission reads as a decision instead of a gap.

**D5 — Supplement timing becomes a closed set.** Today `timing` is a free-text column, which
was safe only while the stack was seed data nobody could edit. The moment users type it,
`"Morning"` and `"morning"` become two groups that render as two buckets. A
`SupplementTiming` enum (Morning · Pre-workout · Post-workout · Evening) with its ordinal
driving the existing `timingOrder` column — so display order and grouping can never disagree,
because there is only one fact.

**D6 — Deleting a supplement leaves history alone.** Past `supplement_taken` rows stand,
exactly as deleting a custom food leaves past log entries alone (CAT-01). You stopped taking
it; you did not stop having taken it.

**D7 — The prep lead is stored, bounded, and re-arms on change.** 0–120 minutes, chosen from
a small set rather than typed (a free-text minutes field invites `-15` and `9999`). Zero is
legitimate and means *at the meal time* — the pre-journeys behaviour, still available to
anyone who wants it. Changing it **re-arms every reminder immediately**; a setting that only
takes effect tomorrow is a setting that looks broken tonight.

**D8 — Settings live in the existing `app_state` row.** Not a table per preference and not a
key-value bag. `app_state` already holds the app's own facts (onboarding, first-open); unit
system and prep lead are the same kind of fact and belong beside them. One row, one read,
one observed stream — so a unit change re-renders every surface with no reload (RS-01).

## Rejected outright

- **A generic key-value settings store.** It defers every typing decision to runtime and
  turns every read into a parse-and-hope. Four typed columns is less code and cannot hold a
  malformed value.
- **Notification channel / quiet-hours configuration.** Real, and platform-specific enough to
  deserve its own slice rather than a checkbox here.
- **Export / backup.** Frequently asked for, genuinely important, and not a *setting* — it is
  a feature with a file format, a schema version and a restore path.

## Open decisions

None. The human's signature on this brief closes all eight.
