package com.kvdm.fuelled.domain.model

/**
 * A catalog entry with nutritional values per serving (see specs/intent.md — Glossary).
 * The canonical domain model for the Foods feature: pure Kotlin, no framework types, the
 * shape the presentation renders and the data layer maps its `FoodEntity` rows into.
 */
data class Food(
    val id: String,
    val name: String,
    val brand: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /**
     * BFL-01/BFL-02: the macros per 100 g — the STORED truth, from which every quantity is
     * derived. [kcal]/[proteinG]/[carbsG]/[fatG] above are this food's DEFAULT PORTION, kept
     * as the display shorthand every existing surface already reads; they are computed from
     * these, never entered beside them.
     *
     * Per-100 g rather than per-portion because a portion is a convenience and 100 g is a
     * fact: "half a chicken breast" has no answer in a per-portion catalog that is not a
     * second row.
     */
    val per100g: Macros100g = Macros100g(),
    /**
     * BFL-03: this food's Body-for-LIFE role. The method's own vocabulary, and what makes the
     * builder possible at all — "a meal" is a protein plus a carb, which the app can only
     * offer if it knows which is which.
     */
    val category: BflCategory = BflCategory.CARB,
    /** BFL-02: the default portion in grams — USDA's own portion weight where one is published. */
    val portionGrams: Int = 100,
    /**
     * BFL-01: the USDA FoodData Central id this food's numbers came from, or null for a food
     * the user created. Provenance on the row itself: any number in this catalog can be taken
     * back to the published record it was generated from.
     */
    val fdcId: Int? = null,
    /**
     * Whether this catalog entry counts as a vegetable (PLAN-22). A property of the FOOD, not
     * of the log entry that references it — "is broccoli a vegetable" is a fact about broccoli.
     * Flagging it here makes the day's veg count derivable from what was actually eaten,
     * without anyone tagging a meal. Defaults false: most of a catalog is not veg, and the
     * flag is a claim to be made deliberately.
     */
    val veg: Boolean = false,
    /**
     * CAT-02: pinned by the user. A favourite is a statement about how OFTEN you eat this,
     * which is why it sorts the tray ahead of everything else — the fastest log is the one
     * that needs no search.
     */
    val favourite: Boolean = false,
    /**
     * CAT-01: created by the user, not seeded. Only a custom food is editable: the seeded
     * catalog's numbers are reference data, and silently editing "Chicken breast" under
     * everyone's past entries would rewrite what those days claimed (log rows snapshot their
     * own macros, so history is safe — but the catalog should still say what it means).
     */
    val custom: Boolean = false,
) {
    /** BFL-02: this food's macros at [grams] — the ONE scaling rule, used by every caller. */
    fun at(grams: Int): Macros = per100g.at(grams)
}

/** BFL-03: the method's four roles. Declaration order IS the order they are offered in. */
enum class BflCategory(val label: String, val plural: String) {
    PROTEIN("Protein", "Proteins"),
    CARB("Carb", "Carbs"),
    VEGETABLE("Vegetable", "Vegetables"),
    FAT("Fat", "Fats"),
}

/**
 * Macros per 100 g, as USDA publishes them — decimals, because 31.02 g of protein is what
 * chicken breast has and rounding at rest would compound across a day of six meals.
 */
data class Macros100g(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
) {
    /**
     * BFL-02: scale to a quantity. Rounding happens HERE, once, on the way out — so a portion,
     * a half portion and a logged entry are the same arithmetic and cannot disagree.
     */
    fun at(grams: Int): Macros {
        val f = grams / 100.0
        return Macros(
            kcal = (kcal * f).roundToIntSafe(),
            proteinG = (proteinG * f).roundToIntSafe(),
            carbsG = (carbsG * f).roundToIntSafe(),
            fatG = (fatG * f).roundToIntSafe(),
        )
    }
}

/** A quantity's macros, rounded for display and for the ledger. */
data class Macros(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

/** Rounds half-up and never goes negative — a macro below zero is a bug, not a value. */
private fun Double.roundToIntSafe(): Int =
    if (this <= 0.0) 0 else (this + 0.5).toInt()
