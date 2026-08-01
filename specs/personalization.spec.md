# Spec: personalization (your goals, one source of truth)

> Usability-pass S1, first half (`docs/features/personalization.md`): the seeded targets
> become editable, and the two goal stores the walkthrough flagged (F5) become one. Every
> clause id is cited by the durable test(s) that verify it (`// SPEC: PERS-NN`).

**Scope of this contract.** Goal editing (kcal, protein), name editing, and the single
goal source. The first-run interview, carbs/fat editors, and dated goal history are named
next slices — nothing here claims them.

- **PERS-01** — Given the stored goal row, Then it is the ONLY store of the daily targets:
  the ring and macros (TODAY-01/02), the week review's targets (JRN-01), and Profile's
  goal rows all read it, and there is no second stored copy anywhere in the schema; When
  the goals are updated, Then every observed surface re-targets on its existing stream
  with no reload call (RS-01) and the logged day's consumed values are untouched — a goal
  edit changes the yardstick, never the history of what was eaten.
- **PERS-02** — Given the Profile screen, When the calorie-target or protein-goal row is
  activated, Then a numeric editor opens for that goal (`profile_goal_editor`,
  `profile_goal_input`, `profile_goal_save`); saving persists the value through the one
  goal store and the screen shows the new value; a non-positive or non-numeric input is
  refused at the ViewModel — no write is attempted — and the activity row remains
  read-only (UX-04: its editor does not exist yet).
- **PERS-03** — Given the Profile screen, When the identity header is activated, Then a
  name editor opens (`profile_name_input`, `profile_name_save`); saving persists the
  name, the header and avatar initials update, and a blank name is refused at the
  ViewModel — no write is attempted.
