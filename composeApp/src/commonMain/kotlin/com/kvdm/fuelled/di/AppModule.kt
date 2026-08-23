package com.kvdm.fuelled.di

import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.data.remote.MealPlanRepositoryImpl
import com.kvdm.fuelled.data.remote.FoodRepositoryImpl
import com.kvdm.fuelled.data.remote.ProfileRepositoryImpl
import com.kvdm.fuelled.data.remote.SupplementRepositoryImpl
import com.kvdm.fuelled.data.remote.TodayRepositoryImpl
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.repository.ProfileRepository
import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.CopyDayForwardUseCase
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.DeleteSupplementUseCase
import com.kvdm.fuelled.domain.usecase.GetHistoryUseCase
import com.kvdm.fuelled.domain.usecase.ObserveGoalHistoryUseCase
import com.kvdm.fuelled.domain.usecase.ObserveWeightLogUseCase
import com.kvdm.fuelled.domain.usecase.RecordWeightUseCase
import com.kvdm.fuelled.domain.usecase.SaveSupplementUseCase
import com.kvdm.fuelled.domain.usecase.SaveWorkoutDayUseCase
import com.kvdm.fuelled.domain.usecase.SetPrepLeadUseCase
import com.kvdm.fuelled.domain.usecase.SetUnitSystemUseCase
import com.kvdm.fuelled.domain.repository.WeightRepository
import com.kvdm.fuelled.data.remote.WeightRepositoryImpl
import com.kvdm.fuelled.presentation.settings.SettingsViewModel
import com.kvdm.fuelled.presentation.builder.MealBuilderViewModel
import com.kvdm.fuelled.domain.usecase.PlanMealUseCase
import com.kvdm.fuelled.domain.usecase.UpdateGoalsUseCase
import com.kvdm.fuelled.domain.usecase.UpdateProfileNameUseCase
import com.kvdm.fuelled.domain.usecase.SetEntryServingsUseCase
import com.kvdm.fuelled.domain.usecase.RestoreLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.SaveFoodUseCase
import com.kvdm.fuelled.domain.usecase.DeleteFoodUseCase
import com.kvdm.fuelled.domain.usecase.SetFavouriteUseCase
import com.kvdm.fuelled.domain.usecase.GetRecentFoodsUseCase
import com.kvdm.fuelled.domain.usecase.ObserveAppStateUseCase
import com.kvdm.fuelled.domain.usecase.CompleteOnboardingUseCase
import com.kvdm.fuelled.domain.repository.AppStateRepository
import com.kvdm.fuelled.data.remote.AppStateRepositoryImpl
import com.kvdm.fuelled.domain.usecase.GetMealTimesUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.RequestNotificationPermissionUseCase
import com.kvdm.fuelled.domain.usecase.TomorrowUnplannedUseCase
import com.kvdm.fuelled.domain.usecase.ReminderStillWantedUseCase
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.data.remote.WorkoutRepositoryImpl
import com.kvdm.fuelled.domain.usecase.SetMealTimeUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.GetProfileUseCase
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.domain.usecase.MarkEntryLoggedUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SetSupplementTakenUseCase
import com.kvdm.fuelled.presentation.foods.FoodDetailViewModel
import com.kvdm.fuelled.presentation.foods.FoodsViewModel
import com.kvdm.fuelled.presentation.meal.MealTrayInitialTarget
import com.kvdm.fuelled.presentation.meal.MealTrayViewModel
import com.kvdm.fuelled.presentation.mealplan.MealPlanViewModel
import com.kvdm.fuelled.presentation.mealplan.MealTimesViewModel
import com.kvdm.fuelled.presentation.profile.ProfileViewModel
import com.kvdm.fuelled.presentation.supplements.SupplementsViewModel
import com.kvdm.fuelled.presentation.today.TodayViewModel
import com.kvdm.fuelled.presentation.progress.ProgressViewModel
import com.kvdm.fuelled.presentation.workouts.WorkoutWeekViewModel
import com.kvdm.fuelled.presentation.onboarding.OnboardingViewModel
import com.kvdm.fuelled.presentation.foods.FoodEditorViewModel
// cmp:anchor di-imports
import kotlinx.datetime.LocalDate
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val repositoryModule = module {
    // The Room-backed catalog source: the FoodDao comes off the platform-bound AppDatabase.
    // ONE TimeSignal for the whole app, and it must be a `single`.
    //
    // `wake()` is how the foreground / date-changed broadcast tells everything time may have
    // jumped, and it reaches only the consumers sharing this instance. A `factory` here would
    // hand each repository its own private wake channel, and the overnight bug would come back
    // wearing a fix — every screen ticking on its own minute timer, none of them woken.
    single<TimeSignal> { RealTimeSignal() }

    single<FoodRepository> { FoodRepositoryImpl(get<AppDatabase>().foodDao(), get<AppDatabase>().todayDao()) }
    // The Room-backed Today source: the TodayDao comes off the same platform-bound AppDatabase.
    single<TodayRepository> { TodayRepositoryImpl(get<AppDatabase>().todayDao(), get()) }
    // START-01/START-02: the app's own state — onboarding + the first-open instant.
    single<AppStateRepository> { AppStateRepositoryImpl(get<AppDatabase>().appStateDao(), get()) }
    // HIST-06: the weigh-in log, off the same AppDatabase.
    single<WeightRepository> { WeightRepositoryImpl(get<AppDatabase>().weightDao()) }
    // The Room-backed Supplements source: the SupplementDao comes off the same AppDatabase.
    single<SupplementRepository> { SupplementRepositoryImpl(get<AppDatabase>().supplementDao(), get()) }
    // WORK-01: the training week and its done-marks, off the same AppDatabase.
    single<WorkoutRepository> { WorkoutRepositoryImpl(get<AppDatabase>().workoutDao(), get()) }
    // The Room-backed Profile source: the ProfileDao comes off the same platform-bound AppDatabase.
    // Profile joins the ONE goal store's targets (PERS-01), so it reads both DAOs.
    single<ProfileRepository> {
        ProfileRepositoryImpl(get<AppDatabase>().profileDao(), get<AppDatabase>().todayDao())
    }
    // The structured day. It takes BOTH daos: the plan's own stored state (times, ticks) and
    // the log rows the containers are filled from — one day is one read across both.
    single<MealPlanRepository> {
        MealPlanRepositoryImpl(get<AppDatabase>().mealPlanDao(), get<AppDatabase>().todayDao(), get(), appStateDao = get<AppDatabase>().appStateDao())
    }
    // cmp:anchor di-repositories
}

val useCaseModule = module {
    factory { GetFoodsUseCase(get()) }
    factory { SearchFoodsUseCase(get()) }
    factory { GetFoodUseCase(get()) }
    factory { GetTodaySummaryUseCase(get()) }
    // The meal-log write path. AddLogEntriesUseCase takes its clock/zone/dayStartHour from its
    // production defaults; tests construct it directly with a fixed clock (MEAL-08).
    factory { AddLogEntriesUseCase(get()) }
    factory { DeleteLogEntryUseCase(get()) }
    factory { MarkEntryLoggedUseCase(get()) }
    factory { GetSupplementStackUseCase(get()) }
    // SUPP-12: taking a dose re-arms, so its remaining rungs today are dropped.
    factory { SetSupplementTakenUseCase(get(), get()) }
    factory { GetProfileUseCase(get()) }
    // The structured day. GetPlanDayUseCase takes its clock/zone/dayStartHour from production
    // defaults, like the tray's write path; tests construct it with a fixed clock (PLAN-23).
    factory { GetPlanDayUseCase(get(), get()) }
    factory { GetMealTimesUseCase(get()) }
    factory { SetSlotDoneUseCase(get()) }
    factory { SetWaterDoneUseCase(get()) }
    factory { CopyDayForwardUseCase(get()) }
    // The ReminderScheduler is PLATFORM-bound: Android arms real alarms, desktop and iOS bind
    // NoOpReminderScheduler (brief decision 9 — iOS notifications are deliberately unpromised).
    // Every reminder family rides ONE arm (SUPP-12/WORK-06): the scheduler replaces the whole
    // armed set, so an arm that did not know about the stack and the week would cancel their
    // alarms as a side effect of ticking breakfast.
    factory { ArmMealRemindersUseCase(get(), get(), get(), get(), get(), get(), get()) }
    // NOTIF-08: the delivery-time re-ask — is this reminder still wanted at the moment it fires?
    factory { ReminderStillWantedUseCase(get(), get(), get()) }
    // NOTIF-04/NOTIF-06: the nudge's one emptiness question — the arm path and the Android
    // delivery re-check both resolve THIS, so "unplanned" cannot drift between the two moments.
    factory { TomorrowUnplannedUseCase(get(), get()) }
    // NOTIF-01/NOTIF-02: the once-ever permission ask, called from Today's open.
    factory { RequestNotificationPermissionUseCase(get(), get(), get()) }
    factory { SetMealTimeUseCase(get(), get()) }
    // The week in review (JRN-01): composed from the two observed reads above — no new
    // repository, deliberately (TODAY-13's no-second-path discipline).
    factory { GetHistoryUseCase(get(), get(), get()) }
    factory { ObserveGoalHistoryUseCase(get()) }
    // BFL-06: one composed meal into one slot across many days.
    factory { PlanMealUseCase(get()) }
    // HIST-06..08: the weigh-in log — the one stored thing on the Progress surface.
    factory { ObserveWeightLogUseCase(get(), get()) }
    factory { RecordWeightUseCase(get(), get()) }
    // SET-02/SET-07: the settings writes. SetPrepLead re-arms as part of the write (SET-08).
    factory { SetUnitSystemUseCase(get()) }
    factory { SetPrepLeadUseCase(get(), get()) }
    // SET-04/SET-05: the stack becomes the user's.
    // SUPP-12/WORK-07: every stack and week write re-arms, so a changed schedule, a new time
    // or a deleted row takes effect immediately rather than at the next app open.
    factory { SaveSupplementUseCase(get(), get()) }
    factory { DeleteSupplementUseCase(get(), get()) }
    factory { SaveWorkoutDayUseCase(get(), get()) }
    factory { UpdateGoalsUseCase(get()) }
    factory { UpdateProfileNameUseCase(get()) }
    factory { SetEntryServingsUseCase(get()) }
    factory { RestoreLogEntryUseCase(get()) }
    factory { SaveFoodUseCase(get()) }
    factory { DeleteFoodUseCase(get()) }
    factory { SetFavouriteUseCase(get()) }
    factory { GetRecentFoodsUseCase(get()) }
    factory { ObserveAppStateUseCase(get()) }
    factory { CompleteOnboardingUseCase(get()) }
    // cmp:anchor di-usecases
}

// ARCH-14: explicit `viewModel { }` factories only — never reflection-based viewModelOf.
// It resolves EVERY declared constructor parameter from the graph, silently ignoring
// default values, which turns a compile-time wiring error into a runtime resolution crash.
// This module had nine of them; MealBuilderViewModel's was already live ammunition (four
// defaulted parameters — MealSlot, Clock, TimeZone, Int — none of them in the graph), and
// nothing caught it because the builder is reachable from no nav destination and has no
// golden. One get() per constructor dependency, and defaults left to the constructor.
val viewModelModule = module {
    viewModel { FoodsViewModel(get(), get()) }
    // The detail's log path (UX-03) takes its clock/zone/dayStartHour from production
    // defaults, so it is wired by hand like the tray — viewModelOf would try to resolve
    // those three from the graph. Tests construct it directly with a FixedClock.
    viewModel { FoodDetailViewModel(get(), get(), get()) }
    viewModel { TodayViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // SUPP-08: due-ness depends on the logical day, so this one takes a clock/zone/dayStartHour
    // from production defaults — wired by hand for the same reason the tray is, since
    // viewModelOf would try to resolve all three from the graph and fail on the zone.
    viewModel { SupplementsViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    // Two graph dependencies; initialSlot/clock/zone/dayStartHour keep their production
    // defaults. viewModelOf resolved all six and would have thrown on MealSlot the first
    // time this screen was opened (ARCH-14).
    viewModel { MealBuilderViewModel(get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    // Like the tray below, the plan's opening day comes from the CALL SITE: the nav destination
    // passes `plan/{date}`'s date as a Koin parameter, so the ViewModel is aimed before its
    // first frame and nothing re-aims it afterwards (PLAN-24). Required for the same reason —
    // the nav layer never composes the plan without a resolved date.
    viewModel { params ->
        MealPlanViewModel(params.get<LocalDate>(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { MealTimesViewModel(get(), get(), get(), get()) }
    // WORK-05: same reason as Supplements above — the training window is derived from the
    // logical day, so the clock/zone/dayStartHour come from production defaults.
    viewModel { ProgressViewModel(get(), get(), get(), get(), get(), get()) }
    // NAV-06: the Training tab. WorkoutRepository + TimeSignal; zone and dayStartHour default.
    viewModel { WorkoutWeekViewModel(get(), get()) }
    viewModel { OnboardingViewModel(get(), get(), get(), get()) }
    viewModel { FoodEditorViewModel(get(), get(), get()) }
    // The tray takes its clock/zone/dayStartHour from its production defaults, so it is wired
    // by hand rather than with viewModelOf — which would try to resolve those three from the
    // graph. Tests construct it directly with a FixedClock (MEAL-10).
    //
    // The opening target comes from the CALL SITE, not the graph: the nav destination passes
    // the tap's target as a Koin parameter (TODAY-07, PLAN-04), so it is set before the first
    // frame. It is REQUIRED (MEAL-10): the nav layer never composes the tray without one, so a
    // missing parameter here is a wiring bug and fails loudly rather than guessing a meal.
    viewModel { params ->
        MealTrayViewModel(get(), get(), get(), initialTarget = params.get<MealTrayInitialTarget>())
    }
    // cmp:anchor di-viewmodels
}

// Aggregated common modules, started from AppApplication (Android) and KoinHelper (iOS).
val appModules = listOf(repositoryModule, useCaseModule, viewModelModule)
