package com.kvdm.fuelled.presentation.appupdates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.core.updates.InstallCapability
import com.kvdm.fuelled.domain.model.AppRelease
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.UpdateAvailability
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.CheckForUpdateUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Updates surface's ViewModel (UPD-05/UPD-09).
 *
 * No `try`/`catch` (ARCH-07): the use case hands back typed [AppResult] values and this folds
 * them into the shared state machine. The failure arm exists because the ONE answer this
 * feature must never give is a confident "up to date" it could not verify — so a check that
 * could not complete says so rather than degrading to good news.
 */
class UpdateViewModel(
    private val checkForUpdate: CheckForUpdateUseCase,
    private val installer: InstallCapability,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<UpdateUi>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<UpdateUi>> = _state.asStateFlow()

    init { check() }

    fun check() {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = when (val result = checkForUpdate()) {
                is AppResult.Failure -> ContentUiState.Error(result.error.message())
                is AppResult.Success -> when (val availability = result.value) {
                    // UPD-08: an unsupported platform renders nothing at all. Empty rather than
                    // an error or a message — there is nothing here, which is different from
                    // something having gone wrong.
                    UpdateAvailability.Unsupported -> ContentUiState.Empty
                    UpdateAvailability.UpToDate ->
                        ContentUiState.Content(UpdateUi.UpToDate(installedLabel()))
                    is UpdateAvailability.Available ->
                        ContentUiState.Content(availability.release.toUi(installedLabel()))
                }
            }
        }
    }

    /**
     * UPD-06: download and hand off. The screen shows [UpdateUi.Downloading] while it runs —
     * without a determinate fraction, because the installer seam reports completion, not
     * progress. A bar that invented one would be the thing UPD-09 forbids.
     */
    fun download(release: AppRelease) {
        viewModelScope.launch {
            _state.value = ContentUiState.Content(
                UpdateUi.Downloading(installedLabel(), release.version, fraction = null),
            )
            val result = installer.downloadAndInstall(release.assetUrl.orEmpty(), release.assetSizeBytes)
            if (result is AppResult.Failure) {
                _state.value = ContentUiState.Content(
                    UpdateUi.Failed(installedLabel(), result.error.downloadMessage()),
                )
            }
            // Success deliberately leaves the Downloading state in place: the system installer
            // is now in front of the user and this process is about to be replaced. Rendering
            // "done" behind a dialog that has not been confirmed yet would be a lie with a
            // very short shelf life.
        }
    }

    private fun installedLabel(): String = installer.installedVersionCode.toString()

    private fun DomainError.message(): String = when (this) {
        is DomainError.Network -> "Could not reach GitHub. Your installed build is unchanged."
        is DomainError.NotFound -> "No releases published yet."
        else -> "Could not check for updates. Your installed build is unchanged."
    }

    private fun DomainError.downloadMessage(): String = when (this) {
        is DomainError.Network -> "That download did not finish. Nothing was installed — try again when you have signal."
        else -> "That download could not be verified, so nothing was installed. Try again."
    }
}

/** The domain release, as the screen shows it (UPD-09). */
private fun AppRelease.toUi(installed: String): UpdateUi.Available = UpdateUi.Available(
    installed = installed,
    version = version,
    publishedAt = publishedAt.take(10),
    sizeLabel = assetSizeBytes?.let { "${(it / 1_048_576.0).toInt()} MB" } ?: "",
    notes = notes,
    assetUrl = assetUrl,
    assetSizeBytes = assetSizeBytes,
)
