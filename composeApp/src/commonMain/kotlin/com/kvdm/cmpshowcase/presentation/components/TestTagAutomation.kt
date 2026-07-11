package com.kvdm.cmpshowcase.presentation.components

import androidx.compose.ui.Modifier

/**
 * Exposes every `Modifier.testTag` in the subtree to the platform's UI-automation layer,
 * so Appium/uiautomator id-selectors (and the cmp-test-generated suites) find them on a
 * stock build with no extra flags.
 *
 * - Android actual: `semantics { testTagsAsResourceId = true }` → tags surface as
 *   `resource-id` in the uiautomator/Appium tree.
 * - iOS actual: same semantics flag → tags surface as `accessibilityIdentifier` (XCUITest).
 * - Desktop actual: no-op — the skiko `ui` artifact has no `testTagsAsResourceId`
 *   (there is no resource-id concept on desktop; tests use the Compose test APIs directly).
 *
 * Apply once at the root (AppShell does); it covers the whole subtree.
 */
expect fun Modifier.exposeTestTagsForAutomation(): Modifier
