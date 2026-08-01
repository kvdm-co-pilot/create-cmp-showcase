# Spec: history (the look back — days, trend, weight)

> Usability-pass S4 ([`docs/features/history.md`](../docs/features/history.md)): the app
> grows a memory longer than a week and an outcome variable. Every clause id is cited by the
> durable test(s) that verify it (`// SPEC: HIST-NN`).

- **HIST-01** — Given the Progress surface (route `week`), Then it renders, in order: the
  seven-day verdict (JRN-01), a **four-week trend**, the **weight** section, and the seven
  day cards; and every number in it derives from the SAME composed history stream the day
  cards read — never a second aggregate query.
- **HIST-02** — Given a day card on the Progress surface, Then it is a labelled control
  (`week_day_<iso-date>`) whose activation opens that logical day's plan (`plan/{date}`),
  and the opened screen shows that day's own entries.
- **HIST-03** — Given the plan screen opened on any logical day, Then its day strip spans
  that day − 1 through that day + 7 and the selected chip is that day — so a day outside the
  current week's window can never render with another day's chip highlighted.
- **HIST-04** — Given the plan screen opened on a logical day BEFORE the current one, Then
  the copy-forward control is absent; given the current day or a future one, it is present.
- **HIST-05** — Given the four-week trend, Then each week shows its average consumed
  calories against target, its protein days hit, and its meals kept; a week with nothing
  logged renders as *no data* and never as a zero-valued bar.
- **HIST-06** — Given a weight is recorded for a logical day, Then it is stored through the
  one write path, a second recording for the SAME logical day replaces it rather than
  appending, and the observed read re-derives the section with no reload (RS-01).
- **HIST-07** — Given no weight has ever been recorded, Then the weight section states that
  and offers the control to add one — it renders no chart, no zero, and no empty axes.
- **HIST-08** — Given at least two weights in the window, Then the section states the latest
  and the signed change across the window ("−1.4 kg in 4 weeks"); given exactly one, it
  states the latest and makes no claim about change.
