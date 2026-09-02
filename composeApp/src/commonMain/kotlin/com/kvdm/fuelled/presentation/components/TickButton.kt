package com.kvdm.fuelled.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.spring
import com.kvdm.fuelled.presentation.theme.tween
import kotlinx.coroutines.launch

/**
 * A tick with a burst (MOTION-09): the registry's [AppIconButton] geometry — 48 dp target,
 * required description, one `Button` node with one `Image` child — plus, when [checked]
 * turns true after the first frame, a `TickPop` on `Lively` and one expanding ring in the
 * tick's colour on `Expressive`, and a `ToggleOn` haptic (OD1). Meals, water, supplements and
 * the session all tick through this; un-ticking is quiet.
 *
 * The burst is drawn behind the button, never composed — the semantics tree a golden holds
 * is exactly [AppIconButton]'s, and the ring can never be a target a screen reader lands on.
 *
 * @param checked Renders the ticked treatment ([checkedTint]) and drives the burst.
 * @param contentDescription What the control does NOW — "Mark Lunch done" / "Undo Lunch done".
 */
@Composable
fun TickButton(
    icon: ImageVector,
    checked: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedTint: Color = Color.Unspecified,
    uncheckedTint: Color = Color.Unspecified,
) {
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    var seen by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked && !seen) {
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            if (motion.moves) {
                launch {
                    pop.snapTo(1f)
                    pop.animateTo(FuelledMotion.TickPop, motion.spring(FuelledMotion.Springs.Lively))
                    pop.animateTo(1f, motion.spring(FuelledMotion.Springs.Settle))
                }
                launch {
                    ring.snapTo(0f)
                    ring.animateTo(1f, motion.tween(FuelledMotion.Duration.Expressive, FuelledMotion.Easings.Enter))
                    ring.snapTo(0f)
                }
            }
        }
        seen = checked
    }
    val resolvedChecked = if (checkedTint == Color.Unspecified) LocalContentColor.current else checkedTint
    val resolvedUnchecked = if (uncheckedTint == Color.Unspecified) LocalContentColor.current else uncheckedTint
    val tint = if (checked) resolvedChecked else resolvedUnchecked
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(AppButtonDefaults.MinTouchTarget)
            .drawBehind {
                val t = ring.value
                if (t > 0f && t < 1f) {
                    val radius = size.minDimension * (0.3f + 0.6f * t)
                    drawCircle(
                        color = resolvedChecked.copy(alpha = 0.9f * (1f - t)),
                        radius = radius,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}
