package com.kvdm.fuelled.testing

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * The one shell channel for the on-device tier. Every organ in this package — the asserts
 * that READ device state ([AlarmAsserts], [NotificationAsserts]) and the controls that
 * MUTATE it ([TimeWarp], [DozeControl], [PermissionControl], [ProcessControl],
 * [NetworkControl], [ConfigControl]) — goes through [exec], and every mutating organ gates
 * on [assumeOnEmulator]. One channel, one guard, so a new organ can't quietly invent a
 * weaker version of either.
 *
 * Why the shell at all: the states these organs put the device into (idle, offline,
 * permission-denied, dark, warped clock) are deliberately not reachable from app APIs —
 * they are the OS's side of the contract. The instrumentation's UiAutomation runs its
 * shell commands with the SHELL uid, which is privileged enough for all of them and
 * available on every stock emulator image. No root: `adb root` is refused on production
 * (user-build) images, including the stock Play-image AVDs, and every command an organ
 * issues has been verified to work without it.
 *
 * Constraints the code can't show:
 *  - STDOUT ONLY. UiAutomation's channel returns the command's stdout and silently drops
 *    stderr. This is not cosmetic: `pm grant` on an install-time permission prints its
 *    SecurityException to stderr (invisible here), and `pm grant` on an UNDECLARED
 *    permission prints nothing anywhere and exits 0 — a silent no-op (both verified on a
 *    stock API 35 image). An organ must therefore prove its command worked by re-reading
 *    the state it changed, never by trusting output or exit status it cannot see.
 *  - No shell interpretation. The command string is tokenized, not run through `sh -c` —
 *    no quoting, no pipes, no redirection. Compose commands accordingly (every organ's
 *    commands are plain token lists).
 */
object Shell {

    /** Runs [command] via UiAutomation (shell uid) and returns its full stdout. */
    fun exec(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
            return stream.readBytes().decodeToString()
        }
    }

    /** True on an emulator (`ro.kernel.qemu == 1`) — the property every guard checks. */
    fun isEmulator(): Boolean = exec("getprop ro.kernel.qemu").trim() == "1"

    /**
     * SKIPs the test (assumption violation, never a failure) unless it runs on an
     * emulator. Every state-mutating organ calls this first with its own message saying
     * what it would have done to a real phone's owner — a QA emulator is disposable
     * state; a person's device never is. Call it at the top of your own test too, so the
     * skip names the test rather than a helper frame.
     */
    fun assumeOnEmulator(message: String) {
        assumeTrue(message, isEmulator())
    }

    /**
     * A setting's current value, or null when unset — `settings get` prints the literal
     * string "null" for absent keys, and this maps it back to the truth.
     */
    fun readSetting(namespace: String, key: String): String? =
        exec("settings get $namespace $key").trim()
            .takeUnless { it.isEmpty() || it == "null" }

    /**
     * Restore a setting to the state [readSetting] captured: a prior value is put back,
     * an absent one is DELETED — putting a guessed default where none existed is itself
     * drift, and the next test would inherit it.
     */
    fun restoreSetting(namespace: String, key: String, prior: String?) {
        if (prior == null) {
            exec("settings delete $namespace $key")
        } else {
            exec("settings put $namespace $key $prior")
        }
    }
}
