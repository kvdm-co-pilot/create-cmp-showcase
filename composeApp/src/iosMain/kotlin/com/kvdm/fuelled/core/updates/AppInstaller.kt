package com.kvdm.fuelled.core.updates

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.SemVer
import com.kvdm.fuelled.domain.result.AppResult

/**
 * iOS's actual (UPD-08): there is none, and there never will be.
 *
 * App Store apps cannot install code and iOS has no sideload equivalent to fall back to, so
 * this is not a stub awaiting an implementation — it is the platform fact, made structural.
 * [supported] = false is what hides the surface entirely: no entry point, no screen, no check.
 * A disabled button would only advertise something the user can do nothing about.
 */
actual class AppInstaller actual constructor(context: Any?) : InstallCapability {

    override val supported: Boolean = false

    /** Never consulted — [supported] gates the whole feature before any comparison happens. */
    override val installedVersion: SemVer? = null

    override suspend fun downloadAndInstall(url: String, expectedSizeBytes: Long?): AppResult<Unit> =
        AppResult.Failure(DomainError.Unexpected(null))
}
