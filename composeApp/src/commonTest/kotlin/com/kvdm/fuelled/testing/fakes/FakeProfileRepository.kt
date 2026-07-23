package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.model.ProfileGoals
import com.kvdm.fuelled.domain.model.ProfileIdentity
import com.kvdm.fuelled.domain.model.WeeklyStats
import com.kvdm.fuelled.domain.repository.ProfileRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks). Follows the
 * FakeTodayRepository pattern: configurable behaviour (`profile`, `failure`), recorded
 * interactions (`getCallCount`), implements the DOMAIN interface, and returns typed
 * [AppResult.Failure] — it never throws (repositories don't, per ARCH-06).
 */
class FakeProfileRepository : ProfileRepository {

    var profile: Profile = sampleProfile
    var failure: DomainError? = null

    var getCallCount: Int = 0
        private set

    override suspend fun getProfile(): AppResult<Profile> {
        getCallCount++
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(profile)
    }

    companion object {
        val sampleProfile = Profile(
            identity = ProfileIdentity(name = "Karel", planLabel = "Cutting", calorieTarget = 2400),
            goals = ProfileGoals(calorieTarget = 2400, proteinGoalG = 180, activity = "Trains 5×/week"),
            weeklyStats = WeeklyStats(streakDays = 12, avgProteinG = 172, weightKg = 82.4),
        )
    }
}
