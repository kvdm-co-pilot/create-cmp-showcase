package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.core.time.TimeSignal
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal

/**
 * A [TimeSignal] a test DRIVES rather than waits for — the successor to [FixedClock] now that
 * "now" is a stream and not a value.
 *
 * [advanceTo] is the whole point: it moves the clock AND emits, which is what lets a test assert
 * the two bugs this type exists to prevent without sleeping for a minute or for a night:
 *
 * - the logical day rolling over (sit at 03:59, `advanceTo` 04:00, assert the day in view moved);
 * - a slot going LATE (sit at 09:29, `advanceTo` 10:01, assert the tag appeared).
 *
 * [wake] re-emits the CURRENT instant without moving it — the foreground case, where the answer
 * changes because real time passed while the process was parked.
 */
class FakeTimeSignal(start: Instant) : TimeSignal {

    private val current = MutableStateFlow(start)

    override fun now(): Instant = current.value

    override val ticks: Flow<Instant> = current

    override fun wake() {
        // A StateFlow conflates equal values, so re-setting the same instant would emit nothing.
        // The production signal genuinely re-reads the clock on wake; here the instant is the
        // test's own, so a wake with no movement is a no-op by construction and any test that
        // needs an emission moves the clock.
        current.value = current.value
    }

    /** Move to [instant] and emit — one call stands in for the passage of time. */
    fun advanceTo(instant: Instant) {
        current.value = instant
    }
}
