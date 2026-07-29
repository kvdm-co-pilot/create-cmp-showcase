package com.kvdm.fuelled.core.time

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

/**
 * The app's moving "now".
 *
 * Everything Fuelled shows about *when* — which logical day is in view, which meal slot is
 * focused, whether that slot is late, which slots are missed, which water is next — is
 * derived from the current instant. Deriving it once and holding the answer is the bug this
 * type exists to make impossible: leave the app open overnight and a screen that read the
 * clock at 21:00 is still speaking for yesterday at 09:00 the next morning, with the ring,
 * the focus container and the LATE tag all frozen with it (observed on-device, 2026-07-28).
 *
 * `logicalDate()` next door is the pure derivation and stays pure. This is the *signal* that
 * says when to derive again, and it has exactly two sources, because a device gives you two
 * kinds of time and only one of them is reliable:
 *
 * 1. **The minute tick.** A coroutine that sleeps to the next wall-clock minute boundary and
 *    emits. Aligned, not `delay(60s)` in a loop — an unaligned ticker drifts and can show
 *    "late since 09:30" up to 59 seconds after 09:30. This is a clock, not a poll: it carries
 *    no data and reads nothing. While no screen collects it (see `WhileSubscribed`) it does
 *    not run at all, so it costs nothing in the background.
 *
 * 2. **[wake].** Coroutine timers do not run dependably while the device sleeps — Doze parks
 *    them — which is precisely why the overnight case failed and why a ticker ALONE is not a
 *    fix. The platform pokes this on the events that mean "time may have jumped": returning to
 *    the foreground, and Android's own `ACTION_DATE_CHANGED` / `ACTION_TIMEZONE_CHANGED`
 *    broadcasts. Same shape as Now in Android's `TimeZoneMonitor`.
 *
 * Consumers never call [now] to build state they then hold. They collect [ticks] (or [days])
 * and re-derive, so the answer moves with the clock by construction rather than by anyone
 * remembering to refresh.
 *
 * Leaf utility code: pure Kotlin plus kotlinx-datetime and coroutines, no app layers (ARCH-10).
 */
interface TimeSignal {
    /** The instant right now. For a one-shot decision at the moment of a WRITE, never for held state. */
    fun now(): Instant

    /**
     * The current instant, re-emitted on every minute boundary and on every [wake]. Emits
     * immediately on collection so a subscriber never waits up to a minute for its first value.
     */
    val ticks: Flow<Instant>

    /**
     * Time may have jumped — re-derive. Called on foreground and on the platform's
     * date/timezone-changed broadcasts. Cheap and idempotent; a spurious call costs one
     * re-derivation.
     */
    fun wake()
}

/**
 * The logical day in view, re-emitted only when it actually CHANGES.
 *
 * Built on [TimeSignal.ticks] and de-duplicated: a screen keyed on the day re-reads once at
 * 04:00 rather than sixty times an hour. Time-of-day state (focus, late, missed) keys on
 * `ticks` instead — it genuinely changes minute to minute.
 */
fun TimeSignal.days(dayStartHour: Int, zone: TimeZone): Flow<LocalDate> =
    ticks.map { logicalDate(it, dayStartHour, zone) }.distinctUntilChanged()

/** The logical day right now — for a write's one-shot decision, not for held state. */
fun TimeSignal.currentDay(dayStartHour: Int, zone: TimeZone): LocalDate =
    logicalDate(now(), dayStartHour, zone)

/**
 * The production [TimeSignal]: a real clock, a boundary-aligned ticker, and a wake channel.
 *
 * [interval] is the tick granularity (one minute in production). Tests inject a fake [clock]
 * and drive the boundary directly — the reason every clock read in this app already goes
 * through an injected `Clock` rather than `Clock.System` inline.
 */
class RealTimeSignal(
    private val clock: Clock = Clock.System,
    private val interval: Duration = 1.minutes,
) : TimeSignal {

    // extraBufferCapacity so wake() never suspends its caller — a lifecycle callback must not
    // block on a collector, and a dropped duplicate wake is harmless (the next one re-derives
    // the same answer).
    private val wakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun now(): Instant = clock.now()

    override val ticks: Flow<Instant> = merge(
        flow {
            while (true) {
                emit(clock.now())
                delay(untilNextBoundary())
            }
        },
        wakes.map { clock.now() },
    )

    override fun wake() {
        wakes.tryEmit(Unit)
    }

    /**
     * Time to the next [interval] boundary, never zero.
     *
     * Aligning to the boundary is what keeps "late since 09:30" honest: an unaligned
     * `delay(interval)` loop lands wherever collection happened to start and can sit up to a
     * full interval behind the minute it is meant to announce. The one-second floor stops a
     * pathological zero-delay spin when the computation lands exactly on a boundary.
     */
    private fun untilNextBoundary(): Duration {
        val period = interval.inWholeMilliseconds
        if (period <= 0) return 1.seconds
        val remainder = clock.now().toEpochMilliseconds() % period
        return maxOf(period - remainder, 1_000L).milliseconds
    }
}
