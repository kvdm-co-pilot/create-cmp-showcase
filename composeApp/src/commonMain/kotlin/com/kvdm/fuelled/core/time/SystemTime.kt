package com.kvdm.fuelled.core.time

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The designated ambient-time provider (ARCH-13).
 *
 * The rule bans `Clock.System`, `TimeZone.currentSystemDefault()` and the `*.now()` family
 * everywhere except a package ending in `.core.time` — because a clock read scattered across
 * sixteen files is sixteen places a test cannot pin, and structure derived from an unpinned
 * clock is a golden tree that passes at 23:00 and fails at 09:00 with no code change.
 *
 * The point of the rule is not that the process never reads the system clock — something must,
 * eventually — but that it reads it in ONE named place that a test can substitute. That place
 * is here.
 *
 * **These are defaults, not a way in.** Every consumer still takes its `clock`/`zone` as a
 * constructor parameter and every test still injects a fake; these functions only supply the
 * production default that used to be written inline at each call site. Reach for the parameter,
 * never for this file, from anywhere outside `core/time`.
 */

/**
 * The device's current time zone.
 *
 * A function, not a `val`: the zone is time-of-run state like the instant is — a device that
 * crosses a timezone boundary (or has one pushed by the network) changes it mid-process, and a
 * cached `val` captured at class-init would go on speaking for wherever the app started.
 * `TimeSignal.wake()` exists for the same reason on the instant side.
 */
fun systemZone(): TimeZone = TimeZone.currentSystemDefault()

/**
 * The production clock — the default for consumers that take a one-shot `Clock` rather than the
 * moving [TimeSignal]. A property with a getter, not a stored value, for the same reason
 * [systemZone] is a function.
 */
val systemClock: Clock get() = Clock.System

/**
 * The device's current CALENDAR date — not the logical day.
 *
 * Deliberately distinct from [currentDay]/[logicalDate], which apply the 04:00 `dayStartHour`
 * boundary (MEAL-01). This is for the one caller that genuinely wants the wall-clock date: the
 * supplement cadence anchor in Settings, which stamps "counting starts today" on a schedule the
 * user is editing right now. Anchoring that to the logical day would mean a schedule created at
 * 01:00 anchors to yesterday, which is not what someone editing a cadence at 01:00 means.
 *
 * Kept as its own named function rather than inlined at the call site so the distinction is
 * stated once, here, instead of being re-derived by whoever reads the call next.
 */
fun systemToday(zone: TimeZone = systemZone()): LocalDate =
    systemClock.now().toLocalDateTime(zone).date
