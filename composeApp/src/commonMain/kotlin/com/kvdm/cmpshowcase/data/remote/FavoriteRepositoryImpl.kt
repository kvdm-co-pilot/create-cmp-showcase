package com.kvdm.cmpshowcase.data.remote

import com.kvdm.cmpshowcase.domain.model.Favorite
import com.kvdm.cmpshowcase.domain.repository.FavoriteRepository
import kotlinx.coroutines.delay

// Example data source for the `favorites` feature. This is intentionally dependency-light
// (no Firebase / no Room coupling) so the scaffold builds in every feature combination.
//
// Real apps swap this for a Firestore/Ktor source and add a Room cache (see data/local).
// The Clean Architecture seam is the FavoriteRepository interface in the domain layer.
class FavoriteRepositoryImpl : FavoriteRepository {
    override suspend fun getFavorites(): List<Favorite> {
        delay(300) // simulate I/O
        return listOf(
            Favorite("1", "Welcome to CMP Showcase", "Your Compose Multiplatform app is wired end-to-end."),
            Favorite("2", "Clean Architecture", "presentation → domain → data, with Koin DI."),
            Favorite("3", "Edge-to-edge, pre-solved", "BaseScreen owns the window insets for you."),
            Favorite("4", "Android + iOS", "One codebase, two green builds."),
        )
    }
}
