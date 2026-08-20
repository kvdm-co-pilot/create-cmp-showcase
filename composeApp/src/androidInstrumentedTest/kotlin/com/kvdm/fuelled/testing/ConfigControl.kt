package com.kvdm.fuelled.testing

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * Configuration control for the on-device tier — dark mode, font scale, and per-app
 * locale as test inputs.
 *
 * Why this exists: configuration is the OTHER classic state-loss lever. Every one of
 * these switches delivers a configuration change, and a configuration change destroys
 * and recreates the foreground activity — the same "came back and it was gone" class
 * [ProcessControl] targets, triggered by the user flipping dark mode or bumping the
 * system font size mid-session. The JVM tiers render one configuration forever; a claim
 * like "the form survives the user toggling dark mode" or "the layout holds at 1.3x
 * font scale" needs the device to actually change underneath the running app. Compose
 * with the seam's observation helpers: flip the config, then assert what the app did
 * about it.
 *
 * Mechanisms, each verified root-free from the shell uid on a stock user-build emulator
 * image (API 35), each snapshot-restored in `finally`:
 *  - [withDarkMode] — `cmd uimode night yes|no` (read back via the same command's
 *    "Night mode: X" line, so restore is snapshot-exact, including `auto` and the
 *    custom modes).
 *  - [withFontScale] — `settings put system font_scale <x>`; an absent prior value is
 *    DELETED on restore, not defaulted to 1.0 (see [Shell.restoreSetting]).
 *  - [withAppLocale] — `cmd locale set-app-locales <pkg> --locales <tags>`, the per-app
 *    locale system (API 33+; the bracket SKIPs below that). App-scoped on purpose: it
 *    is the same state the user's own per-app language setting writes, and it touches
 *    no other app on the device. Restore passes the snapshotted list back, or clears by
 *    omitting `--locales` (the documented "empty when unspecified").
 *
 * Constraints the code can't show:
 *  - DEVICE-WIDE locale has no root-free path — changing it needs
 *    CHANGE_CONFIGURATION/root, so this organ does not offer it rather than offering a
 *    broken version of it. For "the whole device is German", set the AVD up that way;
 *    for "this app renders German", [withAppLocale] is the real user-reachable state.
 *  - Config delivery is asynchronous and, for a foreground activity, DESTRUCTIVE — the
 *    activity you held a reference to before the flip is not the one resumed after it.
 *    That is the point, not a flake: capture identity before, await the recreated
 *    activity ([ProcessControl.resumedActivity]), then assert.
 *  - What the app DOES with the change (re-render, reload resources, lose the form) is
 *    the app's half; these brackets only guarantee the device half moved.
 */
object ConfigControl {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Run [block] with night mode forced on (`cmd uimode night yes`), restoring the
     * snapshotted mode afterwards even when the block throws.
     */
    fun <T> withDarkMode(block: () -> T): T {
        assumeEmulator("Forcing dark mode")
        val prior = readNightMode()
        Shell.exec("cmd uimode night yes")
        try {
            return block()
        } finally {
            Shell.exec("cmd uimode night $prior")
        }
    }

    /**
     * Run [block] with the system font scale at [scale] (e.g. 1.3f — the accessibility
     * sizes users actually run), restoring the prior value (or its absence) afterwards.
     */
    fun <T> withFontScale(scale: Float, block: () -> T): T {
        assumeEmulator("Changing the font scale")
        val prior = Shell.readSetting("system", "font_scale")
        Shell.exec("settings put system font_scale $scale")
        try {
            return block()
        } finally {
            Shell.restoreSetting("system", "font_scale", prior)
        }
    }

    /**
     * Run [block] with the app under test set to [languageTag] (BCP-47, e.g. "fr-FR"),
     * via the per-app locale system — API 33+; SKIPs (assumption) below that, never
     * vacuously green. Restores the snapshotted locale list afterwards.
     */
    fun <T> withAppLocale(languageTag: String, block: () -> T): T {
        assumeEmulator("Changing the app locale")
        assumeTrue(
            "per-app locales need API 33+ (device is ${Build.VERSION.SDK_INT}); there " +
                "is no root-free device-wide locale switch to fall back to — configure " +
                "the AVD's locale instead for older images",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
        val prior = readAppLocales() // comma-separated tags, or "" when following system
        Shell.exec("cmd locale set-app-locales ${context.packageName} --locales $languageTag")
        try {
            return block()
        } finally {
            if (prior.isEmpty()) {
                // Omitting --locales is the documented "empty list", i.e. follow system.
                Shell.exec("cmd locale set-app-locales ${context.packageName}")
            } else {
                Shell.exec("cmd locale set-app-locales ${context.packageName} --locales $prior")
            }
        }
    }

    /** The current night mode word as `cmd uimode night` prints it (`yes`/`no`/`auto`/custom). */
    private fun readNightMode(): String =
        Shell.exec("cmd uimode night").substringAfter("Night mode:").trim().ifEmpty { "no" }

    /** The app's current locale list ("fr-FR,en" style), or "" when it follows the system. */
    private fun readAppLocales(): String =
        Shell.exec("cmd locale get-app-locales ${context.packageName}")
            .substringAfter("are [", "").substringBefore("]").trim()

    private fun assumeEmulator(what: String) {
        Shell.assumeOnEmulator(
            "ConfigControl runs only on emulators (ro.kernel.qemu != 1 here). $what on " +
                "a real phone rewrites its owner's chosen settings — run this suite on " +
                "a stock QA AVD instead.",
        )
    }
}
