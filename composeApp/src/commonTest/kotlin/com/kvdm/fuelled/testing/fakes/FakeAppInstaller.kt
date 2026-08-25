package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.core.updates.InstallCapability
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Hand-written fake for the install seam. Lets a JVM test fix the two facts the DECISION reads
 * — can this platform install, and what versionCode is running — which the real expect/actual
 * class cannot, having exactly one answer per target.
 */
class FakeAppInstaller(
    override val installedVersionCode: Long = 6L,
    override val supported: Boolean = true,
    private val installFails: Boolean = false,
) : InstallCapability {

    var installedUrl: String? = null
        private set

    override suspend fun downloadAndInstall(url: String, expectedSizeBytes: Long?): AppResult<Unit> {
        installedUrl = url
        return if (installFails) AppResult.Failure(DomainError.Network) else AppResult.Success(Unit)
    }
}
