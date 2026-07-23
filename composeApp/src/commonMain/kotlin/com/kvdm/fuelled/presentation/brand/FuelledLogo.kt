package com.kvdm.fuelled.presentation.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledColors

// The Fuelled brand mark, drawn — an energy bolt punched out of a lime badge — so it scales
// crisply at any size and re-tints with the theme. `FuelledMark` is the badge alone (app
// icon / compact header); `FuelledWordmark` sets it beside the wordmark for the top bar.

// Bolt outline in a unit (0..1) box; scaled to the badge's inner area at draw time.
private val BoltPoints = listOf(
    0.58f to 0.06f,
    0.24f to 0.55f,
    0.47f to 0.55f,
    0.40f to 0.94f,
    0.80f to 0.42f,
    0.55f to 0.42f,
)

@Composable
fun FuelledMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val badge = FuelledColors.Primary
    val bolt = FuelledColors.OnPrimary
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { testTag = "brand_mark"; contentDescription = "Fuelled" },
    ) {
        val s = this.size.minDimension
        val radius = s * 0.28f
        drawRoundRect(
            color = badge,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
        drawBolt(bolt, s)
    }
}

private fun DrawScope.drawBolt(color: androidx.compose.ui.graphics.Color, s: Float) {
    val path = Path().apply {
        BoltPoints.forEachIndexed { i, (x, y) ->
            val px = x * s
            val py = y * s
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, color)
}

@Composable
fun FuelledWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 28.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuelledMark(size = markSize)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Fuelled",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
