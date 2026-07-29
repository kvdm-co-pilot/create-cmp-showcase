# Spec: supplements (today's stack, tap to take)

> Supplements is the day's supplement stack grouped by timing, with tap-to-take tracking that
> persists. Data-backed. Every clause id is cited by a durable test (`// SPEC: SUPP-NN`).

- **SUPP-01** — Given the Supplements screen opens, When the stack is loading, Then a loading
  indicator is shown; and when it completes the supplements are grouped by timing (e.g.
  Morning, Pre-workout, Evening) in a stable order, each showing name and dose, with no error.
- **SUPP-02** — Given the stack is loaded, Then a summary shows the count taken of the total
  and a progress bar of taken ÷ total.
- **SUPP-03** — Given a supplement is shown, When the user taps its take control, Then its
  taken state toggles **for the current logical day**, the summary count and progress bar
  update accordingly, and the new state persists across a reload of the screen.
- **SUPP-07** — Given a supplement was taken, When the logical day rolls over, Then it reads
  untaken again and the summary count starts from zero — a supplement stack is a **daily**
  routine, so "taken" is a fact about a day and is stored per day, exactly as water ticks are
  (PLAN-10). It follows that a **fresh install claims nothing taken**: seed data seeds the
  stack, never a dose the user did not swallow.
- **SUPP-04** — Given the stack is empty, When it loads, Then the empty state
  (`supplements_empty`) is shown and no error.
- **SUPP-05** — Given the source fails, When loading completes, Then a mapped error
  (`supplements_error`) is shown from the failure's `DomainError` kind, **and no retry
  control** — the stack is observed, so when the source recovers the next emission renders it
  with nothing tapped. The retry this clause used to promise was wired to a no-op: a button
  that looked like the way out of an error and did nothing (TODAY-05 dropped its own for the
  same reason).
- **SUPP-06** — Given the Supplements screen renders, When its structure is inspected, Then it
  matches its committed golden tree (`qa/golden/supplements.json`).
