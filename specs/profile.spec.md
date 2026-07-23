# Spec: profile (goals, weekly stats, settings)

> Profile is the user's identity, daily goals, weekly stats, and a settings list. Data-backed.
> Every clause id is cited by a durable test (`// SPEC: PROF-NN`).

- **PROF-01** — Given the Profile screen opens, When the profile is loading, Then a loading
  indicator is shown; and when it completes the identity (name, current plan and calorie
  target) is shown, with no error.
- **PROF-02** — Given the profile is loaded, Then the daily goals are shown — calorie target,
  protein goal, and activity — each as a labeled, tappable row. (Goal editors are out of scope
  for this spec; the row is present and actionable, its destination is a later feature.)
- **PROF-03** — Given the profile is loaded, Then the weekly stats are shown: day streak,
  average protein, and current weight.
- **PROF-04** — Given the profile is loaded, Then the settings list is shown (units &
  measurements, reminders, connected apps, account), each as a tappable row. (Settings
  destinations are out of scope for this spec.)
- **PROF-05** — Given the source fails, When loading completes, Then a mapped error
  (`profile_error`) with a retry control (`profile_retry`) is shown from the failure's
  `DomainError` kind; retry after the source recovers renders the profile.
- **PROF-06** — Given the Profile screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/profile.json`).
