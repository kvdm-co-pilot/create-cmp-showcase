package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.data.local.LogEntryEntity
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.TodayGoalEntity

/**
 * Hand-written in-memory [TodayDao] — lets [com.kvdm.fuelled.data.remote.TodayRepositoryImpl]
 * be tested through its DOMAIN contract without a real Room database. It mirrors the DAO's
 * OBSERVABLE behaviour, not just its signatures:
 *
 * - `entries()` returns one logical day's rows ordered by `entryOrder`, exactly as the
 *   `@Query`'s WHERE/ORDER BY declares. Slot order is the repository's job, so this fake must
 *   NOT pre-sort by slot — otherwise a repository that forgot to order by `MealSlot.ordinal`
 *   would still look correct here.
 * - `insertEntriesAtomically` is `@Transaction` in the real DAO, so this fake gives it real
 *   all-or-nothing semantics: it snapshots the rows, and restores them if the write throws
 *   part-way. That is what makes MEAL-05's rollback assertion a proof rather than an
 *   assumption — a repository that inserted row-by-row instead would leave the earlier rows
 *   behind and fail the test.
 */
class FakeTodayDao : TodayDao {

    private var goalRow: TodayGoalEntity? = null
    private val logRows = mutableListOf<LogEntryEntity>()

    /** Every persisted log row, in insertion order — the ledger the write-path tests inspect. */
    val rows: List<LogEntryEntity> get() = logRows.toList()

    /** When set, inserting the row with this id throws — the mid-write failure MEAL-05 names. */
    var failInsertOfId: String? = null

    /** How many times the atomic (transactional) multi-insert was entered. */
    var atomicInsertCount: Int = 0
        private set

    override suspend fun goal(): TodayGoalEntity? = goalRow

    override suspend fun entries(logicalDate: String): List<LogEntryEntity> =
        logRows.filter { it.logicalDate == logicalDate }.sortedBy { it.entryOrder }

    override suspend fun maxEntryOrder(logicalDate: String, slot: String): Int =
        logRows.filter { it.logicalDate == logicalDate && it.slot == slot }
            .maxOfOrNull { it.entryOrder } ?: -1

    override suspend fun goalCount(): Int = if (goalRow != null) 1 else 0

    override suspend fun upsertGoal(goal: TodayGoalEntity) {
        goalRow = goal
    }

    override suspend fun upsertEntry(entry: LogEntryEntity) {
        if (entry.id == failInsertOfId) error("insert failed for ${entry.id}")
        logRows.removeAll { it.id == entry.id }
        logRows.add(entry)
    }

    override suspend fun upsertEntries(entries: List<LogEntryEntity>) {
        for (entry in entries) upsertEntry(entry)
    }

    override suspend fun insertEntriesAtomically(entries: List<LogEntryEntity>) {
        atomicInsertCount += 1
        val snapshot = logRows.toList()
        try {
            for (entry in entries) upsertEntry(entry)
        } catch (failure: Throwable) {
            logRows.clear()
            logRows.addAll(snapshot) // the transaction rolls back; nothing is persisted
            throw failure
        }
    }

    override suspend fun deleteEntry(id: String) {
        logRows.removeAll { it.id == id }
    }

    override suspend fun setStatus(id: String, status: String) {
        val index = logRows.indexOfFirst { it.id == id }
        if (index >= 0) logRows[index] = logRows[index].copy(status = status)
    }

    override suspend fun clearEntries() = logRows.clear()
}
