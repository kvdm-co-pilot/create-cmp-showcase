# Spec: notifications (the permission ask, the plan-tomorrow nudge)

> Brief: [`docs/features/notifications.md`](../docs/features/notifications.md). The meal and
> water reminders themselves are meal-plan contract — PLAN-07 (meals at the prep lead),
> PLAN-08/09 (water at midpoints), PLAN-26 (stale deliveries dropped) — and are unchanged
> here. These clauses cover what that contract left undelivered (the permission is checked
> everywhere and requested nowhere) and the one net-new behavior: the end-of-day nudge when
> tomorrow is unplanned. Every clause id is cited by the durable test(s) that verify it
> (`// SPEC: NOTIF-NN`).

- **NOTIF-01** — Given an onboarded install where notifications are not allowed and the app
  has never asked, When Today is next opened, Then the system notification permission is
  requested, and the ask is recorded on the app-state row — at most one ask, ever. A later
  Today open on a still-denied install asks nothing: denied means denied, and the app never
  re-prompts on its own (brief D2).
- **NOTIF-02** — Given the permission is granted at the prompt, Then reminders re-arm in the
  same act, so the day's schedule is live from the moment of the grant rather than from the
  next app open.
- **NOTIF-03** — Given notifications are denied, Then the times sheet's reminders-off notice
  (`meal_times_notice`) carries an action (`meal_times_notice_action`) that opens the
  system's notification settings for the app — the one sanctioned second chance; no in-app
  toggle exists (brief: rejected outright).
- **NOTIF-04** — Given the next logical day (MEAL-01's boundary) has zero planned entries
  across all six slots, Then the armed set carries one `plan_tomorrow` reminder, firing
  45 minutes after the evening-snack slot time and never later than 22:00 (brief D4) — move
  the evening snack and the nudge moves with it, like water (PLAN-09's discipline).
- **NOTIF-05** — Given the next logical day has at least one planned entry — or its plan
  cannot be read — Then no `plan_tomorrow` reminder is armed: a nudge is never fired on
  unknown state. Every plan write already re-arms (PLAN-07's triggers), so planning
  tomorrow silences the nudge at once.
- **NOTIF-06** — Given the nudge's alarm fires, Then delivery asks the emptiness question
  again — tomorrow may have been planned after the alarm was armed — and posts nothing when
  tomorrow is no longer unplanned (brief D5; the delivery-time twin of PLAN-26's staleness
  guard).
- **NOTIF-07** — Given the nudge was delivered and tomorrow is still unplanned the next
  evening, Then it arms again — one per day, every empty evening, with no self-imposed
  back-off (brief D8); the OS notification channel is the off switch.
