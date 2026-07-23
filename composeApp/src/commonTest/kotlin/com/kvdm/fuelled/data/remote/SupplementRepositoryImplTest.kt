package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.SupplementDao
import com.kvdm.fuelled.data.local.SupplementEntity
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeSupplementDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

/**
 * The Supplements data-layer test. [SupplementRepositoryImpl] is Room-backed via
 * [SupplementDao]; here it runs against a hand-written in-memory DAO fake, exercising the
 * repository through its DOMAIN contract (AppResult in, never an exception out) with no real
 * database — and, crucially, proving the PERSISTENCE contract the feature exists for (SUPP-03).
 */
class SupplementRepositoryImplTest {

    private fun repository() = SupplementRepositoryImpl(FakeSupplementDao())

    // SPEC: SUPP-01
    @Test
    fun `seeds the stack on first read and returns it ordered as Success`() = runTest {
        val stack = when (val result = repository().getStack()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> fail("seeded source should succeed, got $result")
        }

        assertTrue(stack.isNotEmpty(), "the source should seed the stack on first run")
        assertEquals(stack.size, stack.map { it.id }.toSet().size, "supplement ids must be unique")
        assertTrue(stack.all { it.name.isNotBlank() && it.dose.isNotBlank() }, "every supplement needs name + dose")
        // Grouping in the ViewModel relies on the stack arriving timing-ordered: all rows of a
        // timing bucket are contiguous, never interleaved with another bucket's.
        val timings = stack.map { it.timing }
        assertEquals(timings, timings.distinct().flatMap { t -> timings.filter { it == t } }, "timings must be contiguous")
    }

    // SPEC: SUPP-03
    @Test
    fun `setTaken persists and a re-read returns the new state - survives a reload`() = runTest {
        val repo = repository()
        val before = (repo.getStack() as AppResult.Success).value
        val target = before.first { !it.taken }

        assertEquals(AppResult.Success(Unit), repo.setTaken(target.id, true))

        // A fresh read off the same source (as a screen reload does) reflects the persisted write.
        val after = (repo.getStack() as AppResult.Success).value
        assertTrue(after.first { it.id == target.id }.taken, "the taken write must persist across a re-read")
        // And un-taking persists just as well.
        repo.setTaken(target.id, false)
        assertFalse((repo.getStack() as AppResult.Success).value.first { it.id == target.id }.taken)
    }

    // SPEC: SUPP-05
    @Test
    fun `translates a thrown source error into a typed Failure - never lets it escape`() = runTest {
        val result = SupplementRepositoryImpl(ThrowingSupplementDao()).getStack()
        assertIs<AppResult.Failure>(result)
    }

    /** A DAO whose reads fail — proves the repository translates infrastructure errors (never throws). */
    private class ThrowingSupplementDao : SupplementDao {
        override suspend fun getAll(): List<SupplementEntity> = throw IllegalStateException("db unavailable")
        override suspend fun setTaken(id: String, taken: Boolean) = Unit
        override suspend fun count(): Int = 1 // non-zero so the repo skips seeding and hits getAll()
        override suspend fun upsertAll(supplements: List<SupplementEntity>) = Unit
        override suspend fun clear() = Unit
    }
}
