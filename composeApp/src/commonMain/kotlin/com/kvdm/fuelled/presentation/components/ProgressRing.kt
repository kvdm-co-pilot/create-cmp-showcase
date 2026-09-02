package com.kvdm.fuelled.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.spring
import kotlin.math.cos
import kotlin.math.sin

/**
 * The design system's one circular-progress primitive: a full track plus a rounded progress
 * arc, with a free center [content] slot. Screens supply what sits in the middle (a count, a
 * label) — the ring itself owns only the geometry and the token-bound colours.
 *
 * Motion (D8/D9, MOTION-08): the arc SWEEPS to its value on `Weighty` — from 0 on first
 * composition when [sweepFrom] is 0, so a screen's arrival draws the day — and reads as
 * charged rather than filled: a sweep gradient along the arc and a soft glow at its head, both
 * in [progressColor]. Reaching 1.0 takes one `Lively` breath. Under Reduced and Instant the
 * ring simply shows its value. It reports `progressBarRangeInfo` so assistive tech (and the
 * tests) read the fraction the arc shows.
 *
 * @param progress 0f..1f; values outside are clamped.
 * @param sweepFrom Where the sweep starts on first composition; `null` starts at [progress].
 * @param content centered over the ring (e.g. the remaining count and its unit).
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    trackColor: Color = FuelledColors.OutlineVariant,
    progressColor: Color = FuelledColors.Primary,
    sweepFrom: Float? = null,
    content: @Composable () -> Unit = {},
) {
    val target = progress.coerceIn(0f, 1f)
    val motion = LocalMotion.current
    val shown = remember { Animatable(if (motion.moves) (sweepFrom ?: target).coerceIn(0f, 1f) else target) }
    val breath = remember { Animatable(1f) }
    LaunchedEffect(target) {
        shown.animateTo(target, motion.spring(FuelledMotion.Springs.Weighty))
        if (target >= 1f && motion.moves) {
            breath.animateTo(1.06f, motion.spring(FuelledMotion.Springs.Lively))
            breath.animateTo(1f, motion.spring(FuelledMotion.Springs.Settle))
        }
    }
    Box(
        modifier
            .graphicsLayer { scaleX = breath.value; scaleY = breath.value }
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = shown.value
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(size.width - sw, size.height - sw)
            val topLeft = Offset(inset, inset)
            drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (p > 0f) {
                // The sweep gradient starts at 12 o'clock (the arc's start) and fades to 70%
                // by the time it comes round — the head is always the brightest point.
                val brush = Brush.sweepGradient(
                    0f to progressColor,
                    1f to progressColor.copy(alpha = 0.7f),
                    center = center,
                )
                rotate(-90f) {
                    drawArc(brush, 0f, 360f * p, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                }
                val angle = (-90f + 360f * p) * (kotlin.math.PI / 180f).toFloat()
                val radius = (size.minDimension - sw) / 2f
                val head = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(progressColor.copy(alpha = 0.35f), progressColor.copy(alpha = 0f)),
                        center = head,
                        radius = sw,
                    ),
                    radius = sw,
                    center = head,
                )
            }
        }
        content()
    }
}
