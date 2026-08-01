# Spec: entry-editing (change your mind, in place)

> Usability-pass S2 (`docs/features/catalog-and-editing.md`): the log becomes correctable
> where it is read. Every clause id is cited by the durable test(s) that verify it
> (`// SPEC: ENTRY-NN`).

- **ENTRY-01** — Given a logged entry rendered in a meal container — on the plan screen or
  in Today's focused container — Then its row is itself the editor's affordance
  (`plan/today_entry_<id>`, labelled for its action), and tapping it reveals a serving
  stepper (`plan/today_entry_minus_<id>`, `_entry_servings_<id>`, `_entry_plus_<id>`);
  stepping it rewrites that entry's serving multiple through the one write path, its
  calories, protein and the day's totals re-derive through the observed read (RS-01) with no
  reload. Stepping below one serving is a removal (ENTRY-02), never a zero-serving row. The
  stored row keeps PER-SERVING macros and the multiple separately, so the multiple stays
  editable for the life of the entry.
- **ENTRY-03** — Given a serving multiple greater than one, Then the COLLAPSED row states it
  in its serving label ("2 x 100 g") without the stepper being open — progressive disclosure
  hides the control, never the fact. A day of six containers is read far more often than it
  is corrected, so the reading state is the default and the editing state is the one you ask
  for; the reveal is a labelled tap target, never a hidden swipe.
- **ENTRY-02** — Given an entry is removed (UX-02), Then the surface offers an undo
  (`plan/today_undo`, `_undo_action`) naming what was removed, and activating it restores
  that entry exactly — same id, day, slot, order and servings — through the one write
  path; the delete itself returns what it removed, so the undo never re-reads a row that
  no longer exists.
