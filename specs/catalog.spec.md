# Spec: catalog (your foods, favourites, recents)

> Usability-pass S3 (`docs/features/catalog-and-editing.md`): the catalog stops being a
> closed world of seeded reference data and becomes YOURS. Every clause id is cited by the
> durable test(s) that verify it (`// SPEC: CAT-NN`).

**Scope of this contract.** Custom foods, favourites, and recents. Barcode scanning and
per-100g↔per-serving conversion are named next slices — nothing here claims them.

- **CAT-01** — Given the Foods tab, When "New food" is activated, Then an editor opens
  (`food_editor_name`, `food_editor_serving`, `food_editor_kcal`, macros, veg switch) and
  saving writes a catalog food flagged **custom**; a blank name, a blank serving, or a
  non-positive calorie value is refused at the ViewModel — no write is attempted. Editing
  reuses the food's id (never a twin) and preserves its favourite flag, and only a custom
  food offers the editor and the delete: the seeded catalog is reference data. Deleting a
  custom food leaves past log entries untouched — a log row snapshots its own macros.
- **CAT-02** — Given any food's detail, When its favourite control (`food_favourite`) is
  activated, Then the food is pinned or unpinned and **every list the catalog feeds — the
  Foods tab and the meal tray — orders favourites first**, then alphabetically, from the
  one query; the star reflects the stored state after the write.
- **CAT-03** — Given foods have been logged, Then the tray offers the most recently
  logged foods first (`meal_tray_recents`), newest logical day first, resolved from the
  log's own `foodId` provenance; a recent whose food has since been deleted is silently
  omitted, never rendered as a broken row.
