package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.ReminderCapability
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The once-ever permission ask (specs/notifications.spec.md NOTIF-01/NOTIF-02). The whole
 * policy is in the use case's guards, so the whole policy is testable here — no device, no
 * dialog, just the scripted answer on the fake scheduler.
 */
class RequestNotificationPermissionUseCaseTest {

    private val denied = ReminderCapability(notificationsAllowed = false, exactAlarmsAllowed = false)

    private fun fixture(
        appState: FakeAppStateRepository,
        scheduler: FakeReminderScheduler,
    ): RequestNotificationPermissionUseCase {
        val repo = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
        val arm = ArmMealRemindersUseCase(
            repo,
            scheduler,
            appState,
            TomorrowUnplannedUseCase(repo, FakeTimeSignal(TEST_NOW), TEST_ZONE),
        )
        return RequestNotificationPermissionUseCase(appState, scheduler, arm)
    }

    // SPEC: NOTIF-01
    @Test
    fun `asks once on a denied onboarded install, records the ask, and never asks again`() = runTest {
        val appState = FakeAppStateRepository().apply { onboarded = true }
        val scheduler = FakeReminderScheduler(denied).apply { permissionAnswer = false }
        val request = fixture(appState, scheduler)

        request()
        assertEquals(1, scheduler.requestPermissionCount)
        assertTrue(appState.notifPromptShown, "the ask is recorded whatever the answer was")

        // Every later Today open calls this again — that is the design; the guards answer.
        request()
        request()
        assertEquals(1, scheduler.requestPermissionCount, "denied means denied — no re-prompt, ever")
    }

    // SPEC: NOTIF-01
    @Test
    fun `never asks before onboarding, and never asks when notifications are already allowed`() = runTest {
        val notOnboarded = FakeAppStateRepository()
        val deniedScheduler = FakeReminderScheduler(denied)
        fixture(notOnboarded, deniedScheduler).invoke()
        assertEquals(0, deniedScheduler.requestPermissionCount, "reminders cannot matter before the interview")
        assertFalse(notOnboarded.notifPromptShown)

        val onboarded = FakeAppStateRepository().apply { onboarded = true }
        val allowedScheduler = FakeReminderScheduler() // notifications already allowed
        fixture(onboarded, allowedScheduler).invoke()
        assertEquals(0, allowedScheduler.requestPermissionCount, "nothing to ask for")
    }

    // SPEC: NOTIF-01
    @Test
    fun `an ask that could not be shown is not recorded - the one real ask stays available`() = runTest {
        val appState = FakeAppStateRepository().apply { onboarded = true }
        // Null is the scheduler's "no dialog was shown" — no Activity to host it, or a
        // platform with no dialog at all.
        val scheduler = FakeReminderScheduler(denied).apply { permissionAnswer = null }
        val request = fixture(appState, scheduler)

        request()
        assertFalse(appState.notifPromptShown, "a dialog nobody saw must not burn the once-ever ask")

        // The next open CAN show it — and it still gets its one real ask.
        scheduler.permissionAnswer = false
        request()
        assertTrue(appState.notifPromptShown)
    }

    // SPEC: NOTIF-02
    @Test
    fun `a grant re-arms in the same act, so the schedule is live from the yes`() = runTest {
        val appState = FakeAppStateRepository().apply { onboarded = true }
        val scheduler = FakeReminderScheduler(denied).apply { permissionAnswer = true }
        val request = fixture(appState, scheduler)

        request()
        assertTrue(appState.notifPromptShown)
        assertTrue(scheduler.armed.isNotEmpty(), "the grant re-armed the day's schedule")
        // Exact alarms are a separate permission the grant says nothing about — what the
        // re-arm must prove is that the set was built AFTER the grant, i.e. deliverable.
        assertTrue(
            scheduler.armed.none { it.mode == ReminderMode.UNAVAILABLE },
            "armed AFTER the grant — not the UNAVAILABLE set built before it",
        )
    }
}
