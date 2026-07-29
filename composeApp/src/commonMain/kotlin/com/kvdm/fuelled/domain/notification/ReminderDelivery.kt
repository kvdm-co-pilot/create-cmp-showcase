package com.kvdm.fuelled.domain.notification

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Whether a reminder that has just been delivered is still worth showing (PLAN-26).
 *
 * Arming a reminder and delivering it are two different moments, and on a real device they can
 * be hours apart. The OS holds inexact alarms through Doze; a device that was off, or whose
 * clock jumped, hands back every alarm it owes the instant it wakes. The result is an avalanche:
 * nine notifications at 06:30 — the whole of yesterday evening stacked on top of this morning's
 * breakfast (observed on-device, 2026-07-29). Every one of them is announcing a meal whose
 * moment has long passed.
 *
 * So the relevance check belongs at DELIVERY, not at arming. Nothing armed can be trusted to
 * still be true when it fires; the receiver asks this before it posts.
 *
 * Domain-layer: pure Kotlin, no platform types (ARCH-02). The Android receiver reads the clock
 * and the intended instant and asks; every case is testable here without an alarm manager.
 */

/**
 * How late a meal reminder may arrive and still be posted (PLAN-26).
 *
 * Two hours is not arbitrary: the Body-for-LIFE rhythm puts the six meals roughly 2–3 hours
 * apart (`DEFAULT_MEAL_TIMES`), so beyond this window the NEXT meal's reminder is the relevant
 * one and this one would only compete with it. Inside it, a late reminder is still doing its
 * job — a lunch nudge at 13:15 is worth having.
 */
val REMINDER_STALE_AFTER: Duration = 2.hours

/**
 * True when [deliveredAt] is so far past [intendedAt] that posting would be noise (PLAN-26).
 *
 * Early or on-time delivery is never stale — a negative difference is not "very fresh", it is
 * a clock that moved backwards, and the honest response to that is to post the reminder the
 * user is expecting rather than to swallow it.
 */
fun isStaleDelivery(
    intendedAt: Instant,
    deliveredAt: Instant,
    grace: Duration = REMINDER_STALE_AFTER,
): Boolean = deliveredAt - intendedAt > grace
