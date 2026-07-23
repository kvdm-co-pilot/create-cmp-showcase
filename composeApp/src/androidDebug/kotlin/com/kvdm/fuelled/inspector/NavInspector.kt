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
}
