package com.dopavin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.Song
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.data.ArtworkSize
import com.dopavin.ui.components.GradientButton
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

private data class InfoPage(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int)

/** The three "what Dopavin does" pages that follow the welcome page. */
private val InfoPages = listOf(
    InfoPage(R.drawable.ic_search, R.string.onb_search_title, R.string.onb_search_body),
    InfoPage(R.drawable.ic_download, R.string.onb_download_title, R.string.onb_download_body),
    InfoPage(R.drawable.ic_lyrics, R.string.onb_lyrics_title, R.string.onb_lyrics_body),
)

/**
 * First-run onboarding, shown once to a new listener: a welcome, three short pages about what the app does,
 * and (when the local charts have loaded) "pick a few artists" so Home starts personal.
 *
 * It never asks for a country or a language: those are read from the phone. Every page can be skipped.
 * [onFinish] gets the picked artist names; an empty list means the artist page was skipped or not offered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(artists: List<Song>, onFinish: (List<String>) -> Unit) {
    val colors = AppTheme.colors
    // Pages: 0 welcome, 1..3 what the app does, 4 artists (only when there are artists to offer).
    val lastPage = if (artists.isNotEmpty()) InfoPages.size + 1 else InfoPages.size
    var page by rememberSaveable { mutableIntStateOf(0) }
    val current = page.coerceIn(0, lastPage)
    var picked by remember { mutableStateOf(setOf<String>()) }

    // Back goes one page back; on the first page it is left to the system.
    BackHandler(enabled = current > 0) { page = current - 1 }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            // This layer sits on top of the whole app: it must take every touch, or taps would reach Home beneath it.
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .padding(horizontal = Dimens.screenEdge),
    ) {
        // Skip is always one tap away, except on the last page where the main button already finishes.
        Row(Modifier.fillMaxWidth().height(Dimens.touchTarget), horizontalArrangement = Arrangement.End) {
            if (current < lastPage) {
                Box(
                    Modifier
                        .height(Dimens.touchTarget)
                        .clickableScale(pressedScale = 0.97f) { onFinish(emptyList()) }
                        .padding(horizontal = Dimens.md),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(stringResource(R.string.onb_skip), AppTheme.type.bodyStrong, color = colors.textSecondary)
                }
            }
        }

        Crossfade(
            targetState = current,
            animationSpec = tween(AppTheme.motion.medium),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "onboarding page",
        ) { shown ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when {
                    shown == 0 -> WelcomePage()
                    shown <= InfoPages.size -> InfoPageContent(InfoPages[shown - 1])
                    else -> ArtistsPage(artists, picked) { name ->
                        picked = if (name in picked) picked - name else picked + name
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimens.md))
        Dots(count = lastPage + 1, current = current)
        Spacer(Modifier.height(Dimens.lg))
        val buttonLabel = when {
            current == lastPage && artists.isNotEmpty() ->
                stringResource(if (picked.isEmpty()) R.string.welcome_skip else R.string.welcome_done)
            current == lastPage -> stringResource(R.string.onb_start)
            current == 0 -> stringResource(R.string.onb_get_started)
            else -> stringResource(R.string.onb_next)
        }
        GradientButton(
            text = buttonLabel,
            onClick = { if (current < lastPage) page = current + 1 else onFinish(picked.toList()) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.xl))
    }
}

@Composable
private fun WelcomePage() {
    val colors = AppTheme.colors
    AppLogo(120.dp)
    Spacer(Modifier.height(Dimens.xl))
    AppText(stringResource(R.string.onb_welcome_title), AppTheme.type.display, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Dimens.sm))
    AppText(
        stringResource(R.string.onb_welcome_body),
        AppTheme.type.body,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = Dimens.lg),
    )
}

@Composable
private fun InfoPageContent(page: InfoPage) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(page.icon),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(52.dp),
        )
    }
    Spacer(Modifier.height(Dimens.xl))
    AppText(stringResource(page.title), AppTheme.type.headline, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Dimens.sm))
    AppText(
        stringResource(page.body),
        AppTheme.type.body,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = Dimens.lg),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistsPage(artists: List<Song>, picked: Set<String>, onToggle: (String) -> Unit) {
    val colors = AppTheme.colors
    AppText(stringResource(R.string.welcome_title), AppTheme.type.headline, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Dimens.xs))
    AppText(
        stringResource(R.string.welcome_body),
        AppTheme.type.bodySmall,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = Dimens.lg),
    )
    Spacer(Modifier.height(Dimens.lg))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.md, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
        maxItemsInEachRow = 3,
    ) {
        artists.forEach { song ->
            ArtistChoice(song, selected = song.artist in picked) { onToggle(song.artist) }
        }
    }
}

@Composable
private fun ArtistChoice(song: Song, selected: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    val ring = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.3f)
    Column(
        Modifier
            .width(92.dp)
            .clickableScale(pressedScale = 0.95f, onClick = onToggle)
            .semantics(mergeDescendants = true) { contentDescription = song.artist },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ArtworkImage(
                uri = song.artworkUri,
                sizePx = ArtworkSize.Cell,
                shape = CircleShape,
                modifier = Modifier
                    .size(84.dp)
                    .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape),
                seed = song.artist,
            )
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(Dimens.xs))
        AppText(
            song.artist,
            AppTheme.type.caption,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Page dots; the group is announced once ("Page 2 of 5") instead of dot by dot. */
@Composable
private fun Dots(count: Int, current: Int) {
    val colors = AppTheme.colors
    val label = stringResource(R.string.onb_page_of, current + 1, count)
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (index == current) colors.accent else colors.textSecondary.copy(alpha = 0.3f)),
            )
        }
    }
}
