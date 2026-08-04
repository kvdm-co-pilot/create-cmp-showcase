package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.ReminderChannel
import com.kvdm.fuelled.domain.model.ReminderLead
import com.kvdm.fuelled.domain.model.ReminderTarget
import com.kvdm.fuelled.domain.model.MealReminder
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutWeek
import com.kvdm.fuelled.testing.fakes.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The training write path (WORK-01/WORK-04/WORK-07) and the channel split (NOTIF-09).
 */
class WorkoutWritePathTest {

    private val monday = LocalDate(2026, 8, 3)

    // SPEC: WORK-04
    @Test
    fun `marking done is a fact about the logical day, and the next day starts undone`() = runTest {
        val repo = FakeWorkoutRepository(today = monday)

        repo.setDone(true)
        assertEquals(setOf(monday), repo.done, "the mark is stored against the day it was made on")
        assertTrue(repo.doneBetween(monday, monday).let { it is com.kvdm.fuelled.domain.result.AppResult.Success && monday in it.value })

        // The day rolls over. Nothing resets and no boundary job runs — Tuesday simply has no
        // row, exactly as a supplement dose does (SUPP-07) and a water tick does (PLAN-10).
        repo.today = LocalDate(2026, 8, 4)
        val tuesday = repo.doneBetween(repo.today, repo.today)
        assertTrue(tuesday is com.kvdm.fuelled.domain.result.AppResult.Success && tuesday.value.isEmpty())

        repo.today = monday
        repo.setDone(false)
        assertFalse(monday in repo.done, "and it is undoable — a mis-tap is not a permanent claim")
    }

    // SPEC: WORK-01
    @Test
    fun `editing the plan never rewrites the history`() = runTest {
        val repo = FakeWorkoutRepository(today = monday)
        repo.week = WorkoutWeek(mapOf(DayOfWeek.MONDAY to WorkoutDayPlan("Upper body")))
        repo.setDone(true)

        // Monday becomes a rest day. The Mondays already trained are FACTS, held in a separate
        // table — a plan and a history are different nouns, and one row for both would make
        // changing your mind erase what you did.
        SaveWorkoutDayUseCase(repo)(DayOfWeek.MONDAY, WorkoutDayPlan(label = null))

        assertFalse(repo.week[DayOfWeek.MONDAY].isTraining, "the plan changed...")
        assertTrue(monday in repo.done, "...and the session recorded that day still stands")
    }

    // SPEC: WORK-07
    @Test
    fun `clearing the label makes a rest day and drops its time and rungs with it`() = runTest {
        val repo = FakeWorkoutRepository(today = monday)

        SaveWorkoutDayUseCase(repo)(
            DayOfWeek.WEDNESDAY,
            WorkoutDayPlan("   ", LocalTime(18, 0), ReminderLead.DEFAULT),
        )

        val saved = repo.saves.single().second
        assertNull(saved.label, "a blank name is rest, not a session called nothing")
        // If the time survived, an alarm would keep ringing for a day that no longer trains.
        assertNull(saved.remindAt)
        assertTrue(saved.leads.isEmpty())
    }

    // SPEC: WORK-07
    @Test
    fun `a time with no rungs and rungs with no time are both stored as no reminder`() = runTest {
        val repo = FakeWorkoutRepository(today = monday)
        val save = SaveWorkoutDayUseCase(repo)

        save(DayOfWeek.MONDAY, WorkoutDayPlan("Upper body", LocalTime(18, 0), emptySet()))
        save(DayOfWeek.TUESDAY, WorkoutDayPlan("Cardio", null, ReminderLead.DEFAULT))

        // Half a reminder is one that never fires while the row still says it will.
        repo.saves.forEach { (_, plan) ->
            assertNull(plan.remindAt)
            assertTrue(plan.leads.isEmpty())
        }
    }

    // SPEC: NOTIF-09
    @Test
    fun `each reminder family posts to its own channel`() {
        fun reminder(target: ReminderTarget) =
            MealReminder(target, LocalTime(8, 0), ReminderMode.EXACT)

        assertEquals(ReminderChannel.MEALS, reminder(ReminderTarget.Meal(MealSlot.LUNCH)).channel)
        assertEquals(ReminderChannel.MEALS, reminder(ReminderTarget.Water(1)).channel)
        assertEquals(ReminderChannel.MEALS, reminder(ReminderTarget.PlanTomorrow).channel)
        assertEquals(
            ReminderChannel.SUPPLEMENTS,
            reminder(ReminderTarget.Supplement("t", ReminderLead.AT_TIME)).channel,
        )
        assertEquals(
            ReminderChannel.WORKOUTS,
            reminder(ReminderTarget.Workout(DayOfWeek.MONDAY, ReminderLead.AT_TIME)).channel,
        )
        // The channel IS the off switch the app offers instead of building its own (NOTIF-07).
        // Sharing one would mean silencing evening training also silenced every meal reminder.
        assertEquals(
            ReminderChannel.entries.size,
            ReminderChannel.entries.map { it.id }.toSet().size,
            "and the ids are distinct, or the split is cosmetic",
        )
    }
}
