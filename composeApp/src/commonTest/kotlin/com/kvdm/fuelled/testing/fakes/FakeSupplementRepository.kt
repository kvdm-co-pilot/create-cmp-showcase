package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks). Follows the
 * FakeFoodRepository pattern: configurable behaviour (`stack`, `failure`), recorded interactions
 * (`lastSetTaken`), implements the DOMAIN interface, and returns typed [AppResult.Failure] — it
 * never throws (repositories don't, per ARCH-06).
 *
 * `setTaken` mutates the in-memory `stack` so a subsequent `getStack()` returns the new state,
 * mirroring the Room-backed write-through the real repository gets from its DAO (SUPP-03).
 */
class FakeSupplementRepository : SupplementRepository {

    var stack: List<Supplement> = emptyList()
        set(value) { field = value; revision.value += 1 }
    var failure: DomainError? = null
        set(value) { field = value; revision.value += 1 }

    /** Observable like the real one: any change re-emits, so a collector sees it. */
    private val revision = MutableStateFlow(0)

    var lastSetTaken: Pair<String, Boolean>? = null
        private set

    override suspend fun getStack(): AppResult<List<Supplement>> {
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(stack)
    }

    override fun observeStack(): Flow<AppResult<List<Supplement>>> =
        revision.map { failure?.let { AppResult.Failure(it) } ?: AppResult.Success(stack) }

    /** SET-04/SET-05: every save and delete ATTEMPT, recorded before `failure` is applied. */
    val saves: MutableList<Supplement> = mutableListOf()
    val deletes: MutableList<String> = mutableListOf()

    override suspend fun save(supplement: Supplement): AppResult<Unit> {
        saves += supplement
        failure?.let { return AppResult.Failure(it) }
        // Write-through, like the DAO's REPLACE: a re-save of the same id corrects the row
        // rather than appending a twin (SET-04).
        stack = if (stack.any { it.id == supplement.id }) {
            stack.map { if (it.id == supplement.id) supplement.copy(taken = it.taken) else it }
        } else {
            stack + supplement
        }
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        deletes += id
        failure?.let { return AppResult.Failure(it) }
        stack = stack.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun setTaken(id: String, taken: Boolean): AppResult<Unit> {
        lastSetTaken = id to taken
        failure?.let { return AppResult.Failure(it) }
        stack = stack.map { if (it.id == id) it.copy(taken = taken) else it }
        return AppResult.Success(Unit)
    }
}
