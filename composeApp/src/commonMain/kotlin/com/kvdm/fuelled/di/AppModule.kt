package com.kvdm.fuelled.di

import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.data.remote.FoodRepositoryImpl
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.usecase.GetFoodUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.presentation.foods.FoodDetailViewModel
import com.kvdm.fuelled.presentation.foods.FoodsViewModel
// cmp:anchor di-imports
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val repositoryModule = module {
    // The Room-backed catalog source: the FoodDao comes off the platform-bound AppDatabase.
    single<FoodRepository> { FoodRepositoryImpl(get<AppDatabase>().foodDao()) }
    // cmp:anchor di-repositories
}

val useCaseModule = module {
    factory { GetFoodsUseCase(get()) }
    factory { SearchFoodsUseCase(get()) }
    factory { GetFoodUseCase(get()) }
    // cmp:anchor di-usecases
}

val viewModelModule = module {
    viewModelOf(::FoodsViewModel)
    viewModelOf(::FoodDetailViewModel)
    // cmp:anchor di-viewmodels
}

// Aggregated common modules, started from AppApplication (Android) and KoinHelper (iOS).
val appModules = listOf(repositoryModule, useCaseModule, viewModelModule)
