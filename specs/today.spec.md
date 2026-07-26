# Spec: today (the daily macro dashboard)

> Today is the home dashboard: the day's calories and macros against goal, plus the day's
> food log grouped by meal. Data-backed, stateless-over-a-model. Every clause id is cited by
> the durable test(s) that verify it (`// SPEC: TODAY-NN`).

- **TODAY-01** — Given the Today screen opens, When the day's summary is loading, Then a
  loading indicator is shown; and when it completes the screen shows the **logical day's**
  date (derived from `dayStartHour` — MEAL-01, so a 00:30 entry still belongs to the evening
  before), the calorie ring (consumed vs. target), and the calories remaining, with no error.
- **TODAY-02** — Given the day's summary is loaded, Then each tracked macro (protein, carbs,
  fat) is shown as a progress bar of current vs. target, and protein is surfaced as the focus
  with its grams-remaining-to-goal — or a goal-met state when current ≥ target.
- **TODAY-03** — Given the day has logged entries, Then the log is grouped by **meal slot in
  slot order** (MEAL-03); each meal shows its entries (name, serving, calories, protein) and
  the meal's total calories, and the day's consumed total equals the sum of the calories of
  its **`LOGGED`** entries — a `PLANNED` entry (MEAL-08) is never counted as consumed.
- **TODAY-04** — Given the day has no logged entries, When the summary loads, Then the empty
  state (`today_empty`) is shown — the ring reads the full target as remaining — and no error.
- **TODAY-05** — Given the source fails, When loading completes, Then a mapped error
  (`today_error`) with a retry control (`today_retry`) is shown from the failure's
  `DomainError` kind, never a raw exception message; retry after the source recovers renders
  the summary.
- **TODAY-06** — Given the Today screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/today.json`) — structural change must be
  intentional and declared.
