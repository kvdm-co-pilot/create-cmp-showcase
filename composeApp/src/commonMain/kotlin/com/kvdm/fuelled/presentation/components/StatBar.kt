package com.kvdm.fuelled.presentation.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.spring
import com.kvdm.fuelled.presentation.theme.staggerDelayMs
import kotlinx.coroutines.delay

/**
 * The design system's one horizontal progress bar: a rounded track with a coloured fill.
 * [label] and [valueText] are OPTIONAL adornments of the SAME shape (a bar with a caption row),
 * not a second component — omit both for a bare bar. This is deliberately NOT the segmented
 * (stacked-macro) bar, which is a different shape and stays its own thing (do-not-force-reuse).
 *
 * Motion (MOTION-08): the fill grows to its value on `Weighty` — from 0 on first composition
 * when [fillFrom] is 0, delayed by [staggerIndex] so a column of bars draws itself one after
 * another — and carries a brighter tip so the head reads as the live edge. Reports
 * `progressBarRangeInfo` with the target fraction.
 *
 * @param progress 0f..1f; clamped.
 * @param fillFrom Where the fill starts on first composition; `null` starts at [progress].
 * @param staggerIndex This bar's place in an arrival stagger (`index × StaggerStep`).
 */
@Composable
fun StatBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = FuelledColors.Primary,
    label: String? = null,
    valueText: String? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    fillFrom: Float? = null,
    staggerIndex: Int = 0,
) {
    val target = progress.coerceIn(0f, 1f)
    val motion = LocalMotion.current
    val shown = remember { Animatable(if (motion.moves) (fillFrom ?: target).coerceIn(0f, 1f) else target) }
    LaunchedEffect(target) {
        val delayMs = motion.staggerDelayMs(staggerIndex)
        if (delayMs > 0 && shown.value != target) delay(delayMs.toLong())
        shown.animateTo(target, motion.spring(FuelledMotion.Springs.Weighty))
    }
    Column(
        modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null || valueText != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                if (label != null) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                if (valueText != null) {
                    Text(
                        valueText,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumerals),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(trackColor)) {
            Box(
                Modifier
                    .fillMaxWidth(shown.value)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
                    .drawWithContent {
                        drawContent()
                        // The live edge: a short brighter tip at the head of the fill.
                        if (size.width > 0f) {
                            val tip = minOf(size.width, 10.dp.toPx())
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.35f)),
                                    startX = size.width - tip,
                                    endX = size.width,
                                ),
                                topLeft = Offset(size.width - tip, 0f),
                            )
                        }
                    },
            )
        }
    }
}
