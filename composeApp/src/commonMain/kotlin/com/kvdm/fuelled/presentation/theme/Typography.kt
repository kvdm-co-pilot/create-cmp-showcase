package com.kvdm.fuelled.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

// A full premium ramp. `display*` are the hero numerics (calorie/protein counts) — heavy
// and optically tightened; `headline`/`title` structure the screens; `label*` carries the
// small ALL-CAPS eyebrows and metric units. Tight negative tracking on the big numbers is
// what makes a data-forward screen read as a considered product, not a form.
@Composable
fun rememberFuelledTypography(): Typography {
    val dmSans = DmSansFontFamily
    return Typography(
        displayLarge   = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.Bold,     fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-1.5).sp),
        displayMedium  = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.Bold,     fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.0).sp),
        displaySmall   = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.Bold,     fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
        headlineLarge  = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
        titleLarge     = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium    = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge      = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium     = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge     = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
        labelMedium    = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall     = TextStyle(fontFamily = dmSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
    )
}
