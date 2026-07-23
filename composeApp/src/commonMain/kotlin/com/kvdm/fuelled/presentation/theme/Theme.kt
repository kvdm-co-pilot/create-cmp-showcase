package com.kvdm.fuelled.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Seed design tokens. Replace these with your own brand palette — every screen reads
// colours from here (or from MaterialTheme.colorScheme), never as raw hex.
object FuelledColors {
    val Primary          = Color(0xFF0A2540)
    val OnPrimary        = Color(0xFFFFFFFF)
    val Accent           = Color(0xFF00E676)
    val OnAccent         = Color(0xFF0A2540)
    val Secondary        = Color(0xFF00B96B)
    val Error            = Color(0xFFDC2626)
    val Success          = Color(0xFF16A34A)
    val Warning          = Color(0xFFF59E0B)
    val Info             = Color(0xFF2563EB)
    val Background       = Color(0xFFF7F9FC)
    val Surface          = Color(0xFFFFFFFF)
    val SurfaceVariant   = Color(0xFFE8EDF3)
    val OnSurface        = Color(0xFF1A1A1A)
    val OnSurfaceVariant = Color(0xFF6B7280)
    val Outline          = Color(0xFF9CA3AF)
    val OutlineVariant   = Color(0xFFE5E7EB)
    val Divider          = Color(0xFFE5E7EB)
}

private val FuelledColorScheme = lightColorScheme(
    primary              = FuelledColors.Primary,
    onPrimary            = FuelledColors.OnPrimary,
    secondary            = FuelledColors.Secondary,
    onSecondary          = FuelledColors.OnPrimary,
    tertiary             = FuelledColors.Accent,
    onTertiary           = FuelledColors.OnAccent,
    error                = FuelledColors.Error,
    background           = FuelledColors.Background,
    surface              = FuelledColors.Surface,
    surfaceVariant       = FuelledColors.SurfaceVariant,
    onBackground         = FuelledColors.OnSurface,
    onSurface            = FuelledColors.OnSurface,
    onSurfaceVariant     = FuelledColors.OnSurfaceVariant,
    outline              = FuelledColors.Outline,
    outlineVariant       = FuelledColors.OutlineVariant,
    inverseSurface       = FuelledColors.Primary,
    // Kill M3's tonal overlay so dialogs/menus/sheets stay on the design-system surface.
    surfaceTint              = Color.Transparent,
    surfaceContainerLowest   = FuelledColors.Surface,
    surfaceContainerLow      = FuelledColors.Surface,
    surfaceContainer         = FuelledColors.Surface,
    surfaceContainerHigh     = FuelledColors.Surface,
    surfaceContainerHighest  = FuelledColors.SurfaceVariant,
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
