# Feature brief — Notifications

```json cmp:feature
{ "touches": ["feature-spec:meal-plan", "feature-spec:settings"] }
```

> **Status:** signed. Contract: [`specs/notifications.spec.md`](../../specs/notifications.spec.md).

## What was asked, against what already exists

The ask: notifications for each meal, for water, and at the end of the day when nothing is
planned for tomorrow. An audit of the tree shows **the first two already ship**:

- **Meal reminders** — PLAN-07: one reminder per slot, fired at the prep lead before the
  slot time, cancelled when the slot is ticked, re-armed on every plan edit and on boot.
- **Water reminders** — PLAN-08: six containers at meal midpoints, no lead, independent of
  meal ticks. Staleness guard PLAN-26 drops late deliveries.
- **Prep lead setting** — SET-07/08: user-chosen 0..120 min, re-arms on change.

What does NOT exist is what makes them visible: **no code path ever requests
`POST_NOTIFICATIONS`**. The meal-plan brief (decision 9) promised a "runtime prompt on first
arm"; the implementation only *reads* the permission (`AndroidReminderScheduler.kt:82`,
`MealReminderReceiver.kt:67`). On Android 13+ the permission starts denied, so every
reminder is armed in `UNAVAILABLE` mode and nothing is ever delivered — the shipped feature
is silently invisible. This brief closes that gap and adds the one genuinely new behavior:
the end-of-day "plan tomorrow" nudge.

## Decisions

**D1 — Meal and water notifications are not rebuilt; they are made deliverable.** The
policy, scheduler, receivers and re-arm seams stay exactly as PLAN-07/08/26 specify. The
slice delivers the missing permission prompt and the new nudge through the same port
(`ReminderScheduler`), the same policy file, the same staleness guard. *Rejected:* a
parallel "notifications" subsystem — two arming paths is how a slot rings twice.

**D2 — The permission is asked for once, at the moment reminders first matter.** On the
first arrival at Today after onboarding, if capability is `UNAVAILABLE` and the prompt has
never been shown, the system `POST_NOTIFICATIONS` dialog is requested (API 33+; earlier
APIs are granted at install). Denied means denied: the app never re-prompts on its own —
the times sheet already states "reminders are off" (PLAN-07's honesty clause), and that
surface gains a tap-through to the system app-settings screen, which is the only
sanctioned second chance. *Rejected:* prompting at app launch (asks before the user has
seen why the app wants it, the classic instant-deny) and re-prompting on a schedule (how
an app gets uninstalled).

**D3 — The end-of-day nudge fires when tomorrow is still unplanned.** One reminder per
logical day, keyed `plan_tomorrow`, fired in the evening (D4) when the next logical day has
**zero planned entries across all six slots** (slots always exist for every date —
emptiness is judged on entries, never on slot count). Planning anything for tomorrow —
one food in one slot — cancels it via the same re-arm seams the meal reminders already
use; copy-day-forward therefore cancels it too. The notification's tap opens the plan on
tomorrow. *Rejected:* nudging when tomorrow is only *partially* planned — "you planned
breakfast but not dinner" is coaching, not a reminder, and it is exactly the notification
people mute.

**D4 — The nudge's moment derives from the user's own meal times, like everything else.**
It fires 45 minutes after the evening-snack slot time (default 19:30 → 20:15), clamped to
22:00 — after the day's last meal moment, while the evening is still usable for planning.
No new time setting: move your evening snack and the nudge moves with it, the same rule
water already follows. *Rejected:* a fixed clock time (ignores the user's actual day
shape) and a configurable nudge time (a setting nobody asked for, guarding a value the
meal times already imply).

**D5 — Emptiness is re-checked at delivery, not only at arm time.** An alarm armed at
breakfast cannot know what the user plans at lunch. The re-arm seams cancel the nudge on
every plan write, but the receiver still verifies "tomorrow is still empty" before posting
— belt and braces at the same seam where PLAN-26 already drops stale deliveries. A nudge
about a day that meanwhile got planned is never shown.

**D6 — "Tomorrow" means the next logical day.** The 04:00 boundary (`LogicalDay.kt`)
already defines the day everywhere else; the nudge asks "does logical-tomorrow have
entries" and fires during logical-today's evening. At 02:00 the app is still in
yesterday's logical day and the nudge for it has either fired or been cancelled — no
special case.

**D7 — Android-first stands.** Meal-plan decision 9 is unchanged: iOS keeps the
`NoOpReminderScheduler` binding; `UNUserNotificationCenter` remains its own future slice.
Desktop stays no-op.

## Rejected outright

- **A "reminders" master toggle in Settings.** The OS notification channel already IS the
  off switch, honestly reflected back as `UNAVAILABLE` capability. An in-app toggle would
  be a second source of truth that can disagree with the system one. If a real need
  appears (muting water but not meals), that is channel *granularity* — a different brief.
- **Snooze / "tick done" notification actions.** Already on the daily-journeys backlog as
  T2; bundling them here widens a delivery-gap fix into a feature.
- **Retro-editing the meal-plan brief.** Its decision 9 promised the prompt; the signed
  bytes stay as signed. This brief is where the promise is kept.

**D8 — The nudge fires every evening tomorrow is empty; it never goes quiet on its own.**
One notification per day about the app's core loop is the contract, not spam; the off
switch is the OS channel (see "Rejected outright"). *Rejected:* backing off after N
consecutive empty evenings — a reminder that silently stops reminding is indistinguishable
from a broken one.

## Open decisions

None. (Decision history: the clamp in D4 was moved 23:00 → 22:00 by the human before
signing; the prompt moment in D2 and the every-evening horizon in D8 were accepted as
proposed.)
