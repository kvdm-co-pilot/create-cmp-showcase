package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.domain.result.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * The exemplar data-layer test. [ItemRepositoryImpl] is the template's dependency-light
 * example source; when you swap it for a real Firestore/Ktor + Room implementation, keep
 * this shape: test the repository through its DOMAIN contract (AppResult in, never an
 * exception out), under `runTest` virtual time (the simulated I/O delay costs nothing
 * here — delays are skipped, not slept).
 */
class ItemRepositoryImplTest {

    private val repository = ItemRepositoryImpl()

    @Test
    fun `returns the seeded example items as Success`() = runTest {
        val items = when (val result = repository.getItems()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> fail("example source should succeed, got $result")
        }

        assertTrue(items.isNotEmpty(), "example source should seed items")
        assertEquals(items.size, items.map { it.id }.toSet().size, "item ids must be unique")
        assertTrue(items.all { it.title.isNotBlank() }, "every item needs a title")
    }

    // SPEC: ARCH-08
    @Test
    fun `cancellation propagates - a cancelled load never completes as a Failure`() = runTest {
        // A REAL cancellation: getItems() is suspended in its simulated I/O when the caller's
        // job is cancelled. suspendRunCatching must rethrow the CancellationException — if it
        // mapped it, `result` would hold a Failure and this test would fail.
        var result: Any? = null
        val job = launch { result = repository.getItems() }
        testScheduler.runCurrent() // enter getItems() up to the suspension point

        job.cancel()
        job.join()

        assertTrue(job.isCancelled, "the load job should end cancelled, not completed")
        assertNull(result, "a cancelled load must produce NO result — especially not a Failure")
    }
}
