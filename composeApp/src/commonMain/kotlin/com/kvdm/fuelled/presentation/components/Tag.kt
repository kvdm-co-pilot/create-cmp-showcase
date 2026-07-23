package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * The design system's inline tag: a coloured, bold [label] followed by a muted [value]
 * (e.g. "P 38g"). The colour is the caller's — so the tag stays **domain-agnostic**: macros,
 * activity, supplement timing, anything with a short coded label and a value. Deliberately not
 * named for any one feature (a domain-named component is a feature decision in disguise).
 */
@Composable
fun Tag(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        Text(" $value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
