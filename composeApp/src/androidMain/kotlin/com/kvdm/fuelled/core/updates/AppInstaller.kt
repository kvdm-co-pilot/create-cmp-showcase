package com.kvdm.fuelled.core.updates

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.SemVer
import com.kvdm.fuelled.domain.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android's actual (UPD-06/UPD-07): fetch the APK, then hand it to the system installer, where
 * the USER confirms. The app never installs anything itself — silent install needs device-owner
 * or privileged permissions an ordinary sideloaded app cannot hold.
 */
actual class AppInstaller actual constructor(context: Any?) : InstallCapability {

    private val context = context as Context
    private val client = HttpClient()

    override val supported: Boolean = true

    /**
     * This build's own version, read from the package manager rather than BuildConfig, so it is
     * the INSTALLED app's number and not whatever was compiled into a stale constant.
     *
     * `versionName`, not `versionCode`: UPD-02 orders releases by the semver in the asset
     * filename, so the left-hand side of that comparison has to be the same kind of thing. A
     * versionName that is not three integers parses to null — "cannot identify this build" —
     * and the surface then offers nothing rather than comparing against a guess.
     */
    override val installedVersion: SemVer?
        get() = SemVer.parse(
            context.packageManager.getPackageInfo(context.packageName, 0).versionName,
        )

    override suspend fun downloadAndInstall(url: String, expectedSizeBytes: Long?): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            // Cache, not files: an interrupted or superseded download is the OS's to reclaim,
            // and an APK we already handed over has no reason to outlive the install.
            val target = File(context.cacheDir, "update.apk")
            try {
                target.delete()
                client.get(url).bodyAsChannel().copyTo(target.outputStream())

                // UPD-07: fail CLOSED on a size mismatch. A truncated APK is the failure we own
                // (signature verification is the OS's and is not reproduced here), and handing
                // a partial file to the installer produces a parse error the user cannot read.
                if (expectedSizeBytes != null && target.length() != expectedSizeBytes) {
                    target.delete()
                    return@withContext AppResult.Failure(DomainError.Unexpected(null))
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", target)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                AppResult.Success(Unit)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Throwable) {
                target.delete()
                AppResult.Failure(DomainError.Network)
            }
        }
}
