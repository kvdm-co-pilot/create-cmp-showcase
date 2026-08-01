# Spec: entry-editing (change your mind, in place)

> Usability-pass S2 (`docs/features/catalog-and-editing.md`): the log becomes correctable
> where it is read. Every clause id is cited by the durable test(s) that verify it
> (`// SPEC: ENTRY-NN`).

- **ENTRY-01** — Given a logged entry rendered in a meal container — on the plan screen or
  in Today's focused container — Then its row carries a serving stepper
  (`plan/today_entry_minus_<id>`, `_entry_servings_<id>`, `_entry_plus_<id>`); stepping it
  rewrites that entry's serving multiple through the one write path, its calories, protein
  and the day's totals re-derive through the observed read (RS-01) with no reload, and the
  row's serving label states the multiple ("2 x 100 g"). Stepping below one serving is a
  removal (ENTRY-02), never a zero-serving row. The stored row keeps PER-SERVING macros
  and the multiple separately, so the multiple stays editable for the life of the entry.
- **ENTRY-02** — Given an entry is removed (UX-02), Then the surface offers an undo
  (`plan/today_undo`, `_undo_action`) naming what was removed, and activating it restores
  that entry exactly — same id, day, slot, order and servings — through the one write
  path; the delete itself returns what it removed, so the undo never re-reads a row that
  no longer exists.
