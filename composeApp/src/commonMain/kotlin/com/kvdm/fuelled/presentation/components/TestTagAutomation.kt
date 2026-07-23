package com.kvdm.fuelled.presentation.components

import androidx.compose.ui.Modifier

/**
 * Exposes every `Modifier.testTag` in the subtree to the platform's UI-automation layer,
 * so id-based E2E selectors (Maestro, Appium, XCUITest) find them on a stock build with
 * no extra flags. Apply once at the root — `AppShell` does — and it covers the whole
 * subtree.
 *
 * Per-platform actuals:
 * - Android: `semantics { testTagsAsResourceId = true }` — tags surface as `resource-id`
 *   in the uiautomator tree.
 * - iOS: the same semantics flag — tags surface as `accessibilityIdentifier` (XCUITest).
 * - Desktop: no-op — no resource-id concept exists there; desktop tests use the Compose
 *   test APIs directly.
 */
expect fun Modifier.exposeTestTagsForAutomation(): Modifier
