package com.kvdm.cmpshowcase.inspector

import android.annotation.SuppressLint
import androidx.compose.ui.platform.ViewRootForTest
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Debug-only registry of live Compose roots, discovered via the public
 * [ViewRootForTest.Companion.onViewCreatedCallback] hook — the same mechanism the Compose
 * test framework uses. Installed from [startInspector] in Application.onCreate, BEFORE any
 * Activity exists, so every root (including dialogs/popups, each its own AndroidComposeView)
 * is captured.
 *
 * `@SuppressLint("VisibleForTests")`: ViewRootForTest is public API carrying a lint-severity
 * `@VisibleForTesting` annotation — debug-only usage is precisely its spirit. This class never
 * ships in release (the whole `inspector` package exists only in the androidDebug source set).
 */
@SuppressLint("VisibleForTests")
object ComposeRootRegistry {

    private val roots = CopyOnWriteArrayList<WeakReference<ViewRootForTest>>()

    /** Install the root-discovery callback. Must run before the first Activity's setContent. */
    fun install() {
        ViewRootForTest.onViewCreatedCallback = { root ->
            roots.removeAll { it.get() == null }
            roots += WeakReference(root)
        }
    }

    /**
     * The topmost live root: attached to a window, preferring the one with window focus
     * (so an open dialog/popup wins over the screen beneath it).
     */
    fun current(): ViewRootForTest? {
        val live = roots.mapNotNull { it.get() }.filter { it.view.isAttachedToWindow }
        return live.lastOrNull { it.view.hasWindowFocus() } ?: live.lastOrNull()
    }
}
