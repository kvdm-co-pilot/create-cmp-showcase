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

    override suspend fun setTaken(id: String, taken: Boolean): AppResult<Unit> {
        lastSetTaken = id to taken
        failure?.let { return AppResult.Failure(it) }
        stack = stack.map { if (it.id == id) it.copy(taken = taken) else it }
        return AppResult.Success(Unit)
    }
}
