package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.SupplementDao
import com.kvdm.fuelled.data.local.SupplementEntity
import com.kvdm.fuelled.data.local.SupplementTakenEntity
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeSupplementDao
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * The Supplements data-layer test. [SupplementRepositoryImpl] is Room-backed via
 * [SupplementDao]; here it runs against a hand-written in-memory DAO fake, exercising the
 * repository through its DOMAIN contract (AppResult in, never an exception out) with no real
 * database — and, crucially, proving the PERSISTENCE contract the feature exists for (SUPP-03).
 */
class SupplementRepositoryImplTest {

    /** Wednesday 08:00 — comfortably inside one logical day, so nothing rolls under a test's feet. */
    private val wednesdayMorning = Instant.parse("2026-07-29T08:00:00Z")

    private fun repository(
        time: FakeTimeSignal = FakeTimeSignal(wednesdayMorning),
        dao: SupplementDao = FakeSupplementDao(),
    ) = SupplementRepositoryImpl(dao, time, TimeZone.UTC)

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

    // SPEC: SUPP-07
    @Test
    fun `a fresh install claims NOTHING taken - the seed seeds the stack, never a dose`() = runTest {
        val stack = (repository().getStack() as AppResult.Success).value

        assertTrue(stack.isNotEmpty(), "the seed must still deliver a stack")
        assertTrue(
            stack.none { it.taken },
            "a clean install claiming '2 of 6 taken' is the app asserting the user swallowed " +
                "something they did not — the same pretence PLAN-03 purged from the meal seed",
        )
    }

    // SPEC: SUPP-07
    @Test
    fun `taken is a fact about a DAY - it does not carry into the next one`() = runTest {
        val time = FakeTimeSignal(wednesdayMorning)
        val repo = repository(time)
        val target = (repo.getStack() as AppResult.Success).value.first()

        repo.setTaken(target.id, true)
        assertTrue(
            (repo.getStack() as AppResult.Success).value.first { it.id == target.id }.taken,
            "the dose must hold for the rest of the day it was taken on",
        )

        // Thursday, past the 04:00 boundary. Nothing reset anything: the new day has no rows.
        time.advanceTo(Instant.parse("2026-07-30T08:00:00Z"))
        assertTrue(
            (repo.getStack() as AppResult.Success).value.none { it.taken },
            "Thursday inheriting Wednesday's count is a daily routine that can only ever go up",
        )

        // And Wednesday still knows what happened on Wednesday — the dose was recorded, not lost.
        time.advanceTo(wednesdayMorning)
        assertTrue((repo.getStack() as AppResult.Success).value.first { it.id == target.id }.taken)
    }

    // SPEC: SUPP-05
    @Test
    fun `translates a thrown source error into a typed Failure - never lets it escape`() = runTest {
        val result = repository(dao = ThrowingSupplementDao()).observeStack().first()
        assertIs<AppResult.Failure>(result)
    }

    /** A DAO whose reads fail — proves the repository translates infrastructure errors (never throws). */
    private class ThrowingSupplementDao : SupplementDao {
        override suspend fun getAll(): List<SupplementEntity> = throw IllegalStateException("db unavailable")
        override fun getAllStream(): Flow<List<SupplementEntity>> = flow { throw IllegalStateException("db unavailable") }
        override suspend fun takenOn(logicalDate: String): List<SupplementTakenEntity> =
            throw IllegalStateException("db unavailable")
        override fun takenStream(logicalDate: String): Flow<List<SupplementTakenEntity>> =
            flow { throw IllegalStateException("db unavailable") }
        override suspend fun insertTaken(row: SupplementTakenEntity) = Unit
        override suspend fun clearTaken(logicalDate: String, id: String) = Unit
        override suspend fun count(): Int = 1 // non-zero so the repo skips seeding and hits getAll()
        override suspend fun upsertAll(supplements: List<SupplementEntity>) = Unit
        override suspend fun upsert(supplement: SupplementEntity) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun clear() = Unit
    }
}
