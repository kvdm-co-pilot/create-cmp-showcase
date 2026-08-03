package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.model.ReminderCapability
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.domain.model.ReminderTarget
import com.kvdm.fuelled.domain.model.planTomorrowNudge
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The end-of-day nudge (specs/notifications.spec.md): its fire moment, its emptiness question,
 * and its ride on the arm path. Hand-written fakes, a driven clock — TEST_NOW is 12:45 UTC on
 * 2026-07-22, so "tomorrow" is deterministically the 23rd.
 */
class PlanTomorrowNudgeTest {

    private val allowed = ReminderCapability(notificationsAllowed = true, exactAlarmsAllowed = true)
    private val tomorrow = LocalDate(2026, 7, 23)

    // SPEC: NOTIF-04
    @Test
    fun `the nudge fires 45 minutes after the evening snack, and moves with it`() {
        val default = planTomorrowNudge(MealTimes(), tomorrowUnplanned = true, capability = allowed)
        assertEquals(LocalTime(20, 15), default!!.time, "19:30 evening snack + 45")
        assertEquals(ReminderTarget.PlanTomorrow, default.target)
        assertEquals("plan_tomorrow", default.key, "stable key — re-arms replace, never stack")

        val early = MealTimes().withTime(MealSlot.EVENING_SNACK, LocalTime(18, 0))
        assertEquals(
            LocalTime(18, 45),
            planTomorrowNudge(early, tomorrowUnplanned = true, capability = allowed)!!.time,
            "no eighth time setting — the nudge derives from the user's own times (PLAN-09's discipline)",
        )
    }

    // SPEC: NOTIF-04
    @Test
    fun `the nudge never lands later than 22-00`() {
        val late = MealTimes().withTime(MealSlot.EVENING_SNACK, LocalTime(21, 45))
        val nudge = planTomorrowNudge(late, tomorrowUnplanned = true, capability = allowed)
        assertEquals(LocalTime(22, 0), nudge!!.time, "21:45 + 45 would be 22:30 — clamped")
    }

    // SPEC: NOTIF-05
    @Test
    fun `a planned tomorrow means no nudge at all`() {
        assertNull(planTomorrowNudge(MealTimes(), tomorrowUnplanned = false, capability = allowed))
    }

    // SPEC: NOTIF-05, NOTIF-06
    @Test
    fun `tomorrow is unplanned only when every slot has zero entries - and never on a failed read`() =
        runTest {
            val repo = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
            val unplanned = TomorrowUnplannedUseCase(repo, FakeTimeSignal(TEST_NOW), TEST_ZONE)

            // All six slots exist for every date whether or not anything was written — the
            // question is entries, never slot count.
            assertTrue(unplanned(), "nothing was ever written for the 23rd")

            // One food in one slot is a plan: the nudge's claim stops being true.
            repo.entries[tomorrow] = mapOf(
                MealSlot.LUNCH to listOf(
                    LogEntry("p1", "Chicken", "150 g", 240, 45, status = LogStatus.PLANNED),
                ),
            )
            assertFalse(unplanned())

            // A read that failed answers PLANNED — nagging a user whose plan merely failed to
            // load is the one guess this question must never make. This same answer guards the
            // delivery-time re-check (NOTIF-06), which resolves this very use case.
            repo.entries.remove(tomorrow)
            repo.failure = com.kvdm.fuelled.domain.model.DomainError.Network
            assertFalse(unplanned())
        }

    // SPEC: NOTIF-04, NOTIF-05, NOTIF-07
    @Test
    fun `the arm path arms the nudge while tomorrow is empty and silences it once planned`() =
        runTest {
            val repo = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
            val scheduler = FakeReminderScheduler()
            val arm = ArmMealRemindersUseCase(
                repo,
                scheduler,
                FakeAppStateRepository(),
                TomorrowUnplannedUseCase(repo, FakeTimeSignal(TEST_NOW), TEST_ZONE),
            )

            assertIs<AppResult.Success<*>>(arm())
            val nudge = scheduler.armed.single { it.target is ReminderTarget.PlanTomorrow }
            assertEquals(LocalTime(20, 15), nudge.time)
            assertEquals(ReminderMode.EXACT, nudge.mode)

            // Planning tomorrow cancels it on the next re-arm — which every plan write already
            // triggers (PLAN-07's re-arm seams), so there is no separate cancel path to forget.
            repo.entries[tomorrow] = mapOf(
                MealSlot.BREAKFAST to listOf(
                    LogEntry("p1", "Oats", "80 g", 300, 10, status = LogStatus.PLANNED),
                ),
            )
            assertIs<AppResult.Success<*>>(arm())
            assertFalse(scheduler.armed.any { it.target is ReminderTarget.PlanTomorrow })

            // NOTIF-07: and it never backs off — empty again tomorrow evening, armed again.
            repo.entries.remove(tomorrow)
            assertIs<AppResult.Success<*>>(arm())
            assertTrue(scheduler.armed.any { it.target is ReminderTarget.PlanTomorrow })
        }
}
