package com.kvdm.fuelled.presentation.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android's "Remove animations" accessibility switch zeroes the global animator duration
 * scale; developer options can do the same. Either is the person saying "don't move".
 */
@Composable
actual fun platformReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
