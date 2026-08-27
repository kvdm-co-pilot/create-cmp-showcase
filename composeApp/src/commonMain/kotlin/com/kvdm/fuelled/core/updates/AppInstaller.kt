package com.kvdm.fuelled.core.updates

import com.kvdm.fuelled.domain.model.SemVer
import com.kvdm.fuelled.domain.result.AppResult

/**
 * What the update DECISION needs from the platform, as an interface.
 *
 * Split out from [AppInstaller] because an `expect class` cannot be faked in commonTest: it has
 * exactly one implementation per target and none of them is the one a test wants. Depending on
 * the class directly meant [com.kvdm.fuelled.domain.usecase.CheckForUpdateUseCase] could only
 * ever be exercised on a platform whose answers were already fixed — which is not a test of the
 * comparison at all. The domain depends on this; only DI knows the concrete class.
 */
interface InstallCapability {

    /** False where installing applications is impossible — iOS. UPD-08 hides the surface then. */
    val supported: Boolean

    /**
     * This build's own version — the left-hand side of UPD-02's comparison.
     *
     * Null when the platform cannot report one, which is treated as "nothing to compare" rather
     * than as zero: a missing version that defaulted to 0.0.0 would make every release look
     * newer and offer an update over a build we could not identify.
     */
    val installedVersion: SemVer?

    /**
     * Fetch [url] and hand it to the platform installer, where the USER confirms (UPD-06).
     *
     * [expectedSizeBytes] is checked against what arrived and a mismatch fails closed
     * (UPD-07) — the partial is discarded rather than handed over. Signature verification is
     * the OS's and is not reproduced here.
     */
    suspend fun downloadAndInstall(url: String, expectedSizeBytes: Long?): AppResult<Unit>
}

/**
 * The platform's ability to replace this app with a newer build (UPD-06/UPD-08).
 *
 * The seam is here, in `core`, for the same reason `NetworkMonitor` is: common code owns the
 * DECISION (is there something newer — testable on any JVM) and the platform owns the ACT
 * (fetching an APK and handing it to an installer — testable only on a device).
 *
 * iOS's actual reports [supported] = false and refuses [downloadAndInstall]. That is not a
 * degraded implementation to be improved later: App Store apps cannot install code, so there is
 * no iOS version of this to write (UPD-08).
 *
 * The `context: Any?` constructor mirrors [com.kvdm.fuelled.core.connectivity.NetworkMonitor]:
 * Android needs a Context, the other targets need nothing, and `Any?` is how this codebase
 * already expresses "the platform hands its own handle in". The HTTP client is built inside the
 * Android actual rather than injected — it is an implementation detail of fetching, and common
 * code has no business holding one for a capability it cannot use.
 */
expect class AppInstaller(context: Any?) : InstallCapability
