package com.kvdm.cmpshowcase.inspector

import android.app.Application

/**
 * DEBUG variant: install the Compose root registry (must happen BEFORE any Activity so the
 * `onViewCreatedCallback` hook catches every root), then start the loopback-only inspection
 * server on 127.0.0.1:9500. Reach it from the host via `adb forward tcp:9500 tcp:9500`.
 *
 * The release source set carries a same-signature no-op twin — the compiler picks the variant
 * body, so release builds contain no inspector code at all (structural absence, not a flag).
 */
fun Application.startInspector() {
    ComposeRootRegistry.install()
    InspectorHttpServer.start(appId = packageName)
}
