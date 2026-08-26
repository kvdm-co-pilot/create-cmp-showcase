# Spec: settings (units, the stack, the lead)

> Usability-pass S5 ([`docs/features/settings.md`](../docs/features/settings.md)): the
> settings Profile stopped pretending about (UX-04) become real. Every clause id is cited by
> the durable test(s) that verify it (`// SPEC: SET-NN`).

- **SET-01** — Given the Profile screen, Then its settings rows are live controls again
  (`profile_settings_link`) opening the Settings surface (route `settings`), which renders
  the unit system, the supplement stack and the reminder lead.
- **SET-02** — Given the unit system is changed (`settings_units_metric` /
  `settings_units_imperial`), Then it is stored in the one app-state row and every observed
  surface re-derives with no reload (RS-01); weight reads in kg or lb and water in L or fl oz
  accordingly.
- **SET-03** — Given a food's serving text (`"80 g"`, `"1 bowl"`), Then it renders **byte for
  byte as stored** under either unit system — serving text is data the user chose, never a
  measurement the app converts.
- **SET-04** — Given the Settings surface, When a supplement is added (name, dose, timing),
  Then it joins the stack in its timing group and is takeable on the current logical day like
  any other; a blank name or dose is refused before any write.
- **SET-05** — Given an existing supplement, Then it can be edited (name, dose, timing) and
  removed; removing it drops it from the stack, and past `supplement_taken` rows for it are
  left untouched — history stands (CAT-01's discipline).
- **SET-06** — Given supplement timing, Then it is one of a closed set (`SupplementTiming`),
  and the stack's display order derives from that same value — so two supplements at the same
  timing can never land in two differently-named groups.
- **SET-07** — Given the reminder prep lead is set to N minutes (`settings_lead_<n>`, N in
  0..120), Then meal reminders arm at N minutes before their slot time, N = 0 arms them at
  the slot time itself, and the value is refused outside that range.
- **SET-08** — Given the prep lead is changed, Then every armed reminder is re-armed at once
  against the new lead — the change never waits for the next day to take effect.
- **SET-09** — Given the Settings surface on a platform that can install applications, Then it
  offers exactly one entry point into the update surface (`settings_updates`). On a platform
  that cannot install (UPD-08 — iOS, desktop), the row is ABSENT rather than disabled: a
  disabled control advertises a capability the user cannot obtain, and there is nothing they
  could do to obtain it.
