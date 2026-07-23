package com.kvdm.fuelled.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun FuelledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FuelledColorScheme,
        typography  = rememberFuelledTypography(),
        shapes      = FuelledShapes,
        content     = content,
    )
}
