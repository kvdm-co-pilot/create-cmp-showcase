package com.kvdm.cmpshowcase.data.remote

import com.kvdm.cmpshowcase.domain.model.Item
import com.kvdm.cmpshowcase.domain.repository.ItemRepository
import kotlinx.coroutines.delay

// Example data source for the `home` feature. This is intentionally dependency-light
// (no Firebase / no Room coupling) so the scaffold builds in every feature combination.
//
// Real apps swap this for a Firestore/Ktor source and add a Room cache (see data/local).
// The Clean Architecture seam is the ItemRepository interface in the domain layer.
class ItemRepositoryImpl : ItemRepository {
    override suspend fun getItems(): List<Item> {
        delay(300) // simulate I/O
        return listOf(
            Item("1", "Welcome to CMP Showcase", "Your Compose Multiplatform app is wired end-to-end."),
            Item("2", "Clean Architecture", "presentation → domain → data, with Koin DI."),
            Item("3", "Edge-to-edge, pre-solved", "BaseScreen owns the window insets for you."),
            Item("4", "Android + iOS", "One codebase, two green builds."),
        )
    }
}
