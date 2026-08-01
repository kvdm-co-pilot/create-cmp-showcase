# Spec: profile (goals, weekly stats, settings)

> Profile is the user's identity, daily goals, weekly stats, and a settings list. Data-backed.
> Every clause id is cited by a durable test (`// SPEC: PROF-NN`).

- **PROF-01** — Given the Profile screen opens, When the profile is loading, Then a loading
  indicator is shown; and when it completes the identity (name, current plan and calorie
  target) is shown, with no error.
- **PROF-02** — Given the profile is loaded, Then the daily goals are shown — calorie target,
  protein goal, and activity — each as a labeled, **read-only** row (UX-04: no tap affordance
  until the goal editor exists; a row that accepts a tap and does nothing is a broken promise,
  not a placeholder). The editor is the personalization slice (usability-pass S1), which must
  land together with the single-goal-source unification.
- **PROF-03** — Given the profile is loaded, Then the weekly stats are shown: day streak,
  average protein, and current weight.
- **PROF-04** — Given the profile is loaded, Then the settings list is shown (units &
  measurements, reminders, connected apps, account), each as a labeled, **read-only** row
  (UX-04 — destinations are the settings slice, usability-pass S5; the rows regain their tap
  when their destinations exist).
- **PROF-05** — Given the source fails, When loading completes, Then a mapped error
  (`profile_error`) with a retry control (`profile_retry`) is shown from the failure's
  `DomainError` kind; retry after the source recovers renders the profile.
- **PROF-06** — Given the Profile screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/profile.json`).
