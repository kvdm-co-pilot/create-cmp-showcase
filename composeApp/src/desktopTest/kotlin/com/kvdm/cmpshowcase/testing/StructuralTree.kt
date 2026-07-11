package com.kvdm.cmpshowcase.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Serializes a Compose semantics tree to deterministic, diffable JSON — the golden-tree
 * format the verify lane's `goldenTrees` step compares against the committed baselines
 * in `qa/golden/`. (NB: no glob patterns in KDoc — Kotlin block comments nest.)
 *
 * Structure only, no pixels and no absolute geometry: testTag, role, text, and children.
 * That keeps baselines stable across platforms/densities while still catching structural
 * regressions (nodes appearing/disappearing/reordering, text changes, role changes).
 */
object StructuralTree {

    fun serialize(root: SemanticsNode): String = buildString {
        appendNode(root, 0)
        append('\n')
    }

    private fun StringBuilder.appendNode(node: SemanticsNode, depth: Int) {
        val indent = "  ".repeat(depth)
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        val role = node.config.getOrNull(SemanticsProperties.Role)?.toString()
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")

        append(indent).append("{")
        val fields = buildList {
            tag?.let { add(""""tag": ${json(it)}""") }
            role?.let { add(""""role": ${json(it)}""") }
            text?.let { add(""""text": ${json(it)}""") }
            desc?.let { add(""""contentDescription": ${json(it)}""") }
        }
        append(fields.joinToString(", "))

        val children = node.children.filter { it.isMeaningful() }
        if (children.isEmpty()) {
            append("}")
        } else {
            if (fields.isNotEmpty()) append(", ")
            append("\"children\": [\n")
            children.forEachIndexed { i, child ->
                appendNode(child, depth + 1)
                if (i < children.lastIndex) append(",")
                append('\n')
            }
            append(indent).append("]}")
        }
    }

    /** Nodes carrying no signal (pure layout wrappers) are elided so baselines stay tight. */
    private fun SemanticsNode.isMeaningful(): Boolean =
        config.getOrNull(SemanticsProperties.TestTag) != null ||
            config.getOrNull(SemanticsProperties.Text) != null ||
            config.getOrNull(SemanticsProperties.ContentDescription) != null ||
            config.getOrNull(SemanticsProperties.Role) != null ||
            children.any { it.isMeaningful() }

    private fun json(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
