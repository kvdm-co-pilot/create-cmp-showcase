package com.kvdm.fuelled.presentation.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Fuelled design language — dark-first, high-contrast, one electric accent (lime).
// Every screen reads colour from here (or from MaterialTheme.colorScheme), never raw hex.
// Graphite depth ladder: Background < Surface < SurfaceVariant < SurfaceBright so cards,
// chips and sheets stack with real elevation on a near-black base.
object FuelledColors {
    // Electric-lime energy accent — reserved for progress, primary CTAs, selection.
    val Primary          = Color(0xFFB8FF3C)
    val OnPrimary        = Color(0xFF0C1500)
    val Accent           = Color(0xFFB8FF3C)
    val OnAccent         = Color(0xFF0C1500)

    // Muted graphite used for secondary surfaces / unselected chips.
    val Secondary        = Color(0xFF2A332E)
    val OnSecondary      = Color(0xFFE7EEE9)

    // Semantic — tuned bright enough to read on the dark base.
    val Error            = Color(0xFFFF6B6B)
    val Success          = Color(0xFF58E08A)
    val Warning          = Color(0xFFFFC24B)
    val Info             = Color(0xFF6BB4FF)

    // Macro hues — distinct, calm, used only in the data viz.
    val Protein          = Color(0xFFB8FF3C) // the star macro = the brand accent
    val Carbs            = Color(0xFF6BB4FF)
    val Fat              = Color(0xFFFFC24B)

    // Graphite depth ladder.
    val Background       = Color(0xFF0A0C0B)
    val Surface          = Color(0xFF121614)
    val SurfaceVariant   = Color(0xFF1B211E)
    val SurfaceBright    = Color(0xFF232A26)

    val OnSurface        = Color(0xFFF3F6F4)
    val OnSurfaceVariant = Color(0xFFA7B2AC)
    val Outline          = Color(0xFF333B37)
    val OutlineVariant   = Color(0xFF232A26)
    val Divider          = Color(0xFF232A26)
}

private val FuelledColorScheme = darkColorScheme(
    primary              = FuelledColors.Primary,
    onPrimary            = FuelledColors.OnPrimary,
    secondary            = FuelledColors.Secondary,
    onSecondary          = FuelledColors.OnSecondary,
    tertiary             = FuelledColors.Accent,
    onTertiary           = FuelledColors.OnAccent,
    error                = FuelledColors.Error,
    onError              = FuelledColors.OnPrimary,
    background           = FuelledColors.Background,
    onBackground         = FuelledColors.OnSurface,
    surface              = FuelledColors.Surface,
    surfaceVariant       = FuelledColors.SurfaceVariant,
    onSurface            = FuelledColors.OnSurface,
    onSurfaceVariant     = FuelledColors.OnSurfaceVariant,
    outline              = FuelledColors.Outline,
    outlineVariant       = FuelledColors.OutlineVariant,
    inverseSurface       = FuelledColors.OnSurface,
    inverseOnSurface     = FuelledColors.Background,
    // Kill M3's tonal overlay so dialogs/menus/sheets stay on the design-system surface,
    // then hand M3 the explicit graphite ladder for its container levels.
    surfaceTint              = Color.Transparent,
    surfaceContainerLowest   = FuelledColors.Background,
    surfaceContainerLow      = FuelledColors.Surface,
    surfaceContainer         = FuelledColors.Surface,
    surfaceContainerHigh     = FuelledColors.SurfaceVariant,
    surfaceContainerHighest  = FuelledColors.SurfaceBright,
)

/**
 * The motion scheme (motion D2 / MOTION-02): every animation in the app reads its spec through
 * this, so one value decides whether the app moves, cross-fades, or renders end states.
 *
 * - [Full] — the design language as specified in [FuelledMotion].
 * - [Reduced] — meaning kept, motion removed: cross-fades no longer than `Quick`, no rise,
 *   no stagger, no pops, no sweep; springs snap. Chosen from the platform's own setting.
 * - [Instant] — every animation at its end state on frame 0. The preview harness and every
 *   Compose UI test run under it, so a golden tree can never capture a frame mid-flight.
 */
enum class MotionScheme { Full, Reduced, Instant }

/**
 * The scheme in force. [FuelledTheme] provides the platform's; with no theme above — every
 * Compose UI test and golden-tree test composes under bare `MaterialTheme` — it is
 * [MotionScheme.Instant]: no theme, no motion, so a test can never flake on a clock.
 */
val LocalMotion = staticCompositionLocalOf { MotionScheme.Instant }

/** A timed spec through the scheme: `Instant` snaps, `Reduced` fades no longer than Quick. */
fun <T> MotionScheme.tween(
    durationMs: Int,
    easing: Easing = FuelledMotion.Easings.Standard,
): FiniteAnimationSpec<T> = when (this) {
    MotionScheme.Instant -> snap()
    MotionScheme.Reduced -> tween(
        durationMillis = minOf(durationMs, FuelledMotion.Duration.Quick),
        easing = FuelledMotion.Easings.Linear,
    )
    MotionScheme.Full -> tween(durationMillis = durationMs, easing = easing)
}

/** A spring through the scheme: only `Full` springs; the other two snap to the target. */
fun <T> MotionScheme.spring(spec: FuelledMotion.SpringSpec): FiniteAnimationSpec<T> = when (this) {
    MotionScheme.Full -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness)
    MotionScheme.Reduced, MotionScheme.Instant -> snap()
}

/** The arrival stagger for item [index] — `Full` only; the other schemes never stagger. */
fun MotionScheme.staggerDelayMs(index: Int): Int =
    if (this == MotionScheme.Full) FuelledMotion.staggerDelayMs(index) else 0

/** Whether elements may rise, scale, pop or sweep — spatial motion is the Full scheme's alone. */
val MotionScheme.moves: Boolean get() = this == MotionScheme.Full

/** Whether the one permitted loop (the shimmer) runs. */
val MotionScheme.loops: Boolean get() = this == MotionScheme.Full

/** The scheme the running app uses: the platform's reduce-motion setting, else Full. */
@Composable
fun platformMotionScheme(): MotionScheme =
    if (platformReducedMotion()) MotionScheme.Reduced else MotionScheme.Full

@Composable
fun FuelledTheme(
    motion: MotionScheme = platformMotionScheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMotion provides motion) {
        MaterialTheme(
            colorScheme = FuelledColorScheme,
            typography  = rememberFuelledTypography(),
            shapes      = FuelledShapes,
            content     = content,
        )
    }
}
