package com.kvdm.fuelled.inspector

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import com.kvdm.fuelled.presentation.theme.FuelledTypeRamp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The declared design-system catalog, served on GET /inspect/design-system in the shape the
 * cmp-inspector MCP consumes: `{ "colors": {name: "#RRGGBB"}, "dimens": {name: "16dp"} }`.
 *
 * Hand-registry by design (no kotlin-reflect, no codegen): values are read from the REAL
 * `FuelledColors` / `FuelledTokens` objects — never string-literal
 * duplicates — so renaming a token breaks this file at compile time. If you ADD a token to
 * the theme, add it here too (`create-cmp doctor` warns when a declared token is missing).
 */
object InspectorCatalog {

    private val prettyJson = Json { prettyPrint = true }

    fun json(): String {
        val doc = buildJsonObject {
            put("colors", buildJsonObject {
                put("Primary", FuelledColors.Primary.toHex())
                put("OnPrimary", FuelledColors.OnPrimary.toHex())
                put("Accent", FuelledColors.Accent.toHex())
                put("OnAccent", FuelledColors.OnAccent.toHex())
                put("Secondary", FuelledColors.Secondary.toHex())
                put("Error", FuelledColors.Error.toHex())
                put("Success", FuelledColors.Success.toHex())
                put("Warning", FuelledColors.Warning.toHex())
                put("Info", FuelledColors.Info.toHex())
                put("Background", FuelledColors.Background.toHex())
                put("Surface", FuelledColors.Surface.toHex())
                put("SurfaceVariant", FuelledColors.SurfaceVariant.toHex())
                put("OnSurface", FuelledColors.OnSurface.toHex())
                put("OnSurfaceVariant", FuelledColors.OnSurfaceVariant.toHex())
                put("Outline", FuelledColors.Outline.toHex())
                put("OutlineVariant", FuelledColors.OutlineVariant.toHex())
                put("Divider", FuelledColors.Divider.toHex())
            })
            put("dimens", buildJsonObject {
                put("ElevationCard", FuelledTokens.ElevationCard.token())
                put("ElevationModal", FuelledTokens.ElevationModal.token())
                put("PaddingPage", FuelledTokens.PaddingPage.token())
                put("PaddingCard", FuelledTokens.PaddingCard.token())
                put("GapCard", FuelledTokens.GapCard.token())
                put("BottomNavHeight", FuelledTokens.BottomNavHeight.token())
                put("RadiusCard", FuelledTokens.RadiusCard.token())
                put("RadiusPill", FuelledTokens.RadiusPill.token())
                put("RadiusModal", FuelledTokens.RadiusModal.token())
                put("RadiusInput", FuelledTokens.RadiusInput.token())
            })
            // The type ramp, from the same ramp data the Typography factory builds
            // its styles from — so the LIVE tier answers with the same block the
            // headless preview catalog writes, and the console renders one ramp
            // whichever tier it read.
            put("typography", buildJsonArray {
                FuelledTypeRamp.forEach { spec ->
                    add(buildJsonObject {
                        put("name", spec.name)
                        put("weight", spec.weight)
                        put("size", "${spec.sizeSp}sp")
                        put("lineHeight", "${spec.lineHeightSp}sp")
                        val tracking = spec.tracking
                        if (tracking == null) put("tracking", JsonNull) else put("tracking", "${tracking}sp")
                    })
                }
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
