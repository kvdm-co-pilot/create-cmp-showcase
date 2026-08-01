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
import com.kvdm.fuelled.domain.usecase.ObserveWeightLogUseCase
import com.kvdm.fuelled.domain.usecase.RecordWeightUseCase
import com.kvdm.fuelled.domain.usecase.SaveSupplementUseCase
import com.kvdm.fuelled.domain.usecase.SetPrepLeadUseCase
import com.kvdm.fuelled.domain.usecase.SetUnitSystemUseCase
import com.kvdm.fuelled.domain.repository.WeightRepository
import com.kvdm.fuelled.data.remote.WeightRepositoryImpl
import com.kvdm.fuelled.presentation.settings.SettingsViewModel
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
import com.kvdm.fuelled.presentation.onboarding.OnboardingViewModel
import com.kvdm.fuelled.presentation.foods.FoodEditorViewModel
// cmp:anchor di-imports
import kotlinx.datetime.LocalDate
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
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
    factory { SetSupplementTakenUseCase(get()) }
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
    factory { ArmMealRemindersUseCase(get(), get(), get()) }
    factory { SetMealTimeUseCase(get(), get()) }
    // The week in review (JRN-01): composed from the two observed reads above — no new
    // repository, deliberately (TODAY-13's no-second-path discipline).
    factory { GetHistoryUseCase(get(), get()) }
    // HIST-06..08: the weigh-in log — the one stored thing on the Progress surface.
    factory { ObserveWeightLogUseCase(get(), get()) }
    factory { RecordWeightUseCase(get(), get()) }
    // SET-02/SET-07: the settings writes. SetPrepLead re-arms as part of the write (SET-08).
    factory { SetUnitSystemUseCase(get()) }
    factory { SetPrepLeadUseCase(get(), get()) }
    // SET-04/SET-05: the stack becomes the user's.
    factory { SaveSupplementUseCase(get()) }
    factory { DeleteSupplementUseCase(get()) }
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

val viewModelModule = module {
    viewModelOf(::FoodsViewModel)
    // The detail's log path (UX-03) takes its clock/zone/dayStartHour from production
    // defaults, so it is wired by hand like the tray — viewModelOf would try to resolve
    // those three from the graph. Tests construct it directly with a FixedClock.
    viewModel { FoodDetailViewModel(get(), get(), get()) }
    viewModelOf(::TodayViewModel)
    viewModelOf(::SupplementsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ProfileViewModel)
    // Like the tray below, the plan's opening day comes from the CALL SITE: the nav destination
    // passes `plan/{date}`'s date as a Koin parameter, so the ViewModel is aimed before its
    // first frame and nothing re-aims it afterwards (PLAN-24). Required for the same reason —
    // the nav layer never composes the plan without a resolved date.
    viewModel { params ->
        MealPlanViewModel(params.get<LocalDate>(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModelOf(::MealTimesViewModel)
    viewModelOf(::ProgressViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::FoodEditorViewModel)
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
