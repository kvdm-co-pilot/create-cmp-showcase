package com.kvdm.cmpshowcase

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kvdm.cmpshowcase.di.initDesktopKoin

// Desktop dev-client: the shared commonMain UI in a live, clickable JVM window, sized like a
// phone. This is the daily dev loop — run it with Compose Hot Reload and watch edits land
// without restarting:
//
//   ./gradlew :composeApp:hotRunDesktop --auto     # hot reload, auto-reload on save
//   ./gradlew :composeApp:run                      # plain run (any JDK, no hot reload)
//
// See docs/dev-client.md for the full loop.
fun main() {
    initDesktopKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "CMP Showcase dev-client",
            state = rememberWindowState(size = DpSize(411.dp, 891.dp)),
        ) {
            App()
        }
    }
}
