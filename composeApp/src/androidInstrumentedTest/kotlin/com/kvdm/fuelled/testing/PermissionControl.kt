package com.kvdm.fuelled.testing

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue

/**
 * Runtime-permission control for the on-device tier — make "what happens when the user
 * denies it?" a test input instead of a support ticket.
 *
 * Why this exists: the recurring production shape is an app that assumes its grant. A
 * fresh install on API 33+ holds NO notification grant, every `notify()` is dropped
 * before it reaches the shade, and all of it is silent — the code runs, the phone stays
 * dark, and every JVM tier stays green. The denied state is the DEFAULT state for a new
 * user, and it was the one state no test visited.
 *
 * THE TRAP this file must document rather than hide — revoking kills the process:
 * revoking a runtime permission an app currently HOLDS kills that app's process, by OS
 * design (a process must not keep using a permission it lost). Verified live on a stock
 * API 35 emulator: `pm revoke <pkg> POST_NOTIFICATIONS` against a running holder killed
 * its pid on the spot. In this seam the instrumentation lives IN the app's process, so
 * "revoke my own held permission mid-test" is not a test step, it is the test shooting
 * itself: the run dies as "Process crashed", attributing the kill to nothing. That is
 * why this organ ships NO revoke bracket — [withPermissionDenied] instead runs your
 * block when the permission is already denied (the honest, reachable version of the
 * state) and SKIPs with the full story when it is not. To genuinely un-grant between
 * runs, do it from OUTSIDE the process — `adb shell pm revoke <pkg> <permission>` from a
 * terminal (the app dies; that is the documented behavior, happening where it can be
 * seen) — or uninstall/reinstall; grants survive `install -r` but not uninstall.
 *
 * The second trap — only RUNTIME permissions are controllable, and failure is silent:
 * `pm grant` on an install-time permission (INTERNET) throws a SecurityException that
 * UiAutomation's channel cannot even see (stderr is dropped — [Shell]), and on an
 * UNDECLARED permission it prints nothing and exits 0, a verified silent no-op. So
 * [grantPermission] never trusts the command: it re-reads the grant, and on failure
 * diagnoses WHICH misuse happened (undeclared vs install-time vs genuinely refused)
 * from PackageManager instead of from output that does not exist.
 */
object PermissionControl {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** True when [permission] is currently granted to the app under test. */
    fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Grant [permission] to the app under test via `pm grant`, verified by re-reading
     * the grant — never by trusting the command (see the header for why its failures
     * are invisible). ONE-WAY by design: there is no restoring revoke, because the
     * revoke would kill the process doing the restoring. The grant persists until the
     * app is uninstalled; a test that needs the denied state must run before anything
     * grants (see [withPermissionDenied]).
     */
    fun grantPermission(permission: String) {
        Shell.assumeOnEmulator(
            "PermissionControl runs only on emulators (ro.kernel.qemu != 1 here). A " +
                "grant on a real phone persists until uninstall — state the owner did " +
                "not choose. Run this suite on a stock QA AVD instead.",
        )
        if (isGranted(permission)) return
        Shell.exec("pm grant ${context.packageName} $permission")
        if (!isGranted(permission)) {
            fail(
                "pm grant did not grant '$permission' — ${diagnoseUngrantable(permission)}",
            )
        }
    }

    /**
     * Run [block] with [permission] DENIED — the fresh-install default on API 33+, and
     * the state most escaped bugs live in. Two honest branches:
     *
     *  - Already denied: the block runs directly. Nothing is mutated, nothing needs
     *    restoring — this bracket is safe anywhere, so it carries no emulator guard.
     *  - Currently granted: SKIP (assumption violation), because the only path from
     *    granted to denied kills this very process (header). The skip message carries
     *    the outside-the-process command that reaches the state for real.
     *
     * Suite-order honesty: anything that grants — including the seam's own
     * POST_NOTIFICATIONS setup — moves later tests into the skip branch, and the grant
     * outlives the run. The denied state is guaranteed only on a fresh install; a SKIP
     * here is the harness telling you that, not a flake.
     */
    fun <T> withPermissionDenied(permission: String, block: () -> T): T {
        assumeTrue(
            "'$permission' is currently GRANTED, and revoking a held permission kills " +
                "the holding process — this very test. Reach the denied state from " +
                "outside the process instead: `adb shell pm revoke " +
                "${context.packageName} $permission` (the app is killed; that is the " +
                "documented OS behavior), or uninstall and reinstall, then re-run.",
            !isGranted(permission),
        )
        return block()
    }

    /**
     * Name the reason a grant could not land, from PackageManager truth — the shell's
     * own error surface is stderr, which the instrumentation channel drops.
     */
    private fun diagnoseUngrantable(permission: String): String {
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList().orEmpty()
        if (permission !in requested) {
            return "it is not declared in the manifest, and pm grant on an undeclared " +
                "permission is a verified silent no-op (no output, exit 0). Declare it " +
                "with <uses-permission> first."
        }
        val protection = try {
            val info = context.packageManager.getPermissionInfo(permission, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.protection
            } else {
                @Suppress("DEPRECATION")
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return "the permission itself does not exist on this device/API level."
        }
        if (protection != PermissionInfo.PROTECTION_DANGEROUS) {
            return "it is not a runtime permission (protection level $protection): " +
                "install-time permissions are 'not a changeable permission type' — " +
                "granted at install or never, and pm grant/revoke cannot touch them."
        }
        return "it is a declared runtime permission, so this is a genuine refusal — " +
            "check `dumpsys package ${context.packageName}` for a policy-fixed flag."
    }
}
