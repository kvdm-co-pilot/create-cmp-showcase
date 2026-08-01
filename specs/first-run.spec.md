# Spec: first-run (the app's first words, and its first day)

> Usability-pass T1 / daily-journeys J3 (`docs/features/first-run.md`): a new install stops
> greeting its user as a stranger, and stops telling them off for meals it never saw. Every
> clause id is cited by the durable test(s) that verify it (`// SPEC: START-NN`).

- **START-01** — Given a fresh install, When the app opens, Then the first-run interview is
  shown instead of the shell (`onboarding_screen`) asking name, calorie target and protein
  goal; saving writes them through the SAME stores the Profile editors use (PERS-01/PERS-02/
  PERS-03) and the shell replaces the interview in place, with no relaunch; skipping
  (`onboarding_skip`) keeps the seeded defaults; a blank name or a non-positive number
  reaches no write. The interview is shown once — a returning install goes straight to the
  app — and it is never a nav destination, so it cannot be reached by going back.
- **START-02** — Given the logical day this install was FIRST opened on, Then any meal slot
  whose time is earlier than that first-open instant renders as **before you started** —
  muted, excluded from missed-ness and from focus — rather than MISSED, and it stays fully
  back-fillable (its add control and its done-tick are live, PLAN-19's rule). Every later
  day is judged normally: this is a statement about the app's own arrival, not a permanent
  amnesty.
