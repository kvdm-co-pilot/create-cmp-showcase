package com.kvdm.fuelled.presentation.components

import androidx.compose.ui.Modifier

/**
 * iOS: no-op — `testTagsAsResourceId` is Android-only in the compose `ui` artifact at this
 * version set. Compose Multiplatform on iOS already exposes `Modifier.testTag` through the
 * accessibility tree (XCUITest/Appium can match it as the accessibility identifier), so no
 * extra semantics flag is required here.
 */
actual fun Modifier.exposeTestTagsForAutomation(): Modifier = this
