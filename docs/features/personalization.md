# Feature brief: personalization — the app becomes yours (usability-pass S1, first half)

## What and why

Every surface still speaks for a seeded stranger: "Karel, Cutting, 2400 kcal / 180 g".
The intent brief promised a **personalized** goal; the usability pass flagged the latent
divergence behind it (**F5**: two goal stores — the Profile row and the Today goal row —
agreeing only because their seed constants match). This slice makes the numbers yours and
makes them ONE.

## Decisions

1. **The goal row the ring already observes becomes the only goal store.** Targets
   (kcal, protein, carbs, fat) live in the `today_goal` row — already streamed to the
   ring, the macros, and the week review. The Profile row **drops its duplicate columns**
   (schema v9, destructive dev migration — the established pattern) and Profile *reads*
   goals from the goal row. Editing either surface is now impossible to desynchronize,
   because there is no second copy to desynchronize. (Rejected: profile row as source —
   the ring's observed path already flows from the goal row; moving it would churn the
   whole Today read for no user-visible gain.)
2. **Calorie target and protein goal are editable from Profile** — tap the row, get a
   numeric editor, save; the ring re-targets on the same observed stream with no reload
   (RS-01 does the work). Non-positive input is refused at the ViewModel — a zero-target
   ring is a division no screen should meet. Carbs/fat targets keep their stored values;
   their editors ride with the settings slice (S5) — protein and calories are the two
   numbers this audience actually tunes.
3. **Your name is yours** — tap the identity header, rename; the avatar initials follow.
   The seeded starter profile remains the day-zero default; the full first-run interview
   stays T1/S1-second-half (it needs its own moment, not a dialog).
4. **UX-04 is satisfied in its intended direction**: the goal rows regain their tap
   *because the editor now exists*. The activity row and settings rows stay read-only —
   their destinations still don't exist.

## Blast radius

- `feature-brief:personalization` + `feature-spec:personalization` (NEW, PERS-01..03).
- `feature-spec:profile` — already reopened (usability-pass): PROF-02 amended again
  (calorie/protein rows editable; activity read-only).
- Schema v9: `profile` loses `calorieTarget`/`proteinGoalG`.
- Golden: `profile` (rows regain affordance) — declared, regenerated.
- Not touched: `components`, `design-system`, today/meal/meal-plan/week spec texts.

```json cmp:feature
{ "touches": ["feature-spec:profile"] }
```

## Open decisions

- **Dated goals** (S4/T3 territory): editing a goal today rewrites what the week review
  says about last Tuesday (targets are current goals — JRN-01 states it). Goal history
  versioning is deliberately deferred until trends exist to need it.
- **First-run interview** (T1 second half): name + targets asked once on first open.
