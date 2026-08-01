# Spec: profile (goals, weekly stats, settings)

> Profile is the user's identity, daily goals, weekly stats, and a settings list. Data-backed.
> Every clause id is cited by a durable test (`// SPEC: PROF-NN`).

- **PROF-01** — Given the Profile screen opens, When the profile is loading, Then a loading
  indicator is shown; and when it completes the identity (name, current plan and calorie
  target) is shown, with no error.
- **PROF-02** — Given the profile is loaded, Then the daily goals are shown — calorie target,
  protein goal, and activity. The calorie and protein rows are **editable** (PERS-02 — their
  editor exists, so the tap affordance returned with it, UX-04's rule); the activity row stays
  a labeled, **read-only** value until its editor exists (usability-pass S5). Goal values are
  read from the one goal store (PERS-01), never from a second profile-owned copy.
- **PROF-03** — Given the profile is loaded, Then the weekly stats are shown: day streak,
  average protein, and current weight.
- **PROF-04** — Given the profile is loaded, Then the settings list is shown. The rows whose
  destination now exists — units & measurements, supplements, reminders — are live controls
  opening the Settings surface (`profile_settings_link`, SET-01): UX-04's rule satisfied in
  the other direction, the tap returning WITH the destination. Rows whose destination does not
  exist (connected apps, account) stay labeled, **read-only** values.
- **PROF-05** — Given the source fails, When loading completes, Then a mapped error
  (`profile_error`) with a retry control (`profile_retry`) is shown from the failure's
  `DomainError` kind; retry after the source recovers renders the profile.
- **PROF-06** — Given the Profile screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/profile.json`).
