package com.kvdm.fuelled.presentation.foods

import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.usecase.DeleteFoodUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodUseCase
import com.kvdm.fuelled.domain.usecase.SaveFoodUseCase
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The custom-food editor (CAT-01): guards before writes, and a custom flag that sticks. */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeFoodRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = FoodEditorViewModel(
        getFood = GetFoodUseCase(repository),
        saveFood = SaveFoodUseCase(repository),
        deleteFood = DeleteFoodUseCase(repository),
    )

    // SPEC: CAT-01
    @Test
    fun `saving a new food writes it as custom, with the macros as typed`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.save("custom-1", "Mum's lasagne", "", "1 portion", 620, 38, 55, 24, veg = false)
        advanceUntilIdle()

        val saved = repository.savedFoods.single()
        assertEquals("Mum's lasagne", saved.name)
        assertEquals(620, saved.kcal)
        assertEquals(38, saved.proteinG)
        assertTrue(saved.custom, "anything made here is yours, by definition")
        assertEquals("Custom", saved.brand, "a blank brand gets an honest default, not an empty line")
        assertEquals(FoodEditState.Saved, vm.state.value)
    }

    // SPEC: CAT-01
    @Test
    fun `a nameless or zero-calorie food reaches no write`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.save("custom-1", "   ", "", "100 g", 200, 10, 10, 5, veg = false)
        advanceUntilIdle()
        vm.save("custom-1", "Rice", "", "100 g", 0, 10, 10, 5, veg = false)
        advanceUntilIdle()
        vm.save("custom-1", "Rice", "", "  ", 200, 10, 10, 5, veg = false)
        advanceUntilIdle()

        assertEquals(0, repository.savedFoods.size, "an unfindable or macro-less food is refused")
    }

    // SPEC: CAT-01
    @Test
    fun `editing keeps the food's identity and its favourite flag`() = runTest(dispatcher) {
        repository.foods = listOf(
            Food("custom-9", "Protein bar", "Mine", "60 g", 220, 20, 22, 7, favourite = true, custom = true),
        )
        val vm = viewModel()
        vm.load("custom-9")
        advanceUntilIdle()

        vm.save("custom-9", "Protein bar", "Mine", "60 g", 240, 21, 22, 7, veg = false)
        advanceUntilIdle()

        val saved = repository.savedFoods.single()
        assertEquals("custom-9", saved.id, "an edit replaces the same row — never a twin")
        assertEquals(240, saved.kcal)
        assertTrue(saved.favourite, "editing the macros must not silently unpin it")
    }

    // SPEC: CAT-01
    @Test
    fun `deleting removes the food from the catalog`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.delete("custom-9")
        advanceUntilIdle()

        assertEquals(listOf("custom-9"), repository.deletedFoodIds)
    }
}
