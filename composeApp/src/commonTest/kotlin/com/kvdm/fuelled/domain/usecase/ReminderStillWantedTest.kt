package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.notification.LEAD_REMINDER_STALE_AFTER
import com.kvdm.fuelled.domain.notification.REMINDER_STALE_AFTER
import com.kvdm.fuelled.domain.notification.staleAfterFor
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.fakes.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The delivery-time question (NOTIF-08).
 *
 * NOTIF-06 established this for the plan-tomorrow nudge; two more reminder families made it a
 * rule. An alarm set at midnight knows nothing about the dose swallowed at 07:30, and the
 * arming side cannot reason about alarms already sitting in the OS — so the check has to
 * happen at the moment of firing, from nothing but the intent's own extras.
 */
class ReminderStillWantedTest {

    private val today = LocalDate(2026, 8, 3)
    private val supplements = FakeSupplementRepository()
    private val workouts = FakeWorkoutRepository(today = today)
    private val plan = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)

    private fun useCase() = ReminderStillWantedUseCase(
        supplements = supplements,
        workouts = workouts,
        tomorrowUnplanned = TomorrowUnplannedUseCase(plan, FakeTimeSignal(TEST_NOW), TEST_ZONE),
    )

    // SPEC: NOTIF-08
    @Test
    fun `a dose already taken on the day the alarm is about is not posted`() = runTest {
        supplements.takenByDate = mapOf(today to setOf("t"))

        assertFalse(useCase()("supp_t_AT_TIME", today), "it was swallowed before the alarm rang")
        assertTrue(
            useCase()("supp_t_AT_TIME", LocalDate(2026, 8, 6)),
            "and the NEXT dose day is unaffected — the question is about the day it is FOR",
        )
    }

    // SPEC: NOTIF-08
    @Test
    fun `a session already marked done is not announced`() = runTest {
        workouts.done = setOf(today)

        assertFalse(useCase()("workout_MONDAY_AT_TIME", today))
        assertTrue(useCase()("workout_MONDAY_AT_TIME", LocalDate(2026, 8, 10)))
    }

    // SPEC: NOTIF-08
    @Test
    fun `an id containing an underscore survives the key round-trip`() = runTest {
        // Ids are user-minted. Splitting the key from the FRONT would truncate
        // `supp_vit_d3_AT_TIME` to `vit` and check the wrong supplement — which fails open,
        // posting an alarm for a dose already taken, exactly the bug this guard exists for.
        supplements.takenByDate = mapOf(today to setOf("vit_d3"))

        assertFalse(useCase()("supp_vit_d3_AT_TIME", today))
    }

    // SPEC: NOTIF-08
    @Test
    fun `a storage failure posts rather than swallowing`() = runTest {
        supplements.failure = DomainError.Unexpected(RuntimeException("db locked"))

        assertTrue(
            useCase()("supp_t_AT_TIME", today),
            "'the database would not open' is not evidence the dose was taken — silence is the worse failure",
        )
    }

    // SPEC: NOTIF-08
    @Test
    fun `an unrecognised key and a missing date both post`() = runTest {
        supplements.takenByDate = mapOf(today to setOf("t"))

        assertTrue(useCase()("meal_LUNCH", null), "a daily reminder has no per-day fact to check")
        assertTrue(useCase()("something_from_a_newer_build", today))
        assertTrue(useCase()("supp_t_AT_TIME", null), "no date means nothing to ask about")
    }

    // SPEC: NOTIF-10
    @Test
    fun `a lead-time rung is given a far shorter grace than a meal`() {
        // An hour-late "30 minutes before" is not a late warning but a wrong one: it lands
        // after the thing it was warning about.
        assertEquals(LEAD_REMINDER_STALE_AFTER, staleAfterFor("supp_t_THIRTY_MIN"))
        assertEquals(LEAD_REMINDER_STALE_AFTER, staleAfterFor("workout_MONDAY_NIGHT_BEFORE"))
        // "You have not taken it yet" stays true as long as the day does, so the at-time rung
        // keeps the meal-length grace.
        assertEquals(REMINDER_STALE_AFTER, staleAfterFor("supp_t_AT_TIME"))
        assertEquals(REMINDER_STALE_AFTER, staleAfterFor("meal_LUNCH"))
    }
}
