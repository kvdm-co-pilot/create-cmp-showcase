package com.kvdm.cmpshowcase.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The exemplar data-layer test. [ItemRepositoryImpl] is the template's dependency-light
 * example source; when you swap it for a real Firestore/Ktor + Room implementation, keep
 * this shape: test the repository through its DOMAIN contract, under `runTest` virtual
 * time (the simulated I/O delay costs nothing here — delays are skipped, not slept).
 */
class ItemRepositoryImplTest {

    private val repository = ItemRepositoryImpl()

    @Test
    fun `returns the seeded example items`() = runTest {
        val items = repository.getItems()

        assertTrue(items.isNotEmpty(), "example source should seed items")
        assertEquals(items.size, items.map { it.id }.toSet().size, "item ids must be unique")
        assertTrue(items.all { it.title.isNotBlank() }, "every item needs a title")
    }
}
