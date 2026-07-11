package com.kvdm.cmpshowcase.inspector

import android.app.Application

/**
 * RELEASE variant: no-op twin of the androidDebug `startInspector()`. Release builds compile
 * this body instead — no server class, no registry, no inspector endpoint strings. The
 * inspector is structurally absent from release, not merely disabled.
 */
@Suppress("UnusedReceiverParameter")
fun Application.startInspector() {
    // Intentionally empty.
}
