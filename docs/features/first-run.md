# Feature brief: first-run — the app's first words (usability-pass T1, journeys J3)

## What and why

Two defects with one root: **the app had no concept of its own arrival.** A fresh install
opened onto someone else's dashboard (S1 gave the numbers an editor but never asked), and
a first day greeted its user with MISSED tags for meals eaten before the app existed — the
journeys walkthrough graded J3 a B− for exactly this and named the fix T1.

## Decisions

1. **A three-answer interview, once.** Name, calorie target, protein goal — nothing else.
   Every other setting has a real default; an interview that asks for everything is one
   people abandon. It writes through the SAME use cases Profile's editors use, so there is
   no onboarding-shaped second write path to keep in step.
2. **Skippable, honestly.** The seeded targets are usable, so "Skip for now" is a real
   answer rather than a trap door. Blank/non-positive answers are refused by the same
   guards, and skipping still marks the interview done — it never asks twice.
3. **The gate lives ABOVE the nav graph, not in it.** Onboarding is not somewhere you can
   navigate back to. It is observed, so finishing swaps the shell in place; the pre-read
   frame renders nothing rather than flashing someone else's dashboard.
4. **The app records when it arrived** (`app_state.startedAtEpochMs`, schema v10, stamped
   on first read — the only moment it could be captured). Slots before that instant on the
   first day read *before you started*: not missed, excluded from focus, still back-fillable.
   Every later day is judged normally — this is a statement about the app's arrival, not a
   permanent amnesty.

## Blast radius

- NEW: `feature-brief:first-run`, `feature-spec:first-run` (START-01/START-02).
- `feature-spec:meal-plan` — the slot-state vocabulary gains BEFORE_START (PLAN-19 unchanged
  in substance: a missed slot is still routine and back-fillable; this is a state that sits
  above it).
- Schema v10 (shared with S2/S3): `app_state` table.
- Goldens: `meal-plan` (a new slot state in the card's when), NEW `onboarding`.

```json cmp:feature
{ "touches": ["feature-spec:meal-plan"] }
```

## Open decisions

- **Re-running the interview** (S5 settings): useful after a long lapse; deliberately not a
  control today, because a re-openable "first run" is a settings screen wearing a costume.
