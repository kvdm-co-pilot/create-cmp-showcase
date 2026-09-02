package com.kvdm.fuelled.presentation.today

import com.kvdm.fuelled.domain.model.MacroProgress
import kotlinx.datetime.LocalDate

// ── The goal bloom's trigger (motion D8 / OD3, MOTION-10) ────────────────────────────────
// The bloom itself is a registry primitive (`Modifier.goalBloom`) that fires whenever its
// trigger changes to a new non-null value. WHEN it fires is a decision, and this is it, as a
// pure function so it can be tested without a composition: once per logical day, the day the
// protein goal is reached on. A later change of the same day's value never re-fires it, and a
// new logical day fires it again — a day that starts at or above the goal blooms on arrival.

/**
 * The date the protein goal's bloom should fire on, or null when it should not.
 *
 * @param protein The day's protein progress; a zero target can never be "reached".
 * @param date The logical day on screen.
 * @param lastBloomed The date the bloom last fired on, or null if it never has.
 */
internal fun goalBloomTrigger(protein: MacroProgress, date: LocalDate, lastBloomed: LocalDate?): LocalDate? =
    if (protein.target > 0 && protein.current >= protein.target && lastBloomed != date) date else null
