@file:OptIn(ExperimentalSharedTransitionApi::class)
package com.dopavin.ui.glass.nav
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopavin.ui.glass.floatingtabbar.FloatingTabBar
import com.dopavin.ui.glass.floatingtabbar.FloatingTabBarDefaults
import com.dopavin.ui.glass.floatingtabbar.FloatingTabBarScrollConnection
import com.dopavin.ui.glass.floatingtabbar.LocalTabBarBackdropFrozen
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import androidx.compose.foundation.shape.RoundedCornerShape
data class NavTab(
    val key: String,
    val label: String,
    @DrawableRes val icon: Int,
    val standalone: Boolean = false,
)
private val NavBarShape = RoundedCornerShape(percent = 50)
private val NavBarMinTabWidth = 56.dp
private const val TintedChromeAlpha = 0.88f
@Composable
fun DopavinNavBar(
    tabs: List<NavTab>,
    currentKey: String,
    onNavigate: (NavTab) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
    accessory: (@Composable (isInline: Boolean, contentColor: Color, modifier: Modifier) -> Unit)? = null,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    if (!useGlass) return
    val colors = AppTheme.colors
    val tabTextColor by animateColorAsState(
        targetValue = glassConfig.textColor,
        animationSpec = tween(durationMillis = 200),
        label = "navBarTextColor",
    )
    val selectedContentColor = colors.accent
    val unselectedContentColor = tabTextColor.copy(alpha = 0.62f)
    val glassFrozen = LocalTabBarBackdropFrozen.current
    val tabBarContentModifier = Modifier.liquidGlass(
        config = glassConfig,
        shape = NavBarShape,
        highlightAlpha = 0.45f,
        frozen = glassFrozen,
    )
    val miniGlass = glassConfig.isEnabledFor(GlassComponent.MINI_PLAYER)
    val accessoryGlassModifier = if (miniGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = NavBarShape,
            highlightAlpha = 0.45f,
            frozen = glassFrozen,
        )
    } else {
        Modifier
            .background(colors.surface.copy(alpha = TintedChromeAlpha), NavBarShape)
            .tintedRim(NavBarShape)
    }
    val haptics = LocalHapticFeedback.current
    val navigate: (NavTab) -> Unit = { tab ->
        if (tab.key != currentKey) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onNavigate(tab)
    }
    val inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        if (accessory != null) {
            { accessoryModifier, _ ->
                accessory(true, tabTextColor, accessoryModifier.then(accessoryGlassModifier))
            }
        } else {
            null
        }
    val expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        if (accessory != null) {
            { accessoryModifier, _ ->
                accessory(false, tabTextColor, accessoryModifier.fillMaxWidth().then(accessoryGlassModifier))
            }
        } else {
            null
        }
    val inks = tabs.associate { tab ->
        tab.key to animateColorAsState(
            if (tab.key == currentKey) selectedContentColor else unselectedContentColor,
            tween(220),
            label = "tabInk",
        )
    }
    BoxWithConstraints(
        modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        val groupTabs = tabs.filter { !it.standalone }
        val standaloneTab = tabs.firstOrNull { it.standalone }
        val tabWidth = if (groupTabs.isEmpty()) {
            FloatingTabBarDefaults.TabWidth
        } else {
            ((maxWidth - if (standaloneTab != null) 64.dp else 0.dp) / groupTabs.size).coerceIn(NavBarMinTabWidth, FloatingTabBarDefaults.TabWidth)
        }
        var lastGroupKey by remember { mutableStateOf(groupTabs.firstOrNull()?.key) }
        if (groupTabs.any { it.key == currentKey } && lastGroupKey != currentKey) lastGroupKey = currentKey
        FloatingTabBar(
            inlineFallbackKey = lastGroupKey,
            selectedTabKey = currentKey,
            scrollConnection = scrollConnection,
            modifier = Modifier.fillMaxWidth(),
            tabBarContentModifier = tabBarContentModifier,
            inlineAccessory = inlineAccessory,
            expandedAccessory = expandedAccessory,
            colors = FloatingTabBarDefaults.colors(
                backgroundColor = Color.Transparent,
                accessoryBackgroundColor = Color.Transparent,
            ),
            sizes = FloatingTabBarDefaults.sizes(
                tabBarContentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                tabExpandedContentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp),
                tabInlineContentPadding = PaddingValues(8.dp),
                tabWidth = tabWidth,
            ),
            backdrop = LocalAppBackdrop.current,
            accentColor = selectedContentColor,
        ) {
            groupTabs.forEach { tab ->
                val color = inks.getValue(tab.key).value
                tab(
                    key = tab.key,
                    title = {
                        Text(
                            text = tab.label,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    icon = {
                        Icon(
                            painter = painterResource(tab.icon),
                            contentDescription = tab.label,
                            tint = color,
                            modifier = Modifier.size(Dimens.iconControl),
                        )
                    },
                    onClick = { navigate(tab) },
                    indication = null,
                )
            }
            if (standaloneTab != null) {
                val color = inks.getValue(standaloneTab.key).value
                standaloneTab(
                    key = standaloneTab.key,
                    icon = {
                        Icon(
                            painter = painterResource(standaloneTab.icon),
                            contentDescription = standaloneTab.label,
                            tint = color,
                            modifier = Modifier.size(Dimens.iconControl),
                        )
                    },
                    onClick = { navigate(standaloneTab) },
                    indication = null,
                )
            }
        }
    }
}
@Composable
private fun Modifier.tintedRim(shape: Shape): Modifier {
    val colors = AppTheme.colors
    val top = if (colors.isDark) Color.White.copy(alpha = 0.28f) else colors.divider
    val bottom = if (colors.isDark) Color.White.copy(alpha = 0.06f) else colors.divider
    return this.border(0.8.dp, Brush.verticalGradient(listOf(top, bottom)), shape)
}
