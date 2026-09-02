package com.kvdm.fuelled.presentation.today

import com.kvdm.fuelled.domain.model.MacroProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

/**
 * The goal bloom's once-per-logical-day rule (MOTION-10), as the pure function Today feeds
 * `Modifier.goalBloom` from. No composition, no clock: a progress, a date, and the date it last
 * fired on go in; the date it should fire on — or null — comes out.
 */
class GoalBloomTest {

    private val today = LocalDate(2026, 7, 22)

    private fun protein(current: Int, target: Int = 180) = MacroProgress("Protein", current, target, "g")

    // SPEC: MOTION-10
    @Test
    fun `fires with the day's date when the goal is reached and nothing has bloomed today`() {
        val trigger = goalBloomTrigger(protein(current = 180), today, lastBloomed = null)

        assertEquals(today, trigger)
    }

    // SPEC: MOTION-10
    @Test
    fun `does not re-fire when the same day's value changes after it bloomed`() {
        val trigger = goalBloomTrigger(protein(current = 195), today, lastBloomed = today)

        assertNull(trigger)
    }

    // SPEC: MOTION-10
    @Test
    fun `fires again on a new logical day`() {
        val tomorrow = LocalDate(2026, 7, 23)

        val trigger = goalBloomTrigger(protein(current = 180), tomorrow, lastBloomed = today)

        assertEquals(tomorrow, trigger)
    }

    // SPEC: MOTION-10
    @Test
    fun `never fires below the goal`() {
        val trigger = goalBloomTrigger(protein(current = 179), today, lastBloomed = null)

        assertNull(trigger)
    }

    // SPEC: MOTION-10
    @Test
    fun `never fires with a zero target`() {
        val trigger = goalBloomTrigger(protein(current = 0, target = 0), today, lastBloomed = null)

        assertNull(trigger)
    }
}
