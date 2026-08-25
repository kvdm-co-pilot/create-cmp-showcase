package com.kvdm.fuelled.domain.model

/**
 * A published release of this app, as the update check needs it (UPD-01/UPD-02).
 *
 * [versionCode] is the only field the "is it newer" decision reads. [version] is the tag as
 * published and exists for DISPLAY — UPD-02 keeps those jobs apart on purpose, because version
 * strings sort wrong ("0.10.0" < "0.9.0") and a release-name typo must never be able to decide
 * whether the app replaces itself.
 *
 * [assetUrl] is null for a release with no installable asset — a source-only tag, which UPD-04
 * treats as "nothing to install" rather than as an error.
 */
data class AppRelease(
    val versionCode: Long,
    val version: String,
    val publishedAt: String,
    val notes: String,
    val assetUrl: String?,
    val assetSizeBytes: Long?,
)

/**
 * The answer the check produces (UPD-03/UPD-04) — a closed set, so presentation cannot invent a
 * fifth reading of two integers.
 *
 * There is no `Downgrade`: an installed build NEWER than the latest release reports
 * [UpToDate]. Android would refuse the install anyway, and offering to replace a newer build
 * with an older one is a data-loss suggestion whatever the OS does.
 */
sealed interface UpdateAvailability {
    data object UpToDate : UpdateAvailability
    data class Available(val release: AppRelease) : UpdateAvailability
    /** UPD-08: this platform cannot install applications at all. Not an error — a fact. */
    data object Unsupported : UpdateAvailability
}
