package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.AppRelease
import com.kvdm.fuelled.domain.model.SemVer
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.UpdateAvailability
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeAppInstaller
import com.kvdm.fuelled.testing.fakes.FakeUpdateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The update DECISION (UPD-02..UPD-05, UPD-08) — every branch that compares two integers,
 * tested on the JVM without a device. The act of installing is the part that needs one; this
 * is deliberately not that part.
 */
class CheckForUpdateUseCaseTest {

    private val repository = FakeUpdateRepository()

    private fun release(version: SemVer, asset: String? = "https://example/fuelled.apk") = AppRelease(
        version = version,
        displayVersion = version.toString(),
        publishedAt = "2026-08-25T10:00:00Z",
        notes = "notes",
        assetUrl = asset,
        assetSizeBytes = 1024,
    )

    private fun useCase(installed: SemVer? = SemVer(0, 6, 0), supported: Boolean = true) =
        CheckForUpdateUseCase(repository, FakeAppInstaller(installed, supported))

    // SPEC: UPD-02
    @Test
    fun `0 point 10 is newer than 0 point 9 — the case string ordering gets wrong`() = runTest {
        // THE regression this clause exists for. Compared as strings "0.10.0" < "0.9.0",
        // because '1' < '9' — so a lexical implementation would refuse to offer 0.10.0 to
        // someone on 0.9.0, silently, forever. Compared component by component it is newer.
        repository.release = release(SemVer(0, 10, 0))
        val available = assertIs<AppResult.Success<UpdateAvailability>>(
            useCase(installed = SemVer(0, 9, 0))(),
        ).value
        assertIs<UpdateAvailability.Available>(available)

        repository.release = release(SemVer(0, 9, 0))
        val notAvailable = assertIs<AppResult.Success<UpdateAvailability>>(
            useCase(installed = SemVer(0, 10, 0))(),
        ).value
        assertEquals(UpdateAvailability.UpToDate, notAvailable, "0.10.0 is not behind 0.9.0")
    }

    // SPEC: UPD-03
    @Test
    fun `an unreadable installed version offers nothing`() = runTest {
        // The platform could not report a parseable versionName. "Newer than unknown" is not a
        // claim this feature is allowed to make — it would offer an update over a build it
        // could not identify, which is how a downgrade ships.
        repository.release = release(SemVer(9, 9, 9))
        assertEquals(
            UpdateAvailability.UpToDate,
            assertIs<AppResult.Success<UpdateAvailability>>(useCase(installed = null)()).value,
        )
    }

    // SPEC: UPD-03
    @Test
    fun `an equal or newer installed build is up to date, never a downgrade offer`() = runTest {
        repository.release = release(SemVer(0, 6, 0))
        assertEquals(
            UpdateAvailability.UpToDate,
            assertIs<AppResult.Success<UpdateAvailability>>(useCase(installed = SemVer(0, 6, 0))()).value,
            "equal is current",
        )

        // A local debug build ahead of every release. Android would refuse the install anyway,
        // and offering it would be proposing to replace newer work with older.
        repository.release = release(SemVer(0, 6, 0))
        assertEquals(
            UpdateAvailability.UpToDate,
            assertIs<AppResult.Success<UpdateAvailability>>(useCase(installed = SemVer(9, 9, 9))()).value,
        )
    }

    // SPEC: UPD-04
    @Test
    fun `a release with no installable asset is nothing to install, not an error`() = runTest {
        repository.release = release(SemVer(9, 9, 9), asset = null)
        assertEquals(
            UpdateAvailability.UpToDate,
            assertIs<AppResult.Success<UpdateAvailability>>(useCase()()).value,
            "a source-only tag is normal; nothing is wrong",
        )
    }

    // SPEC: UPD-04
    @Test
    fun `a repository that has published nothing is up to date`() = runTest {
        repository.release = null
        assertEquals(
            UpdateAvailability.UpToDate,
            assertIs<AppResult.Success<UpdateAvailability>>(useCase()()).value,
        )
    }

    // SPEC: UPD-05
    @Test
    fun `a failed check surfaces the failure and never reports up to date`() = runTest {
        repository.failure = DomainError.Network
        val result = useCase()()
        assertTrue(result is AppResult.Failure, "a check that could not complete must not claim currency")
    }

    // SPEC: UPD-08
    @Test
    fun `an unsupported platform never even asks GitHub`() = runTest {
        repository.failure = DomainError.Network // would fail loudly if it were consulted
        assertEquals(
            UpdateAvailability.Unsupported,
            assertIs<AppResult.Success<UpdateAvailability>>(useCase(supported = false)()).value,
            "no network call is worth making for an answer that cannot be acted on",
        )
    }
}
