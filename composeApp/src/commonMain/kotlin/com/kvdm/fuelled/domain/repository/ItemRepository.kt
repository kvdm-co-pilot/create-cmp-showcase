package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.Item
import com.kvdm.fuelled.domain.result.AppResult

// Domain-facing contract. Presentation depends on THIS, never on a concrete data source.
// One-shot operations return AppResult — they never throw (ARCH-06): failures cross the
// boundary as typed DomainError values, translated inside the data implementation.
interface ItemRepository {
    suspend fun getItems(): AppResult<List<Item>>
}
