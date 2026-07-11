package com.kvdm.cmpshowcase.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Seed design tokens. Replace these with your own brand palette — every screen reads
// colours from here (or from MaterialTheme.colorScheme), never as raw hex.
object CMPShowcaseColors {
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

private val CMPShowcaseColorScheme = lightColorScheme(
    primary              = CMPShowcaseColors.Primary,
    onPrimary            = CMPShowcaseColors.OnPrimary,
    secondary            = CMPShowcaseColors.Secondary,
    onSecondary          = CMPShowcaseColors.OnPrimary,
    tertiary             = CMPShowcaseColors.Accent,
    onTertiary           = CMPShowcaseColors.OnAccent,
    error                = CMPShowcaseColors.Error,
    background           = CMPShowcaseColors.Background,
    surface              = CMPShowcaseColors.Surface,
    surfaceVariant       = CMPShowcaseColors.SurfaceVariant,
    onBackground         = CMPShowcaseColors.OnSurface,
    onSurface            = CMPShowcaseColors.OnSurface,
    onSurfaceVariant     = CMPShowcaseColors.OnSurfaceVariant,
    outline              = CMPShowcaseColors.Outline,
    outlineVariant       = CMPShowcaseColors.OutlineVariant,
    inverseSurface       = CMPShowcaseColors.Primary,
    // Kill M3's tonal overlay so dialogs/menus/sheets stay on the design-system surface.
    surfaceTint              = Color.Transparent,
    surfaceContainerLowest   = CMPShowcaseColors.Surface,
    surfaceContainerLow      = CMPShowcaseColors.Surface,
    surfaceContainer         = CMPShowcaseColors.Surface,
    surfaceContainerHigh     = CMPShowcaseColors.Surface,
    surfaceContainerHighest  = CMPShowcaseColors.SurfaceVariant,
)

@Composable
fun CMPShowcaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CMPShowcaseColorScheme,
        typography  = rememberCMPShowcaseTypography(),
        shapes      = CMPShowcaseShapes,
        content     = content,
    )
}
