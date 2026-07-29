package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.data.local.LogEntryEntity
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.TodayGoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

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


    /**
     * Room's invalidation tracker, faked honestly: every write bumps this, and every `*Stream`
     * re-queries off it. A fake that returned `flowOf(currentRows)` would emit once and go
     * quiet — which is exactly the bug under test, so it would let the regression back in.
     */
    private val version = MutableStateFlow(0)

    // Bumped AFTER each mutation, never before — Room's invalidation tracker fires after the
    // transaction commits, and a bump-first fake under an immediate dispatcher lets a collector
    // re-query pre-write state with no emission ever carrying the write.
    private fun bump() { version.value += 1 }

    override suspend fun goal(): TodayGoalEntity? = goalRow

    override fun goalStream(): Flow<TodayGoalEntity?> = version.map { goalRow }

    override fun entriesStream(logicalDate: String): Flow<List<LogEntryEntity>> =
        version.map { logRows.filter { it.logicalDate == logicalDate }.sortedBy { it.entryOrder } }

    override suspend fun entries(logicalDate: String): List<LogEntryEntity> =
        logRows.filter { it.logicalDate == logicalDate }.sortedBy { it.entryOrder }

    override suspend fun maxEntryOrder(logicalDate: String, slot: String): Int =
        logRows.filter { it.logicalDate == logicalDate && it.slot == slot }
            .maxOfOrNull { it.entryOrder } ?: -1

    override suspend fun goalCount(): Int = if (goalRow != null) 1 else 0

    override suspend fun upsertGoal(goal: TodayGoalEntity) {
        goalRow = goal
        bump()
    }

    override suspend fun upsertEntry(entry: LogEntryEntity) {
        if (entry.id == failInsertOfId) error("insert failed for ${entry.id}")
        logRows.removeAll { it.id == entry.id }
        logRows.add(entry)
        bump()
    }

    override suspend fun upsertEntries(entries: List<LogEntryEntity>) {
        for (entry in entries) upsertEntry(entry) // each row bumps after its own mutation
    }

    override suspend fun insertEntriesAtomically(entries: List<LogEntryEntity>) {
        atomicInsertCount += 1
        val snapshot = logRows.toList()
        try {
            for (entry in entries) upsertEntry(entry)
        } catch (failure: Throwable) {
            logRows.clear()
            logRows.addAll(snapshot) // the transaction rolls back; nothing is persisted
            bump()
            throw failure
        }
    }

    override suspend fun deleteEntry(id: String) {
        logRows.removeAll { it.id == id }
        bump()
    }

    override suspend fun setStatus(id: String, status: String) {
        val index = logRows.indexOfFirst { it.id == id }
        if (index >= 0) logRows[index] = logRows[index].copy(status = status)
        bump()
    }

    override suspend fun clearEntries() = logRows.clear()
}
