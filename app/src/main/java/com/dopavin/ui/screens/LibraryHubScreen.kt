package com.dopavin.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.LocalBottomInset
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/**
 * F-133. The Library tab: a short list of the places the user keeps things (playlists, subscriptions,
 * favourites, recently played, downloads). Downloads used to be the tab itself.
 */
@Composable
fun LibraryHubScreen(actions: MusicActions) {
    var importDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (importDialog) {
        SpotifyImportDialog(
            onImport = { link ->
                importDialog = false
                actions.importSpotify(link)
            },
            onDismiss = { importDialog = false },
        )
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Dimens.lg, start = Dimens.screenEdge, end = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.lib_title),
                style = AppTheme.type.display,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconAction(
                icon = R.drawable.ic_settings,
                description = stringResource(R.string.cd_settings),
                onClick = actions::openSettings,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Dimens.md, bottom = Dimens.xl + LocalBottomInset.current),
        ) {
            item { HubRow(R.drawable.ic_queue, R.string.lib_playlists, actions::openPlaylists) }
            item { HubRow(R.drawable.ic_person, R.string.lib_subscriptions, actions::openSubscriptions) }
            item { HubRow(R.drawable.ic_favorite, R.string.lib_favorites, actions::openFavourites) }
            item { HubRow(R.drawable.ic_library, R.string.lib_recent, actions::openHistory) }
            item { HubRow(R.drawable.ic_download, R.string.lib_downloads, actions::openDownloads) }
            item { HubRow(R.drawable.ic_audio_lines, R.string.lib_recap, actions::openRecap) }
            item { HubRow(R.drawable.ic_download, R.string.lib_spotify_import) { importDialog = true } }
        }
    }
}

@Composable
private fun HubRow(@DrawableRes icon: Int, title: Int, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textPrimary),
            modifier = Modifier.size(Dimens.iconControl),
        )
        Spacer(Modifier.size(Dimens.lg))
        AppText(
            text = stringResource(title),
            style = AppTheme.type.title,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textSecondary),
            modifier = Modifier.size(20.dp),
        )
    }
}
