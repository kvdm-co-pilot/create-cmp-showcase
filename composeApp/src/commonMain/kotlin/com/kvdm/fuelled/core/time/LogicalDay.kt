package com.kvdm.fuelled.core.time

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The logical day — the date a log entry belongs to, which is not the calendar date
 * (see specs/meal.spec.md, MEAL-01/MEAL-02; the reasoning is the signed brief,
 * docs/features/meal.md §"today must go into tomorrow after twelve").
 *
 * A 01:30 snack belongs to the evening it followed, not to the calendar date the clock had
 * already rolled into. So the day starts at the profile's `dayStartHour` (default 4) and
 * runs through 03:59 of the following calendar day. Midnight is not a separate rule — it is
 * `dayStartHour = 0`, the special case of the same setting.
 *
 * Leaf utility code: pure Kotlin plus kotlinx-datetime, no app layers (ARCH-10).
 */

/** The `dayStartHour` a profile carries until the user changes it (MEAL-01). */
const val DEFAULT_DAY_START_HOUR: Int = 4

/**
 * The logical date [instant] falls in, for a day that starts at [dayStartHour] in [zone].
 *
 * Deliberately wall-clock arithmetic — convert to the zone's local date-time, and take the
 * previous calendar date when the clock has not yet reached [dayStartHour] — because that is
 * what the clause says ("from `dayStartHour`:00 through 03:59 of the following calendar
 * day"). Subtracting a fixed duration from the instant instead would drift by an hour on
 * every DST transition: on a 23-hour day, 04:30 local minus 4h lands at 00:30 of the *same*
 * date only by luck of the offset.
 *
 * There is no stored boundary and no scheduled rollover: callers derive this from the
 * current instant on every read, so the day in view rolls over by re-derivation, never by a
 * job that rewrites rows (MEAL-02).
 *
 * @param dayStartHour hour of the local day the logical day begins, 0..23. Out of range is a
 *   programming error — a caller passed something a settings screen must never produce — so
 *   it throws rather than returning a `DomainError`.
 */
fun logicalDate(instant: Instant, dayStartHour: Int, zone: TimeZone): LocalDate {
    require(dayStartHour in 0..23) { "dayStartHour must be in 0..23, was $dayStartHour" }
    val local = instant.toLocalDateTime(zone)
    return if (local.hour < dayStartHour) local.date.minus(1, DateTimeUnit.DAY) else local.date
}
