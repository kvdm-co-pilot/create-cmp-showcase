package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.AppRelease
import com.kvdm.fuelled.domain.model.DomainError
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
 * UPD-02: the versionCode rides the ASSET FILENAME (`fuelled-<versionCode>.apk`), because the
 * releases API does not report it — it is an Android concept GitHub knows nothing about.
 *
 * An asset that does not match the convention is not an installable asset: it resolves to a
 * release with a null [AppRelease.assetUrl], which UPD-04 reads as "nothing to install".
 * Deliberately NOT falling back to parsing the tag — an ordering derived from a semver string
 * is exactly the comparison UPD-02 forbids.
 */
private val ASSET_NAME = Regex("""^fuelled-(\d+)\.apk$""")

private fun GitHubRelease.toDomain(): AppRelease {
    val installable = assets.firstNotNullOfOrNull { asset ->
        ASSET_NAME.find(asset.name)?.groupValues?.get(1)?.toLongOrNull()?.let { it to asset }
    }
    return AppRelease(
        versionCode = installable?.first ?: 0L,
        version = name?.takeIf { it.isNotBlank() } ?: tagName,
        publishedAt = publishedAt.orEmpty(),
        notes = body.orEmpty(),
        assetUrl = installable?.second?.browserDownloadUrl,
        assetSizeBytes = installable?.second?.size,
    )
}
