package com.kvdm.cmpshowcase.inspector

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.kvdm.cmpshowcase.presentation.theme.CMPShowcaseColors
import com.kvdm.cmpshowcase.presentation.theme.CMPShowcaseTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The declared design-system catalog, served on GET /inspect/design-system in the shape the
 * cmp-inspector MCP consumes: `{ "colors": {name: "#RRGGBB"}, "dimens": {name: "16dp"} }`.
 *
 * Hand-registry by design (no kotlin-reflect, no codegen): values are read from the REAL
 * `CMPShowcaseColors` / `CMPShowcaseTokens` objects — never string-literal
 * duplicates — so renaming a token breaks this file at compile time. If you ADD a token to
 * the theme, add it here too (`create-cmp doctor` warns when a declared token is missing).
 */
object InspectorCatalog {

    private val prettyJson = Json { prettyPrint = true }

    fun json(): String {
        val doc = buildJsonObject {
            put("colors", buildJsonObject {
                put("Primary", CMPShowcaseColors.Primary.toHex())
                put("OnPrimary", CMPShowcaseColors.OnPrimary.toHex())
                put("Accent", CMPShowcaseColors.Accent.toHex())
                put("OnAccent", CMPShowcaseColors.OnAccent.toHex())
                put("Secondary", CMPShowcaseColors.Secondary.toHex())
                put("Error", CMPShowcaseColors.Error.toHex())
                put("Success", CMPShowcaseColors.Success.toHex())
                put("Warning", CMPShowcaseColors.Warning.toHex())
                put("Info", CMPShowcaseColors.Info.toHex())
                put("Background", CMPShowcaseColors.Background.toHex())
                put("Surface", CMPShowcaseColors.Surface.toHex())
                put("SurfaceVariant", CMPShowcaseColors.SurfaceVariant.toHex())
                put("OnSurface", CMPShowcaseColors.OnSurface.toHex())
                put("OnSurfaceVariant", CMPShowcaseColors.OnSurfaceVariant.toHex())
                put("Outline", CMPShowcaseColors.Outline.toHex())
                put("OutlineVariant", CMPShowcaseColors.OutlineVariant.toHex())
                put("Divider", CMPShowcaseColors.Divider.toHex())
            })
            put("dimens", buildJsonObject {
                put("ElevationCard", CMPShowcaseTokens.ElevationCard.token())
                put("ElevationModal", CMPShowcaseTokens.ElevationModal.token())
                put("PaddingPage", CMPShowcaseTokens.PaddingPage.token())
                put("PaddingCard", CMPShowcaseTokens.PaddingCard.token())
                put("GapCard", CMPShowcaseTokens.GapCard.token())
                put("BottomNavHeight", CMPShowcaseTokens.BottomNavHeight.token())
                put("RadiusCard", CMPShowcaseTokens.RadiusCard.token())
                put("RadiusPill", CMPShowcaseTokens.RadiusPill.token())
                put("RadiusModal", CMPShowcaseTokens.RadiusModal.token())
                put("RadiusInput", CMPShowcaseTokens.RadiusInput.token())
            })
        }
        return prettyJson.encodeToString(JsonElement.serializer(), doc)
    }

    /** "#RRGGBB" for fully-opaque colours (the catalog convention), "#AARRGGBB" otherwise. */
    private fun Color.toHex(): String {
        val argb = toArgb()
        val alpha = (argb ushr 24) and 0xFF
        return if (alpha == 0xFF) {
            "#%06X".format(argb and 0xFFFFFF)
        } else {
            "#%08X".format(argb)
        }
    }

    /** "16dp" (integer dp values render without the decimal point). */
    private fun Dp.token(): String {
        val v = value
        return if (v == v.toInt().toFloat()) "${v.toInt()}dp" else "${v}dp"
    }
}
