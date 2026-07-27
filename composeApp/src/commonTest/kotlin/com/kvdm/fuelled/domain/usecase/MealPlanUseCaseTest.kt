package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.ReminderCapability
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.domain.model.ReminderTarget
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The structured day's use cases (specs/meal-plan.spec.md). Hand-written fakes, a fixed clock,
 * no mocking framework — and no wall-clock reads anywhere, so every assertion about focus,
 * lateness or the day boundary is deterministic.
 */
class MealPlanUseCaseTest {

    private val today = LocalDate(2026, 7, 22)
    private val clock = FixedClock(TEST_NOW) // 12:45 UTC

    private fun getPlanDay(repo: FakeMealPlanRepository) =
        GetPlanDayUseCase(repo, clock = clock, zone = TEST_ZONE)

    // SPEC: PLAN-02
    @Test
    fun `every day renders all six containers and six waters, planned or not`() = runTest {
        val repo = FakeMealPlanRepository()

        val day = assertIs<AppResult.Success<*>>(getPlanDay(repo)(today)).value as com.kvdm.fuelled.domain.model.PlanDay

        // Nothing has ever been written for this date — and the day is still a full day.
        assertEquals(6, day.slots.size)
        assertEquals(MealSlot.entries, day.slots.map { it.slot }, "slot order is the enum's order")
        assertEquals(6, day.water.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), day.water.map { it.index })
        assertTrue(day.slots.all { it.entries.isEmpty() }, "PLAN-03: nothing is seeded")
    }

    // SPEC: PLAN-23
    @Test
    fun `only the current logical day makes punctuality claims`() = runTest {
        val repo = FakeMealPlanRepository()

        val current = plan(repo, today)
        val tomorrow = plan(repo, LocalDate(2026, 7, 23))
        val yesterday = plan(repo, LocalDate(2026, 7, 21))

        // At 12:45 with nothing done, today has a focused container and two missed ones.
        assertTrue(current.isCurrentDay)
        assertEquals(MealSlot.LUNCH, current.focusedSlot?.slot)
        assertTrue(current.slots.any { it.missed })

        // A day being planned ahead is not running late, and a day already gone is history —
        // both render their containers and make no claims at all.
        for (other in listOf(tomorrow, yesterday)) {
            assertFalse(other.isCurrentDay)
            assertEquals(null, other.focusedSlot)
            assertTrue(other.slots.none { it.missed }, "a non-current day never reads missed")
            assertTrue(other.slots.none { it.late })
            assertEquals(6, other.slots.size, "it still renders every container")
        }
    }

    // SPEC: PLAN-13
    @Test
    fun `ticking a container logs its planned entries and records the slot done`() = runTest {
        val repo = FakeMealPlanRepository().apply {
            entries[today] = mapOf(
                MealSlot.LUNCH to listOf(
                    LogEntry("l1", "Chicken & rice", "200 g", 620, 58, status = LogStatus.PLANNED),
                ),
            )
        }

        assertIs<AppResult.Success<Unit>>(SetSlotDoneUseCase(repo)(today, MealSlot.LUNCH, done = true))

        assertEquals(
            FakeMealPlanRepository.DoneCall(today, MealSlot.LUNCH, true),
            repo.doneCalls.single(),
        )
        assertTrue(plan(repo, today).slots.single { it.slot == MealSlot.LUNCH }.done)
    }

    // SPEC: PLAN-14
    @Test
    fun `ticking an EMPTY container is a completion and fabricates no food`() = runTest {
        val repo = FakeMealPlanRepository()

        SetSlotDoneUseCase(repo)(today, MealSlot.MORNING_SNACK, done = true)

        val slot = plan(repo, today).slots.single { it.slot == MealSlot.MORNING_SNACK }
        assertTrue(slot.done)
        assertTrue(slot.entries.isEmpty(), "eaten off-plan is a completion, not a food")
        assertTrue(slot.tickedEmpty)
        // And the day's containers are unchanged in number — nothing was invented to fill it.
        assertEquals(6, plan(repo, today).slots.size)
    }

    // SPEC: PLAN-10
    @Test
    fun `water ticks are per day - each 500 ml, and a new day starts at zero`() = runTest {
        val repo = FakeMealPlanRepository()

        SetWaterDoneUseCase(repo)(today, index = 1, done = true)
        SetWaterDoneUseCase(repo)(today, index = 2, done = true)

        assertEquals(1000, plan(repo, today).waterMl)
        assertEquals(0, plan(repo, LocalDate(2026, 7, 23)).waterMl, "a new logical day starts dry")

        // Un-ticking takes it back off: 0.5 L you did not drink must not survive a mis-tap.
        SetWaterDoneUseCase(repo)(today, index = 2, done = false)
        assertEquals(500, plan(repo, today).waterMl)
    }

    // SPEC: PLAN-22
    @Test
    fun `the veg count counts CONTAINERS holding a vegetable, not vegetables`() = runTest {
        val repo = FakeMealPlanRepository().apply {
            entries[today] = mapOf(
                // Three vegetables, ONE container: the method's rule is about meals with veg.
                MealSlot.LUNCH to listOf(
                    LogEntry("l1", "Broccoli", "100 g", 35, 2, veg = true),
                    LogEntry("l2", "Green beans", "100 g", 31, 2, veg = true),
                    LogEntry("l3", "Chicken breast", "200 g", 330, 62),
                ),
                MealSlot.DINNER to listOf(LogEntry("d1", "Salmon", "180 g", 360, 40)),
            )
        }

        assertEquals(1, plan(repo, today).vegMeals)

        repo.entries[today] = repo.entries.getValue(today) + mapOf(
            MealSlot.DINNER to listOf(
                LogEntry("d1", "Salmon", "180 g", 360, 40),
                LogEntry("d2", "Mixed greens", "1 bowl", 90, 3, veg = true),
            ),
        )
        assertEquals(2, plan(repo, today).vegMeals, "two containers with veg meets the method's rule")
    }

    // SPEC: PLAN-20
    @Test
    fun `copy-forward duplicates the day's plan onto the days after it and leaves the source alone`() =
        runTest {
            val source = mapOf(
                MealSlot.BREAKFAST to listOf(
                    LogEntry("b1", "Oats & whey", "80 g", 430, 38, status = LogStatus.PLANNED),
                ),
            )
            val repo = FakeMealPlanRepository().apply { entries[today] = source }

            assertIs<AppResult.Success<Unit>>(CopyDayForwardUseCase(repo)(today, days = 3))

            // Consecutive days after the source — "the rest of my week", not an arbitrary set.
            assertEquals(
                listOf(LocalDate(2026, 7, 23), LocalDate(2026, 7, 24), LocalDate(2026, 7, 25)),
                repo.copyCalls.single().to,
            )
            assertEquals(source, repo.entries[LocalDate(2026, 7, 24)])
            assertEquals(source, repo.entries[today], "the source day is unchanged")
        }

    // SPEC: PLAN-12
    @Test
    fun `entries added to a FUTURE day are written PLANNED and never counted as consumed`() = runTest {
        val repo = FakeMealPlanRepository().apply {
            entries[LocalDate(2026, 7, 25)] = mapOf(
                MealSlot.DINNER to listOf(
                    LogEntry("d1", "Salmon", "180 g", 610, 45, status = LogStatus.PLANNED),
                ),
            )
        }

        val future = plan(repo, LocalDate(2026, 7, 25))
        val dinner = future.slots.single { it.slot == MealSlot.DINNER }

        // The plan renders it — it is scheduled and real — but it is not done, so nothing about
        // it reads as eaten. What it contributes to a consumed total is TODAY-03's job, and
        // that total only sums LOGGED rows.
        assertEquals(LogStatus.PLANNED, dinner.entries.single().status)
        assertFalse(dinner.done)
    }

    // SPEC: PLAN-21
    @Test
    fun `a planned entry on a day that ended unlogged is stale plan - still there, still tickable`() =
        runTest {
            val past = LocalDate(2026, 7, 20)
            val repo = FakeMealPlanRepository().apply {
                entries[past] = mapOf(
                    MealSlot.DINNER to listOf(
                        LogEntry("d1", "Salmon", "180 g", 610, 45, status = LogStatus.PLANNED),
                    ),
                )
            }

            val dinner = plan(repo, past).slots.single { it.slot == MealSlot.DINNER }
            assertTrue(dinner.stalePlan, "planned, the day ended, never logged")
            assertFalse(dinner.done)

            // Back-fillable: ticking it is the ordinary completion, on a day already gone.
            SetSlotDoneUseCase(repo)(past, MealSlot.DINNER, done = true)
            val after = plan(repo, past).slots.single { it.slot == MealSlot.DINNER }
            assertTrue(after.done)
            assertFalse(after.stalePlan, "once ticked it is history that happened, not a stale promise")
        }

    // SPEC: PLAN-07
    @Test
    fun `reminders are armed for every slot and water, and a ticked slot's is cancelled`() = runTest {
        val repo = FakeMealPlanRepository()
        val scheduler = FakeReminderScheduler()
        val arm = ArmMealRemindersUseCase(repo, scheduler)

        assertIs<AppResult.Success<*>>(arm())
        assertEquals(12, scheduler.armed.size, "six meals + six waters")
        assertTrue(scheduler.armed.all { it.mode == ReminderMode.EXACT })

        // A meal already eaten is never announced — and water is untouched by a meal tick.
        assertIs<AppResult.Success<*>>(arm(doneSlots = setOf(MealSlot.BREAKFAST)))
        assertEquals(11, scheduler.armed.size)
        assertFalse(scheduler.armed.any { it.target == ReminderTarget.Meal(MealSlot.BREAKFAST) })
        assertEquals(6, scheduler.armed.count { it.target is ReminderTarget.Water })
    }

    // SPEC: PLAN-07
    @Test
    fun `no exact-alarm permission means WINDOWED, denied notifications mean UNAVAILABLE - never nothing`() =
        runTest {
            val repo = FakeMealPlanRepository()

            // The COMMON case on modern Android: exact alarms are not granted. Windowed is the
            // normal path, not an error path — the day still gets all twelve reminders.
            val windowed = FakeReminderScheduler(
                ReminderCapability(notificationsAllowed = true, exactAlarmsAllowed = false),
            )
            ArmMealRemindersUseCase(repo, windowed)()
            assertEquals(12, windowed.armed.size)
            assertTrue(windowed.armed.all { it.mode == ReminderMode.WINDOWED_INEXACT })

            // Notifications denied outright: the intent is still enumerated, carrying
            // UNAVAILABLE, which is what lets the times sheet SAY reminders are off rather than
            // render an empty schedule that looks like nothing was ever set up.
            val denied = FakeReminderScheduler(
                ReminderCapability(notificationsAllowed = false, exactAlarmsAllowed = false),
            )
            ArmMealRemindersUseCase(repo, denied)()
            assertEquals(12, denied.armed.size)
            assertTrue(denied.armed.all { it.mode == ReminderMode.UNAVAILABLE })
        }

    // SPEC: PLAN-06, PLAN-07, PLAN-09
    @Test
    fun `changing a slot time re-arms that slot and moves the water either side of it`() = runTest {
        val repo = FakeMealPlanRepository()
        val scheduler = FakeReminderScheduler()
        val setTime = SetMealTimeUseCase(repo, ArmMealRemindersUseCase(repo, scheduler))

        ArmMealRemindersUseCase(repo, scheduler)()
        val waterBefore = scheduler.armed.filter { it.target is ReminderTarget.Water }.map { it.time }

        assertIs<AppResult.Success<*>>(setTime(MealSlot.LUNCH, LocalTime(13, 0)))

        val lunch = scheduler.armed.single { it.target == ReminderTarget.Meal(MealSlot.LUNCH) }
        assertEquals(LocalTime(13, 0), lunch.time, "the slot's reminder moved with its time")

        // PLAN-09: the water either side moved too — nobody computed which ones, because water
        // times are midpoints of the meal times and were never stored separately.
        val waterAfter = scheduler.armed.filter { it.target is ReminderTarget.Water }.map { it.time }
        assertTrue(waterAfter != waterBefore)
        assertEquals(LocalTime(11, 15), waterAfter[1]) // 09:30 → 13:00
        assertEquals(LocalTime(13, 45), waterAfter[2]) // 13:00 → 14:30
        // And no other MEAL time moved (PLAN-06).
        assertEquals(LocalTime(7, 0), scheduler.armed.single { it.target == ReminderTarget.Meal(MealSlot.BREAKFAST) }.time)
    }

    // SPEC: PLAN-06
    @Test
    fun `a write that would invert the timetable is coerced, not stored verbatim`() = runTest {
        val repo = FakeMealPlanRepository()
        val setTime = SetMealTimeUseCase(repo, ArmMealRemindersUseCase(repo, FakeReminderScheduler()))

        // The shift-worker case: dinner before breakfast. Every derivation downstream assumes
        // the day runs forwards, so the domain clamps rather than accepting it.
        val stored = assertIs<AppResult.Success<*>>(setTime(MealSlot.DINNER, LocalTime(5, 0)))
        val times = stored.value as com.kvdm.fuelled.domain.model.MealTimes

        assertTrue(times[MealSlot.DINNER] > times[MealSlot.AFTERNOON_SNACK])
        assertTrue(times[MealSlot.DINNER] < times[MealSlot.EVENING_SNACK])
        val ordered = times.inSlotOrder().map { it.second }
        assertEquals(ordered, ordered.sorted(), "the stored timetable stays ascending")
    }

    private suspend fun plan(repo: FakeMealPlanRepository, date: LocalDate) =
        assertIs<AppResult.Success<com.kvdm.fuelled.domain.model.PlanDay>>(getPlanDay(repo)(date)).value
}
