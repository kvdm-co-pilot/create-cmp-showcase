package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.core.updates.InstallCapability
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.SemVer
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Hand-written fake for the install seam. Lets a JVM test fix the two facts the DECISION reads
 * — can this platform install, and what version is running — which the real expect/actual class
 * cannot, having exactly one answer per target.
 *
 * [installedVersion] is nullable because the real Android actual can genuinely fail to parse a
 * versionName, and a fake that could not express that would leave UPD-03's "cannot identify
 * this build" branch untested.
 */
class FakeAppInstaller(
    override val installedVersion: SemVer? = SemVer(0, 6, 0),
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
