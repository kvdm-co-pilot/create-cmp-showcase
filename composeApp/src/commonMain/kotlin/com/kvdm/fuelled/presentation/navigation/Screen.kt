package com.kvdm.fuelled.presentation.navigation

// Type-safe route registry. Add a Screen object + a Routes constant per destination.
sealed class Screen(val route: String) {
    data object Shell : Screen(Routes.SHELL)
    data object FoodDetail : Screen(Routes.FOOD_DETAIL)
    // cmp:anchor screen-objects
}

object Routes {
    const val SHELL       = "shell"
    const val FOOD_DETAIL = "food/{foodId}"
    // cmp:anchor route-consts
    fun foodDetail(foodId: String) = "food/$foodId"
}
