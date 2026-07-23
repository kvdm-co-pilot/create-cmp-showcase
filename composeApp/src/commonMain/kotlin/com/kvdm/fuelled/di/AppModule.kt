package com.kvdm.fuelled.di

import com.kvdm.fuelled.data.remote.ItemRepositoryImpl
import com.kvdm.fuelled.domain.repository.ItemRepository
import com.kvdm.fuelled.domain.usecase.GetItemsUseCase
import com.kvdm.fuelled.presentation.home.HomeViewModel
// cmp:anchor di-imports
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val repositoryModule = module {
    single<ItemRepository> { ItemRepositoryImpl() }
    // cmp:anchor di-repositories
}

val useCaseModule = module {
    factory { GetItemsUseCase(get()) }
    // cmp:anchor di-usecases
}

val viewModelModule = module {
    viewModelOf(::HomeViewModel)
    // cmp:anchor di-viewmodels
}

// Aggregated common modules, started from AppApplication (Android) and KoinHelper (iOS).
val appModules = listOf(repositoryModule, useCaseModule, viewModelModule)
