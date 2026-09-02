package com.kvdm.fuelled.presentation.theme

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** iOS Settings → Accessibility → Motion → Reduce Motion. */
@Composable
actual fun platformReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
