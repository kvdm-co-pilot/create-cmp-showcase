package com.kvdm.cmpshowcase.domain.repository

import com.kvdm.cmpshowcase.domain.model.Item

// Domain-facing contract. Presentation depends on THIS, never on a concrete data source.
interface ItemRepository {
    suspend fun getItems(): List<Item>
}
