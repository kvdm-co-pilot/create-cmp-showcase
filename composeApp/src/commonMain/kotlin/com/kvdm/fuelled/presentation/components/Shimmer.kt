package com.kvdm.fuelled.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledTokens

/**
 * A left-to-right shimmer sweep: an infinite transition driving a linear-gradient brush.
 * Hand-rolled with zero dependencies — Accompanist's `placeholder` artifact is
 * deprecated and Android-only, so no library option exists for this project's
 * commonMain. Apply to any box that stands in for loading content.
 */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    background(
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.surfaceVariant,
            ),
            start = Offset(translateX, 0f),
            end = Offset(translateX + 400f, 400f),
        ),
    )
}

/**
 * A skeleton row matching [ListItemCard]'s geometry — same shape, elevation, and padding
 * tokens — so the loaded list replaces it without a layout jump. Decorative and
 * semantics-silent by design: the loading container (`ContentStateDefaults.ListSkeleton`)
 * carries the `<screenTag>_loading` tag and the "Loading" `contentDescription`, so
 * assistive tech announces one loading state, not a stack of anonymous bars.
 */
@Composable
fun ListItemSkeleton(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = FuelledTokens.ElevationCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(FuelledTokens.PaddingCard)) {
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .clip(MaterialTheme.shapes.small)
                    .shimmer(),
            )
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .shimmer(),
            )
        }
    }
}
