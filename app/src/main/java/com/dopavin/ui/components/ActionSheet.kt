package com.dopavin.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dopavin.data.ArtworkSize
import com.dopavin.ui.DownloadsUi
import com.dopavin.ui.MusicActions
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/** One tappable row of an action sheet. [destructive] tints it with the error colour. */
data class SheetAction(
    val label: String,
    val icon: Int? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** What the sheet shows: a header (optional artwork + title + subtitle) and the action rows. */
data class SheetRequest(
    val title: String,
    val subtitle: String? = null,
    val artwork: Uri? = null,
    val actions: List<SheetAction>,
    /** Bounds of the button that opened the menu (window coordinates); filled in by [SheetHost.show]. */
    val anchor: androidx.compose.ui.geometry.Rect? = null,
)

/**
 * The single place that owns the "more options" menu (F-148: an anchored dropdown next to the
 * button, replacing the F-109 bottom sheet). Any screen asks for it through [show]; [ActionSheetLayer]
 * (mounted once, at the root of the app) draws it.
 */
@Stable
class SheetHost {
    var request by mutableStateOf<SheetRequest?>(null)
        private set

    fun show(request: SheetRequest) {
        this.request = if (request.anchor != null) request else request.copy(anchor = MenuAnchor.fresh())
    }

    fun hide() {
        request = null
    }
}

val LocalSheetHost = staticCompositionLocalOf { SheetHost() }

/** The app's actions and download state, so a song row needs no extra parameters to offer "Download". */
val LocalActions = staticCompositionLocalOf<MusicActions?> { null }
val LocalDownloads = staticCompositionLocalOf { DownloadsUi() }

/** F-206: the update check state, for the About page. */
val LocalUpdate = staticCompositionLocalOf { com.dopavin.data.UpdateUi() }

/** F-217: what the Accounts page shows (no secrets). */
val LocalAccounts = staticCompositionLocalOf { com.dopavin.data.AccountsUi() }

/**
 * Bridge for call sites that keep a local "open" flag: when [expanded] turns true the sheet is shown
 * on the shared host and [onDismiss] resets the caller's flag at once (the host owns visibility).
 */
@Composable
fun ShowSheetEffect(
    expanded: Boolean,
    onDismiss: () -> Unit,
    request: SheetRequest,
) {
    val host = LocalSheetHost.current
    LaunchedEffect(expanded) {
        if (expanded) {
            host.show(request)
            onDismiss()
        }
    }
}

/** Draws the current [SheetHost] request as the anchored dropdown next to the button that opened it. */
@Composable
fun ActionSheetLayer(host: SheetHost) {
    val request = host.request ?: return
    val type = AppTheme.type
    AnchoredMenu(anchor = request.anchor, onDismiss = host::hide) {
        val textColor = LocalMenuInk.current
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (request.artwork != null) {
                ArtworkImage(
                    uri = request.artwork,
                    sizePx = ArtworkSize.Row,
                    shape = RoundedCornerShape(Dimens.radiusSmall),
                    modifier = Modifier.size(40.dp),
                    seed = request.title,
                )
                Spacer(Modifier.width(Dimens.md))
            }
            Column(Modifier.weight(1f)) {
                AppText(request.title, type.bodyStrong, color = textColor)
                if (request.subtitle != null) {
                    AppText(request.subtitle, type.caption, color = textColor.copy(alpha = 0.66f))
                }
            }
        }
        request.actions.forEach { action ->
            AnchoredMenuItem(
                label = action.label,
                icon = action.icon,
                destructive = action.destructive,
                onClick = {
                    // Close first, then act, so the action's own UI (snackbar, new screen) is never hidden by the menu.
                    host.hide()
                    action.onClick()
                },
            )
        }
    }
}
