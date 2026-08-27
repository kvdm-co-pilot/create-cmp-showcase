package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.AppRelease
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.SemVer
import com.kvdm.fuelled.domain.repository.UpdateRepository
import com.kvdm.fuelled.domain.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The GitHub Releases source (UPD-01). The only place in the app that knows GitHub's response
 * shape exists; the domain sees [AppRelease] and nothing else.
 *
 * Unauthenticated on purpose. This repository is public, so the endpoint answers without a
 * token — and a token could not safely be shipped anyway: an APK is a public artifact, and a
 * credential inside one is a published credential. The 60-requests-per-hour unauthenticated
 * limit is ~60x the cadence the surface actually uses.
 */
class UpdateRepositoryImpl(
    private val client: HttpClient,
    private val owner: String,
    private val repo: String,
) : UpdateRepository {

    override suspend fun latestRelease(): AppResult<AppRelease?> = suspendRunCatching(
        mapError = { DomainError.Network },
    ) {
        val response: HttpResponse = client.get("https://api.github.com/repos/$owner/$repo/releases/latest") {
            // GitHub asks for an explicit API version and a UA; without a UA it 403s outright.
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header("User-Agent", "Fuelled")
        }
        when (response.status) {
            // A repository with no published release is not a failure — there is simply
            // nothing newer, which is UPD-04's shape rather than an error state.
            HttpStatusCode.NotFound -> null
            HttpStatusCode.OK -> response.body<GitHubRelease>().toDomain()
            // 403 here is the unauthenticated rate limit far more often than a real
            // forbidden (UPD-05). Either way it is a typed failure, never a quiet
            // "up to date" — the one answer this feature must not invent.
            else -> throw IllegalStateException("github releases: ${response.status}")
        }
    }
}

/** GitHub's release payload, narrowed to the fields UPD-01..04 actually read. */
@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0,
)

/**
 * UPD-02: the version rides the ASSET FILENAME (`fuelled-<major>.<minor>.<patch>.apk`), because
 * the releases API reports no version the app can order — the tag is free text and `versionCode`
 * is an Android concept GitHub knows nothing about.
 *
 * Parsed into a [SemVer] and compared as three integers, never as a string: `"0.10.0" < "0.9.0"`
 * lexically, which is the bug this parsing exists to prevent rather than cause.
 *
 * An asset that does not match is not an installable asset: it resolves to a release with a null
 * [AppRelease.assetUrl], which UPD-04 reads as "nothing to install". Deliberately NOT falling
 * back to the tag — `tag_name` is whatever a human typed when they cut the release.
 */
private val ASSET_NAME = Regex("""^fuelled-(\d+\.\d+\.\d+)\.apk$""")

private fun GitHubRelease.toDomain(): AppRelease? {
    val installable = assets.firstNotNullOfOrNull { asset ->
        SemVer.parse(ASSET_NAME.find(asset.name)?.groupValues?.get(1))?.let { it to asset }
    }
    // No parseable asset means no version to order by, so there is nothing this release could
    // be compared against — UPD-04's "nothing to install", surfaced as a null release rather
    // than as an AppRelease carrying a fabricated 0.0.0 that every installed build outranks.
    if (installable == null) return null
    return AppRelease(
        version = installable.first,
        displayVersion = name?.takeIf { it.isNotBlank() } ?: tagName,
        publishedAt = publishedAt.orEmpty(),
        notes = body.orEmpty(),
        assetUrl = installable.second.browserDownloadUrl,
        assetSizeBytes = installable.second.size,
    )
}
