package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.ProfileDao
import com.kvdm.fuelled.data.local.ProfileEntity
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeProfileDao
import com.kvdm.fuelled.testing.fakes.FakeTodayDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

/**
 * The Profile data-layer test. [ProfileRepositoryImpl] is Room-backed via [ProfileDao]; here it
 * runs against a hand-written in-memory DAO fake, exercising the repository through its DOMAIN
 * contract (AppResult in, never an exception out) with no real database (mirrors
 * TodayRepositoryImplTest's shape).
 */
class ProfileRepositoryImplTest {

    private fun repository() = ProfileRepositoryImpl(FakeProfileDao(), FakeTodayDao())

    // SPEC: PROF-01
    @Test
    fun `seeds a realistic profile on first read and returns it as Success`() = runTest {
        val profile = when (val result = repository().getProfile()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> fail("seeded source should succeed, got $result")
        }

        assertTrue(profile.identity.name.isNotBlank(), "the seeded profile needs a name")
        assertTrue(profile.identity.planLabel.isNotBlank(), "the seeded profile needs a plan label")
        assertTrue(profile.goals.calorieTarget > 0, "the seeded profile needs a calorie target")
        assertTrue(profile.goals.proteinGoalG > 0, "the seeded profile needs a protein goal")
        assertTrue(profile.weeklyStats.weightKg > 0, "the seeded profile needs a weight")
    }

    // SPEC: PROF-01
    @Test
    fun `seeding is idempotent - a second read does not duplicate the profile`() = runTest {
        val dao = FakeProfileDao()
        val repository = ProfileRepositoryImpl(dao, FakeTodayDao())

        repository.getProfile()
        repository.getProfile()

        assertEquals(1, dao.count(), "the source must seed exactly one profile row")
    }

    // SPEC: PROF-05
    @Test
    fun `translates a thrown source error into a typed Failure - never lets it escape`() = runTest {
        val result = ProfileRepositoryImpl(ThrowingProfileDao(), FakeTodayDao()).getProfile()
        assertIs<AppResult.Failure>(result)
    }

    // SPEC: PERS-01
    @Test
    fun `profile goals ARE the goal store - an update there is what profile reads back`() = runTest {
        val profileDao = FakeProfileDao()
        val todayDao = FakeTodayDao()
        val todayRepository = TodayRepositoryImpl(todayDao)
        val profileRepository = ProfileRepositoryImpl(profileDao, todayDao)

        todayRepository.updateGoals(targetKcal = 3000, proteinTargetG = 200)

        val profile = when (val result = profileRepository.getProfile()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> fail("expected Success, got $result")
        }
        assertEquals(3000, profile.goals.calorieTarget, "one goal store, no second copy (F5)")
        assertEquals(200, profile.goals.proteinGoalG)
        assertEquals(3000, profile.identity.calorieTarget, "the identity line reads the same row")
    }

    // SPEC: PERS-03
    @Test
    fun `updateName renames the identity and nothing else`() = runTest {
        val dao = FakeProfileDao()
        val repository = ProfileRepositoryImpl(dao, FakeTodayDao())
        val before = when (val r = repository.getProfile()) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> fail("expected Success, got $r")
        }

        repository.updateName("Alex")

        val after = when (val r = repository.getProfile()) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> fail("expected Success, got $r")
        }
        assertEquals("Alex", after.identity.name)
        assertEquals(before.goals, after.goals, "a rename must not touch the goals")
        assertEquals(before.weeklyStats, after.weeklyStats, "nor the stats")
    }

    /** A DAO whose reads fail — proves the repository translates infrastructure errors (never throws). */
    private class ThrowingProfileDao : ProfileDao {
        override suspend fun get(): ProfileEntity? = throw IllegalStateException("db unavailable")
        override suspend fun count(): Int = 1 // non-zero so the repo skips seeding and hits get()
        override suspend fun upsert(profile: ProfileEntity) = Unit
    }
}
