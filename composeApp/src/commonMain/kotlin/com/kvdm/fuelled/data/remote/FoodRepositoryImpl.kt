package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.FoodDao
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.local.toEntity
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.BflCategory
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.Macros100g
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The Room-backed Foods catalog — the fully-wired exemplar's data source. Unlike a
 * dependency-light in-memory stub, this reads and writes the on-device [FoodDao] (Room),
 * seeding the catalog on first run so the app has content offline from install.
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions to
 * typed [DomainError] values and ALWAYS rethrows CancellationException. A real remote-backed
 * variant swaps the DAO for a Firestore/Ktor source behind this same interface — the Clean
 * Architecture seam is the [FoodRepository] interface in the domain layer, not this class.
 */
class FoodRepositoryImpl(
    private val dao: FoodDao,
    // CAT-03: recents are a fact about the LOG, not the catalog — the catalog only resolves
    // the ids back to foods. Reading the log dao here keeps that derivation in one place
    // instead of asking every caller to join two repositories.
    private val todayDao: TodayDao,
) : FoodRepository {

    override suspend fun getFoods(): AppResult<List<Food>> = suspendRunCatching {
        ensureSeeded()
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun searchFoods(query: String): AppResult<List<Food>> = suspendRunCatching {
        ensureSeeded()
        val trimmed = query.trim()
        val rows = if (trimmed.isEmpty()) dao.getAll() else dao.search(trimmed)
        rows.map { it.toDomain() }
    }

    override suspend fun getFood(id: String): AppResult<Food> = suspendRunCatching(
        // A missing catalog entry is a typed NotFound, not an Unexpected — the detail maps it
        // to its own copy. Everything else falls through to the default Unexpected classifier.
        mapError = { if (it is NoSuchEntryException) DomainError.NotFound else DomainError.Unexpected(it) },
    ) {
        ensureSeeded()
        dao.getById(id)?.toDomain() ?: throw NoSuchEntryException(id)
    }

    override suspend fun saveFood(food: Food): AppResult<Unit> = suspendRunCatching {
        ensureSeeded()
        dao.upsert(food.toEntity())
    }

    override suspend fun deleteFood(id: String): AppResult<Unit> = suspendRunCatching {
        dao.delete(id)
    }

    override suspend fun setFavourite(id: String, favourite: Boolean): AppResult<Unit> =
        suspendRunCatching {
            ensureSeeded()
            val row = dao.getById(id) ?: throw NoSuchEntryException(id)
            dao.upsert(row.copy(favourite = favourite))
        }

    /**
     * CAT-03: resolve the recently-logged food ids back to catalog foods, KEEPING the
     * recency order (the `IN` query returns rows in table order, not id order) and dropping
     * any id whose food has since been deleted — a recent that no longer exists is not an
     * error, it is simply no longer offered.
     */
    override suspend fun recentFoods(limit: Int): AppResult<List<Food>> = suspendRunCatching {
        ensureSeeded()
        val ids = todayDao.recentFoodIds(limit)
        ids.mapNotNull { id -> dao.getById(id)?.toDomain() }
    }

    /** Seed the catalog on first run so the app ships with content offline (idempotent). */
    private suspend fun ensureSeeded() {
        if (dao.count() == 0) dao.upsertAll(SEED_CATALOG.map { it.toEntity() })
    }


    private class NoSuchEntryException(id: String) : NoSuchElementException("no food with id '$id'")

    private companion object {
        /**
         * BFL-01/BFL-02: build a seeded food from its per-100 g USDA values.
         *
         * The DISPLAY macros (kcal/protein/carbs/fat) are derived here from the per-100 g truth at
         * the food's portion — they are never passed in beside it, because two numbers that must
         * agree and can be set independently eventually will not (usability-pass F5, the two goal
         * stores, was exactly this).
         */
        private fun seed(
            id: String,
            name: String,
            category: BflCategory,
            kcal100: Double,
            protein100: Double,
            carbs100: Double,
            fat100: Double,
            portionGrams: Int,
            portionLabel: String,
            veg: Boolean,
            fdcId: Int,
        ): Food {
            val per100g = Macros100g(kcal100, protein100, carbs100, fat100)
            val portion = per100g.at(portionGrams)
            return Food(
                id = id,
                name = name,
                brand = "USDA",
                serving = portionLabel,
                kcal = portion.kcal,
                proteinG = portion.proteinG,
                carbsG = portion.carbsG,
                fatG = portion.fatG,
                veg = veg,
                per100g = per100g,
                category = category,
                portionGrams = portionGrams,
                fdcId = fdcId,
            )
        }

        /**
         * The catalog, seeded once into Room on first run (BFL-01/BFL-03) — offline, no
         * account, no network. Lives in the data layer (the source owns its seed data); the
         * presentation layer keeps its own preview fixtures.
         *
         * **Every number here is USDA SR Legacy, and every row carries the FoodData Central
         * id it came from.** These rows were GENERATED from the published dataset by a script
         * (the full SR Legacy CSV release, indexed locally) — nobody retyped 59 × 4 values,
         * which is the only way they are all right. The generator refuses to emit a food whose
         * FDC record is missing a macro, so a silent zero cannot reach this list.
         *
         * One dataset throughout, deliberately: USDA's newer Foundation Foods CALCULATES
         * protein from nitrogen where SR Legacy ANALYSED it, and the two disagree by 28% on
         * oats. Mixing per food is how a catalog starts contradicting its own totals.
         *
         * The portion is USDA's own published weight where there is one (food_portion.csv);
         * the palm/fist wording is Body-for-LIFE's language wrapped around a real gram figure,
         * never instead of one.
         */
        val SEED_CATALOG = listOf(
        seed("chicken-breast", "Chicken breast", BflCategory.PROTEIN, 165.0, 31.02, 0.0, 3.57, 120, "1 palm (120 g)", false, 171477),
        seed("turkey-breast", "Turkey breast", BflCategory.PROTEIN, 147.0, 30.13, 0.0, 2.08, 120, "1 palm (120 g)", false, 171496),
        seed("lean-beef-round", "Top round steak", BflCategory.PROTEIN, 190.0, 36.12, 0.0, 4.0, 120, "1 palm (120 g)", false, 170236),
        seed("ground-beef-95", "Lean beef mince 95%", BflCategory.PROTEIN, 174.0, 27.31, 0.0, 6.37, 120, "1 palm (120 g)", false, 174029),
        seed("ground-beef-93", "Lean beef mince 93%", BflCategory.PROTEIN, 209.0, 28.88, 0.0, 9.51, 120, "1 palm (120 g)", false, 174755),
        seed("pork-tenderloin", "Pork tenderloin", BflCategory.PROTEIN, 143.0, 26.17, 0.0, 3.51, 120, "1 palm (120 g)", false, 168250),
        seed("salmon", "Salmon fillet", BflCategory.PROTEIN, 206.0, 22.1, 0.0, 12.35, 120, "1 palm (120 g)", false, 175168),
        seed("tuna-canned", "Tuna, canned in water", BflCategory.PROTEIN, 116.0, 25.51, 0.0, 0.82, 120, "1 tin drained (120 g)", false, 171986),
        seed("tilapia", "Tilapia", BflCategory.PROTEIN, 128.0, 26.15, 0.0, 2.65, 120, "1 palm (120 g)", false, 175177),
        seed("cod", "Cod", BflCategory.PROTEIN, 105.0, 22.83, 0.0, 0.86, 120, "1 palm (120 g)", false, 171956),
        seed("halibut", "Halibut", BflCategory.PROTEIN, 111.0, 22.54, 0.0, 1.61, 120, "1 palm (120 g)", false, 174201),
        seed("shrimp", "Prawns", BflCategory.PROTEIN, 119.0, 22.78, 1.52, 1.7, 120, "1 palm (120 g)", false, 171971),
        seed("egg-white", "Egg whites", BflCategory.PROTEIN, 52.0, 10.9, 0.73, 0.17, 99, "3 large whites (99 g)", false, 172183),
        seed("whole-egg", "Whole egg", BflCategory.PROTEIN, 143.0, 12.56, 0.72, 9.51, 100, "2 large eggs (100 g)", false, 171287),
        seed("cottage-cheese", "Cottage cheese 1%", BflCategory.PROTEIN, 72.0, 12.39, 2.72, 1.02, 113, "1/2 cup (113 g)", false, 173417),
        seed("greek-yogurt", "Greek yogurt, plain 0%", BflCategory.PROTEIN, 59.0, 10.19, 3.6, 0.39, 170, "1 pot (170 g)", false, 170894),
        seed("brown-rice", "Brown rice, cooked", BflCategory.CARB, 123.0, 2.74, 25.58, 0.97, 195, "1 fist (195 g)", false, 169704),
        seed("white-rice", "White rice, cooked", BflCategory.CARB, 130.0, 2.69, 28.17, 0.28, 158, "1 cup (158 g)", false, 168878),
        seed("baked-potato", "Baked potato", BflCategory.CARB, 95.0, 2.63, 21.44, 0.13, 173, "1 medium (173 g)", false, 170030),
        seed("sweet-potato", "Sweet potato", BflCategory.CARB, 90.0, 2.01, 20.71, 0.15, 180, "1 large (180 g)", false, 168483),
        seed("oats-dry", "Oats, dry", BflCategory.CARB, 389.0, 16.89, 66.27, 6.9, 40, "1/2 cup dry (40 g)", false, 169705),
        seed("oatmeal", "Oatmeal, cooked", BflCategory.CARB, 71.0, 2.54, 12.0, 1.52, 234, "1 cup (234 g)", false, 173905),
        seed("wholewheat-bread", "Wholewheat bread", BflCategory.CARB, 252.0, 12.45, 42.71, 3.5, 56, "2 slices (56 g)", false, 172688),
        seed("wholewheat-pasta", "Wholewheat pasta, cooked", BflCategory.CARB, 149.0, 5.99, 30.07, 1.71, 140, "1 fist (140 g)", false, 168910),
        seed("quinoa", "Quinoa, cooked", BflCategory.CARB, 120.0, 4.4, 21.3, 1.92, 185, "1 cup (185 g)", false, 168917),
        seed("barley", "Pearl barley, cooked", BflCategory.CARB, 123.0, 2.26, 28.22, 0.44, 157, "1 cup (157 g)", false, 170285),
        seed("black-beans", "Black beans", BflCategory.CARB, 132.0, 8.86, 23.71, 0.54, 172, "1 cup (172 g)", false, 173735),
        seed("lentils", "Lentils", BflCategory.CARB, 116.0, 9.02, 20.13, 0.38, 198, "1 cup (198 g)", false, 172421),
        seed("sweetcorn", "Sweetcorn", BflCategory.CARB, 96.0, 3.41, 20.98, 1.5, 164, "1 cup (164 g)", false, 169999),
        seed("rice-cakes", "Rice cakes", BflCategory.CARB, 387.0, 8.2, 81.5, 2.8, 18, "2 cakes (18 g)", false, 170250),
        seed("banana", "Banana", BflCategory.CARB, 89.0, 1.09, 22.84, 0.33, 118, "1 medium (118 g)", false, 173944),
        seed("apple", "Apple", BflCategory.CARB, 52.0, 0.26, 13.81, 0.17, 182, "1 medium (182 g)", false, 171688),
        seed("orange", "Orange", BflCategory.CARB, 47.0, 0.94, 11.75, 0.12, 131, "1 medium (131 g)", false, 169097),
        seed("strawberries", "Strawberries", BflCategory.CARB, 32.0, 0.67, 7.68, 0.3, 152, "1 cup (152 g)", false, 167762),
        seed("blueberries", "Blueberries", BflCategory.CARB, 57.0, 0.74, 14.49, 0.33, 148, "1 cup (148 g)", false, 171711),
        seed("cantaloupe", "Cantaloupe", BflCategory.CARB, 34.0, 0.84, 8.16, 0.19, 160, "1 cup (160 g)", false, 169092),
        seed("grapefruit", "Grapefruit", BflCategory.CARB, 42.0, 0.77, 10.66, 0.14, 123, "1/2 fruit (123 g)", false, 174673),
        seed("broccoli", "Broccoli", BflCategory.VEGETABLE, 35.0, 2.38, 7.18, 0.41, 156, "1 cup (156 g)", true, 169967),
        seed("asparagus", "Asparagus", BflCategory.VEGETABLE, 22.0, 2.4, 4.11, 0.22, 180, "1 cup (180 g)", true, 168390),
        seed("green-beans", "Green beans", BflCategory.VEGETABLE, 35.0, 1.89, 7.88, 0.28, 125, "1 cup (125 g)", true, 169141),
        seed("spinach", "Spinach", BflCategory.VEGETABLE, 23.0, 2.86, 3.63, 0.39, 85, "3 handfuls (85 g)", true, 168462),
        seed("kale", "Kale", BflCategory.VEGETABLE, 35.0, 2.92, 4.42, 1.49, 67, "1 cup (67 g)", true, 168421),
        seed("romaine", "Romaine lettuce", BflCategory.VEGETABLE, 17.0, 1.23, 3.29, 0.3, 94, "2 cups (94 g)", true, 169247),
        seed("tomato", "Tomato", BflCategory.VEGETABLE, 18.0, 0.88, 3.89, 0.2, 123, "1 medium (123 g)", true, 170457),
        seed("cucumber", "Cucumber", BflCategory.VEGETABLE, 15.0, 0.65, 3.63, 0.11, 104, "1 cup (104 g)", true, 168409),
        seed("bell-pepper", "Red pepper", BflCategory.VEGETABLE, 26.0, 0.99, 6.03, 0.3, 119, "1 medium (119 g)", true, 170108),
        seed("cauliflower", "Cauliflower", BflCategory.VEGETABLE, 23.0, 1.84, 4.11, 0.45, 124, "1 cup (124 g)", true, 170397),
        seed("carrots", "Carrots", BflCategory.VEGETABLE, 41.0, 0.93, 9.58, 0.24, 128, "1 cup (128 g)", true, 170393),
        seed("mushrooms", "Mushrooms", BflCategory.VEGETABLE, 22.0, 3.09, 3.26, 0.34, 70, "1 cup (70 g)", true, 169251),
        seed("zucchini", "Courgette", BflCategory.VEGETABLE, 17.0, 1.21, 3.11, 0.32, 124, "1 cup (124 g)", true, 169291),
        seed("onion", "Onion", BflCategory.VEGETABLE, 40.0, 1.1, 9.34, 0.1, 110, "1 medium (110 g)", true, 170000),
        seed("brussels-sprouts", "Brussels sprouts", BflCategory.VEGETABLE, 36.0, 2.55, 7.1, 0.5, 156, "1 cup (156 g)", true, 169971),
        seed("green-peas", "Garden peas", BflCategory.VEGETABLE, 84.0, 5.36, 15.63, 0.22, 160, "1 cup (160 g)", true, 170420),
        seed("olive-oil", "Olive oil", BflCategory.FAT, 884.0, 0.0, 0.0, 100.0, 14, "1 tbsp (14 g)", false, 171413),
        seed("avocado", "Avocado", BflCategory.FAT, 160.0, 2.0, 8.53, 14.66, 100, "1/2 fruit (100 g)", false, 171705),
        seed("almonds", "Almonds", BflCategory.FAT, 579.0, 21.15, 21.55, 49.93, 28, "1 small handful (28 g)", false, 170567),
        seed("walnuts", "Walnuts", BflCategory.FAT, 654.0, 15.23, 13.71, 65.21, 28, "1 small handful (28 g)", false, 170187),
        seed("peanut-butter", "Peanut butter", BflCategory.FAT, 598.0, 22.21, 22.31, 51.36, 32, "2 tbsp (32 g)", false, 172470),
        seed("flaxseed", "Flaxseed", BflCategory.FAT, 534.0, 18.29, 28.88, 42.16, 10, "1 tbsp (10 g)", false, 169414),
        )
    }
}
