package com.kvdm.fuelled.presentation.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.MotionScheme
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.spring
import com.kvdm.fuelled.presentation.theme.staggerDelayMs
import com.kvdm.fuelled.presentation.theme.tween
import kotlinx.coroutines.delay

// ── The motion primitives (motion D4) ────────────────────────────────────────────────────
// Screens compose these; they never animate by hand — the same rule as "do not hand-roll a
// header". Every spec here is read through LocalMotion, so the Reduced and Instant schemes
// (MOTION-02) are honoured by construction. What they guarantee is CONTENT, not node count
// (MOTION-14): a layer or semantics modifier here DOES materialise as a wrapper node in the
// tree serialiser, and the goldens hold those wrappers — but no tag, role, text or
// description a test or a Maestro flow selects on ever changes because a thing moved.

/**
 * Press feedback for any card, row or nav item: scale to `PressScale` while pressed, back on
 * `Settle` (MOTION-09's press half). Pair it with the `clickable`/`onClick` that owns the
 * [interactionSource] — this modifier only listens.
 */
fun Modifier.pressable(interactionSource: MutableInteractionSource): Modifier = composed {
    val motion = LocalMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion.moves) FuelledMotion.PressScale else 1f,
        animationSpec = motion.spring(FuelledMotion.Springs.Settle),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * An arrival (MOTION-06): fade in and rise `EnterRise`, delayed `index × StaggerStep` (capped
 * at `StaggerCap`), once per entry into the composition — never on recomposition, never on
 * scroll. Under Reduced it is a Quick fade with no rise; under Instant it renders arrived.
 */
fun Modifier.enterRise(index: Int = 0): Modifier = composed {
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val progress = remember { Animatable(if (motion == MotionScheme.Instant) 1f else 0f) }
    LaunchedEffect(Unit) {
        val delayMs = motion.staggerDelayMs(index)
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, motion.tween(FuelledMotion.Duration.Standard, FuelledMotion.Easings.Enter))
    }
    val rise = with(density) { FuelledMotion.EnterRise.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = if (motion.moves) (1f - progress.value) * rise else 0f
    }
}

/**
 * The goal bloom (motion D8 / OD3, MOTION-10): one lime radial sweep across the surface on
 * `Celebration`, plus a `Confirm` haptic (OD1). Fires each time [trigger] changes to a new
 * non-null value — the caller decides "once per logical day" by handing it the date the goal
 * was reached on. Draws over the content; adds no node.
 */
fun Modifier.goalBloom(trigger: Any?): Modifier = composed {
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (!motion.moves) return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(1f, motion.tween(FuelledMotion.Duration.Celebration, FuelledMotion.Easings.Standard))
        sweep.snapTo(0f)
    }
    drawWithContent {
        drawContent()
        val t = sweep.value
        if (t > 0f && t < 1f) {
            val fade = if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f
            val x = size.width * (-0.6f + 2.2f * t)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(FuelledColors.Primary.copy(alpha = 0.35f * fade), FuelledColors.Primary.copy(alpha = 0f)),
                    center = Offset(x, size.height / 2f),
                    radius = size.width * 0.6f,
                ),
            )
        }
    }
}

// ── Shared elements (OD5, FOODS-09) ───────────────────────────────────────────────────────

/** The NavHost's shared-transition scope; `null` wherever there is no navigation (previews, tests). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The current destination's animated-visibility scope; `null` outside a NavHost destination. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The app gate's animated-visibility scope (intro → onboarding → app); `null` outside `App`. */
val LocalGateAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks a piece of content as travelling between two destinations under one [key] — a food
 * row's title becoming its detail's header. A no-op wherever no NavHost provides the scopes,
 * so screens stay previewable and testable in isolation; the bounds move on `Settle`, which
 * snaps under Reduced and Instant.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedTitle(key: String): Modifier = composed {
    sharedBoundsIn(key, LocalNavAnimatedVisibilityScope.current)
}

/**
 * The intro's ring becoming Today's hero ring (MOTION-13): the same bounds hand-off as
 * [sharedTitle], but across the APP GATE's transition (intro → app) rather than a nav push —
 * both participants read the gate's visibility scope, so the match is the canonical two-state
 * case. A no-op outside `App`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedHero(key: String): Modifier = composed {
    sharedBoundsIn(key, LocalGateAnimatedVisibilityScope.current)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
private fun Modifier.sharedBoundsIn(key: String, visibility: AnimatedVisibilityScope?): Modifier {
    val shared = LocalSharedTransitionScope.current
    val motion = LocalMotion.current
    return if (shared == null || visibility == null) {
        this
    } else {
        with(shared) {
            this@sharedBoundsIn
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key),
                    animatedVisibilityScope = visibility,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    boundsTransform = { _, _ -> motion.spring(FuelledMotion.Springs.Settle) },
                )
                .skipToLookaheadSize()
        }
    }
}
