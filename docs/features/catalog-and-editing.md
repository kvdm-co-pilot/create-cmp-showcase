# Feature brief: catalog & entry editing (usability-pass S2 + S3)

## What and why

Two of the three verbs the usability pass named as "the last friction in daily logging",
built together because they share one schema change: the log row now stores **per-serving
macros plus a multiple**, which is what makes both an in-place serving edit and a
provenance-based recents list possible at all.

- **S2 — change your mind where you are.** Fixing "I actually had two" meant delete and
  re-add: four taps and a trip to the tray for a number already on screen. And a delete was
  final — UX-02 shipped removal deliberately without undo, promising it here.
- **S3 — the catalog becomes yours.** A tracker whose food list you cannot add to is a
  tracker you outgrow in a week. The intent's own reference apps (MyFitnessPal, Cronometer)
  are catalog-ownership apps first.

## Decisions

1. **Log rows store the BASE serving and the multiple, never pre-multiplied totals**
   (schema v10). Pre-multiplied totals made "2 ×" unrecoverable — you cannot divide back out
   without knowing what was multiplied. One mapper renders "2 x 100 g", so the tray, the
   plan screen, and an in-place edit can never phrase it differently.
2. **The serving stepper lives on the entry row itself** (ENTRY-01) — the fix belongs where
   the mistake is visible. Stepping below one serving is a removal, with the undo bar, so
   the stepper never strands a zero-contribution row.
3. **Undo is a BAR, not a floating snackbar** (ENTRY-02). These screens are plain scrolling
   columns with no Scaffold, and an undo you must catch before it fades is worse than one
   that waits until you act. The delete RETURNS what it removed, so the restore is exact —
   same id, day, slot, order, servings — and idempotent.
4. **Only custom foods are editable** (CAT-01). The seeded catalog is reference data;
   silently rewritable reference data is data you cannot trust. Deleting a custom food is
   safe because log rows snapshot their own macros — history stands.
5. **Favourites sort in SQL, not in callers** (CAT-02): one `ORDER BY favourite DESC, name`
   keeps the Foods tab and the tray in one agreed order forever.
6. **Recents are derived from the log's own provenance** (CAT-03) — the new `foodId` column
   — not from a separate "recently used" table that would immediately need its own
   invalidation rules. A recent whose food was deleted is omitted, not rendered broken.
7. **The Foods tab finally has a job** (the usability pass's open question, closed): it is
   where YOUR foods live — create, favourite, edit.

## Blast radius

- NEW: `feature-brief:catalog-and-editing`, `feature-spec:catalog` (CAT-01..03),
  `feature-spec:entry-editing` (ENTRY-01/02).
- Schema v10: log rows gain `servings` + `foodId` and hold per-serving macros; foods gain
  `favourite` + `custom`.
- `exemplar-feature` (foods) — REOPENED: the exemplar's file set grows the editor.
- `feature-design:meal-plan` — REOPENED: entry rows gain the stepper and the undo bar.
- Goldens: `meal-plan`, `foods`, NEW `food-editor`.

```json cmp:feature
{ "touches": ["exemplar-feature", "feature-design:meal-plan"] }
```

## Open decisions

- **Fractional servings** (still open from the usability pass): the multiple is an Int.
  Half-portions want a quarter-step model and their own look at the write path.
- **Per-100g ↔ per-serving entry** in the editor: real catalogs carry both; deferred until
  someone actually types a label the current single-serving model cannot express.
- **Barcode scan**: needs a camera pipeline; revisit once custom foods have real usage.
