package com.kvdm.fuelled.di

import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.data.remote.FoodRepositoryImpl
import com.kvdm.fuelled.data.remote.ProfileRepositoryImpl
import com.kvdm.fuelled.data.remote.SupplementRepositoryImpl
import com.kvdm.fuelled.data.remote.TodayRepositoryImpl
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.repository.ProfileRepository
import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
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
import com.kvdm.fuelled.presentation.profile.ProfileViewModel
import com.kvdm.fuelled.presentation.supplements.SupplementsViewModel
import com.kvdm.fuelled.presentation.today.TodayViewModel
// cmp:anchor di-imports
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val repositoryModule = module {
    // The Room-backed catalog source: the FoodDao comes off the platform-bound AppDatabase.
    single<FoodRepository> { FoodRepositoryImpl(get<AppDatabase>().foodDao()) }
    // The Room-backed Today source: the TodayDao comes off the same platform-bound AppDatabase.
    single<TodayRepository> { TodayRepositoryImpl(get<AppDatabase>().todayDao()) }
    // The Room-backed Supplements source: the SupplementDao comes off the same AppDatabase.
    single<SupplementRepository> { SupplementRepositoryImpl(get<AppDatabase>().supplementDao()) }
    // The Room-backed Profile source: the ProfileDao comes off the same platform-bound AppDatabase.
    single<ProfileRepository> { ProfileRepositoryImpl(get<AppDatabase>().profileDao()) }
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
    // cmp:anchor di-usecases
}

val viewModelModule = module {
    viewModelOf(::FoodsViewModel)
    viewModelOf(::FoodDetailViewModel)
    viewModelOf(::TodayViewModel)
    viewModelOf(::SupplementsViewModel)
    viewModelOf(::ProfileViewModel)
    // The tray takes its clock/zone/dayStartHour from its production defaults, so it is wired
    // by hand rather than with viewModelOf — which would try to resolve those three from the
    // graph. Tests construct it directly with a FixedClock (MEAL-04/MEAL-10).
    //
    // The opening target comes from the CALL SITE, not the graph: the nav destination passes
    // the tap's target as a Koin parameter (TODAY-07/TODAY-08), so it is set before the first
    // frame. No parameter — or an unparseable route argument — resolves to null, which is the
    // ViewModel's own clock-derived default.
    viewModel { params ->
        MealTrayViewModel(get(), get(), get(), initialTarget = params.getOrNull<MealTrayInitialTarget>())
    }
    // cmp:anchor di-viewmodels
}

// Aggregated common modules, started from AppApplication (Android) and KoinHelper (iOS).
val appModules = listOf(repositoryModule, useCaseModule, viewModelModule)
