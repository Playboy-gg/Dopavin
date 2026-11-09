package com.dopavin.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dopavin.R
import com.dopavin.YouTubeSignInActivity
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.GlassDialog
import com.dopavin.ui.components.LocalAccounts
import com.dopavin.ui.components.SettingsRow
import com.dopavin.ui.components.ToggleRow
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/**
 * F-217: "Accounts & services". Everything here is optional and off until the owner connects it:
 * ListenBrainz scrobbling, the owner's own Subsonic music server, and a YouTube Music sign-in.
 * [match] filters rows while Settings search is active.
 */
@Composable
fun AccountsPage(actions: MusicActions, match: (String) -> Boolean) {
    val acc = LocalAccounts.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Coming back from the sign-in screen: the session was stored there, so reload what this page shows.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) actions.refreshAccounts() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var lbDialog by remember { mutableStateOf(false) }
    var subDialog by remember { mutableStateOf(false) }
    var searchDialog by remember { mutableStateOf(false) }
    val full = match("")

    // ListenBrainz
    val lbConnect = stringResource(R.string.acc_lb_connect)
    val lbSend = stringResource(R.string.acc_lb_send)
    if (full || match(lbConnect) || match(lbSend)) Label(R.string.acc_lb_section)
    if (!acc.listenBrainzConnected) {
        if (match(lbConnect)) {
            SettingsRow(lbConnect, onClick = { lbDialog = true }, subtitle = stringResource(R.string.acc_lb_connect_hint))
        }
    } else {
        if (match(lbSend)) {
            val pending = if (acc.pendingListens > 0) "  ·  " + stringResource(R.string.acc_lb_pending, acc.pendingListens) else ""
            ToggleRow(
                title = lbSend,
                subtitle = stringResource(R.string.acc_lb_connected_as, acc.listenBrainzUser.ifBlank { "ListenBrainz" }) + pending,
                checked = acc.scrobbling,
                onCheckedChange = actions::setScrobbling,
            )
        }
        val off = stringResource(R.string.acc_disconnect)
        if (match(off)) SettingsRow(off, onClick = actions::disconnectListenBrainz, chevron = false)
    }

    // Subsonic
    val subConnect = stringResource(R.string.acc_sub_connect)
    val subRandom = stringResource(R.string.acc_sub_random)
    val subStarred = stringResource(R.string.acc_sub_starred)
    val subSearch = stringResource(R.string.acc_sub_search)
    if (full || match(subConnect) || match(subRandom) || match(subStarred) || match(subSearch)) Label(R.string.acc_sub_section)
    if (!acc.subsonicConfigured) {
        if (match(subConnect)) {
            SettingsRow(subConnect, onClick = { subDialog = true }, subtitle = stringResource(R.string.acc_sub_connect_hint))
        }
    } else {
        if (match(subRandom)) SettingsRow(subRandom, onClick = { actions.openSubsonic(subRandom, "random") })
        if (match(subStarred)) SettingsRow(subStarred, onClick = { actions.openSubsonic(subStarred, "starred") })
        if (match(subSearch)) SettingsRow(subSearch, onClick = { searchDialog = true })
        val off = stringResource(R.string.acc_sub_disconnect, acc.subsonicHost)
        if (full) SettingsRow(off, onClick = actions::disconnectSubsonic, chevron = false)
    }

    // YouTube Music
    val ytIn = stringResource(R.string.acc_yt_sign_in)
    val ytLikes = stringResource(R.string.acc_yt_likes)
    val ytImport = stringResource(R.string.acc_yt_import)
    if (full || match(ytIn) || match(ytLikes) || match(ytImport)) Label(R.string.acc_yt_section)
    if (!acc.youTubeSignedIn) {
        if (match(ytIn)) {
            SettingsRow(
                ytIn,
                onClick = { context.startActivity(Intent(context, YouTubeSignInActivity::class.java)) },
                subtitle = stringResource(R.string.acc_yt_sign_in_hint),
            )
        }
    } else {
        if (match(ytLikes)) SettingsRow(ytLikes, onClick = actions::openYouTubeLikes, subtitle = stringResource(R.string.acc_yt_signed_in))
        if (match(ytImport)) {
            SettingsRow(
                ytImport,
                onClick = { if (!acc.busy) actions.importYouTubePlaylists() },
                subtitle = stringResource(if (acc.busy) R.string.acc_working else R.string.acc_yt_import_hint),
                chevron = false,
            )
        }
        val out = stringResource(R.string.acc_yt_sign_out)
        if (match(out)) SettingsRow(out, onClick = actions::signOutYouTube, chevron = false)
    }

    if (full) {
        AppText(
            stringResource(R.string.acc_privacy),
            AppTheme.type.caption,
            color = AppTheme.colors.textSecondary,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
        )
    }

    if (lbDialog) {
        var token by remember { mutableStateOf("") }
        GlassDialog(
            title = lbConnect,
            confirmLabel = stringResource(R.string.acc_connect),
            confirmEnabled = token.isNotBlank(),
            onConfirm = { lbDialog = false; actions.connectListenBrainz(token) },
            onDismiss = { lbDialog = false },
        ) {
            DialogField(token, { token = it }, stringResource(R.string.acc_lb_token_hint))
        }
    }
    if (subDialog) {
        var url by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        GlassDialog(
            title = subConnect,
            confirmLabel = stringResource(R.string.acc_connect),
            confirmEnabled = url.isNotBlank() && user.isNotBlank() && pass.isNotEmpty(),
            onConfirm = { subDialog = false; actions.connectSubsonic(url, user, pass) },
            onDismiss = { subDialog = false },
        ) {
            DialogField(url, { url = it }, stringResource(R.string.acc_sub_url_hint), keyboard = KeyboardType.Uri)
            DialogField(user, { user = it }, stringResource(R.string.acc_sub_user_hint))
            DialogField(pass, { pass = it }, stringResource(R.string.acc_sub_pass_hint), secret = true)
        }
    }
    if (searchDialog) {
        var text by remember { mutableStateOf("") }
        GlassDialog(
            title = subSearch,
            confirmLabel = stringResource(R.string.acc_sub_search_go),
            confirmEnabled = text.isNotBlank(),
            onConfirm = { searchDialog = false; actions.openSubsonic(text.trim(), "s:" + text.trim()) },
            onDismiss = { searchDialog = false },
        ) {
            DialogField(text, { text = it }, stringResource(R.string.acc_sub_search_hint))
        }
    }
}

@Composable
private fun Label(res: Int) {
    AppText(
        stringResource(res),
        AppTheme.type.caption,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(start = Dimens.screenEdge, top = Dimens.lg, bottom = Dimens.xs),
    )
}

/** Pill text field in the style of the playlist-name dialog. */
@Composable
private fun DialogField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val colors = AppTheme.colors
    val ink = if (com.dopavin.ui.glass.LocalGlassStyle.current.enabled && com.dopavin.ui.glass.LocalGlassBackdrop.current != null) {
        if (colors.isDark) Color.White else Color.Black
    } else {
        colors.textPrimary
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(ink.copy(alpha = 0.10f))
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) AppText(hint, AppTheme.type.body, color = ink.copy(alpha = 0.55f))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = AppTheme.type.body.copy(color = ink),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(Dimens.sm))
}
