package com.kvdm.fuelled.presentation.appupdates

import com.kvdm.fuelled.domain.model.AppRelease
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.usecase.CheckForUpdateUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.fakes.FakeAppInstaller
import com.kvdm.fuelled.testing.fakes.FakeUpdateRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The update surface's state machine (UPD-05/UPD-06/UPD-08/UPD-09). */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeUpdateRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val release = AppRelease(
        versionCode = 7,
        version = "0.7.0",
        publishedAt = "2026-08-25T10:00:00Z",
        notes = "notes",
        assetUrl = "https://example/fuelled-7.apk",
        assetSizeBytes = 18_400_000,
    )

    private fun viewModel(installer: FakeAppInstaller = FakeAppInstaller()) =
        UpdateViewModel(CheckForUpdateUseCase(repository, installer), installer)

    // SPEC: UPD-09
    @Test
    fun `an available update renders its version, size and notes`() = runTest(dispatcher) {
        repository.release = release
        val vm = viewModel()
        advanceUntilIdle()

        val ui = assertIs<ContentUiState.Content<UpdateUi>>(vm.state.value).data
        val available = assertIs<UpdateUi.Available>(ui)
        assertEquals("0.7.0", available.version)
        assertEquals("2026-08-25", available.publishedAt, "the timestamp is trimmed to the date")
        assertEquals("17 MB", available.sizeLabel)
    }

    // SPEC: UPD-05
    @Test
    fun `a failed check renders the error arm, never a confident up to date`() = runTest(dispatcher) {
        repository.failure = DomainError.Network
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is ContentUiState.Error, "currency the app could not verify is not reported")
        assertTrue(state.message.contains("unchanged"), "the copy says the installed build is untouched")
    }

    // SPEC: UPD-08
    @Test
    fun `an unsupported platform renders nothing at all`() = runTest(dispatcher) {
        repository.release = release
        val vm = viewModel(FakeAppInstaller(supported = false))
        advanceUntilIdle()

        assertEquals(
            ContentUiState.Empty,
            vm.state.value,
            "not an error and not a message — there is nothing here",
        )
    }

    // SPEC: UPD-06
    @Test
    fun `downloading hands the asset url to the installer`() = runTest(dispatcher) {
        repository.release = release
        val installer = FakeAppInstaller()
        val vm = viewModel(installer)
        advanceUntilIdle()

        vm.download(release)
        advanceUntilIdle()

        assertEquals("https://example/fuelled-7.apk", installer.installedUrl)
    }

    // SPEC: UPD-07
    @Test
    fun `a failed download says nothing was installed`() = runTest(dispatcher) {
        repository.release = release
        val vm = viewModel(FakeAppInstaller(installFails = true))
        advanceUntilIdle()

        vm.download(release)
        advanceUntilIdle()

        val ui = assertIs<ContentUiState.Content<UpdateUi>>(vm.state.value).data
        val failed = assertIs<UpdateUi.Failed>(ui)
        assertTrue(failed.reason.contains("Nothing was installed"))
    }
}
