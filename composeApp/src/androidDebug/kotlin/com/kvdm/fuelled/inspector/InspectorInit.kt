package com.kvdm.fuelled.inspector

import android.app.Application

/**
 * DEBUG variant: install the Compose root registry and the nav-inspection listener (both must
 * happen BEFORE any Activity — the registry so `onViewCreatedCallback` catches every root, the
 * nav listener so the first `AppNavHost` composition is observed), chain in the crash recorder,
 * then start the loopback-only inspection server on 127.0.0.1:9500. Reach it from the host via
 * `adb forward tcp:9500 tcp:9500`.
 *
 * The release source set carries a same-signature no-op twin — the compiler picks the variant
 * body, so release builds contain no inspector code at all (structural absence, not a flag).
 */
fun Application.startInspector() {
    ComposeRootRegistry.install()
    NavInspector.install()
    CrashRecorder.install(this)
    InspectorHttpServer.start(appId = packageName, context = this)
}
