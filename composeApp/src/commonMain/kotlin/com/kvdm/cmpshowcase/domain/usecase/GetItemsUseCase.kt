package com.kvdm.cmpshowcase.domain.usecase

import com.kvdm.cmpshowcase.domain.model.Item
import com.kvdm.cmpshowcase.domain.repository.ItemRepository

// A use case is a single business action. ViewModels depend on use cases, not repositories
// directly, so business rules stay testable and out of the presentation layer.
class GetItemsUseCase(
    private val repository: ItemRepository,
) {
    suspend operator fun invoke(): List<Item> = repository.getItems()
}
