package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import com.kvdm.fuelled.presentation.theme.designToken

/**
 * The list row: title, optional subtitle, optional leading/trailing slots, on a card
 * surface bound to `RadiusCard`, `ElevationCard`, and `PaddingCard`. The `clickable`
 * sits on the `Surface`, so the whole row is the touch target, with a 48 dp minimum
 * height. Reach for it in any list before hand-rolling a row.
 *
 * @param onClick Row-level click handler; the whole card is the target.
 * @param modifier Per-item testTags go here (`Modifier.testTag("home_item_$id")`) — ids
 *   are domain data, so the component does not derive them.
 * @param subtitle Optional second line, in the muted variant color.
 * @param leading Slot before the text column — an icon or avatar.
 * @param trailing Slot after the text column — a chevron or badge.
 */
@Composable
fun ListItemCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = FuelledTokens.ElevationCard,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .designToken(
                tokens = listOf("RadiusCard", "ElevationCard", "PaddingCard"),
                resolved = mapOf(
                    "radius" to "${FuelledTokens.RadiusCard.value.toInt()}dp",
                    "elevation" to "${FuelledTokens.ElevationCard.value.toInt()}dp",
                    "padding" to "${FuelledTokens.PaddingCard.value.toInt()}dp",
                    "color" to "#FFFFFFFF",
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(FuelledTokens.PaddingCard),
            verticalAlignment = Alignment.CenterVertically,
            // The slots need air between them: without it a leading icon touches the title
            // and a trailing chevron touches the subtitle, which is what made rows read as
            // cramped on device wherever this card carries both.
            horizontalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            if (leading != null) leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) trailing()
        }
    }
}
