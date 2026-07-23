package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.Item
import com.kvdm.fuelled.domain.repository.ItemRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.delay

// Example data source for the `home` feature. This is intentionally dependency-light
// (no Firebase / no Room coupling) so the scaffold builds in every feature combination.
//
// Real apps swap this for a Firestore/Ktor source and add a Room cache (see data/local).
// The Clean Architecture seam is the ItemRepository interface in the domain layer.
//
// The repository is the ONLY exception-translation point: I/O runs inside
// suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions
// to typed DomainError values and ALWAYS rethrows CancellationException. Pass a mapError
// lambda to classify your real source's exceptions (IOException -> Network, etc).
class ItemRepositoryImpl : ItemRepository {
    override suspend fun getItems(): AppResult<List<Item>> = suspendRunCatching {
        delay(300) // simulate I/O
        listOf(
            Item("1", "Welcome to Fuelled", "Your Compose Multiplatform app is wired end-to-end."),
            Item("2", "Clean Architecture", "presentation → domain → data, with Koin DI."),
            Item("3", "Edge-to-edge, pre-solved", "BaseScreen owns the window insets for you."),
            Item("4", "Android + iOS", "One codebase, two green builds."),
        )
    }
}
