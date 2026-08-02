package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.logicalDate
import com.kvdm.fuelled.data.local.DEFAULT_TODAY_GOAL
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import com.kvdm.fuelled.data.local.ProfileDao
import com.kvdm.fuelled.data.local.ProfileEntity
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.repository.ProfileRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The Room-backed Profile source — the fully-wired data source. Reads the flat [ProfileDao]
 * identity/stats row, joins the ONE goal store's targets ([TodayDao] — PERS-01: the profile
 * row deliberately holds no goal columns since schema v9), and maps both into the domain
 * [Profile]. Seeds on first run so the app has content offline from install (idempotent).
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions to typed
 * DomainError values and ALWAYS rethrows CancellationException. Seed data lives here in the data
 * layer (ARCH-09) — it never reaches for the presentation layer's preview fixtures.
 */
class ProfileRepositoryImpl(
    private val dao: ProfileDao,
    private val todayDao: TodayDao,
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : ProfileRepository {

    /** The logical day right now (MEAL-01) — which goal row Profile is showing (GOAL-04). */
    private fun currentLogicalDay(): LocalDate = logicalDate(time.now(), dayStartHour, zone)

    override suspend fun getProfile(): AppResult<Profile> = suspendRunCatching {
        ensureSeeded()
        // GOAL-04: Profile shows what your goals ARE — the row in force today.
        val goal = todayDao.goalOn(currentLogicalDay().toString()) ?: DEFAULT_TODAY_GOAL
        (dao.get() ?: error("profile row missing after seeding")).toDomain(goal)
    }

    override suspend fun updateName(name: String): AppResult<Unit> = suspendRunCatching {
        ensureSeeded()
        val current = dao.get() ?: error("profile row missing after seeding")
        dao.upsert(current.copy(name = name))
    }

    /** Seed a realistic profile on first run so the screen ships with content offline (idempotent). */
    private suspend fun ensureSeeded() {
        if (dao.count() == 0) dao.upsert(SEED)
        // The goal row may not exist yet if Profile is the first surface ever opened — seed
        // the SHARED default (PERS-01), the same row Today's read would have seeded.
        if (todayDao.goalCount() == 0) todayDao.upsertGoal(DEFAULT_TODAY_GOAL)
    }

    private companion object {
        // The starter profile, seeded once into Room. Lives in the data layer (the source owns
        // its seed data, ARCH-09). Identity and stats ONLY — the goal numbers live in the one
        // goal store (PERS-01), never here.
        val SEED = ProfileEntity(
            id = "current",
            name = "Karel",
            planLabel = "Cutting",
            activity = "Trains 5×/week",
            streakDays = 12,
            avgProteinG = 172,
            weightKg = 82.4,
        )
    }
}
