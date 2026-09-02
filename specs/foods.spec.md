# Spec: foods (the exemplar feature)

> The reference spec — new features copy this shape. Foods is the fully-wired, Room-backed
> exemplar: a searchable catalog that goes through every architectural layer (presentation →
> domain → data). Every clause id is cited by the durable test(s) that verify it
> (`// SPEC: FOODS-NN`).

- **FOODS-01** — Given the Foods screen opens, When the catalog is being loaded, Then a
  loading indicator is shown; and when loading completes the foods are listed with their name,
  brand, serving, and per-serving macros, and no error is shown.
- **FOODS-02** — Given the catalog is loaded, When the user types a query, Then the list is
  filtered to foods whose name or brand matches (case-insensitive) — the filter runs at the
  repository/use-case, driven by the ViewModel's query state, never in the composable.
- **FOODS-03** — Given a query that matches no catalog entry, When the search completes, Then
  the empty state is shown (`foods_empty`) and neither foods nor an error are visible.
- **FOODS-04** — Given the repository fails, When loading completes, Then a human-readable
  error message is shown (`foods_error`) with a retry control (`foods_retry`) — the copy is
  mapped in presentation from the failure's `DomainError` kind, never a raw exception message;
  and when the source recovers and retry is tapped, the foods render.
- **FOODS-05** — Given foods are listed, When the user taps a food, Then the app navigates to
  that food's detail, passing the food's id.
- **FOODS-06** — Given a food detail is opened for an id, When the detail loads, Then the food
  is resolved by id through the repository and its full nutritional breakdown is shown.
- **FOODS-07** — Given a food detail is opened for an id absent from the catalog, When the
  detail loads, Then the resolution fails with `DomainError.NotFound` and the mapped
  not-found copy is shown, never a crash.
- **FOODS-08** — Given the Foods screen renders, When its structure is inspected, Then the
  screen matches its committed golden tree (`qa/golden/foods.json`) — structural change must
  be intentional and declared.

## The shared title (motion OD5)

- **FOODS-09** — Given a food row (`foods_item_<id>`) opens its detail, Then the row's title
  and the detail header's title (`food_detail_title`) are declared as ONE shared element under
  the key `food-title-<id>` (`Modifier.sharedTitle`), so the name travels from the row into
  the header on `Settle` rather than cutting; in isolation — previews, tests, no NavHost above
  — the modifier is a no-op and both render as plain text.
