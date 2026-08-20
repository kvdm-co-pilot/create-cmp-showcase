package com.kvdm.fuelled.testing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail

/**
 * Network-state control for the on-device tier — make "offline" a state the test puts
 * the device into, instead of a branch the code claims to handle.
 *
 * Why this exists: the template ships a connectivity abstraction
 * (`core/connectivity/NetworkMonitor.kt`, expect/actual) whose entire reason to exist is
 * behavior under network loss — and no tier could ever produce network loss, so every
 * claim built on it (offline banners, retry paths, queued writes) was structurally
 * unprovable. Desktop fakes prove the ViewModel folds a Boolean; only the device can
 * prove the Boolean tracks the world.
 *
 * Mechanisms, each verified root-free from the shell uid on a stock user-build emulator
 * image (API 35):
 *  - [withAirplaneMode] — `cmd connectivity airplane-mode enable|disable`, the modern,
 *    reliable, root-free switch (verified: active default network gone on enable, back
 *    on disable; the same command queries the state, so restore is snapshot-exact).
 *    The LEGACY route — `settings put global airplane_mode_on` plus a broadcast — is NOT
 *    usable root-free on modern API levels: the broadcast is protected, and the setting
 *    alone changes nothing. Prefer this bracket for "the app is offline".
 *  - [withWifiDisabled] / [withMobileDataDisabled] — `svc wifi|data disable|enable`,
 *    for claims about ONE transport (e.g. metered-only behavior, wifi-to-cellular
 *    failover). Prior state is snapshotted from `settings get global wifi_on` /
 *    `mobile_data` (both verified to track the toggles) so restore matches what was.
 *
 * Constraints the code can't show:
 *  - State changes are ASYNCHRONOUS. Each bracket polls ConnectivityManager (bounded)
 *    until the state actually lands before running the block — asserting "offline
 *    behavior" while the network is still up tests nothing — and polls the way back on
 *    exit, because a device left offline poisons every later test (the restore runs in
 *    `finally` either way; if connectivity does not return in time the bracket fails
 *    loudly rather than leaving a silent trap).
 *  - The emulator's adb transport rides the qemu pipe, not the device's network — so
 *    airplane mode cannot sever the harness from the device. On a REAL device attached
 *    over wifi-adb it would, which is one more reason these brackets are emulator-only.
 *  - Emulator networking is NAT through the host: "wifi" and "cellular" here are both
 *    the host's connection wearing different transports. Presence/absence and transport
 *    switching are faithful; bandwidth, latency, captive portals, and flaky-RSSI
 *    behavior are not reproduced.
 */
object NetworkControl {

    private val connectivity: ConnectivityManager
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private const val SETTLE_TIMEOUT_MS = 15_000L

    /**
     * Run [block] with the device fully offline (airplane mode), restoring the prior
     * airplane state afterwards even when the block throws. The block runs only after
     * ConnectivityManager reports no active network; on exit the bracket waits for
     * connectivity to return and fails loudly if it does not.
     */
    fun <T> withAirplaneMode(block: () -> T): T {
        assumeEmulator("Airplane mode")
        val prior = Shell.exec("cmd connectivity airplane-mode").trim() // enabled|disabled
        Shell.exec("cmd connectivity airplane-mode enable")
        try {
            awaitState("no active network (airplane mode)") { connectivity.activeNetwork == null }
            return block()
        } finally {
            Shell.exec("cmd connectivity airplane-mode ${if (prior == "enabled") "enable" else "disable"}")
            if (prior != "enabled") {
                awaitState("connectivity restored after airplane mode") {
                    connectivity.activeNetwork != null
                }
            }
        }
    }

    /**
     * Run [block] with wifi off (`svc wifi disable`), restoring the snapshotted state.
     * The block runs once no active network carries TRANSPORT_WIFI — on an emulator with
     * cellular up, the default network fails over to it, which is itself testable
     * behavior (assert what [block] sees, not that everything went dark).
     */
    fun <T> withWifiDisabled(block: () -> T): T =
        withTransportDisabled("wifi", "wifi_on", NetworkCapabilities.TRANSPORT_WIFI, block)

    /** Run [block] with mobile data off (`svc data disable`), restoring the snapshotted state. */
    fun <T> withMobileDataDisabled(block: () -> T): T =
        withTransportDisabled("data", "mobile_data", NetworkCapabilities.TRANSPORT_CELLULAR, block)

    private fun <T> withTransportDisabled(
        svcName: String,
        settingKey: String,
        transport: Int,
        block: () -> T,
    ): T {
        assumeEmulator("Disabling $svcName")
        val prior = Shell.readSetting("global", settingKey) // "1" | "0" | null
        Shell.exec("svc $svcName disable")
        try {
            awaitState("no active network on the '$svcName' transport") {
                !activeNetworkHas(transport)
            }
            return block()
        } finally {
            if (prior != "0") {
                Shell.exec("svc $svcName enable")
                awaitState("'$svcName' transport restored") { activeNetworkHas(transport) }
            }
        }
    }

    private fun activeNetworkHas(transport: Int): Boolean {
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.hasTransport(transport) == true
    }

    /** Bounded poll; fails naming [what] so a timeout diagnoses itself. */
    private fun awaitState(what: String, timeoutMs: Long = SETTLE_TIMEOUT_MS, state: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (state()) return
            Thread.sleep(200)
        }
        fail("network state never settled: waited ${timeoutMs}ms for $what")
    }

    private fun assumeEmulator(what: String) {
        Shell.assumeOnEmulator(
            "NetworkControl runs only on emulators (ro.kernel.qemu != 1 here). $what " +
                "on a real phone cuts its owner's calls and messages — and over wifi-adb " +
                "it severs the test harness itself. Run this suite on a stock QA AVD.",
        )
    }
}
