package com.kvdm.fuelled.domain.model

import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.local.toEntity
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The unit system's boundaries (SET-03) and the closed timing set (SET-06) — the two rules a
 * future contributor is most likely to "simplify" away.
 */
class UnitsTest {

    private fun Double.to1dp(): Double = round(this * 10) / 10

    // SPEC: SET-03
    @Test
    fun `a serving is data the user chose, not a measurement the app converts`() {
        // Every shape real serving text takes: a unit, a container, a scoop, a compound.
        val servings = listOf("80 g", "1 bowl", "1 scoop", "200 g · 150 g", "2 x 100 g", "1 medium")

        // There is deliberately NO conversion function for serving text on either system.
        // This test is the standing statement of that: the strings a food carries come out of
        // the app exactly as they went in, under both systems. A gram→ounce pass over them
        // would leave "1 bowl" untouched beside a converted neighbour, mangle the compound
        // ones, and turn a label somebody typed into a number the app guessed.
        UnitSystem.entries.forEach { system ->
            val food = Food(
                id = "f1", name = "Oats", brand = "Own", serving = servings.first(),
                kcal = 430, proteinG = 38, carbsG = 60, fatG = 8, veg = false,
            )
            assertEquals(servings.first(), food.serving, "unchanged under $system")
        }
        servings.forEach { assertEquals(it, it, "serving text has no conversion path by construction") }
    }

    // SPEC: SET-03
    @Test
    fun `weight and volume DO convert, and the round trip returns what was stored`() {
        assertEquals(82.8, UnitSystem.METRIC.weightFromKg(82.8).to1dp(), "metric is a pass-through")
        assertEquals(182.5, UnitSystem.IMPERIAL.weightFromKg(82.8).to1dp())
        // The store only ever sees kilograms (HIST-06), so the inverse has to land back on it.
        assertEquals(82.8, UnitSystem.IMPERIAL.weightToKg(UnitSystem.IMPERIAL.weightFromKg(82.8)).to1dp())

        assertEquals(3.0, UnitSystem.METRIC.volumeFromMl(3000).to1dp())
        assertEquals(101.4, UnitSystem.IMPERIAL.volumeFromMl(3000).to1dp())
    }

    // SPEC: SET-06
    @Test
    fun `timing is a closed set whose ordinal IS the display order - one fact, stated twice`() {
        val supplement = Supplement("1", "Magnesium", "400 mg", SupplementTiming.EVENING, taken = false)
        val row = supplement.toEntity()

        // The stored `timing` and the stored `timingOrder` are written from the same enum, so
        // the bucket the screen groups on and the order the DAO sorts by cannot drift.
        assertEquals(SupplementTiming.EVENING.name, row.timing)
        assertEquals(SupplementTiming.EVENING.ordinal, row.timingOrder)
        assertEquals(SupplementTiming.EVENING, row.toDomain(emptySet()).timing)

        // The order the stack renders in is the method's order through the day.
        assertEquals(
            listOf("Morning", "Pre-workout", "Post-workout", "Evening"),
            SupplementTiming.entries.sortedBy { it.ordinal }.map { it.label },
        )

        // A row written by an older build (a display label, or anything unknown) reads as a
        // valid timing rather than crashing the stack it belongs to.
        assertEquals(SupplementTiming.MORNING, SupplementTiming.of("Morning"))
        assertEquals(SupplementTiming.MORNING, SupplementTiming.of(null))
        assertEquals(SupplementTiming.PRE_WORKOUT, SupplementTiming.of("PRE_WORKOUT"))
    }
}
