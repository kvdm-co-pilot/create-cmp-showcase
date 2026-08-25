package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.AppRelease
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.repository.UpdateRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks). Configurable
 * behaviour (`release`, `failure`), implements the DOMAIN interface, and returns typed
 * [AppResult.Failure] rather than throwing (ARCH-06).
 */
class FakeUpdateRepository : UpdateRepository {

    var release: AppRelease? = null
    var failure: DomainError? = null

    override suspend fun latestRelease(): AppResult<AppRelease?> =
        failure?.let { AppResult.Failure(it) } ?: AppResult.Success(release)
}
