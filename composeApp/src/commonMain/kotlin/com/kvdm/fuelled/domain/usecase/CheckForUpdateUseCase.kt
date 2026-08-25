package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.core.updates.InstallCapability
import com.kvdm.fuelled.domain.model.UpdateAvailability
import com.kvdm.fuelled.domain.repository.UpdateRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The whole decision, in one testable place (UPD-02/UPD-03/UPD-04/UPD-08).
 *
 * Deliberately owns nothing platform-shaped: it asks the installer two questions (can this
 * platform install at all, and what versionCode is running) and the repository one (what is the
 * latest release), then compares two integers. That is why it can be tested on any JVM without
 * a device — the act of installing is the part that needs one.
 */
class CheckForUpdateUseCase(
    private val repository: UpdateRepository,
    private val installer: InstallCapability,
) {

    suspend operator fun invoke(): AppResult<UpdateAvailability> {
        // UPD-08 first, before any network call: a platform that cannot install has no reason
        // to ask GitHub anything, and asking would spend a user's data on an answer that
        // cannot be acted on.
        if (!installer.supported) return AppResult.Success(UpdateAvailability.Unsupported)

        return when (val result = repository.latestRelease()) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val release = result.value
                val available = release != null &&
                    // UPD-04: no installable asset is "nothing to install", not an error.
                    release.assetUrl != null &&
                    // UPD-03: strictly greater. Equal is up to date, and an installed build
                    // that is NEWER (a local debug build) is also up to date — never a
                    // downgrade offer.
                    release.versionCode > installer.installedVersionCode
                AppResult.Success(
                    if (available) UpdateAvailability.Available(release!!) else UpdateAvailability.UpToDate,
                )
            }
        }
    }
}
