package com.kvdm.fuelled.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import com.kvdm.fuelled.generated.resources.DMSans_Bold
import com.kvdm.fuelled.generated.resources.DMSans_Medium
import com.kvdm.fuelled.generated.resources.DMSans_Regular
import com.kvdm.fuelled.generated.resources.DMSans_SemiBold
import com.kvdm.fuelled.generated.resources.Res

// DM Sans font family — weights 400/500/600/700 bundled in composeResources/font/.
val DmSansFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.DMSans_Regular,  weight = FontWeight.Normal),
        Font(Res.font.DMSans_Medium,   weight = FontWeight.Medium),
        Font(Res.font.DMSans_SemiBold, weight = FontWeight.SemiBold),
        Font(Res.font.DMSans_Bold,     weight = FontWeight.Bold),
    )

/**
 * One rung of the ramp, as plain data. The ramp has to be readable WITHOUT a
 * composition — the preview harness publishes it into `design-system.json`, and
 * the studio console's Design language page renders the type ramp from that
 * catalog. Holding the numbers here (rather than only inside the [Typography]
 * factory below, which needs `@Composable` for the font family) is what keeps
 * the published ramp and the rendered ramp the same numbers by construction.
 *
 * [tracking] is nullable on purpose: `null` means "leave letter spacing
 * unspecified", which is NOT the same as `0.sp` — Compose resolves those
 * differently, and flattening one into the other would silently retrack the
 * mid-ramp styles.
 */
data class FuelledTypeStyle(
    val name: String,
    val weight: Int,
    val sizeSp: Int,
    val lineHeightSp: Int,
    val tracking: Float? = null,
)

/**
 * A full ramp, not a minimal one. `display*` are the hero numerics (counts,
 * totals, dashboard figures) — heavy and optically tightened; `headline`/`title`
 * structure the screens; `label*` carries small ALL-CAPS eyebrows and metric
 * units. Tight negative tracking on the big sizes is what makes a data-forward
 * screen read as a considered product rather than a form — a thin ramp is the
 * single cheapest tell of a scaffold.
 */
val FuelledTypeRamp: List<FuelledTypeStyle> = listOf(
    FuelledTypeStyle("displayLarge",   weight = 700, sizeSp = 56, lineHeightSp = 58, tracking = -1.5f),
    FuelledTypeStyle("displayMedium",  weight = 700, sizeSp = 44, lineHeightSp = 46, tracking = -1.0f),
    FuelledTypeStyle("displaySmall",   weight = 700, sizeSp = 32, lineHeightSp = 36, tracking = -0.5f),
    FuelledTypeStyle("headlineLarge",  weight = 700, sizeSp = 28, lineHeightSp = 32, tracking = -0.5f),
    FuelledTypeStyle("headlineMedium", weight = 600, sizeSp = 22, lineHeightSp = 28, tracking = -0.3f),
    FuelledTypeStyle("titleLarge",     weight = 600, sizeSp = 18, lineHeightSp = 24),
    FuelledTypeStyle("titleMedium",    weight = 600, sizeSp = 16, lineHeightSp = 22),
    FuelledTypeStyle("bodyLarge",      weight = 400, sizeSp = 16, lineHeightSp = 24),
    FuelledTypeStyle("bodyMedium",     weight = 400, sizeSp = 14, lineHeightSp = 20),
    FuelledTypeStyle("labelLarge",     weight = 600, sizeSp = 14, lineHeightSp = 18),
    FuelledTypeStyle("labelMedium",    weight = 500, sizeSp = 12, lineHeightSp = 16, tracking = 0.5f),
    FuelledTypeStyle("labelSmall",     weight = 600, sizeSp = 11, lineHeightSp = 14, tracking = 0.8f),
)

private fun ramp(name: String): FuelledTypeStyle =
    FuelledTypeRamp.first { it.name == name }

@Composable
fun rememberFuelledTypography(): Typography {
    val dmSans = DmSansFontFamily
    fun style(name: String): TextStyle {
        val spec = ramp(name)
        return TextStyle(
            fontFamily = dmSans,
            fontWeight = FontWeight(spec.weight),
            fontSize = spec.sizeSp.sp,
            lineHeight = spec.lineHeightSp.sp,
            letterSpacing = spec.tracking?.sp ?: TextUnit.Unspecified,
        )
    }
    return Typography(
        displayLarge   = style("displayLarge"),
        displayMedium  = style("displayMedium"),
        displaySmall   = style("displaySmall"),
        headlineLarge  = style("headlineLarge"),
        headlineMedium = style("headlineMedium"),
        titleLarge     = style("titleLarge"),
        titleMedium    = style("titleMedium"),
        bodyLarge      = style("bodyLarge"),
        bodyMedium     = style("bodyMedium"),
        labelLarge     = style("labelLarge"),
        labelMedium    = style("labelMedium"),
        labelSmall     = style("labelSmall"),
    )
}
