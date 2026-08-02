package com.kvdm.fuelled.domain.model

/**
 * A composed meal — one food per Body-for-LIFE role (BFL-05).
 *
 * The method's grammar, made into a type: *a portion of protein, a portion of carbohydrate,
 * and vegetables with two meals a day*. Holding it this way is what lets the app offer a
 * builder at all — with a flat list of foods, "add a meal" can only mean "add some foods".
 *
 * Every slot is optional because a real meal often is: a mid-morning snack is a protein and
 * a piece of fruit, and an app that refuses to compose it because there is no vegetable has
 * stopped being useful in order to be correct.
 */
data class ComposedMeal(
    val protein: Food? = null,
    val carb: Food? = null,
    val vegetable: Food? = null,
    val fat: Food? = null,
) {
    val foods: List<Food> get() = listOfNotNull(protein, carb, vegetable, fat)

    val isEmpty: Boolean get() = foods.isEmpty()

    /**
     * BFL-05: the running total.
     *
     * Sums each food's PORTION macros — the very numbers the cards above it display — rather
     * than recomputing from `per100g`. Those two agree for every seeded food (the portion is
     * derived from per-100 g at seed time) and they are the same arithmetic, but only one of
     * them can be the total: computing it separately means a food whose two representations
     * ever drift shows one number on its card and contributes another to the sum. That is
     * exactly the two-sources defect the goal stores were collapsed to fix (usability-pass
     * F5), and it showed up here as a builder rendering "0 kcal" under three chosen foods
     * (seen on the rendered surface, 2026-08-02).
     */
    val total: Macros
        get() = foods.fold(Macros(0, 0, 0, 0)) { acc, f ->
            Macros(acc.kcal + f.kcal, acc.proteinG + f.proteinG, acc.carbsG + f.carbsG, acc.fatG + f.fatG)
        }

    /**
     * BFL-08: whether this meal has the method's shape. Reported, never enforced — the app
     * states what the method asks for and lets a grown adult decide. A builder that refuses
     * to save a protein-only snack is a builder people stop using.
     */
    val hasProtein: Boolean get() = protein != null
    val hasCarb: Boolean get() = carb != null
    val hasVegetable: Boolean get() = vegetable != null

    fun with(food: Food): ComposedMeal = when (food.category) {
        BflCategory.PROTEIN -> copy(protein = food)
        BflCategory.CARB -> copy(carb = food)
        BflCategory.VEGETABLE -> copy(vegetable = food)
        BflCategory.FAT -> copy(fat = food)
    }

    /** Deselect whatever occupies [category] — picking is a toggle, so a mis-tap costs one tap. */
    fun without(category: BflCategory): ComposedMeal = when (category) {
        BflCategory.PROTEIN -> copy(protein = null)
        BflCategory.CARB -> copy(carb = null)
        BflCategory.VEGETABLE -> copy(vegetable = null)
        BflCategory.FAT -> copy(fat = null)
    }

    operator fun get(category: BflCategory): Food? = when (category) {
        BflCategory.PROTEIN -> protein
        BflCategory.CARB -> carb
        BflCategory.VEGETABLE -> vegetable
        BflCategory.FAT -> fat
    }
}

/**
 * A named starting point (BFL-07) — the combinations lifters actually eat, drawn from what
 * training sites and the method's own sample days recommend rather than invented here.
 *
 * A preset is nothing but a set of catalog ids: activating one fills the builder's selection
 * and stops. It cannot do anything the builder cannot, which is the point — there is no
 * second path to change or audit.
 */
data class MealPreset(
    val id: String,
    val name: String,
    val foodIds: List<String>,
)

/** The presets offered. Ids refer to seeded catalog foods (BFL-01). */
val MEAL_PRESETS: List<MealPreset> = listOf(
    MealPreset("classic", "Chicken, rice & broccoli", listOf("chicken-breast", "brown-rice", "broccoli")),
    MealPreset("breakfast", "Oats & egg whites", listOf("egg-white", "oats-dry")),
    MealPreset("salmon-dinner", "Salmon, sweet potato & asparagus", listOf("salmon", "sweet-potato", "asparagus")),
    MealPreset("lean-lunch", "Turkey, quinoa & greens", listOf("turkey-breast", "quinoa", "spinach")),
    MealPreset("snack", "Cottage cheese & berries", listOf("cottage-cheese", "blueberries")),
    MealPreset("steak-night", "Steak, potato & green beans", listOf("lean-beef-round", "baked-potato", "green-beans")),
    MealPreset("fish-light", "Cod, brown rice & broccoli", listOf("cod", "brown-rice", "broccoli")),
    MealPreset("post-workout", "Greek yogurt & banana", listOf("greek-yogurt", "banana")),
)
