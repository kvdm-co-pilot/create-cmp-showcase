package com.kvdm.fuelled.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.presentation.navigation.AppNavHost
import com.kvdm.fuelled.presentation.onboarding.OnboardingRoute
import com.kvdm.fuelled.presentation.onboarding.OnboardingViewModel
import com.kvdm.fuelled.presentation.onboarding.StartGate
import com.kvdm.fuelled.presentation.theme.FuelledTheme
import org.koin.compose.viewmodel.koinViewModel

// Root composable. Wraps the whole app in the theme and hosts navigation.
//
// START-01: the first-run interview is a GATE, not a nav destination. It sits above the nav
// graph deliberately — onboarding is not somewhere you can navigate back to, and putting it
// on the back stack is how apps end up letting you swipe out of a setup you haven't done.
// The gate is observed, so finishing the interview swaps the shell in place.
@Composable
fun App(viewModel: OnboardingViewModel = koinViewModel()) {
    FuelledTheme {
        val gate by viewModel.gate.collectAsStateWithLifecycle()
        when (gate) {
            // UNKNOWN is the first frame before the state has been read. Rendering NOTHING
            // beats rendering the app and yanking it away: a one-frame flash of someone
            // else's dashboard is exactly the "whose app is this?" feeling S1 exists to fix.
            StartGate.UNKNOWN -> Unit
            StartGate.ONBOARDING -> OnboardingRoute(viewModel)
            StartGate.APP -> AppNavHost()
        }
    }
}
