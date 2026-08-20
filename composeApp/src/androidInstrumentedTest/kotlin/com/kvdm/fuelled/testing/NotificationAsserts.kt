package com.kvdm.fuelled.testing

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue

/**
 * Notification assertions against the REAL NotificationManager — the shade as the OS sees
 * it, not as the app hopes it is.
 *
 * Why this exists: "the notification posted" is an asynchronous OS-side fact. Posting is
 * fire-and-forget, delivery is ranked/filtered/deferred, and none of it is observable from
 * a JVM test. The recurring production failures this file targets are all of the shape
 * "the code ran, the phone stayed dark": a channel created with the wrong importance, a
 * full-screen intent silently dropped, a re-post under a changed channel id that Android
 * discards without error.
 *
 * Constraints the code can't show:
 *  - `activeNotifications` only returns notifications the OS ACCEPTED. On API 33+ a fresh
 *    install has no POST_NOTIFICATIONS grant and everything is dropped before it reaches
 *    the shade — take the grant in your test's @Before via
 *    `uiAutomation.grantRuntimePermission(pkg, "android.permission.POST_NOTIFICATIONS")`
 *    (see PlatformBehaviorSeamTest). A suite that forgets the grant reports the exact bug
 *    it exists to catch, so [awaitNotification]'s failure message reminds you.
 *  - Posting is asynchronous; every positive assertion here is a bounded poll, never a
 *    single read. The negative check ([assertNoNotification]) polls the FULL window —
 *    absence is only meaningful after the post would have landed.
 *  - Channel behavior (sound, vibration, DND bypass) is partly OS/OEM-owned: importance is
 *    app-controlled at creation, but `lockscreenVisibility`/`bypassDnd`/sound routing are
 *    "modifiable by the system and the ranker" and read back as defaults regardless of
 *    what you set. Assert what the app controls; leave the OEM half to the manual tier
 *    (see docs/TESTING.md).
 */
object NotificationAsserts {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private const val DEFAULT_TIMEOUT_MS = 10_000L
    private const val POLL_MS = 100L

    /**
     * Waits (bounded poll, [timeoutMs]) for a posted notification matching [predicate] and
     * returns it. Fails with the current shade contents — the diff you actually need.
     */
    fun awaitNotification(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        predicate: (StatusBarNotification) -> Boolean,
    ): StatusBarNotification {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            manager.activeNotifications.firstOrNull(predicate)?.let { return it }
            Thread.sleep(POLL_MS)
        }
        fail(
            "no matching notification posted within ${timeoutMs}ms. " +
                "Shade holds: ${describeShade()}. " +
                "If the shade is empty on API 33+, check the POST_NOTIFICATIONS grant first.",
        )
        error("unreachable")
    }

    /** [awaitNotification] by the (id, tag) pair the app posted under. */
    fun awaitNotification(
        id: Int,
        tag: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): StatusBarNotification =
        awaitNotification(timeoutMs) { it.id == id && it.tag == tag }

    /**
     * Asserts NOTHING matching [predicate] is in the shade for the full [windowMs].
     * Deliberately slow: a notification that appears 2s late is still a failure, so the
     * whole window is watched — use this sparingly, for "the cancel actually cancelled".
     */
    fun assertNoNotification(
        windowMs: Long = 3_000L,
        predicate: (StatusBarNotification) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + windowMs
        while (System.currentTimeMillis() < deadline) {
            val hit = manager.activeNotifications.firstOrNull(predicate)
            if (hit != null) fail("unexpected notification in the shade: ${describe(hit)}")
            Thread.sleep(POLL_MS)
        }
    }

    /**
     * Asserts the channel exists with AT LEAST [importanceFloor]. A floor, not equality:
     * the user can raise a channel's importance and the app must keep working — what the
     * app must guarantee is the minimum it created the channel with.
     *
     * The classic escaped bug: a heads-up channel created as IMPORTANCE_DEFAULT renders as
     * a silent shade line — code identical, behavior invisible to every JVM tier.
     *
     * Channels exist from API 26; on older devices the test is skipped (assumption), never
     * vacuously green.
     */
    fun assertChannelExists(
        channelId: String,
        importanceFloor: Int = NotificationManager.IMPORTANCE_DEFAULT,
    ) {
        assumeTrue(
            "notification channels need API 26+ (device is ${Build.VERSION.SDK_INT})",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
        )
        val channel = manager.getNotificationChannel(channelId)
            ?: fail(
                "notification channel '$channelId' does not exist. Channels present: " +
                    manager.notificationChannels.joinToString { it.id },
            ).let { error("unreachable") }
        assertTrue(
            "channel '$channelId' importance is ${channel.importance}, below the required " +
                "floor $importanceFloor — it will not behave (heads-up/sound) the way the " +
                "feature assumes",
            channel.importance >= importanceFloor,
        )
    }

    /**
     * Asserts the app is currently CAPABLE of a full-screen (lock-screen takeover)
     * notification on [channelId]:
     *  - the channel exists at IMPORTANCE_HIGH or above (below HIGH the OS won't launch
     *    the full-screen intent, it just posts quietly), and
     *  - on API 34+, `canUseFullScreenIntent()` — Android 14 turned USE_FULL_SCREEN_INTENT
     *    into a revocable special access, so a manifest permission alone stopped being
     *    proof. On 24..33 the manifest grant is install-time and not queryable, so only
     *    the channel half is asserted there.
     *
     * This is capability, not delivery: whether a takeover actually renders over the lock
     * screen on a locked, Doze-ing, OEM-skinned device belongs to the manual tier.
     */
    fun assertFullScreenIntentCapable(channelId: String) {
        assertChannelExists(channelId, NotificationManager.IMPORTANCE_HIGH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertTrue(
                "canUseFullScreenIntent() is false — Android 14+ treats full-screen intents " +
                    "as revocable special access; declare USE_FULL_SCREEN_INTENT in the " +
                    "manifest and (for alarm/call apps outside the auto-grant) send the user " +
                    "to ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT",
                manager.canUseFullScreenIntent(),
            )
        }
    }

    private fun describeShade(): String {
        val active = manager.activeNotifications
        return if (active.isEmpty()) "(empty)" else active.joinToString { describe(it) }
    }

    private fun describe(sbn: StatusBarNotification): String =
        "[id=${sbn.id} tag=${sbn.tag} channel=${
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sbn.notification.channelId else "n/a"
        }]"
}
