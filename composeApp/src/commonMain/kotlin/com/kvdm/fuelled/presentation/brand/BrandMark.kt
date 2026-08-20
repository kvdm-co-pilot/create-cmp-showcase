package com.kvdm.fuelled.presentation.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledColors

// The app's brand mark — a guided placeholder, not final art. Brand is its own governed
// category (presentation/brand/, distinct from the components registry): identity, not
// design-system vocabulary. This starter renders a theme-tinted badge with the app's
// initial plus a wordmark, so "we need a logo" has a home from day one. Replace the badge
// with a DRAWN mark (Canvas paths — see how a real one is built: a shape punched out of
// the badge, scaled from a unit box) when the identity lands; keep the testTags.

private const val APP_NAME = "Fuelled"

/** The badge alone — compact header, avatar seats, launcher-adjacent surfaces. */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(FuelledColors.Primary)
            .semantics { testTag = "brand_mark"; contentDescription = APP_NAME },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = APP_NAME.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FuelledColors.OnPrimary,
        )
    }
}

/** Badge + wordmark, for headers and about/launch surfaces. */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 28.dp,
) {
    Row(
        modifier = modifier.semantics { testTag = "brand_wordmark" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = markSize)
        Spacer(Modifier.width(10.dp))
        Text(
            text = APP_NAME,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
