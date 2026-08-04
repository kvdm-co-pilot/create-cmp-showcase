package com.kvdm.fuelled.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reminder ladder (SUPP-12 / WORK-06) — the policy that decides WHICH alarms exist and
 * WHEN, with no alarm manager anywhere near it.
 *
 * This is the layer where the 0.5.0 defect class lives: reminders that were "armed" correctly
 * by a scheduler nobody could question. Every rung here is asserted as a (date, time) pair, so
 * "fires on the right day" is a test rather than a hope.
 */
class ReminderLadderTest {

    private val monday = LocalDate(2026, 8, 3)
    private val tuesday = LocalDate(2026, 8, 4)
    private val thursday = LocalDate(2026, 8, 6)
    private val nightBefore = LocalTime(21, 30)
    private val allowed = ReminderCapability(notificationsAllowed = true, exactAlarmsAllowed = true)

    private fun trt(leads: Set<ReminderLead> = ReminderLead.DEFAULT) = Supplement(
        id = "t",
        name = "Testosterone",
        dose = "100 mg",
        timing = SupplementTiming.MORNING,
        taken = false,
        schedule = SupplementSchedule.OnDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
        remindAt = LocalTime(8, 0),
        leads = leads,
    )

    private fun List<MealReminder>.at(lead: ReminderLead): Pair<LocalDate?, LocalTime>? =
        firstOrNull { (it.target as? ReminderTarget.Supplement)?.lead == lead }
            ?.let { it.onDate to it.time }

    // SPEC: SUPP-12
    @Test
    fun `all three rungs are armed on the schedule's own dates, not on tomorrow`() {
        // Tuesday morning. The next dose is THURSDAY — a "next occurrence of 08:00" scheduler
        // would ring this on Wednesday, every day, forever.
        val armed = supplementReminders(
            stack = listOf(trt()),
            today = tuesday,
            now = LocalTime(9, 0),
            nightBefore = nightBefore,
            capability = allowed,
        )

        assertEquals(3, armed.size, "one alarm per rung")
        assertEquals(thursday to LocalTime(8, 0), armed.at(ReminderLead.AT_TIME))
        assertEquals(thursday to LocalTime(7, 30), armed.at(ReminderLead.THIRTY_MIN))
        assertEquals(
            LocalDate(2026, 8, 5) to nightBefore,
            armed.at(ReminderLead.NIGHT_BEFORE),
            "the night-before rung fires the EVENING BEFORE the dose, not on the dose's own day",
        )
    }

    // SPEC: SUPP-12
    @Test
    fun `a rung whose moment has passed today rolls to the next due date, never into the past`() {
        // Monday at 09:00: today's 08:00 dose time is gone. The at-time rung must not arm an
        // instant already behind us (it would fire immediately or never) — it belongs to
        // Thursday now.
        val armed = supplementReminders(
            stack = listOf(trt()),
            today = monday,
            now = LocalTime(9, 0),
            nightBefore = nightBefore,
            capability = allowed,
        )

        assertEquals(thursday to LocalTime(8, 0), armed.at(ReminderLead.AT_TIME))
    }

    // SPEC: SUPP-12
    @Test
    fun `a dose already taken today silences today's rungs and arms the next occurrence`() {
        val armed = supplementReminders(
            stack = listOf(trt()),
            today = monday,
            now = LocalTime(6, 0), // before the 08:00 dose — today's rungs are still live...
            nightBefore = nightBefore,
            capability = allowed,
            takenToday = setOf("t"), // ...but it has already been swallowed.
        )

        assertEquals(
            thursday to LocalTime(8, 0),
            armed.at(ReminderLead.AT_TIME),
            "an alarm for a dose already taken is pure noise",
        )
    }

    // SPEC: SUPP-12
    @Test
    fun `the night-before rung is never armed for a daily schedule`() {
        val daily = trt().copy(schedule = SupplementSchedule.Daily)

        val armed = supplementReminders(
            stack = listOf(daily),
            today = monday,
            now = LocalTime(6, 0),
            nightBefore = nightBefore,
            capability = allowed,
        )

        assertEquals(2, armed.size, "'tomorrow is creatine day' is noise — the rung names an exception")
        assertTrue(armed.none { (it.target as? ReminderTarget.Supplement)?.lead == ReminderLead.NIGHT_BEFORE })
    }

    // SPEC: SUPP-13
    @Test
    fun `no time and no rungs each arm nothing`() {
        val noTime = trt().copy(remindAt = null)
        val noRungs = trt(leads = emptySet())

        listOf(noTime, noRungs).forEach { supplement ->
            val armed = supplementReminders(
                stack = listOf(supplement),
                today = monday,
                now = LocalTime(6, 0),
                nightBefore = nightBefore,
                capability = allowed,
            )
            assertTrue(armed.isEmpty(), "half a reminder arms nothing at all")
        }
    }

    // SPEC: SUPP-12
    @Test
    fun `denied notifications still produce the intended list, carrying UNAVAILABLE`() {
        val denied = ReminderCapability(notificationsAllowed = false, exactAlarmsAllowed = false)

        val armed = supplementReminders(
            stack = listOf(trt()),
            today = tuesday,
            now = LocalTime(9, 0),
            nightBefore = nightBefore,
            capability = denied,
        )

        // The same honesty remindersFor keeps: the list is what the app INTENDS, so a surface
        // can say plainly that reminders are off rather than render an empty schedule.
        assertEquals(3, armed.size)
        assertTrue(armed.all { it.mode == ReminderMode.UNAVAILABLE })
    }

    // SPEC: SUPP-12
    @Test
    fun `each rung of each supplement gets its own stable key`() {
        val armed = supplementReminders(
            stack = listOf(trt()),
            today = tuesday,
            now = LocalTime(9, 0),
            nightBefore = nightBefore,
            capability = allowed,
        )

        assertEquals(
            setOf("supp_t_NIGHT_BEFORE", "supp_t_THIRTY_MIN", "supp_t_AT_TIME"),
            armed.map { it.key }.toSet(),
            "three rungs are three alarms — separately replaceable and separately cancellable",
        )
    }

    // SPEC: WORK-06
    @Test
    fun `a training day arms its own rungs at its own time, on that weekday's date`() {
        val week = WorkoutWeek(
            mapOf(
                DayOfWeek.MONDAY to WorkoutDayPlan("Upper body", LocalTime(18, 0), ReminderLead.DEFAULT),
                DayOfWeek.SATURDAY to WorkoutDayPlan("Cardio", LocalTime(9, 0), ReminderLead.DEFAULT),
            ),
        )

        val armed = workoutReminders(
            week = week,
            today = tuesday,
            now = LocalTime(9, 0),
            nightBefore = nightBefore,
            capability = allowed,
        )

        // WORK-07: per-day times. Saturday's session is at 09:00 and Monday's at 18:00, and
        // both are armed from the same call — one time for the whole week would be wrong here.
        val saturday = armed.first { it.key == "workout_SATURDAY_AT_TIME" }
        val nextMonday = armed.first { it.key == "workout_MONDAY_AT_TIME" }
        assertEquals(LocalDate(2026, 8, 8) to LocalTime(9, 0), saturday.onDate to saturday.time)
        assertEquals(LocalDate(2026, 8, 10) to LocalTime(18, 0), nextMonday.onDate to nextMonday.time)
    }

    // SPEC: WORK-06
    @Test
    fun `a rest day and a session already done arm nothing`() {
        val week = WorkoutWeek(
            mapOf(
                // A rest day carrying a stale time — the shape a day left behind when it
                // stopped being a training day. It must not ring.
                DayOfWeek.TUESDAY to WorkoutDayPlan(null, LocalTime(18, 0), ReminderLead.DEFAULT),
                DayOfWeek.THURSDAY to WorkoutDayPlan("Cardio", LocalTime(18, 0), ReminderLead.DEFAULT),
            ),
        )

        val armed = workoutReminders(
            week = week,
            today = tuesday,
            now = LocalTime(9, 0),
            nightBefore = nightBefore,
            capability = allowed,
            doneDates = setOf(thursday),
        )

        assertTrue(armed.none { it.key.startsWith("workout_TUESDAY") }, "a rest day has nothing to announce")
        assertTrue(
            armed.none { it.onDate == thursday },
            "and a session already marked done is not announced again",
        )
    }

    // SPEC: SUPP-12, WORK-06
    @Test
    fun `night before rides the plan-tomorrow evening rather than a setting of its own`() {
        val times = MealTimes()
        // One derived evening moment, shared by NOTIF-04's nudge and both ladders — so moving
        // the evening snack moves all three together and nothing drifts out of step.
        val nudge = planTomorrowNudge(times, tomorrowUnplanned = true, capability = allowed)
        assertEquals(times.eveningNudgeTime, nudge?.time)

        val armed = supplementReminders(
            stack = listOf(trt()),
            today = tuesday,
            now = LocalTime(9, 0),
            nightBefore = times.eveningNudgeTime,
            capability = allowed,
        )
        assertEquals(times.eveningNudgeTime, armed.at(ReminderLead.NIGHT_BEFORE)?.second)
    }
}
