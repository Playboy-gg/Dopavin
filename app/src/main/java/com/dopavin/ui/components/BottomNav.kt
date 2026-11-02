package com.dopavin.ui.components
import androidx.annotation.DrawableRes
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.Destination
import com.dopavin.ui.glass.floatingtabbar.FloatingTabBarScrollConnection
import com.dopavin.ui.glass.glassSurface
import com.dopavin.ui.glass.nav.DopavinNavBar
import com.dopavin.ui.glass.nav.NavTab
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import com.kyant.capsule.ContinuousCapsule
private class RailTab(
    val destination: Destination,
    val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val iconSelected: Int,
)
private val Tabs = listOf(
    RailTab(Destination.Home, R.string.nav_home, R.drawable.ic_home_outline, R.drawable.ic_home),
    RailTab(Destination.Search, R.string.nav_search, R.drawable.ic_search, R.drawable.ic_search),
    RailTab(Destination.Library, R.string.nav_library_hub, R.drawable.ic_library_outline, R.drawable.ic_library),
)
@Composable
fun BottomNav(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
    accessory: (@Composable (isInline: Boolean, contentColor: Color, modifier: Modifier) -> Unit)? = null,
) {
    val labels = Tabs.map { stringResource(it.label) }
    val tabs = remember(labels) {
        Tabs.mapIndexed { i, t ->
            NavTab(t.destination.name, labels[i], t.icon, standalone = t.destination == Destination.Search)
        }
    }
    DopavinNavBar(
        tabs = tabs,
        currentKey = selected.name,
        onNavigate = { tab -> Tabs.firstOrNull { it.destination.name == tab.key }?.let { onSelect(it.destination) } },
        scrollConnection = scrollConnection,
        modifier = modifier,
        accessory = accessory,
    )
}
@Composable
fun SolidBottomNav(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = ContinuousCapsule()
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Dimens.lg, end = Dimens.lg, bottom = Dimens.sm, top = Dimens.xs)
            .height(Dimens.bottomNavHeight)
            .clip(shape)
            .background(colors.surface)
            .border(Dimens.hairline, colors.divider, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tabs.forEach { tab ->
            val isSelected = tab.destination == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (isSelected) colors.accent.copy(alpha = 0.14f) else Color.Transparent),
            ) {
                NavItem(
                    label = stringResource(tab.label),
                    icon = tab.icon,
                    iconSelected = tab.iconSelected,
                    selected = isSelected,
                    onClick = { onSelect(tab.destination) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
@Composable
private fun NavItem(
    label: String,
    @DrawableRes icon: Int,
    @DrawableRes iconSelected: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val motion = AppTheme.motion
    val tint by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = tween(motion.short),
        label = "navTint",
    )
    Column(
        modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(if (selected) iconSelected else icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconControl),
        )
        AppText(
            text = label,
            style = AppTheme.type.caption,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
@Composable
fun NavRail(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = ContinuousCapsule()
    Column(
        modifier
            .padding(start = Dimens.md)
            .width(NavRailWidth)
            .glassSurface(shape, fallback = colors.surface, tint = colors.surface, floating = true)
            .border(Dimens.hairline, colors.divider, shape)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tabs.forEach { tab ->
            val isSelected = tab.destination == selected
            Box(
                Modifier
                    .height(64.dp)
                    .width(NavRailWidth - 8.dp)
                    .then(
                        if (isSelected) {
                            Modifier.glassSurface(
                                shape = shape,
                                fallback = colors.accent.copy(alpha = 0.18f),
                                tint = colors.accent,
                                blurDp = 1f,
                                tintAlpha = 0.16f,
                                lensScale = 0.8f,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                NavItem(
                    label = stringResource(tab.label),
                    icon = tab.icon,
                    iconSelected = tab.iconSelected,
                    selected = isSelected,
                    onClick = { onSelect(tab.destination) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
val NavRailWidth = 72.dp
val NavRailInset = NavRailWidth + Dimens.md + Dimens.sm
