package com.kvdm.fuelled

import androidx.compose.ui.window.ComposeUIViewController

// Compose Multiplatform iOS entry point, bridged from ContentView.swift via ComposeView.
// CMP 1.10: the iOS accessibility tree syncs automatically, so Compose testTag values are
// exposed to XCUITest/Appium as accessibilityIdentifier without extra config.
fun MainViewController() = ComposeUIViewController {
    App()
}
