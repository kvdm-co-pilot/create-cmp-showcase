package com.kvdm.fuelled.inspector

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.kvdm.fuelled.presentation.theme.DesignTokenKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToInt

/**
 * Walks a Compose [SemanticsNode] tree and serialises it to JSON matching the create-cmp
 * inspector contract (schemaVersion 1, source "headless-jvm"). Every node carries pixel,
 * root-relative `bounds` and a (possibly empty) `children` array; testTag / text /
 * contentDescription / designToken are nullable; `role` / `clickable` / `disabled` are the
 * additive interaction fields, and `size` is the additive full-composed-size field (see
 * [sizeJson]).
 *
 * This dumper reads THIS project's [DesignTokenKey], so the resolved design tokens the
 * component kit self-reports (Modifier.designToken) appear in the dump — which is what
 * makes the tree design-system-aware, not just geometry.
 */
object PreviewSemanticsJson {

    private val prettyJson = Json { prettyPrint = true }

    fun dumpTree(root: SemanticsNode): String {
        val doc = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("source", JsonPrimitive("headless-jvm"))
            put("root", nodeToJson(root))
        }
        return prettyJson.encodeToString(JsonElement.serializer(), doc)
    }

    private fun nodeToJson(node: SemanticsNode): JsonObject = buildJsonObject {
        put("testTag", node.config.getOrNull(SemanticsProperties.TestTag).toJson())
        put(
            "text",
            node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString(" ") { it.text }?.takeIf { it.isNotEmpty() }.toJson(),
        )
        put(
            "contentDescription",
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString(" ")?.takeIf { it.isNotEmpty() }.toJson(),
        )
        put("role", node.config.getOrNull(SemanticsProperties.Role)?.toString().toJson())
        put("clickable", JsonPrimitive(node.config.contains(SemanticsActions.OnClick)))
        put("disabled", JsonPrimitive(node.config.contains(SemanticsProperties.Disabled)))
        put("bounds", node.boundsJson())
        put("size", node.sizeJson())
        put("designToken", node.designTokenJson())
        put("children", buildJsonArray { node.children.forEach { add(nodeToJson(it)) } })
    }

    /**
     * Full composed (UNCLIPPED) size. `bounds` is the visible slice after ancestor clipping —
     * a scroll container's fold truncates it — while `size` is what the node actually
     * measures. Additive contract field (consumers treat it as optional); the a11y audit
     * judges touch targets on it so a fold-clipped list row is never a false violation.
     */
    private fun SemanticsNode.sizeJson(): JsonObject = buildJsonObject {
        put("width", JsonPrimitive(size.width))
        put("height", JsonPrimitive(size.height))
    }

    private fun SemanticsNode.boundsJson(): JsonObject {
        val rect = boundsInRoot
        return buildJsonObject {
            put("x", JsonPrimitive(rect.left.roundToInt()))
            put("y", JsonPrimitive(rect.top.roundToInt()))
            put("width", JsonPrimitive(rect.width.roundToInt()))
            put("height", JsonPrimitive(rect.height.roundToInt()))
        }
    }

    private fun SemanticsNode.designTokenJson(): JsonElement {
        val info = config.getOrNull(DesignTokenKey) ?: return JsonNull
        return buildJsonObject {
            put("tokens", buildJsonArray { info.tokens.forEach { add(JsonPrimitive(it)) } })
            put("resolved", buildJsonObject {
                info.resolved.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            })
        }
    }

    private fun String?.toJson(): JsonElement =
        if (this == null) JsonNull else JsonPrimitive(this)
}
