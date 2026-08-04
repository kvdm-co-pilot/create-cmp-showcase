# Feature brief: supplement schedules

**Spec:** [`specs/supplements.spec.md`](../../specs/supplements.spec.md) — SUPP-08..SUPP-13
(SUPP-01..07 are the daily stack this grows out of).

## The ask

> "I use test replacement twice a week, Monday and Thursday, and another pen every other day.
> I want to add them with reminders under supplements. These are not daily. It requires a
> reminder the night before, as well as 30 min before and on the time."

## The problem

The stack was daily by construction. `Supplement` had a `timing` — *when in the day* — and
nothing at all about *which days*, because when it was written the stack was seed data nobody
could edit and every row was a daily vitamin.

Injection protocols are not daily, and the failure mode is not cosmetic. A Monday-and-Thursday
dose sitting untaken in Tuesday's Morning bucket is the app **claiming a missed dose that was
never due** — and the summary counting it in the denominator makes every Tuesday read as a
failure. For a medication schedule that is worse than not having the feature.

## Decisions

**D1 — Schedule is a closed set, separate from timing.** Three shapes: daily, fixed weekdays,
every N days from an anchor. Separate from `timing` because they answer different questions —
folding them together would make a Monday-only evening dose unrepresentable without inventing
a timing bucket per weekday.

**D2 — Due-ness is derived, never stored.** No due-today column, no nightly job. The same
discipline as the meal grid coming from the enum (PLAN-02) and the logical day being
re-derived (MEAL-02): nothing stored is nothing that can be stale after a week away.

**D3 — A missed dose does not move the cadence.** Skipping Tuesday on an every-2-days pen
leaves Thursday due. *Considered and rejected:* restarting the cycle from the last dose
actually taken — it matches some protocols, but it makes due-ness depend on stored history
that can be edited, deleted or absent, and a schedule that cannot be re-derived from its own
definition drifts. **Open for Karel:** if the real protocol restarts from the actual dose,
this flips, and the anchor becomes a write rather than a constant.

**D4 — Off-day rows stay visible, dated, and untappable.** "Did I take it, or is it not a dose
day?" is the anxiety a Mon/Thu injection actually creates. A date answers it; hiding the row
does not. No take control, because an off-schedule dose is a decision — it belongs in the
editor where the schedule can be corrected, not one mis-tap away on the morning screen.
**Open for Karel:** if doses genuinely shift (travel, a missed Thursday taken Friday), that
needs a sanctioned path; today the answer is "edit the schedule".

**D5 — The summary counts only what is due today.** "2 of 5 due today taken". An off-day pen
must never read as a miss.

**D6 — A daily row is unchanged.** No caption, no extra control, byte-identical rendering. The
feature costs the common case nothing — which is what makes it safe to ship into a stack most
of whose rows are ordinary vitamins.

**D7 — Three reminder rungs, independently switchable.** A single alarm at the moment of the
dose fails for anything you cannot do instantly. Night before / 30 minutes / at the time, each
answering a different question: *plan for it*, *get ready*, *now*.

**D8 — Night-before rides the plan-tomorrow evening.** Same derived moment as NOTIF-04 —
45 minutes after the evening snack, never later than 22:00. *Considered and rejected:* its own
time setting, which would be an eighth clock to keep in step and would put two notifications
minutes apart on the same evening. Not offered at all on a daily schedule: "tomorrow is
creatine day" is noise.

**D9 — The dose name is not in the notification copy.** A lock screen is readable by whoever
is holding the phone. "Dose due — 08:00 · Open Fuelled to see which." The app knows; so does
the user.

**D10 — Half a reminder is stored as none.** A time with no rungs, or rungs with no time, is
normalised away at the one write — otherwise the row says "reminds 08:00" and nothing ever
fires.

## Not built

Dose history beyond the existing per-day taken table, titration schedules, supply/refill
tracking, "skip this one" as a first-class action.
