package com.kvdm.cmpshowcase.presentation

import androidx.compose.runtime.Composable
import com.kvdm.cmpshowcase.presentation.navigation.AppNavHost
import com.kvdm.cmpshowcase.presentation.theme.CMPShowcaseTheme

// Root composable. Wraps the whole app in the theme and hosts navigation.
@Composable
fun App() {
    CMPShowcaseTheme {
        AppNavHost()
    }
}
