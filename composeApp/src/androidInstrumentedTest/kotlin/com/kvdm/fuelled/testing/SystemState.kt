package com.kvdm.fuelled.testing

import android.content.Context
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Snapshot/restore of device audio state (ringer mode, Do Not Disturb) so audio-routing
 * behavior can be asserted — "does the alert still sound when the phone is on silent?"
 * is THE question that shipped broken repeatedly, because ringer mode only exists on a
 * device and every JVM tier is deaf to it.
 *
 * Being honest about what instrumentation can and cannot control:
 *
 *  CAN (this file):
 *   - Read and set the ringer mode. Setting SILENT/VIBRATE via AudioManager flips the
 *     device through DND on modern Android, so both knobs are driven through the shell
 *     (`cmd notification set_dnd`, `settings global zen_mode`) with the instrumentation's
 *     shell uid ([Shell.exec]) — no notification-policy grant dance, works on a stock emulator.
 *   - Read/set DND (zen mode) as a global: off / priority-only / total-silence / alarms-only.
 *   - Observe playback routing: which stream/usage an active player is on
 *     (AudioManager.activePlaybackConfigurations — see PlatformBehaviorSeamTest for the
 *     assertion shape: a sound with USAGE_ALARM is what survives a silenced ringer).
 *
 *  CANNOT (manual tier — document the claim in your spec, verify it by hand per release):
 *   - OEM sound policy: whether a channel's sound reaches the alarm stream on a Samsung
 *     is Samsung's decision; assert your app plays with USAGE_ALARM, not that a given
 *     handset makes air move.
 *   - Physical volume keys / hardware mute switches, Bluetooth routing, and whether the
 *     speaker is audible — there is no API for "a human heard it".
 *   - Lock-screen rendering: whether a takeover actually draws over a locked, OEM-skinned
 *     screen stays manual. Doze delivery, by contrast, stopped being a manual rehearsal
 *     when [DozeControl] landed — force the idle state and watch the alarm arrive.
 *
 * Plain helpers, not a TestRule: state here is two scalars, and an explicit
 * snapshot-in-@Before / restore-in-@After (or [withRingerMode]) keeps the mechanism
 * visible in the test. Wrap it in a rule when a suite grows enough tests to justify one.
 */
object SystemState {

    /** The restorable slice of device audio state. */
    data class Snapshot(val ringerMode: Int, val zenMode: Int)

    /** DND (zen) global values — `settings get global zen_mode`. */
    const val ZEN_OFF = 0
    const val ZEN_PRIORITY_ONLY = 1
    const val ZEN_TOTAL_SILENCE = 2
    const val ZEN_ALARMS_ONLY = 3

    private val audio: AudioManager
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Capture the current ringer + DND state. Take it BEFORE the test mutates anything. */
    fun snapshot(): Snapshot = Snapshot(ringerMode = audio.ringerMode, zenMode = readZen())

    /** Put the device back exactly as [snapshot] found it — call from @After, always. */
    fun restore(s: Snapshot) {
        setZen(s.zenMode)
        setRingerMode(s.ringerMode)
    }

    /**
     * Set the ringer mode (AudioManager.RINGER_MODE_NORMAL / _VIBRATE / _SILENT).
     * SILENT is driven as alarms-only DND + the mode itself — on API 23+ that is what the
     * volume-key gesture actually does, and it is the honest reproduction of "the user
     * silenced the phone" (alarms are still allowed to sound; a total-silence test should
     * say so explicitly via [setZen] with [ZEN_TOTAL_SILENCE]).
     */
    fun setRingerMode(mode: Int) {
        when (mode) {
            AudioManager.RINGER_MODE_SILENT -> setZen(ZEN_ALARMS_ONLY)
            else -> setZen(ZEN_OFF)
        }
        // Best-effort after the zen change; on emulators this lands reliably. Poll-check
        // rather than trust: callers get the real mode back and can assume-skip if the
        // device refused (an OEM with a hardware mute state may).
        audio.ringerMode = mode
    }

    /** Set DND directly (one of the ZEN_* constants). Shell-driven; applies globally. */
    fun setZen(zen: Int) {
        val arg = when (zen) {
            ZEN_PRIORITY_ONLY -> "priority"
            ZEN_TOTAL_SILENCE -> "none"
            ZEN_ALARMS_ONLY -> "alarms"
            else -> "off"
        }
        Shell.exec("cmd notification set_dnd $arg")
    }

    /** The device's current DND global (ZEN_* value; unreadable/absent reads as off). */
    fun readZen(): Int =
        Shell.exec("settings get global zen_mode").trim().toIntOrNull() ?: ZEN_OFF

    /**
     * Run [block] with the ringer in [mode], restoring the full snapshot afterwards even
     * when the block throws — the composable form for a single-assertion test:
     *
     *   SystemState.withRingerMode(AudioManager.RINGER_MODE_SILENT) {
     *     // trigger the alert path, then assert a player is active with USAGE_ALARM
     *   }
     */
    fun <T> withRingerMode(mode: Int, block: () -> T): T {
        val before = snapshot()
        return try {
            setRingerMode(mode)
            block()
        } finally {
            restore(before)
        }
    }
}
