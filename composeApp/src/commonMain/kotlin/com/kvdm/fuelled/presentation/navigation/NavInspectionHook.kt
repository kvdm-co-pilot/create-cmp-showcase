package com.kvdm.fuelled.presentation.navigation

/**
 * A tiny common seam [AppNavHost] reports every navigation change to. [listener] is `null` by
 * default — a structural no-op — and is registered only by the optional debug-only on-device
 * inspector, when that feature is included in the project. Release builds never register a
 * listener, so invoking a `null` one stays free.
 *
 * This indirection is the whole point: `AppNavHost` lives in commonMain and must never
 * reference a debug-only class directly (a debug-only source set may not even exist in every
 * build of this project) — it only ever talks to this common object.
 */
object NavInspectionHook {
    /**
     * Invoked on every back-stack change with `currentRoute` (nullable — no destination
     * resolved yet) and `backStack` (root-to-top, best-effort: derived from
     * `NavController.currentBackStack`, which is itself a live/soon-to-be-live snapshot, not a
     * durable history).
     */
    var listener: ((currentRoute: String?, backStack: List<String>) -> Unit)? = null

    /**
     * The JUMP half of the seam: [AppNavHost] registers a function that navigates the real
     * `NavController` to a route. Debug-only callers (the debug inspector's navigate endpoint) use it
     * so a walkthrough can enumerate the nav graph mechanically — coverage by route-jump —
     * instead of synthesizing taps with guessed settle times. Taps remain the tool for
     * BEHAVIOUR proofs (a toggle, a search); this is for COVERAGE. `null` outside debug
     * inspection, and must only ever be invoked on the main thread (NavController requirement).
     */
    var navigator: ((route: String) -> Unit)? = null
}
