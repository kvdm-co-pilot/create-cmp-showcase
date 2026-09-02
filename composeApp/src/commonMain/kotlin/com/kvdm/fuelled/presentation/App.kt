package com.kvdm.fuelled.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.presentation.motion.shouldReplayIntro
import kotlin.time.Instant
import org.koin.compose.koinInject
import com.kvdm.fuelled.presentation.components.LocalGateAnimatedVisibilityScope
import com.kvdm.fuelled.presentation.components.LocalSharedTransitionScope
import com.kvdm.fuelled.presentation.motion.IntroScreen
import com.kvdm.fuelled.presentation.navigation.AppNavHost
import com.kvdm.fuelled.presentation.onboarding.OnboardingRoute
import com.kvdm.fuelled.presentation.onboarding.OnboardingViewModel
import com.kvdm.fuelled.presentation.onboarding.StartGate
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.FuelledTheme
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.MotionScheme
import com.kvdm.fuelled.presentation.theme.tween
import org.koin.compose.viewmodel.koinViewModel

/** What the root shows: the ignition, the first-run interview, or the app. */
private enum class Stage { INTRO, ONBOARDING, APP }

// Root composable. Wraps the whole app in the theme and hosts navigation.
//
// START-01: the first-run interview is a GATE, not a nav destination. It sits above the nav
// graph deliberately — onboarding is not somewhere you can navigate back to, and putting it
// on the back stack is how apps end up letting you swipe out of a setup you haven't done.
// The gate is observed, so finishing the interview swaps the shell in place.
//
// MOTION-13 (motion D18): the ignition plays once per process start while the gate resolves.
// It replaced the blank UNKNOWN frame: the app used to render NOTHING until the start state
// was read, which beat flashing someone else's dashboard — and now beats both, because the
// instrument powering up is the loading screen. When the intro finishes AND the gate has
// resolved, the stage cross-fades (motion D7) and the intro's ring hands off into Today's
// hero ring through the SharedTransitionLayout hosted here — it has to span the gate, which
// is why it is not inside AppNavHost.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App(viewModel: OnboardingViewModel = koinViewModel()) {
    FuelledTheme {
        val gate by viewModel.gate.collectAsStateWithLifecycle()
        val motion = LocalMotion.current
        // Saved across configuration changes: a rotation must not replay the ignition. Under
        // Instant (tests, previews) the intro is over before it starts.
        var introDone by rememberSaveable { mutableStateOf(motion == MotionScheme.Instant) }

        // MOTION-13: the ignition plays on a cold start AND on a real return to the
        // foreground. It used to be gated on composition state alone, which meant it never
        // ran for an already-onboarded user: a warm resume does not recompose this root, so
        // `introDone` stayed true from the previous run and the app went straight to Today
        // (observed on-device, 2026-09-02 — exactly the report that found this).
        //
        // The away interval is measured with the INJECTED clock (ARCH-13), and only a stay
        // longer than IntroReplayAfter counts: glancing at a notification and coming back is
        // not an app open. Instant never replays — tests and previews get one end state.
        val time: TimeSignal = koinInject()
        val lifecycleOwner = LocalLifecycleOwner.current
        var awayAt by remember { mutableStateOf<Instant?>(null) }
        DisposableEffect(lifecycleOwner, motion) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> awayAt = time.now()
                    Lifecycle.Event.ON_START -> {
                        val since = awayAt
                        if (shouldReplayIntro(awayFor = since?.let { time.now() - it }, motion = motion)) {
                            introDone = false
                        }
                        awayAt = null
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val stage = when {
            !introDone || gate == StartGate.UNKNOWN -> Stage.INTRO
            gate == StartGate.ONBOARDING -> Stage.ONBOARDING
            else -> Stage.APP
        }
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        fadeIn(motion.tween(FuelledMotion.Duration.Standard, FuelledMotion.Easings.Enter)) togetherWith
                            fadeOut(motion.tween(FuelledMotion.Duration.Standard, FuelledMotion.Easings.Exit))
                    },
                    label = "gate",
                ) { current ->
                    CompositionLocalProvider(LocalGateAnimatedVisibilityScope provides this) {
                        when (current) {
                            Stage.INTRO -> IntroScreen(onDone = { introDone = true })
                            Stage.ONBOARDING -> OnboardingRoute(viewModel)
                            Stage.APP -> AppNavHost()
                        }
                    }
                }
            }
        }
    }
}
