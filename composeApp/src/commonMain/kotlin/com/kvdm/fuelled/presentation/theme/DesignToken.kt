package com.kvdm.fuelled.presentation.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * The resolved design-token payload a component self-reports into the semantics tree.
 *
 * [tokens] are the declared token names (e.g. "RadiusCard"); [resolved] maps a facet
 * (e.g. "radius", "color") to its concrete resolved value (e.g. "16dp", "#FFFFFFFF").
 * This makes the semantics tree design-system-aware, not just geometry — which is what
 * the create-cmp inspector reads to catch token drift. Only uses semantics + Modifier,
 * so it adds no dependencies.
 */
data class DesignTokenInfo(val tokens: List<String>, val resolved: Map<String, String>)

val DesignTokenKey = SemanticsPropertyKey<DesignTokenInfo>("DesignToken")

var SemanticsPropertyReceiver.designToken by DesignTokenKey

/** Attaches the resolved design-token payload to a node's semantics. */
fun Modifier.designToken(tokens: List<String>, resolved: Map<String, String>): Modifier =
    this.semantics { designToken = DesignTokenInfo(tokens, resolved) }
