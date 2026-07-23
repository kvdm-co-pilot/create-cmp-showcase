package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.data.local.LogEntryEntity
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.TodayGoalEntity

/**
 * Hand-written in-memory [TodayDao] — lets [com.kvdm.fuelled.data.remote.TodayRepositoryImpl]
 * be tested through its DOMAIN contract without a real Room database. Mirrors the DAO's
 * observable behaviour: `entries()` returns the log meal-grouped and stably ordered
 * (`mealOrder`, then `entryOrder`), exactly as the `@Query`'s ORDER BY declares.
 */
class FakeTodayDao : TodayDao {

    private var goalRow: TodayGoalEntity? = null
    private val logRows = mutableListOf<LogEntryEntity>()

    override suspend fun goal(): TodayGoalEntity? = goalRow

    override suspend fun entries(): List<LogEntryEntity> =
        logRows.sortedWith(compareBy({ it.mealOrder }, { it.entryOrder }))

    override suspend fun goalCount(): Int = if (goalRow != null) 1 else 0

    override suspend fun upsertGoal(goal: TodayGoalEntity) {
        goalRow = goal
    }

    override suspend fun upsertEntries(entries: List<LogEntryEntity>) {
        for (entry in entries) {
            logRows.removeAll { it.id == entry.id }
            logRows.add(entry)
        }
    }

    override suspend fun clearEntries() = logRows.clear()
}
