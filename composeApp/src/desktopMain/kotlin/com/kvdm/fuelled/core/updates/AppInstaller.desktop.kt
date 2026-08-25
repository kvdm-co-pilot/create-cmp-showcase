package com.kvdm.fuelled.core.updates

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Desktop's actual: unsupported, for the same reason iOS is (UPD-08).
 *
 * Desktop exists in this project as the PREVIEW and test target — the gallery renders here and
 * the JVM test tiers run here — not as a shipping surface. An app that cannot be installed from
 * a release has nothing to update.
 */
actual class AppInstaller actual constructor(context: Any?) : InstallCapability {

    override val supported: Boolean = false

    override val installedVersionCode: Long = 0L

    override suspend fun downloadAndInstall(url: String, expectedSizeBytes: Long?): AppResult<Unit> =
        AppResult.Failure(DomainError.Unexpected(null))
}
