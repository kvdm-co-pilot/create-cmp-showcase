package com.kvdm.fuelled.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.flow.first

/**
 * The structured day's derivations: slot times, the water schedule that falls out of them, and
 * the focus/lateness the current day is read through. Pure values in, values out — no clock is
 * read here, so "now" is always the caller's choice and every assertion is deterministic
 * (ARCH-12).
 */
class MealPlanTest {

    private fun at(hour: Int, minute: Int) = LocalTime(hour, minute)

    // SPEC: PLAN-01
    @Test
    fun `the six containers are the slot enum in day order`() {
        assertEquals(6, MealSlot.entries.size)
        assertEquals(MealSlot.BREAKFAST, MealSlot.entries.first())
        assertEquals(MealSlot.EVENING_SNACK, MealSlot.entries.last())
    }

    // SPEC: PLAN-05
    @Test
    fun `an unset profile gets the body-for-life defaults and is never left timeless`() {
        val times = MealTimes()
        assertEquals(at(7, 0), times[MealSlot.BREAKFAST])
        assertEquals(at(9, 30), times[MealSlot.MORNING_SNACK])
        assertEquals(at(12, 0), times[MealSlot.LUNCH])
        assertEquals(at(14, 30), times[MealSlot.AFTERNOON_SNACK])
        assertEquals(at(17, 0), times[MealSlot.DINNER])
        assertEquals(at(19, 30), times[MealSlot.EVENING_SNACK])
        // Every slot resolves even when only one has ever been stored.
        val partial = MealTimes(mapOf(MealSlot.LUNCH to at(13, 0)))
        assertEquals(6, partial.inSlotOrder().size)
        assertEquals(at(7, 0), partial[MealSlot.BREAKFAST])
    }

    // SPEC: PLAN-06
    @Test
    fun `changing one slot's time moves that slot and no other`() {
        val times = MealTimes().withTime(MealSlot.LUNCH, at(13, 15))
        assertEquals(at(13, 15), times[MealSlot.LUNCH])
        MealSlot.entries.filter { it != MealSlot.LUNCH }.forEach { slot ->
            assertEquals(DEFAULT_MEAL_TIMES.getValue(slot), times[slot], "$slot moved")
        }
    }

    // SPEC: PLAN-06
    @Test
    fun `no write can produce a non-ascending timetable - dinner at 05-00 cannot exist`() {
        // The shift-worker case: rearranging the day must never let a later slot cross an
        // earlier one, because every derivation (water midpoints, focus order) assumes the
        // enum order IS clock order.
        val broken = MealTimes().withTime(MealSlot.DINNER, at(5, 0))
        val ordered = broken.inSlotOrder().map { (_, t) -> t }
        assertEquals(ordered, ordered.sorted(), "timetable must stay strictly ascending")
        // The write was coerced into the window between the afternoon snack and the
        // evening snack, not applied verbatim.
        assertTrue(broken[MealSlot.DINNER] > broken[MealSlot.AFTERNOON_SNACK])
        assertTrue(broken[MealSlot.DINNER] < broken[MealSlot.EVENING_SNACK])
        // And an in-range change is applied exactly as given.
        assertEquals(at(18, 0), MealTimes().withTime(MealSlot.DINNER, at(18, 0))[MealSlot.DINNER])
    }

    // SPEC: PLAN-08
    @Test
    fun `six water containers of 500ml sit at the midpoints, the last 75 minutes after dinner's snack`() {
        val water = waterSchedule()
        assertEquals(6, water.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), water.map { it.index })
        assertTrue(water.all { it.millilitres == 500 })
        assertEquals(3000, WATER_DAY_GOAL_ML)
        // Midpoints of the default timetable: 07:00→09:30 is 08:15, 09:30→12:00 is 10:45, …
        assertEquals(listOf(at(8, 15), at(10, 45), at(13, 15), at(15, 45), at(18, 15)), water.take(5).map { it.time })
        // The evening snack has no next meal to split the difference with: 19:30 + 75 min.
        assertEquals(at(20, 45), water.last().time)
        assertEquals(MealSlot.EVENING_SNACK, water.last().afterSlot)
    }

    // SPEC: PLAN-08
    @Test
    fun `the last water reminder clamps at 23-59 - it never wraps past midnight`() {
        // A 23:30 evening snack would put +75 min at 00:45 — the TOP of a time-ordered
        // list for a reminder that belongs at the BOTTOM of the day.
        val lateNight = MealTimes()
            .withTime(MealSlot.DINNER, at(21, 0))
            .withTime(MealSlot.EVENING_SNACK, at(23, 30))
        val water = waterSchedule(lateNight)
        assertEquals(at(23, 59), water.last().time)
        val ordered = water.map { it.time }
        assertEquals(ordered, ordered.sorted(), "water times stay in day order")
    }

    // SPEC: PLAN-09
    @Test
    fun `moving a meal time moves the water on both sides of it, with nothing left behind`() {
        val movedLunch = waterSchedule(MealTimes().withTime(MealSlot.LUNCH, at(13, 0)))
        // Water 2 follows the morning snack toward the later lunch; water 3 follows lunch.
        assertEquals(at(11, 15), movedLunch[1].time) // 09:30 → 13:00
        assertEquals(at(13, 45), movedLunch[2].time) // 13:00 → 14:30
        // Untouched neighbours stay exactly where the defaults put them.
        assertEquals(at(8, 15), movedLunch[0].time)
        assertEquals(at(15, 45), movedLunch[3].time)
    }

    // SPEC: PLAN-15
    @Test
    fun `focus is the earliest slot not yet done`() {
        assertEquals(
            DayFocus.Slot(MealSlot.BREAKFAST, late = false),
            focusFor(doneSlots = emptySet(), now = at(7, 0)),
        )
        assertEquals(
            DayFocus.Slot(MealSlot.LUNCH, late = false),
            focusFor(doneSlots = setOf(MealSlot.BREAKFAST, MealSlot.MORNING_SNACK), now = at(12, 0)),
        )
        // Done out of order, within the snack's own window: the EARLIEST outstanding slot
        // still holds focus — it is not yet missed, because lunch's time has not arrived.
        assertEquals(
            DayFocus.Slot(MealSlot.MORNING_SNACK, late = false),
            focusFor(doneSlots = setOf(MealSlot.BREAKFAST, MealSlot.LUNCH), now = at(9, 30)),
        )
    }

    // SPEC: PLAN-15, PLAN-19
    @Test
    fun `a skipped snack goes MISSED at the next meal's time and stops holding focus hostage`() {
        // THE Body-for-LIFE edge case: skip the 09:30 snack at work. The old rule kept
        // "Snack, late since 09:30" focused at 19:00 and dinner never became next.
        val done = setOf(MealSlot.BREAKFAST)
        // At 19:00 the snack, lunch, and afternoon snack are all missed — focus is DINNER.
        assertEquals(
            setOf(MealSlot.MORNING_SNACK, MealSlot.LUNCH, MealSlot.AFTERNOON_SNACK),
            missedSlots(done, at(19, 0)),
        )
        // Dinner (due 17:00) holds focus — and reads LATE, honestly: it is past its own
        // grace but not yet missed, because the evening snack's 19:30 has not arrived.
        assertEquals(DayFocus.Slot(MealSlot.DINNER, late = true), focusFor(done, at(19, 0)))
        // The boundary is half-open: at 12:00 sharp the snack is missed and lunch is next.
        assertEquals(DayFocus.Slot(MealSlot.LUNCH, late = false), focusFor(done, at(12, 0)))
        // One second before, the snack still holds focus (late — past its grace).
        assertEquals(DayFocus.Slot(MealSlot.MORNING_SNACK, late = true), focusFor(done, at(11, 59)))
    }

    // SPEC: PLAN-19
    @Test
    fun `the last slot never reads missed and a ticked slot never reads missed`() {
        // The evening snack has no successor — it stays focused (late) until the day ends,
        // which is the day-boundary's job (MEAL-02), not a missed marker's.
        val allButLast = MealSlot.entries.dropLast(1).toSet()
        assertEquals(emptySet(), missedSlots(allButLast, at(23, 59)))
        assertEquals(DayFocus.Slot(MealSlot.EVENING_SNACK, late = true), focusFor(allButLast, at(23, 59)))
        // Done takes precedence: a ticked slot is done, never missed (PLAN-13/PLAN-14).
        assertEquals(
            emptySet(),
            missedSlots(MealSlot.entries.toSet(), at(23, 0)),
        )
    }

    // SPEC: PLAN-16
    @Test
    fun `the focused slot reads late only after the 30 minute grace`() {
        val done = setOf(MealSlot.BREAKFAST, MealSlot.MORNING_SNACK)
        assertEquals(30, LATE_GRACE_MINUTES)
        // Due at 12:00 — at the time, and through the grace, it is next rather than late.
        assertFalse((focusFor(done, at(12, 0)) as DayFocus.Slot).late)
        assertFalse((focusFor(done, at(12, 30)) as DayFocus.Slot).late)
        assertTrue((focusFor(done, at(12, 31)) as DayFocus.Slot).late)
        // Early is never late.
        assertFalse((focusFor(done, at(11, 0)) as DayFocus.Slot).late)
        // And lateness is BOUNDED: at the next slot's time (14:30) lunch goes missed and
        // the afternoon snack is next — "late" can never outlive its own meal window.
        assertEquals(DayFocus.Slot(MealSlot.AFTERNOON_SNACK, late = false), focusFor(done, at(14, 30)))
    }

    // SPEC: PLAN-17
    @Test
    fun `a finished day points at tomorrow's breakfast rather than at nothing`() {
        assertEquals(
            DayFocus.NextDayBreakfast,
            focusFor(doneSlots = MealSlot.entries.toSet(), now = at(20, 0)),
        )
    }

    // SPEC: PLAN-17
    @Test
    fun `a free day advances the same way - ignore everything and the day just goes quiet`() {
        // Decision 11 defers the free-day FEATURE, but taking one must not corrupt the
        // surface: with nothing done all day, everything except the evening snack is
        // missed by evening; tick (or miss) that last one and focus is tomorrow.
        val nothingDone = emptySet<MealSlot>()
        assertEquals(
            DayFocus.Slot(MealSlot.EVENING_SNACK, late = true),
            focusFor(nothingDone, at(23, 0)),
            "only the successor-less last slot still competes on an ignored day",
        )
        assertEquals(
            DayFocus.NextDayBreakfast,
            focusFor(doneSlots = setOf(MealSlot.EVENING_SNACK), now = at(23, 0)),
        )
    }

    // SPEC: PLAN-25
    @Test
    fun `the focused slot is only DUE once its time arrives - next is not the same as now`() {
        fun dayAt(hour: Int, minute: Int, done: Set<MealSlot> = emptySet()) = buildPlanDay(
            date = LocalDate(2026, 7, 30),
            isCurrentDay = true,
            now = at(hour, minute),
            times = MealTimes(),
            entriesBySlot = emptyMap(),
            doneSlots = done,
            waterTicks = emptySet(),
        )

        // 07:02, the morning of the on-device report: breakfast is focused and its time HAS
        // come, so "up now" is true of it — and the grace has not run out, so it is not late.
        val morning = dayAt(7, 2)
        val breakfast = morning.slots.first { it.slot == MealSlot.BREAKFAST }
        assertTrue(breakfast.focused)
        assertTrue(breakfast.due, "07:02 is past a 07:00 slot")
        assertFalse(breakfast.late)

        // The 09:30 snack at the same moment: the day's next-but-one, and nothing about it is
        // happening now.
        assertFalse(morning.slots.first { it.slot == MealSlot.MORNING_SNACK }.due)

        // Tick breakfast and the snack BECOMES the focused slot — still at 07:02, still not
        // due. This is the exact state that rendered "NEXT · up now" two and a half hours early.
        val snack = dayAt(7, 2, done = setOf(MealSlot.BREAKFAST))
            .slots.first { it.slot == MealSlot.MORNING_SNACK }
        assertTrue(snack.focused, "with breakfast done the 09:30 snack is next")
        assertFalse(snack.due, "but it is not up NOW — the clock says 07:02")

        // PLAN-23: a day that is not the current one makes no claim either way.
        assertTrue(
            buildPlanDay(
                date = LocalDate(2026, 8, 5),
                isCurrentDay = false,
                now = at(23, 0),
                times = MealTimes(),
                entriesBySlot = emptyMap(),
                doneSlots = emptySet(),
                waterTicks = emptySet(),
            ).slots.none { it.due },
            "a future day being planned is not 'due' at any hour",
        )
    }
}
