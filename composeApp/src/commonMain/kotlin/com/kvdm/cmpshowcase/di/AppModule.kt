package com.kvdm.cmpshowcase.di

import com.kvdm.cmpshowcase.data.remote.ItemRepositoryImpl
import com.kvdm.cmpshowcase.domain.repository.ItemRepository
import com.kvdm.cmpshowcase.domain.usecase.GetItemsUseCase
import com.kvdm.cmpshowcase.presentation.home.HomeViewModel
import com.kvdm.cmpshowcase.data.remote.FavoriteRepositoryImpl
import com.kvdm.cmpshowcase.domain.repository.FavoriteRepository
import com.kvdm.cmpshowcase.domain.usecase.GetFavoritesUseCase
import com.kvdm.cmpshowcase.presentation.favorites.FavoritesViewModel
// cmp:anchor di-imports
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val repositoryModule = module {
    single<ItemRepository> { ItemRepositoryImpl() }
    single<FavoriteRepository> { FavoriteRepositoryImpl() }
    // cmp:anchor di-repositories
}

val useCaseModule = module {
    factory { GetItemsUseCase(get()) }
    factory { GetFavoritesUseCase(get()) }
    // cmp:anchor di-usecases
}

val viewModelModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::FavoritesViewModel)
    // cmp:anchor di-viewmodels
}

// Aggregated common modules, started from AppApplication (Android) and KoinHelper (iOS).
val appModules = listOf(repositoryModule, useCaseModule, viewModelModule)
