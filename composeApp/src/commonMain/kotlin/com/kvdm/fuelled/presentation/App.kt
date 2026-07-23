package com.kvdm.fuelled.presentation

import androidx.compose.runtime.Composable
import com.kvdm.fuelled.presentation.navigation.AppNavHost
import com.kvdm.fuelled.presentation.theme.FuelledTheme

// Root composable. Wraps the whole app in the theme and hosts navigation.
@Composable
fun App() {
    FuelledTheme {
        AppNavHost()
    }
}
