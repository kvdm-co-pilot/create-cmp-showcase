package com.kvdm.fuelled.testing

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Alarm assertions against the OS alarm table (`dumpsys alarm`) — what AlarmManager
 * actually HOLDS for this app, not what the app believes it scheduled.
 *
 * Why this exists — the PendingIntent identity trap: AlarmManager keys alarms by
 * PendingIntent identity (creator + request code + intent filterEquals — extras excluded).
 * Two logically distinct alarms built with the same request code and an intent differing
 * only in extras silently collapse into ONE slot: the second `set()` replaces the first,
 * no error, no log line, and every JVM tier stays green. The only place the collapse is
 * visible is the OS alarm table, which is what this file reads.
 *
 * Constraints the code can't show:
 *  - `dumpsys alarm` is a human-oriented dump whose exact format shifts across API levels
 *    and OEMs. The parser below is deliberately tolerant — it anchors on the stable parts
 *    (`Alarm{... this-package}` entry lines, the indented `tag=` detail) and carries the
 *    raw block so a predicate can always fall back to substring matching. If a future
 *    Android release reshuffles the dump, fix the parser here, once — tests state intent
 *    through predicates, not format.
 *  - Reading the table proves REGISTRATION, not delivery. Delivery is its own proof:
 *    [TimeWarp] warps the clock past the trigger, and [DozeControl] holds the device in
 *    forced idle while it happens. App-standby buckets and OEM battery managers remain
 *    outside the seam's reach (see docs/TESTING.md).
 *  - The shell runs with the instrumentation's uiAutomation (shell uid, via [Shell.exec]),
 *    so no permission or root is needed.
 */
object AlarmAsserts {

    /**
     * One row of the OS alarm table attributed to [packageName].
     *
     * @property whenMs the alarm's trigger wall/elapsed time in ms as printed by the dump
     *   (`when` field), or null when the entry line carries no parseable `when`.
     * @property tag the `tag=` detail line if present (AlarmManager's listener/operation
     *   tag, e.g. `*walarm*:pkg/receiver`), else "".
     * @property raw the full dump block for this entry — the fallback surface for
     *   predicates when the parsed fields aren't enough.
     */
    data class RegisteredAlarm(val whenMs: Long?, val tag: String, val raw: String)

    /** All alarms the OS currently holds for [packageName] (default: the app under test). */
    fun registeredAlarms(
        packageName: String =
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
    ): List<RegisteredAlarm> = parseDumpsysAlarm(Shell.exec("dumpsys alarm"), packageName)

    /**
     * Asserts at least one alarm matching [predicate] is registered. [what] names the
     * expectation in the failure message ("the daily-reset alarm").
     */
    fun assertAlarmRegistered(
        what: String = "a matching alarm",
        packageName: String =
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
        predicate: (RegisteredAlarm) -> Boolean,
    ) {
        val alarms = registeredAlarms(packageName)
        assertTrue(
            "$what is not in the OS alarm table for $packageName. " +
                "Registered: ${describe(alarms)}",
            alarms.any(predicate),
        )
    }

    /**
     * Asserts EXACTLY [expected] matching alarms are registered — the generic form of the
     * PendingIntent-identity-collision bug. If two logical alarms share one PendingIntent
     * slot, the table holds one entry and this fails with the survivors listed; schedule
     * your N alarms, then assert N distinct rows.
     */
    fun assertDistinctAlarms(
        expected: Int,
        what: String = "matching alarms",
        packageName: String =
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
        predicate: (RegisteredAlarm) -> Boolean,
    ) {
        val matching = registeredAlarms(packageName).filter(predicate)
        assertEquals(
            "expected $expected distinct $what in the OS alarm table but found " +
                "${matching.size} — fewer than scheduled usually means two logical alarms " +
                "share one PendingIntent identity (same request code + filter-equal intent) " +
                "and the later set() replaced the earlier. Found: ${describe(matching)}",
            expected,
            matching.size,
        )
    }

    // ── dump plumbing ───────────────────────────────────────────────────────

    /**
     * Tolerant `dumpsys alarm` parse: an entry starts at a line containing
     * `Alarm{... <packageName>` (batched and unbatched dumps both print this shape) and
     * runs until the next entry line or an outdent. `when <millis>` is read off the entry
     * line when present; the first indented `tag=` detail line is captured.
     */
    internal fun parseDumpsysAlarm(dump: String, packageName: String): List<RegisteredAlarm> {
        val entryRe = Regex("""Alarm\{[^}]*\b${Regex.escape(packageName)}\b[^}]*\}""")
        // Entry-line trigger time. Two shapes across API levels: legacy prints
        // `when <ms>`, modern (S+) prints `origWhen <ms> whenElapsed <ms>` — origWhen is
        // the requested wall/elapsed time, which is what a test scheduled and can equate.
        // Digits only, deliberately: the DETAIL lines print `when=+56m6s0ms` (a formatted
        // duration), and a looser pattern would swallow that "+56" as milliseconds.
        val whenRe = Regex("""\b(?:origWhen|when)\b[ =]+(\d+)""")
        val tagRe = Regex("""\btag=(\S+)""")

        val lines = dump.lines()
        val alarms = mutableListOf<RegisteredAlarm>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (entryRe.containsMatchIn(line)) {
                val entryIndent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val block = StringBuilder(line)
                var tag = tagRe.find(line)?.groupValues?.get(1) ?: ""
                var j = i + 1
                while (j < lines.size) {
                    val next = lines[j]
                    val nextIndent = next.indexOfFirst { !it.isWhitespace() }
                    // Stop at the next entry, a blank line, or an outdent back to/above
                    // this entry's level — the detail lines of an entry are indented past it.
                    if (next.isBlank() || entryRe.containsMatchIn(next) ||
                        (nextIndent in 0..entryIndent)
                    ) break
                    block.append('\n').append(next)
                    if (tag.isEmpty()) tag = tagRe.find(next)?.groupValues?.get(1) ?: ""
                    j++
                }
                alarms.add(
                    RegisteredAlarm(
                        whenMs = whenRe.find(line)?.groupValues?.get(1)?.toLongOrNull(),
                        tag = tag,
                        raw = block.toString(),
                    ),
                )
                i = j
            } else {
                i++
            }
        }
        return alarms
    }

    private fun describe(alarms: List<RegisteredAlarm>): String =
        if (alarms.isEmpty()) "(none)"
        else alarms.joinToString { "[when=${it.whenMs} tag=${it.tag}]" }
}
