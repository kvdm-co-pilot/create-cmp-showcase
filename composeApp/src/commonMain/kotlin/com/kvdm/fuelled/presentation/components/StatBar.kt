package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledColors

/**
 * The design system's one horizontal progress bar: a rounded track with a coloured fill.
 * [label] and [valueText] are OPTIONAL adornments of the SAME shape (a bar with a caption row),
 * not a second component — omit both for a bare bar. This is deliberately NOT the segmented
 * (stacked-macro) bar, which is a different shape and stays its own thing (do-not-force-reuse).
 *
 * @param progress 0f..1f; clamped.
 */
@Composable
fun StatBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = FuelledColors.Primary,
    label: String? = null,
    valueText: String? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null || valueText != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                if (label != null) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                if (valueText != null) {
                    Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(trackColor)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}
