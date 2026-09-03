package com.kvdm.fuelled.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.navigation.AppTab
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.designToken
import com.kvdm.fuelled.presentation.theme.spring

/**
 * The bottom tab bar: Material 3's [NavigationBar], with this app's sliding indicator.
 *
 * It was hand-rolled, and an on-device review found the cost (2026-09-03). Every item wrapped
 * its own content with `SpaceEvenly` between, so roughly 40% of the bar was dead space that
 * swallowed taps; the container was 72 dp against M3's 80; the indicator was lime at 14%,
 * which composites to 1.44:1 against the bar and is invisible; and the press-scale had been
 * bought by passing `indication = null`, which removed the ripple — the clearest tell on
 * Android that a control is not a system component.
 *
 * M3's own component fixes all four by construction: each [NavigationBarItem] takes an equal
 * `weight(1f)` of the row so the whole cell is tappable, the container is 80 dp and applies
 * its own navigation-bar insets, and the item carries the ripple and the `selectable(Tab)`
 * role. What stays ours is the MOTION-05 indicator: M3 cross-fades its pill per item, this
 * app SLIDES one pill between them on `Settle`. So M3's indicator is made transparent and
 * ours is DRAWN behind the bar — never composed, so `app_bottom_nav`'s children stay exactly
 * the `nav_<slug>` items and the semantics tree is unchanged.
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
    // Each item's centre x within the bar, reported as it lays out; the pill follows the
    // selected one. -1 until the first layout, when the pill is simply not drawn.
    val centers = remember { mutableStateMapOf<Int, Float>() }
    val pillX = remember { Animatable(-1f) }
    val targetX = centers[selectedIndex]
    LaunchedEffect(targetX) {
        if (targetX == null) return@LaunchedEffect
        if (pillX.value < 0f) pillX.snapTo(targetX)
        else pillX.animateTo(targetX, motion.spring(FuelledMotion.Springs.Settle))
    }
    NavigationBar(
        modifier = modifier
            // Inspector: the bottom-nav container self-reports its height token.
            .designToken(
                tokens = listOf("BottomNavHeight"),
                resolved = mapOf("height" to "${FuelledTokens.BottomNavHeight.value.toInt()}dp"),
            )
            .semantics { testTag = "app_bottom_nav" }
            // The container is painted HERE, not by NavigationBar, so the pill can sit
            // between the background and the items. drawBehind renders under the
            // composable's own drawing, and NavigationBar's opaque container would
            // otherwise cover the pill entirely (it did — caught on the first render).
            .drawBehind {
                drawRect(color = FuelledColors.Surface)
                val x = pillX.value
                if (x >= 0f) {
                    val w = IndicatorWidth.toPx()
                    val h = IndicatorHeight.toPx()
                    drawRoundRect(
                        color = FuelledColors.NavIndicator,
                        topLeft = Offset(x - w / 2f, IndicatorTop.toPx()),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2f, h / 2f),
                    )
                }
            },
        containerColor = Color.Transparent,
    ) {
        tabs.forEachIndexed { index, tab ->
            NavItem(
                scope = this,
                label = tab.label,
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                modifier = Modifier.onGloballyPositioned {
                    centers[index] = it.positionInParent().x + it.size.width / 2f
                },
            ) {
                Icon(imageVector = tab.icon, contentDescription = tab.label, modifier = Modifier.size(IconSize))
            }
        }
    }
}

/** M3's navigation-bar indicator geometry, and where ours sits inside the 80 dp container. */
private val IndicatorWidth = 64.dp
private val IndicatorHeight = 32.dp
private val IndicatorTop = 12.dp
private val IconSize = 24.dp

/**
 * Deterministic automation tag for a nav item: `nav_` + the label lowercased with every
 * non-[a-z0-9] run collapsed to `_` and trimmed (e.g. "My Stuff!" → `nav_my_stuff`).
 * Must mirror `navSlug` in create-cmp's engine (src/lib/tabs.mjs), which generates
 * `qa/e2e/smoke.yaml`'s id selectors from the configured tabs — keep the two in sync.
 */
private fun navItemTag(label: String): String =
    "nav_" + label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/**
 * A single tab item: M3's [NavigationBarItem], tagged from its label via [navItemTag].
 * Internal (not private) so the component story (`component.nav-item`) can render it in
 * isolation; absent from the public API surface.
 *
 * The [scope] is passed as a PARAMETER rather than taken as a `RowScope` receiver, which
 * would read better: the lane's component↔story parity gate scans for `@Composable fun
 * <Name>(` and cannot see a receiver extension, so `fun RowScope.NavItem(` registers as no
 * component at all and its story fails as orphaned. Kept explicit rather than deleting the
 * story — the gate's blind spot is reported upstream, not worked around silently.
 *
 * M3's own indicator is made transparent because this app draws a SLIDING one behind the
 * bar (MOTION-05). Everything else is M3's: the ripple, the `selectable(Tab)` role and its
 * `selected` state, the equal-weight cell, the 12 sp `labelMedium` label, and the
 * `onSecondaryContainer`/`onSurfaceVariant` colour pair — here bound to this app's accent.
 *
 * @param selected True renders the selected treatment and reports `selected` to a11y.
 * @param icon Icon slot, rendered above the label.
 */
@Composable
internal fun NavItem(
    scope: RowScope,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) = with(scope) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        alwaysShowLabel = true,
        // Durable selection handle (tests/E2E select by testTag, never display text).
        modifier = modifier.semantics { testTag = navItemTag(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = FuelledColors.Primary,
            selectedTextColor = FuelledColors.Primary,
            unselectedIconColor = FuelledColors.OnSurfaceVariant,
            unselectedTextColor = FuelledColors.OnSurfaceVariant,
            // Ours slides, drawn behind the bar — M3's per-item pill would double it.
            indicatorColor = Color.Transparent,
        ),
    )
}
