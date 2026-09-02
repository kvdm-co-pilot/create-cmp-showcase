package com.kvdm.fuelled.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.navigation.AppTab
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.designToken
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.spring

/**
 * The bottom tab bar: one item per tab, icon over label. Owns the 48 dp touch targets,
 * the deterministic `nav_<slug>` testTags, token-bound colors, the navigation-bar inset
 * padding, and the `BottomNavHeight` inspector self-report. Selection state stays with
 * the caller; `AppShell` wires it.
 *
 * Motion (D5, MOTION-05): a lime pill at 14% (`RadiusPill`) sits behind the selected icon and
 * SLIDES between items on `Settle` — the instrument's needle — and the icon pops on `Lively`
 * as it becomes selected. The pill is drawn behind the row, never composed, so the semantics
 * tree the tests and E2E flows key on is exactly the five items; the selected one reports
 * `selected = true`. Labels sit on the ramp's `labelSmall` (the raw 10 sp is retired).
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
    val motion = LocalMotion.current
    // Each item's center x within the row, reported as it lays out; the pill follows the
    // selected one. -1 until the first layout, when the pill is simply not drawn.
    val centers = remember { mutableStateMapOf<Int, Float>() }
    val pillX = remember { Animatable(-1f) }
    val targetX = centers[selectedIndex]
    LaunchedEffect(targetX) {
        if (targetX == null) return@LaunchedEffect
        if (pillX.value < 0f) pillX.snapTo(targetX)
        else pillX.animateTo(targetX, motion.spring(FuelledMotion.Springs.Settle))
    }
    val pillColor = FuelledColors.Primary.copy(alpha = 0.14f)
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
                .drawBehind {
                    val x = pillX.value
                    if (x >= 0f) {
                        val w = 56.dp.toPx()
                        val h = 32.dp.toPx()
                        val top = (size.height - 8.dp.toPx() - h) / 2f - 10.dp.toPx()
                        drawRoundRect(
                            color = pillColor,
                            topLeft = Offset(x - w / 2f, top),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(h / 2f, h / 2f),
                        )
                    }
                }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val scale by animateFloatAsState(
                    targetValue = if (selected && motion.moves) 1.1f else 1f,
                    animationSpec = motion.spring(FuelledMotion.Springs.Lively),
                    label = "tabIcon",
                )
                NavItem(
                    label = tab.label,
                    selected = selected,
                    onClick = { onSelect(index) },
                    modifier = Modifier.onGloballyPositioned {
                        centers[index] = it.positionInParent().x + it.size.width / 2f
                    },
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) FuelledColors.Primary
                               else FuelledColors.OnSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
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
 * @param selected True renders the item in the selected treatment (primary color, bold label)
 *   and reports `selected` to assistive tech.
 * @param icon Icon slot, rendered above the label.
 */
@Composable
internal fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressable(interaction)
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            // a11y: guarantee the 48dp minimum touch target regardless of label width
            // (the verify lane's a11y step flags anything smaller).
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            // Durable selection handle (tests/E2E select by testTag, never display text).
            .semantics {
                testTag = navItemTag(label)
                this.selected = selected
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) FuelledColors.Primary else FuelledColors.OnSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
