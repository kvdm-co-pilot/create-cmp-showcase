package com.kvdm.fuelled.core.time

import app.cash.turbine.test
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The time signal's contract (specs/reactive-state.spec.md): the logical day is a STREAM that
 * moves only when the derived day moves, and `wake()` is the recovery path for real time that
 * passed while coroutine timers were parked (Doze) — the overnight bug, reproduced in minutes
 * of virtual time instead of a night on a device.
 */
class TimeSignalTest {

    private val zone = TimeZone.UTC
    private val dayStartHour = DEFAULT_DAY_START_HOUR

    // SPEC: RS-02
    @Test
    fun `days emits the logical day only when it actually changes - the boundary, not the minute`() = runTest {
        val signal = FakeTimeSignal(Instant.parse("2026-07-22T12:45:00Z"))

        signal.days(dayStartHour, zone).test {
            assertEquals(LocalDate(2026, 7, 22), awaitItem())

            // Minutes pass, midnight passes: at 03:59 the calendar says the 23rd but the
            // logical day is still the 22nd (MEAL-01) — and NOTHING re-emits, because the
            // stream is de-duplicated on the derived day: day-scoped queries downstream are
            // not torn down sixty times an hour by ticks that change no answer.
            signal.advanceTo(Instant.parse("2026-07-22T21:00:00Z"))
            signal.advanceTo(Instant.parse("2026-07-23T03:59:00Z"))
            expectNoEvents()

            // The boundary. The day in view moves — this is the emission the dashboard keys on.
            signal.advanceTo(Instant.parse("2026-07-23T04:00:00Z"))
            assertEquals(LocalDate(2026, 7, 23), awaitItem())
        }
    }

    // SPEC: RS-03
    @Test
    fun `wake re-derives the day from the real clock - the overnight case the ticker alone cannot cover`() =
        runTest {
            val clock = SteppingClock(Instant.parse("2026-07-22T21:00:00Z"))
            val signal = RealTimeSignal(clock)

            signal.days(dayStartHour, zone).test {
                assertEquals(LocalDate(2026, 7, 22), awaitItem())

                // The device sleeps through the boundary: REAL time moves (the clock), but no
                // coroutine timer fires — which is exactly Doze, and exactly the on-device
                // overnight failure (2026-07-28). Held state would still say the 22nd here.
                clock.instant = Instant.parse("2026-07-23T09:00:00Z")
                expectNoEvents()

                // Foreground. wake() re-reads the clock and the day in view re-derives NOW —
                // no waiting for the next tick that Doze may never have delivered.
                signal.wake()
                assertEquals(LocalDate(2026, 7, 23), awaitItem())
            }
        }

    // SPEC: RS-03
    @Test
    fun `ticks emits immediately on collection and re-reads the clock on every wake`() = runTest {
        val clock = SteppingClock(Instant.parse("2026-07-22T12:45:10Z"))
        val signal = RealTimeSignal(clock)

        signal.ticks.test {
            assertEquals(
                Instant.parse("2026-07-22T12:45:10Z"),
                awaitItem(),
                "a subscriber never waits up to a minute for its first value",
            )

            clock.instant = Instant.parse("2026-07-22T12:45:40Z")
            signal.wake()
            assertEquals(
                Instant.parse("2026-07-22T12:45:40Z"),
                awaitItem(),
                "a wake re-reads the clock — never a cached instant",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A [Clock] the test moves BY HAND — real time passing while coroutine timers stand still. */
    private class SteppingClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }
}
