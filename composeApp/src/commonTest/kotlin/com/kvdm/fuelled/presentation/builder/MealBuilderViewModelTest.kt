package com.kvdm.fuelled.presentation.builder

import com.kvdm.fuelled.domain.model.BflCategory
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.Macros100g
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.PlanMealUseCase
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import com.kvdm.fuelled.testing.keepCollecting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/**
 * The meal builder (BFL-05..08) — the method's grammar, made tappable: a protein and a carb
 * compose a meal, and one meal lands on as many days as you choose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealBuilderViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val foods = FakeFoodRepository()
    private val today = FakeTodayRepository()
    private val currentDay = LocalDate(2026, 7, 22)

    /**
     * Built the way the seed builds one (BFL-01): the portion macros are DERIVED from the
     * per-100 g values through the app's own scaling, never hand-computed beside them. A
     * fixture that rounds differently from the app tests arithmetic the app does not do.
     */
    private fun food(id: String, name: String, cat: BflCategory, kcal: Double, protein: Double): Food {
        val per100g = Macros100g(kcal, protein, 0.0, 0.0)
        val portion = per100g.at(120)
        return Food(
            id = id, name = name, brand = "USDA", serving = "1 portion",
            kcal = portion.kcal, proteinG = portion.proteinG, carbsG = portion.carbsG, fatG = portion.fatG,
            per100g = per100g, category = cat, portionGrams = 120,
        )
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        foods.foods = listOf(
            food("chicken-breast", "Chicken breast", BflCategory.PROTEIN, 165.0, 31.02),
            food("salmon", "Salmon", BflCategory.PROTEIN, 206.0, 22.1),
            food("brown-rice", "Brown rice", BflCategory.CARB, 123.0, 2.74),
            food("broccoli", "Broccoli", BflCategory.VEGETABLE, 35.0, 2.38),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MealBuilderViewModel(
        getFoods = GetFoodsUseCase(foods),
        planMeal = PlanMealUseCase(
            AddLogEntriesUseCase(today, clock = FixedClock(TEST_NOW), zone = TEST_ZONE),
        ),
        initialSlot = MealSlot.LUNCH,
        clock = FixedClock(TEST_NOW),
        zone = TEST_ZONE,
    )

    // SPEC: BFL-04
    // SPEC: BFL-05
    @Test
    fun `the catalog is offered by Body-for-LIFE role, and picking composes one meal per role`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.catalog)
            keepCollecting(vm.meal)
            advanceUntilIdle()

            assertEquals(2, vm.catalog.value[BflCategory.PROTEIN]?.size)
            assertEquals(1, vm.catalog.value[BflCategory.CARB]?.size)

            vm.onPick(foods.foods.first { it.id == "chicken-breast" })
            vm.onPick(foods.foods.first { it.id == "brown-rice" })
            assertEquals("chicken-breast", vm.meal.value.protein?.id)
            assertEquals("brown-rice", vm.meal.value.carb?.id)

            // One food per role: choosing a second protein REPLACES the first rather than
            // stacking, which is what makes the total mean "this meal".
            vm.onPick(foods.foods.first { it.id == "salmon" })
            assertEquals("salmon", vm.meal.value.protein?.id)
            assertEquals(1, vm.meal.value.foods.count { it.category == BflCategory.PROTEIN })

            // And picking the chosen one again clears it — a mis-tap costs one tap.
            vm.onPick(foods.foods.first { it.id == "salmon" })
            assertNull(vm.meal.value.protein)
        }

    // SPEC: BFL-05
    // SPEC: BFL-08
    @Test
    fun `the total is the sum at each food's portion, and the method's shape is reported not enforced`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.catalog)
            keepCollecting(vm.meal)
            advanceUntilIdle()

            vm.onPick(foods.foods.first { it.id == "chicken-breast" })
            // 165 kcal/100 g at a 120 g portion = 198.
            assertEquals(198, vm.meal.value.total.kcal)
            assertEquals(37, vm.meal.value.total.proteinG)

            vm.onPick(foods.foods.first { it.id == "brown-rice" })
            assertEquals(198 + 148, vm.meal.value.total.kcal, "the total is the sum of the portions")

            // BFL-08: a protein and a carb with no vegetable is REPORTED as such and stays
            // fully plannable — the app states what the method asks for and lets you decide.
            assertTrue(vm.meal.value.hasProtein && vm.meal.value.hasCarb)
            assertEquals(false, vm.meal.value.hasVegetable)
            vm.onPlan()
            advanceUntilIdle()
            assertIs<BuildState.Planned>(vm.state.value, "a meal without veg still plans")
        }

    // SPEC: BFL-06
    @Test
    fun `one meal is written into one slot across every chosen day, through the one write path`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.catalog)
            keepCollecting(vm.state)
            advanceUntilIdle()

            vm.onPick(foods.foods.first { it.id == "chicken-breast" })
            vm.onPick(foods.foods.first { it.id == "brown-rice" })
            vm.onSlot(MealSlot.DINNER)
            vm.onAllDays()
            vm.onPlan()
            advanceUntilIdle()

            val planned = assertIs<BuildState.Planned>(vm.state.value)
            assertEquals(7, planned.days, "the whole week in one act — the reason this exists")
            assertEquals(MealSlot.DINNER, planned.slot)

            // Two foods × seven days, all through AddLogEntriesUseCase (MEAL-05).
            assertEquals(14, today.addCalls.sumOf { it.entries.size }, "two foods on each of seven days")
            assertEquals(setOf(MealSlot.DINNER), today.addCalls.map { it.slot }.toSet())
            assertEquals(7, today.addCalls.map { it.date }.toSet().size)
            // Today's copy is LOGGED; the six ahead are PLANNED (MEAL-08).
            assertTrue(today.addCalls.any { it.date == currentDay })
        }

    // SPEC: BFL-07
    @Test
    fun `a preset fills the selection and changes nothing else`() = runTest(dispatcher) {
        val vm = viewModel()
        keepCollecting(vm.catalog)
        keepCollecting(vm.meal)
        advanceUntilIdle()

        vm.onPreset("classic")

        assertEquals("chicken-breast", vm.meal.value.protein?.id)
        assertEquals("brown-rice", vm.meal.value.carb?.id)
        assertEquals("broccoli", vm.meal.value.vegetable?.id)

        // Still fully editable: a preset sets the selection and stops. It cannot do anything
        // the builder cannot, which is why there is no second path to audit.
        vm.onPick(foods.foods.first { it.id == "salmon" })
        assertEquals("salmon", vm.meal.value.protein?.id)
    }

    // SPEC: BFL-05
    @Test
    fun `an empty meal reaches no write - the guard is in the ViewModel, not on a button`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()

            vm.onPlan()
            advanceUntilIdle()

            assertTrue(today.addCalls.isEmpty(), "no write is attempted from any caller")
            assertEquals(BuildState.Composing, vm.state.value)
        }
}
