package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalDate

/**
 * The outcome variable (specs/history.spec.md HIST-06..08).
 *
 * Everything else in this app measures INPUT — what went in, against a target. Weight is the
 * only thing here that measures what the input is doing, which is why a nutrition tracker
 * without it can tell you that you hit your protein for four weeks and not whether it worked.
 *
 * Stored, not derived — the one thing on the Progress surface that is (decision D7). Kept in
 * kilograms at rest and converted only for display (SET-02): a stored unit that follows a
 * display preference is a stored unit that silently reinterprets old rows the day someone
 * flips a switch.
 */
data class WeightEntry(val date: LocalDate, val kg: Double)

/**
 * The window's readings, ascending by date.
 *
 * One reading per logical day by construction (HIST-06 — the store upserts on date), so
 * weighing twice in a morning corrects the morning rather than recording a trend inside it.
 */
data class WeightLog(val entries: List<WeightEntry>) {
    val latest: WeightEntry? get() = entries.lastOrNull()

    /**
     * HIST-08: the signed change across the window, or `null` with fewer than two readings.
     *
     * Null rather than 0.0 deliberately — "no change" and "not enough readings to say" are
     * different facts, and a first weigh-in reporting "0.0 kg in 4 weeks" is the app inventing
     * a result out of a single data point.
     */
    val change: Double? get() = if (entries.size < 2) null else entries.last().kg - entries.first().kg
}
