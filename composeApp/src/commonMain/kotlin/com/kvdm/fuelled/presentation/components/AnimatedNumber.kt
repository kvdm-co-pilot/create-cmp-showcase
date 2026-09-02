package com.kvdm.fuelled.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.spring
import kotlin.math.roundToInt

/** Tabular figures: every digit the same width, so a counting number never wobbles (motion D10). */
val TabularNumerals = "tnum"

/**
 * A figure that counts to its value on `Weighty` (MOTION-07): the hero's "kcal left", the
 * protein figure, the tray total, a stepper. Renders in tabular numerals. The text it shows
 * is the rounded live value — under Instant that is [value] on frame 0, which is what every
 * test and golden tree reads.
 *
 * @param countFrom Where the count starts on first composition; `null` starts at [value] (no
 *   count-up on arrival). Today's hero passes 0 so the day counts itself up.
 * @param format Renders the rounded value — a unit, a thousands separator, a prefix.
 */
@Composable
fun AnimatedNumber(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    countFrom: Int? = null,
    format: (Int) -> String = { it.toString() },
) {
    val motion = LocalMotion.current
    val shown = remember { Animatable((if (motion.moves) countFrom ?: value else value).toFloat()) }
    LaunchedEffect(value) {
        shown.animateTo(value.toFloat(), motion.spring(FuelledMotion.Springs.Weighty))
    }
    Text(
        text = format(shown.value.roundToInt()),
        style = style.copy(fontFeatureSettings = TabularNumerals),
        color = color,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}
