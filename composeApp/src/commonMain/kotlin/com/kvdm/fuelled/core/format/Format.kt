package com.kvdm.fuelled.core.format

/**
 * KMP-safe formatting helpers.
 *
 * `String.format` / `"%02d".format(...)` are JVM-only — they compile in `androidMain` but do
 * not exist in `commonMain`, and reaching for them is the single most common first-week
 * porting mistake in a shared module. These cover the cases that actually come up; add here
 * rather than sprinkling `padStart` call sites.
 */

/** Pad an Int to two digits: 7 -> "07". The `%02d` you were about to write. */
fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** "HH:mm" from minutes-since-midnight: 555 -> "09:15". */
fun clockLabel(minutesOfDay: Int): String {
    val m = ((minutesOfDay % (24 * 60)) + 24 * 60) % (24 * 60) // wrap + never negative
    return "${pad2(m / 60)}:${pad2(m % 60)}"
}

/**
 * Fixed decimal places without java.text: 12.5 -> "12.5" (1 dp). Rounds half away from zero
 * via floor(abs + 0.5) — deterministic on every backend (kotlin.math.round's tie behavior and
 * `%.Nf` locale handling both vary). For layout-stable numeric UI text, not accounting math.
 */
fun fixed(value: Double, decimals: Int = 1): String {
    require(decimals >= 0) { "decimals must be >= 0" }
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = kotlin.math.floor(kotlin.math.abs(value) * factor + 0.5).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return "$sign$scaled"
    val whole = scaled / factor
    val frac = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$whole.$frac"
}
