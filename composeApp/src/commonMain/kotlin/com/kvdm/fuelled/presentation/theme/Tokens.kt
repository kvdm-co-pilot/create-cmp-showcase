package com.kvdm.fuelled.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FuelledTokens {
    val ElevationCard  = 2.dp
    val ElevationModal = 8.dp

    // motion OD4 (closed: 20 dp everywhere). Today, Profile and Supplements already padded
    // 20 dp by hand; the token now says what the three hero surfaces were already doing, and
    // every screen root reads it through ScreenColumn.
    val PaddingPage = 20.dp
    val PaddingCard = 16.dp
    val GapCard     = 12.dp

    val BottomNavHeight = 80.dp

    val RadiusCard  = 16.dp
    val RadiusPill  = 999.dp
    val RadiusModal = 24.dp
    val RadiusInput = 14.dp
}

/**
 * The motion layer of the design system (`docs/features/motion.md` D1). Lives in THIS file,
 * not a `Motion.kt`, because the governed `design-system` artifact hashes exactly `Theme.kt`
 * + `Tokens.kt` — a motion value outside them would be a design value nobody signed.
 *
 * Every animation in the app reads its spec through the [MotionScheme] in `Theme.kt`, which
 * turns these raw numbers into a `FiniteAnimationSpec` honouring reduced motion and the
 * instant scheme the preview harness and tests run under. A `tween(`/`spring(` whose spec is
 * not one of these, outside `presentation/theme` and `presentation/components`, fails
 * MOTION-01 (the same gate ARCH-05 is for colours).
 */
object FuelledMotion {

    /** Durations, in milliseconds. */
    object Duration {
        const val Instant     = 0
        const val Quick       = 120  // press feedback, colour/state swaps, outgoing fades
        const val Standard    = 240  // fades, rises, cross-fades, expand/collapse
        const val Emphasized  = 400  // screen pushes and pops, tab fade-through
        const val Expressive  = 700  // the ring's sweep, the bars' first fill, count-ups
        const val Celebration = 1100 // the goal bloom — once per logical day, nothing else
        const val ShimmerSweep = 1200 // the loading shimmer — the one loop in the app
    }

    /** Easings: Material's emphasized pair for arriving/leaving, one standard curve. */
    object Easings {
        val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val Enter: Easing    = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val Exit: Easing     = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        val Linear: Easing   = LinearEasing
    }

    /** A spring as plain data — damping ratio and stiffness in Compose's own units. */
    data class SpringSpec(val dampingRatio: Float, val stiffness: Float)

    object Springs {
        /** Layout, the tab indicator, press scale. Fast, no overshoot. */
        val Settle  = SpringSpec(dampingRatio = 1.0f, stiffness = 1500f)
        /** The ring, bars and animated numbers. Heavy, decisive, no bounce. */
        val Weighty = SpringSpec(dampingRatio = 1.0f, stiffness = 200f)
        /** The tick pop, tag pop-in, "goal hit". One overshoot, then home. */
        val Lively  = SpringSpec(dampingRatio = 0.6f, stiffness = 400f)
    }

    /** Per-item delay in an arrival stagger, and the index past which items share a delay. */
    const val StaggerStepMs = 40
    const val StaggerCap    = 6

    /** How far an arriving card rises. */
    val EnterRise: Dp = 16.dp
    /** How far a pushed screen slides in; the outgoing screen leads by [ScreenLead]. */
    val ScreenSlide: Dp = 24.dp
    val ScreenLead: Dp  = 8.dp

    const val PressScale = 0.97f
    const val TabScale   = 0.96f
    const val TickPop    = 1.25f

    /** The stagger delay for the [index]-th arriving item under the Full scheme (MOTION-06). */
    fun staggerDelayMs(index: Int): Int = index.coerceIn(0, StaggerCap) * StaggerStepMs
}
