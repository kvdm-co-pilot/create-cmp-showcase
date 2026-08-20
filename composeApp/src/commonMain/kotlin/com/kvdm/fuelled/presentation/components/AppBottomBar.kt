package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kvdm.fuelled.presentation.navigation.AppTab
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import com.kvdm.fuelled.presentation.theme.designToken

/**
 * The bottom tab bar: one item per tab, icon over label. Owns the 48 dp touch targets,
 * the deterministic `nav_<slug>` testTags, token-bound colors, the navigation-bar inset
 * padding, and the `BottomNavHeight` inspector self-report. Selection state stays with
 * the caller; `AppShell` wires it.
 *
 * @param tabs Tabs in display order; each label also derives its item's `nav_*` testTag.
 * @param selectedIndex Index of the selected tab in [tabs].
 * @param onSelect Called with the index of the tapped tab.
 */
@Composable
fun AppBottomBar(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FuelledColors.OutlineVariant)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FuelledColors.Surface)
                // Lift tabs above the gesture pill / 3-button nav (maps to iOS safe area).
                .navigationBarsPadding()
                .height(FuelledTokens.BottomNavHeight)
                // Inspector: the bottom-nav container self-reports its height token.
                .designToken(
                    tokens = listOf("BottomNavHeight"),
                    resolved = mapOf("height" to "${FuelledTokens.BottomNavHeight.value.toInt()}dp"),
                )
                .semantics { testTag = "app_bottom_nav" }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { index, tab ->
                NavItem(
                    label = tab.label,
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selectedIndex == index) FuelledColors.Primary
                               else FuelledColors.OnSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Deterministic automation tag for a nav item: `nav_` + the label lowercased with every
 * non-[a-z0-9] run collapsed to `_` and trimmed (e.g. "My Stuff!" → `nav_my_stuff`).
 * Must mirror `navSlug` in create-cmp's engine (src/lib/tabs.mjs), which generates
 * `qa/e2e/smoke.yaml`'s id selectors from the configured tabs — keep the two in sync.
 */
private fun navItemTag(label: String): String =
    "nav_" + label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/**
 * A single tab item: icon over label, a 48 dp minimum touch target, tagged from its
 * label via [navItemTag]. Internal (not private) so the component story
 * (`component.nav-item`) can render it in isolation; absent from the public API surface.
 *
 * @param selected True renders the item in the selected treatment (primary color, bold label).
 * @param icon Icon slot, rendered above the label.
 */
@Composable
internal fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            // a11y: guarantee the 48dp minimum touch target regardless of label width
            // (the verify lane's a11y step flags anything smaller).
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            // Durable selection handle (tests/E2E select by testTag, never display text).
            .semantics { testTag = navItemTag(label) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) FuelledColors.Primary else FuelledColors.OnSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
