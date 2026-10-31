package com.dopavin.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dopavin.R
import com.dopavin.ui.theme.Dimens

/**
 * The back button of pushed pages, drawn once by the app root ABOVE the page content so its liquid
 * glass really samples what scrolls underneath (the page itself can't sample its own layer).
 * A page calls [FloatingBackButton]; it draws nothing itself, it only tells the root what back does.
 */
class FloatingBackState {
    internal var owner: Any? by mutableStateOf(null)
    internal var onClick: (() -> Unit)? by mutableStateOf(null)
}

val LocalFloatingBack = staticCompositionLocalOf<FloatingBackState?> { null }

/** False for a page drawn only as the "underneath" preview of the back gesture: it must not take the button. */
val LocalFloatingBackEnabled = staticCompositionLocalOf { true }

@Composable
fun FloatingBackButton(onClick: () -> Unit) {
    if (!LocalFloatingBackEnabled.current) return
    val host = LocalFloatingBack.current
    val latest by rememberUpdatedState(onClick)
    val token = remember { Any() }
    if (host == null) {
        // No root layer (previews, tests): draw it in place.
        IconAction(
            icon = R.drawable.ic_back,
            description = stringResource(R.string.cd_back),
            onClick = onClick,
            glass = true,
        )
        return
    }
    DisposableEffect(host) {
        host.owner = token
        host.onClick = { latest() }
        onDispose {
            // A newer page may already have taken over during the screen cross-fade.
            if (host.owner === token) {
                host.owner = null
                host.onClick = null
            }
        }
    }
}

/** Root-level drawing of the registered back button. */
@Composable
fun FloatingBackLayer(
    state: FloatingBackState,
    modifier: Modifier = Modifier,
    /** When set, the root decides how back happens (animated slide) instead of calling the page's click. */
    intercept: (() -> Unit)? = null,
) {
    val click = state.onClick ?: return
    IconAction(
        icon = R.drawable.ic_back,
        description = stringResource(R.string.cd_back),
        onClick = intercept ?: click,
        modifier = modifier
            .statusBarsPadding()
            .padding(start = Dimens.sm, top = Dimens.sm),
        glass = true,
    )
}

/** Alignment helper so the root can place the layer without importing Alignment. */
val FloatingBackAlignment = Alignment.TopStart
