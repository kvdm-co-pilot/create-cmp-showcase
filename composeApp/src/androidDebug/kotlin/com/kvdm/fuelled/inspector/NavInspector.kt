package com.kvdm.fuelled.inspector

import com.kvdm.fuelled.presentation.navigation.NavInspectionHook
import java.util.concurrent.atomic.AtomicReference

/**
 * Debug-only sink for [NavInspectionHook]: [install] registers a listener that stores the
 * latest route/back-stack snapshot the common `AppNavHost` reports, so `GET /inspect/nav` can
 * read it synchronously from the HTTP thread without touching Compose state directly.
 *
 * Best-effort by design: until the first navigation event fires (cold start, before
 * `AppNavHost` has composed at least once), [current] reports an empty snapshot rather than
 * blocking or erroring — mirrors the tree/screenshot routes' "not ready yet, retry" posture,
 * just without the 503 (an empty nav snapshot is a valid, if uninteresting, answer).
 */
object NavInspector {

    data class Snapshot(val currentRoute: String?, val backStack: List<String>)

    private val EMPTY = Snapshot(currentRoute = null, backStack = emptyList())
    private val state = AtomicReference(EMPTY)

    /** Must run before the first Activity's setContent — same timing rule as [ComposeRootRegistry]. */
    fun install() {
        NavInspectionHook.listener = { current, backStack ->
            state.set(Snapshot(current, backStack))
        }
    }

    fun current(): Snapshot = state.get()

    /**
     * The jump half (`GET /inspect/navigate?route=…`): request navigation to [route] on the
     * main thread and wait (bounded) for the outcome. Coverage tool, not a behaviour proof —
     * see [NavInspectionHook.navigator]. Outcomes are honest, never fabricated:
     *  - `null` on success (the NavController accepted the route);
     *  - a message when no navigator is registered (nav host not composed yet — retry), when
     *    the route is unknown (NavController's own IllegalArgumentException, surfaced
     *    verbatim), or when the main thread didn't get to it in time.
     */
    fun navigate(route: String, timeoutMs: Long = 5_000): String? {
        val error = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val latch = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val nav = NavInspectionHook.navigator
            if (nav == null) {
                error.set("nav host not composed yet — no navigator registered. Retry shortly.")
            } else {
                try {
                    nav(route)
                } catch (e: IllegalArgumentException) {
                    error.set("unknown route ${'"'}$route${'"'} — ${e.message}")
                }
            }
            latch.countDown()
        }
        if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            return "main thread did not service the navigation within ${timeoutMs}ms"
        }
        return error.get()
    }
}
