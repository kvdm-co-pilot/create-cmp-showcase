# Spec: home (the exemplar feature)

> The reference spec — new features copy this shape. Every clause id is cited by the durable
> test(s) that verify it (`// SPEC: HOME-NN`).

- **HOME-01** — Given the Home screen opens, When items are being loaded, Then a loading
  indicator is shown and no items are visible.
- **HOME-02** — Given the repository returns items, When loading completes, Then the items are
  listed with their title and subtitle, and no error is shown.
- **HOME-03** — Given the repository fails, When loading completes, Then a human-readable
  error message is shown (`home_error`) and no items are visible.
- **HOME-04** — Given a load has failed, When the data source recovers and the user triggers a
  reload, Then the error clears and the items render.
- **HOME-05** — Given items are listed, When the user taps an item, Then the app navigates to
  that item's detail.
- **HOME-06** — Given the Home screen renders, When its structure is inspected, Then the
  screen matches its committed golden tree (`qa/golden/home.json`) — structural regressions
  are intentional, declared changes only.
