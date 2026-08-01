package com.kvdm.fuelled.domain.model

/**
 * The unit system (specs/settings.spec.md SET-02/SET-03).
 *
 * ONE choice, applied to every measurement the app computes — not a picker per value
 * (settings decision D2). What it governs is deliberately narrow:
 *
 * **It converts what the app COMPUTES.** Weight and water are numbers this app derives, so
 * they convert.
 *
 * **It never touches what the user TYPED.** A food's serving is free text — `"80 g"`,
 * `"1 bowl"`, `"1 scoop"`, `"200 g · 150 g"` — and is data, not a measurement (SET-03,
 * decision D3). A gram→ounce pass over those strings would leave `"1 bowl"` untouched beside
 * a converted neighbour, mangle the compound ones, and turn a label somebody chose into a
 * number the app guessed. This is the single most likely thing here to be "simplified" later
 * by someone who sees `g` in a string and reaches for a regex. Do not.
 *
 * **Energy is not here at all** (decision D4). The catalog, the target, the goal editor, the
 * ring and every day card are kcal; converting display alone would put two units on one
 * screen, and converting the stored goal is its own slice.
 */
enum class UnitSystem { METRIC, IMPERIAL }

private const val POUNDS_PER_KG: Double = 2.20462262
private const val FLUID_OUNCES_PER_LITRE: Double = 33.8140226

/** Kilograms in this system's unit — kg unchanged, or pounds. */
fun UnitSystem.weightFromKg(kg: Double): Double =
    if (this == UnitSystem.METRIC) kg else kg * POUNDS_PER_KG

/** The inverse: this system's unit back to the kilograms that are stored (see [WeightEntry]). */
fun UnitSystem.weightToKg(value: Double): Double =
    if (this == UnitSystem.METRIC) value else value / POUNDS_PER_KG

/** Millilitres in this system's unit — litres, or US fluid ounces. */
fun UnitSystem.volumeFromMl(ml: Int): Double =
    if (this == UnitSystem.METRIC) ml / 1000.0 else ml / 1000.0 * FLUID_OUNCES_PER_LITRE
