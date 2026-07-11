package com.kvdm.cmpshowcase.domain.repository

import com.kvdm.cmpshowcase.domain.model.Favorite

// Domain-facing contract. Presentation depends on THIS, never on a concrete data source.
interface FavoriteRepository {
    suspend fun getFavorites(): List<Favorite>
}
