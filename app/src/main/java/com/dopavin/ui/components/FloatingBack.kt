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
class FloatingBackState {
    internal var owner: Any? by mutableStateOf(null)
    internal var onClick: (() -> Unit)? by mutableStateOf(null)
}
val LocalFloatingBack = staticCompositionLocalOf<FloatingBackState?> { null }
val LocalFloatingBackEnabled = staticCompositionLocalOf { true }
@Composable
fun FloatingBackButton(onClick: () -> Unit) {
    if (!LocalFloatingBackEnabled.current) return
    val host = LocalFloatingBack.current
    val latest by rememberUpdatedState(onClick)
    val token = remember { Any() }
    if (host == null) {
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
            if (host.owner === token) {
                host.owner = null
                host.onClick = null
            }
        }
    }
}
@Composable
fun FloatingBackLayer(
    state: FloatingBackState,
    modifier: Modifier = Modifier,
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
val FloatingBackAlignment = Alignment.TopStart
