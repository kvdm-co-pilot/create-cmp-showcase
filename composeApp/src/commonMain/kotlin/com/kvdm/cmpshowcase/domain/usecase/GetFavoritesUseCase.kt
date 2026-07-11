package com.kvdm.cmpshowcase.domain.usecase

import com.kvdm.cmpshowcase.domain.model.Favorite
import com.kvdm.cmpshowcase.domain.repository.FavoriteRepository

// A use case is a single business action. ViewModels depend on use cases, not repositories
// directly, so business rules stay testable and out of the presentation layer.
class GetFavoritesUseCase(
    private val repository: FavoriteRepository,
) {
    suspend operator fun invoke(): List<Favorite> = repository.getFavorites()
}
