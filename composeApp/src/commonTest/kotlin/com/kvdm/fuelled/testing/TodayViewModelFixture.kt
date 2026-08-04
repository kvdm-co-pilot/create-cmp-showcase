package com.kvdm.fuelled.testing

import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.RestoreLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.SetEntryServingsUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.domain.usecase.RequestNotificationPermissionUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.domain.usecase.TomorrowUnplannedUseCase
import com.kvdm.fuelled.presentation.today.TodayViewModel
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FakeWorkoutRepository
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal

/**
 * The instant every Today/plan test freezes at unless it says otherwise: **2026-07-22 12:45
 * UTC**, a Wednesday.
 *
 * Mid-day is chosen deliberately — it is the only time of day at which the interesting states
 * coexist: breakfast and the morning snack are behind, lunch is focused and past its grace, the
 * afternoon is still upcoming. A test frozen at 03:00 would agree with almost any
 * implementation.
 */
val TEST_NOW: Instant = Instant.parse("2026-07-22T12:45:00Z")

/** UTC, so the logical day and the wall clock cannot disagree for reasons the test is not about. */
val TEST_ZONE: TimeZone = TimeZone.UTC

/**
 * Build a [TodayViewModel] over hand-written fakes.
 *
 * Today reads three sources and writes through the plan's use cases (TODAY-13), so its
 * constructor is six parameters wide. Assembling it here keeps every test's setup to the fakes
 * it actually cares about, and — more importantly — means all of them wire the SAME real use
 * cases the app does. A test that constructed a shortcut would stop proving TODAY-13 the moment
 * the shortcut and the app diverged.
 */
fun todayViewModel(
    today: FakeTodayRepository = FakeTodayRepository(),
    plan: FakeMealPlanRepository = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE),
    supplements: FakeSupplementRepository = FakeSupplementRepository(),
    scheduler: FakeReminderScheduler = FakeReminderScheduler(),
    clock: Clock = FixedClock(TEST_NOW),
    appState: FakeAppStateRepository = FakeAppStateRepository(),
    /** WORK-03: the training week Today's card reads. Defaults to the seeded split. */
    workouts: FakeWorkoutRepository = FakeWorkoutRepository(),
): TodayViewModel {
    val getPlanDay = GetPlanDayUseCase(plan, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE)
    val armReminders = ArmMealRemindersUseCase(
        plan,
        scheduler,
        appState,
        TomorrowUnplannedUseCase(plan, FakeTimeSignal(TEST_NOW), TEST_ZONE),
    )
    return TodayViewModel(
        getTodaySummary = GetTodaySummaryUseCase(today),
        getPlanDay = getPlanDay,
        getSupplementStack = GetSupplementStackUseCase(supplements),
        setSlotDone = SetSlotDoneUseCase(plan),
        setWaterDone = SetWaterDoneUseCase(plan),
        armReminders = armReminders,
        requestNotificationPermission = RequestNotificationPermissionUseCase(appState, scheduler, armReminders),
        deleteLogEntry = DeleteLogEntryUseCase(today),
            setEntryServings = SetEntryServingsUseCase(today),
            restoreLogEntry = RestoreLogEntryUseCase(today),
        workouts = workouts,
    )
}
