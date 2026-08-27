package com.kvdm.fuelled.presentation.appupdates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.AppRelease
import com.kvdm.fuelled.domain.model.SemVer
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import org.koin.compose.viewmodel.koinViewModel
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.components.StatBar
import com.kvdm.fuelled.presentation.theme.FuelledColors

// ── Updates: is this build stale? (UPD-01..09) ───────────────────────────────────────────
// DESIGN DRAFT — stub data only, no ViewModel, no network. The brief's step 2: the screens
// are drafted here and rendered so a human signs what they SEE. The real
// UpdateRoute/UpdateViewModel arrive at Build, after the contract is written.
//
// The whole screen answers one question — is the versionCode on this phone behind the latest
// GitHub release — so it is deliberately small. Everything here composes the registry
// vocabulary (ScreenColumn, AppHeader, AppPrimaryButton); no hand-rolled headers or rows.

/**
 * What the screen renders, as one value.
 *
 * Four states, because four things can be true and each needs different words: you are current,
 * there is something newer, it is coming down, or the attempt failed. There is no fifth
 * "checking" arm — that is [com.kvdm.fuelled.presentation.components.ContentUiState.Loading],
 * owned by ContentStateContainer at the Route level like every other screen in this app.
 */
sealed interface UpdateUi {
    /** The installed version, always shown — it is the thing every other state is relative to. */
    val installed: String

    data class UpToDate(override val installed: String) : UpdateUi

    data class Available(
        override val installed: String,
        val version: String,
        val publishedAt: String,
        val sizeLabel: String,
        val notes: String,
        /**
         * What the download control acts on. Carried on the UI state rather than held
         * separately in the ViewModel so the version on screen and the version fetched cannot
         * drift apart — there is only one of them.
         */
        val assetUrl: String? = null,
        val assetSizeBytes: Long? = null,
        /**
         * The ORDERED version, carried alongside the display string. [version] is what the
         * release was called; this is what decided it was newer. Held here because the
         * download control rebuilds an [AppRelease] from this state, and a reconstruction that
         * had to invent a version would be inventing the one field the decision turns on.
         */
        val semver: SemVer? = null,
    ) : UpdateUi

    /** [fraction] is 0f..1f, or null for a download whose total size the server never gave. */
    data class Downloading(
        override val installed: String,
        val version: String,
        val fraction: Float?,
    ) : UpdateUi

    /**
     * [reason] is already user-facing copy, mapped from a DomainError kind at the Route.
     * Presentation never sees an exception (ARCH-06/ARCH-07).
     */
    data class Failed(
        override val installed: String,
        val reason: String,
    ) : UpdateUi
}

// PREVIEW/DEMO fixtures — the four states, fixed so gallery renders and golden diffs stay
// deterministic (ARCH-12). Never a clock read, never a live version.
//
// NOTE the version numbers are INVENTED: OD1 in the brief is still open — nobody has said which
// repository publishes Fuelled's releases — so there is no real release to draw from. When that
// is answered these strings stay exactly as they are (they are a preview seam, not config); it
// is the Build-stage constant that gains the real owner/repo.
val sampleUpToDate = UpdateUi.UpToDate(installed = "0.6.0")

val sampleAvailable = UpdateUi.Available(
    installed = "0.6.0",
    version = "0.7.0",
    publishedAt = "Aug 25, 2026",
    sizeLabel = "18.4 MB",
    notes = "Five tabs: Today, Week, Meals, Training, Profile. The training week gets its own " +
        "surface. Supplements moves off the bottom bar and onto Today's highlight.",
)

val sampleDownloading = UpdateUi.Downloading(installed = "0.6.0", version = "0.7.0", fraction = 0.42f)

val sampleFailed = UpdateUi.Failed(
    installed = "0.6.0",
    reason = "That download did not finish. Nothing was installed — try again when you have signal.",
)

/**
 * The stateless updates screen — the preview/UI-first seam, defaulted to a sample so the
 * registry renders it without a VM or Koin.
 *
 * A tab would inherit insets from AppShell; this is a pushed destination, so at Build its Route
 * owns them (SHELL-05).
 */
@Composable
fun UpdateScreen(
    model: UpdateUi = sampleAvailable,
    onCheck: () -> Unit = {},
    onDownload: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    ScreenColumn(screenTag = "updates", scrollable = true) {
        AppHeader(title = "Updates", screenTag = "updates", onBack = onBack)

        InstalledRow(model.installed)

        when (model) {
            is UpdateUi.UpToDate -> UpToDateCard()
            is UpdateUi.Available -> AvailableCard(model, onDownload)
            is UpdateUi.Downloading -> DownloadingCard(model)
            is UpdateUi.Failed -> FailedCard(model, onCheck)
        }
    }
}

/**
 * The installed version, on every state.
 *
 * "You are on X" is the fact the whole screen is relative to, and the one a human reads out
 * when reporting a problem — so it never hides behind a state that happens to be interesting.
 */
@Composable
private fun InstalledRow(installed: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics { testTag = "updates_installed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "INSTALLED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = installed,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UpToDateCard() {
    Card(tag = "updates_current") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FuelledColors.Success)
            Spacer(Modifier.fillMaxWidth(0f))
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "You're on the latest build",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Nothing to install.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The one state with an action. The button says DOWNLOAD, not "Update": Android's own installer
 * dialog does the installing and the user confirms there (D1), so promising "update" here would
 * name a step this screen does not actually take.
 */
@Composable
private fun AvailableCard(model: UpdateUi.Available, onDownload: () -> Unit) {
    Card(tag = "updates_available") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = FuelledColors.Primary)
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    text = model.version,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { testTag = "updates_version" },
                )
                Text(
                    text = "${model.publishedAt} · ${model.sizeLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // Plain text, not markdown — OD3 in the brief. GitHub returns the body as markdown and
        // this app has no markdown composable; adding one is a components re-approval for a
        // first slice that does not need it.
        Text(
            text = model.notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "updates_notes" },
        )
        Spacer(Modifier.height(16.dp))
        AppPrimaryButton(
            text = "Download ${model.version}",
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "updates_download" },
        )
    }
}

/**
 * Determinate when the server gave a content length, indeterminate when it did not — rather
 * than a fake percentage. A progress bar that invents its own numbers is the reason people stop
 * believing progress bars.
 */
@Composable
private fun DownloadingCard(model: UpdateUi.Downloading) {
    Card(tag = "updates_downloading") {
        Text(
            text = "Downloading ${model.version}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        // StatBar, not LinearProgressIndicator: ARCH-11 bans a hand-rolled indicator in a
        // screen, and the registry already owns the determinate progress bar. (The rule is
        // written about LOADING, and this is progress — but the vocabulary is the same one, so
        // there is nothing to argue about: the exception nobody needs is the exception nobody
        // should carve.)
        //
        // A null fraction means the server sent no content length. It renders as TEXT with no
        // bar rather than a bar at zero or an indeterminate sweep — a progress bar that invents
        // its own number is why people stop believing progress bars.
        if (model.fraction != null) {
            StatBar(
                progress = model.fraction,
                color = FuelledColors.Primary,
                label = "Downloading",
                valueText = "${(model.fraction * 100).toInt()}%",
                modifier = Modifier.semantics { testTag = "updates_progress" },
            )
        } else {
            Text(
                text = "Downloading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "updates_progress" },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Android will ask you to confirm the install when it finishes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The failure arm says what did NOT happen ("nothing was installed") before offering the retry.
 * A failed self-update is the moment a user most needs to know their working app is untouched.
 */
@Composable
private fun FailedCard(model: UpdateUi.Failed, onCheck: () -> Unit) {
    Card(tag = "updates_failed") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = FuelledColors.Warning)
            Text(
                text = "Update failed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = model.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "updates_reason" },
        )
        Spacer(Modifier.height(16.dp))
        AppPrimaryButton(
            text = "Check again",
            onClick = onCheck,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "updates_retry" },
        )
    }
}

/** The screen's one surface treatment, so the four states cannot drift apart visually. */
@Composable
private fun Card(tag: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
            .semantics { testTag = tag },
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

/**
 * The VM-backed Updates destination (UPD-09).
 *
 * BaseScreen because this is registered directly on the NavHost and owns its insets (SHELL-05).
 *
 * The Empty arm is UPD-08's: a platform that cannot install renders nothing. It is reached only
 * on iOS and desktop, where [com.kvdm.fuelled.core.updates.AppInstaller.supported] is false —
 * and on those targets Settings does not offer the entry point either, so in practice nobody
 * arrives here. The arm exists so that "unsupported" has a defined rendering rather than
 * depending on nobody finding the route.
 */
@Composable
fun UpdateRoute(
    viewModel: UpdateViewModel = koinViewModel(),
    onBack: () -> Unit = {},
) {
    BaseScreen {
        val state by viewModel.state.collectAsStateWithLifecycle()
        ContentStateContainer(state = state, screenTag = "updates", onRetry = viewModel::check) { model ->
            UpdateScreen(
                model = model,
                onCheck = viewModel::check,
                onDownload = { (model as? UpdateUi.Available)?.let { viewModel.download(it.toRelease()) } },
                onBack = onBack,
            )
        }
    }
}

/**
 * The screen's model back to a domain release for the download call.
 *
 * Only [AppRelease.assetUrl] and [AppRelease.assetSizeBytes] are actually read by
 * [UpdateViewModel.download] — the rest is carried so the shapes line up. The ViewModel holds
 * no release of its own on purpose: the thing on screen IS the thing that downloads, so the
 * two cannot disagree about which version was offered.
 */
private fun UpdateUi.Available.toRelease(): AppRelease = AppRelease(
    version = semver ?: SemVer(0, 0, 0),
    displayVersion = version,
    publishedAt = publishedAt,
    notes = notes,
    assetUrl = assetUrl,
    assetSizeBytes = assetSizeBytes,
)
