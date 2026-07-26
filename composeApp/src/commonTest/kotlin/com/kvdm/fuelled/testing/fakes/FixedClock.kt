package com.kvdm.fuelled.testing.fakes

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A clock frozen at [instant] — the hand-written fake that lets a test DRIVE the logical-day
 * boundary (MEAL-01/MEAL-08) instead of racing the wall clock.
 *
 * Everything that needs "now" takes a [Clock] parameter, so a test can sit an instant one
 * minute before the 04:00 day start and assert the day it belongs to. Nothing under test ever
 * calls a global now.
 */
class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
