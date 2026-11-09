package com.dopavin.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.CategoryKind
import com.dopavin.data.HomeCategorySpec
import com.dopavin.data.Song
import com.dopavin.data.Visible
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.HomeArtistShelf
import com.dopavin.ui.HomeUi
import com.dopavin.ui.Destination
import com.dopavin.data.Mood
import com.dopavin.ui.MusicActions
import com.dopavin.ui.ViewState
import com.dopavin.ui.components.LocalBottomInset
import com.dopavin.ui.glass.glassSurface
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.ui.components.CategoryArt
import com.dopavin.ui.components.CategoryArtKind
import com.dopavin.ui.components.EmptyState
import com.dopavin.ui.components.EqualizerBars
import com.dopavin.ui.components.GradientButton
import com.dopavin.ui.components.GlassIconPill
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.RADIO_QUERY_PREFIX
import com.dopavin.ui.components.SectionHeader
import com.dopavin.ui.components.SongActionsMenu
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.components.focusRing
import com.dopavin.ui.components.greetingText
import com.dopavin.ui.components.rememberArtworkTint
import com.dopavin.ui.components.songCountText
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/** S-02, F-97. Home is the online scene: greeting + hero, mood chips (they work offline),
 * category tiles, the top played artists as YouTube shelves, "Because you played X", and a
 * history shelf (the play log, newest first) that stays under the YouTube toggle. */
@Composable
fun HomeScreen(
    view: ViewState,
    visible: Visible,
    playback: PlaybackUi,
    currentSong: Song?,
    home: HomeUi,
    liked: Set<Long>,
    actions: MusicActions,
) {
    if (visible.totalSongs == 0 && !home.active) {
        EmptyState(
            title = stringResource(R.string.dl_empty_title),
            body = stringResource(R.string.dl_empty_body),
            actionLabel = stringResource(R.string.dl_empty_action),
            onAction = { actions.setDestination(Destination.Search) },
        )
    } else {
        HomeContent(visible, playback, currentSong, home, liked, actions)
    }
}

private const val RECENT_SHELF_SIZE = 12

/** The six seed categories (F-87): a label for the UI, a YouTube Music query for the back end. */
/** The art that goes with a tile; every language without its own scene shares the [CategoryArtKind.Regional] one. */
private fun categoryArt(kind: CategoryKind): CategoryArtKind = when (kind) {
    CategoryKind.Charts -> CategoryArtKind.Charts
    CategoryKind.Punjabi -> CategoryArtKind.Punjabi
    CategoryKind.Bollywood -> CategoryArtKind.Bollywood
    CategoryKind.Romantic -> CategoryArtKind.Romantic
    CategoryKind.Lofi -> CategoryArtKind.Lofi
    CategoryKind.English -> CategoryArtKind.English
    CategoryKind.Regional -> CategoryArtKind.Regional
}

/** A tile's name; a regional one is named in the phone's own language ("Tamil hits"). */
@Composable
private fun categoryLabel(spec: HomeCategorySpec): String = when (spec.kind) {
    CategoryKind.Charts -> stringResource(R.string.cat_charts)
    CategoryKind.Punjabi -> stringResource(R.string.cat_punjabi)
    CategoryKind.Bollywood -> stringResource(R.string.cat_bollywood)
    CategoryKind.Romantic -> stringResource(R.string.cat_romantic)
    CategoryKind.Lofi -> stringResource(R.string.cat_lofi)
    CategoryKind.English -> stringResource(R.string.cat_english)
    CategoryKind.Regional -> stringResource(R.string.cat_regional, languageName(spec.languageCode))
}

private fun languageName(code: String?): String {
    val tag = code.orEmpty()
    val name = java.util.Locale.forLanguageTag(tag).getDisplayLanguage(java.util.Locale.getDefault())
    return name.replaceFirstChar { it.uppercase() }.ifBlank { tag }
}

private const val QUICK_PICKS = 8
private const val TRENDING_COLUMNS = 3
private const val TRENDING_PER_COLUMN = 5
private const val TRENDING_SHOWN = TRENDING_COLUMNS * TRENDING_PER_COLUMN
private const val SUBS_SHELF_SIZE = 12
private val BIG_CARD_WIDTH = 280.dp

/** Height of one ranked trending row; five of them make a column. */
private val TRENDING_ROW_HEIGHT = 68.dp

/** Recent plays first, topped up with the newest downloads; never the same song twice. */
private fun quickPicks(history: List<Song>, downloaded: List<Song>): List<Song> {
    val out = ArrayList<Song>(QUICK_PICKS)
    for (song in history + downloaded.sortedByDescending { it.dateAdded }) {
        if (out.size >= QUICK_PICKS) break
        if (out.none { it.id == song.id }) out += song
    }
    return out
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    visible: Visible,
    playback: PlaybackUi,
    currentSong: Song?,
    home: HomeUi,
    liked: Set<Long>,
    actions: MusicActions,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val quick = remember(home.history, visible.songs) { quickPicks(home.history, visible.songs) }
    // Lag fix: this sort used to run inside the LazyColumn content lambda, i.e. on the main thread each
    // time Home recomposed (every play / pause / song change). It only depends on the library.
    val recentDownloads = remember(visible.songs) {
        visible.songs.sortedByDescending { it.dateAdded }.take(RECENT_SHELF_SIZE)
    }
    val currentId = playback.mediaId
    val radioSeeds = remember(home.topArtists, home.history, home.trending) {
        radioSeeds(home.topArtists, home.history, home.trending)
    }

    var refreshing by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(refreshing) {
        if (refreshing) {
            kotlinx.coroutines.delay(1200)
            refreshing = false
        }
    }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            actions.refreshHome()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
        ) {
            item(key = "header") {
                HomeHeader(
                    subtitle = if (visible.totalSongs > 0) {
                        stringResource(R.string.home_subtitle, songCountText(visible.totalSongs))
                    } else {
                        stringResource(R.string.home_subtitle_empty)
                    },
                    onSearch = { actions.setDestination(Destination.Search) },
                    onSettings = actions::openSettings,
                )
            }

            // 1. The one big thing: continue what is playing (or shuffle the library).
            item(key = "hero") {
                HeroCard(
                    playback = playback,
                    song = currentSong,
                    totalSongs = visible.totalSongs,
                    onShuffleAll = {
                        if (visible.totalSongs > 0) {
                            actions.playCollection(visible.songs, true)
                        } else {
                            actions.setDestination(Destination.Search)
                        }
                    },
                    onOpen = actions::openNowPlaying,
                    onToggle = actions::togglePlayPause,
                    modifier = Modifier
                        .padding(horizontal = Dimens.screenEdge)
                        .widthIn(max = 560.dp),
                )
            }

            // 2. Round shortcuts into places that already exist.
            item(key = "shortcuts") {
                ShortcutRow(
                    likedCount = liked.size,
                    hasDownloads = visible.totalSongs > 0,
                    actions = actions,
                )
            }

            // 3. Jump back in: poster cards (recent plays topped up with newest downloads).
            if (quick.isNotEmpty()) {
                item(key = "jump_h") {
                    SectionHeader(
                        title = stringResource(R.string.home_continue),
                        modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                    )
                }
                item(key = "jump") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                    ) {
                        itemsIndexed(quick, key = { _, song -> "jump_${song.id}" }) { index, song ->
                            PosterCard(
                                song = song,
                                list = quick,
                                index = index,
                                current = song.id.toString() == currentId,
                                playing = playback.isPlaying,
                                liked = liked,
                                actions = actions,
                            )
                        }
                    }
                }
            }

            // 4. Moods in two rows.
            item(key = "moods_h") {
                SectionHeader(
                    title = stringResource(R.string.home_moods),
                    modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                )
            }
            item(key = "moods") { MoodGrid(actions) }

            // 5. Top artists.
            if (home.topArtists.isNotEmpty()) {
                item(key = "top_artists_h") {
                    SectionHeader(
                        title = stringResource(R.string.home_top_artists),
                        modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                    )
                }
                item(key = "top_artists") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
                    ) {
                        items(home.topArtists, key = { it.artist.lowercase() }) { song ->
                            TopArtistBubble(song, home.avatars[song.artist.trim().lowercase()]) { actions.openArtistOf(song) }
                        }
                    }
                }
            }

            // 5b. Radios: YouTube's own radio (mix) of songs you listen to; opens as a full song list.
            if (home.active && radioSeeds.isNotEmpty()) {
                item(key = "radios_h") {
                    SectionHeader(
                        title = stringResource(R.string.home_radios),
                        modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                    )
                }
                item(key = "radios") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
                    ) {
                        items(radioSeeds, key = { "radio_${it.id}" }) { seed ->
                            RadioCard(seed) {
                                actions.openOnlineList(
                                    context.getString(R.string.radio_card_title, seed.artist.ifBlank { seed.title }),
                                    RADIO_QUERY_PREFIX + seed.videoId,
                                )
                            }
                        }
                    }
                }
            }

            // 6. Explore: the existing YouTube feed (subscriptions, trending, related, artist shelves).
            if (home.subFeed.isNotEmpty()) {
                songShelf(
                    key = "subs",
                    title = context.getString(R.string.home_subscriptions),
                    songs = home.subFeed.take(SUBS_SHELF_SIZE),
                    currentId = currentId,
                    playing = playback.isPlaying,
                    liked = liked,
                    actions = actions,
                    big = true,
                    seeAll = context.getString(R.string.subs_manage),
                    onSeeAll = actions::openSubscriptions,
                )
            } else if (home.active && home.subCount == 0) {
                item(key = "subs_tip") {
                    SubscriptionsTip(onClick = actions::openSubscriptions)
                }
            }

            if (home.active && home.loading && home.trending.isEmpty()) {
                item(key = "skeleton_h") {
                    SectionHeader(
                        title = stringResource(R.string.home_trending),
                        modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.xs),
                    )
                }
                item(key = "skeleton_row") { ShelfSkeleton() }
            }
            if (home.trending.isNotEmpty()) {
                item(key = "trending_h") {
                    SectionHeader(
                        title = stringResource(R.string.home_trending),
                        actionLabel = stringResource(R.string.see_all),
                        onAction = { actions.openOnlineList(context.getString(R.string.home_trending), "top songs this week") },
                        modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.xs),
                    )
                }
                item(key = "trending_columns") {
                    TrendingColumns(
                        songs = home.trending.take(TRENDING_SHOWN),
                        currentId = currentId,
                        playing = playback.isPlaying,
                        liked = liked,
                        actions = actions,
                    )
                }
            }
            if (home.relatedSongs.isNotEmpty()) {
                songShelf(
                    key = "related",
                    title = context.getString(R.string.home_related, home.relatedTitle),
                    songs = home.relatedSongs,
                    currentId = currentId,
                    playing = playback.isPlaying,
                    liked = liked,
                    actions = actions,
                )
            }
            if (visible.totalSongs > 0) {
                songShelf(
                    key = "downloaded",
                    title = context.getString(R.string.home_downloaded),
                    songs = recentDownloads,
                    currentId = currentId,
                    playing = playback.isPlaying,
                    liked = liked,
                    actions = actions,
                    seeAll = context.getString(R.string.see_all),
                    onSeeAll = { actions.setDestination(Destination.Library) },
                )
            }
            home.artists.forEachIndexed { artistIndex, shelf ->
                songShelf(
                    key = "artist$artistIndex",
                    title = context.getString(R.string.home_artist_shelf, shelf.name),
                    songs = shelf.songs,
                    currentId = currentId,
                    playing = playback.isPlaying,
                    liked = liked,
                    actions = actions,
                    seeAll = context.getString(R.string.see_all),
                    onSeeAll = { actions.openOnlineList(shelf.name, shelf.name) },
                )
            }

            if (home.active) {
                item(key = "categories_h") {
                    SectionHeader(
                        title = stringResource(R.string.home_online_categories),
                        modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                    )
                }
                item(key = "categories") { GenreGrid(home.categories, actions) }
            }

            // F-97: the play log as a shelf. Newest first; stays when the online toggle is off.
            songShelf(
                key = "history",
                title = context.getString(R.string.home_history),
                songs = home.history,
                currentId = currentId,
                playing = playback.isPlaying,
                liked = liked,
                actions = actions,
            )
        }
    }
}

/** One horizontal shelf: a header (with optional "See all") and a row of song cards that play on tap. */
private fun LazyListScope.songShelf(
    key: String,
    title: String,
    songs: List<Song>,
    currentId: String?,
    playing: Boolean,
    liked: Set<Long>,
    actions: MusicActions,
    seeAll: String? = null,
    onSeeAll: (() -> Unit)? = null,
    /** Big wide cards (Subscriptions, Trending) instead of the small square ones. */
    big: Boolean = false,
) {
    if (songs.isEmpty()) return
    item(key = "${key}_header") {
        SectionHeader(
            title = title,
            actionLabel = seeAll,
            onAction = onSeeAll,
            modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.xs),
        )
    }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
            horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
        ) {
            itemsIndexed(songs, key = { _, song -> "${key}_${song.id}" }) { index, song ->
                if (big) {
                    BigShelfCard(
                        song = song,
                        list = songs,
                        index = index,
                        current = song.id.toString() == currentId,
                        playing = playing,
                        liked = liked,
                        actions = actions,
                    )
                } else SongShelfCard(
                    song = song,
                    list = songs,
                    index = index,
                    current = song.id.toString() == currentId,
                    playing = playing,
                    onClick = { actions.playSongs(songs, index) },
                    liked = liked,
                    actions = actions,
                )
            }
        }
    }
}

/** Up to six online songs, one per artist, to build radios from: most played artists, then recent plays, then trending. */
private fun radioSeeds(top: List<Song>, history: List<Song>, trending: List<Song>): List<Song> {
    val seen = HashSet<String>()
    val out = ArrayList<Song>(RADIO_SEEDS)
    for (song in top.asSequence() + history.asSequence() + trending.asSequence()) {
        if (out.size >= RADIO_SEEDS) break
        if (!song.isOnline || song.videoId.isNullOrBlank()) continue
        val who = song.artist.trim().lowercase().ifBlank { song.id.toString() }
        if (seen.add(who)) out += song
    }
    return out
}

private const val RADIO_SEEDS = 6

/** One radio: the seed song's thumbnail with a radio badge, "<Artist> Radio" and where it starts. */
@Composable
private fun RadioCard(seed: Song, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusLarge)
    val name = stringResource(R.string.radio_card_title, seed.artist.ifBlank { seed.title })
    val description = stringResource(R.string.cd_radio_card, name, seed.title)
    Column(
        Modifier
            .width(Dimens.artworkCard)
            .clickableScale(pressedScale = 0.97f, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(
            Modifier
                .size(Dimens.artworkCard)
                .shadow(8.dp, shape)
                .clip(shape)
                .focusRing(shape),
        ) {
            ArtworkImage(
                uri = seed.artworkUri,
                sizePx = ArtworkSize.Row,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
                seed = seed.artist,
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Dimens.sm)
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_audio_lines),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(Dimens.sm))
        AppText(name, AppTheme.type.label, color = colors.textPrimary)
        AppText(stringResource(R.string.radio_card_subtitle, seed.title), AppTheme.type.caption, color = colors.textSecondary)
    }
}

/** Grey placeholder cards shown while the very first shelf loads (no cache yet). */
@Composable
private fun ShelfSkeleton() {
    val colors = AppTheme.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
        userScrollEnabled = false,
    ) {
        items(4) {
            Column(Modifier.width(Dimens.artworkCard)) {
                Box(
                    Modifier
                        .size(Dimens.artworkCard)
                        .clip(RoundedCornerShape(Dimens.radiusLarge))
                        .background(colors.surfaceRaised),
                )
                Spacer(Modifier.height(Dimens.sm))
                Box(
                    Modifier
                        .width(110.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceRaised),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(70.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.surfaceRaised),
                )
            }
        }
    }
}

/**
 * The Home hero. While something is loaded in the player it is a full-bleed artwork card (title and a
 * play pill laid over a dark gradient) that opens the player; otherwise a brand-gradient card that
 * offers "shuffle all" (or "discover" when nothing is saved yet).
 */
@Composable
private fun HeroCard(
    playback: PlaybackUi,
    song: Song?,
    totalSongs: Int,
    onShuffleAll: () -> Unit,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val shape = RoundedCornerShape(Dimens.radiusXLarge)
    val continuing = playback.hasItem && song != null
    val discover = !continuing && totalSongs == 0
    val label = stringResource(
        when {
            continuing -> R.string.hero_continue
            discover -> R.string.hero_discover
            else -> R.string.hero_library
        },
    )
    val title = when {
        continuing -> playback.title
        discover -> stringResource(R.string.hero_discover_title)
        else -> stringResource(R.string.hero_shuffle_title)
    }
    val subtitle = when {
        continuing -> playback.artist
        discover -> stringResource(R.string.hero_discover_body)
        else -> stringResource(R.string.home_subtitle, songCountText(totalSongs))
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (continuing) 1.05f else 1.9f)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(colors.gradient)
            .focusRing(shape)
            .clickableScale(pressedScale = 0.98f, onClick = if (continuing) onOpen else onShuffleAll)
            .semantics(mergeDescendants = true) { contentDescription = "$label. $title. $subtitle" },
    ) {
        if (continuing) {
            ArtworkImage(
                uri = song?.artworkUri,
                sizePx = ArtworkSize.Large,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
                seed = song?.artist ?: "",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.28f),
                            0.4f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
            )
        } else {
            Icon(
                painter = painterResource(if (discover) R.drawable.ic_search else R.drawable.ic_shuffle),
                contentDescription = null,
                tint = colors.onAccent.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Dimens.lg)
                    .size(120.dp),
            )
        }
        val onCard = if (continuing) Color.White else colors.onAccent
        val onCardSecondary = onCard.copy(alpha = 0.8f)
        AppText(
            text = label.uppercase(),
            style = type.caption,
            color = onCard,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Dimens.lg)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.Black.copy(alpha = if (continuing) 0.35f else 0.14f))
                .padding(horizontal = Dimens.md, vertical = Dimens.xs),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(Dimens.xl),
        ) {
            AppText(title, type.headline, color = onCard, maxLines = 2)
            AppText(subtitle, type.body, color = onCardSecondary)
            Spacer(Modifier.height(Dimens.lg))
            if (continuing) {
                HeroPlayPill(playing = playback.isPlaying, onClick = onToggle)
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.onAccent)
                        .clickableScale(onClick = onShuffleAll)
                        .padding(horizontal = Dimens.xl, vertical = Dimens.md),
                ) {
                    AppText(
                        stringResource(if (discover) R.string.hero_discover_button else R.string.hero_shuffle_button),
                        type.label,
                        color = colors.accentStart,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPlayPill(playing: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val label = stringResource(if (playing) R.string.cd_pause else R.string.cd_play)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.textPrimary)
            .clickableScale(onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playing) {
            EqualizerBars(playing = true, color = colors.canvas, size = 20.dp)
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = colors.canvas,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(Dimens.sm))
        AppText(label, AppTheme.type.label, color = colors.canvas)
    }
}

/** F-98: a Home shelf card with the shared song-action menu. Long-press opens it; the tap
 * still plays the card. Wraps [OnlineSongCard] visually unchanged. */
@Composable
private fun SongShelfCard(
    song: Song,
    list: List<Song>,
    index: Int,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    liked: Set<Long>,
    actions: MusicActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        OnlineSongCard(
            song = song,
            current = current,
            playing = playing,
            onClick = onClick,
            onLongPress = { menuOpen = true },
            onMore = { menuOpen = true },
        )
        SongActionsMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            song = song,
            onPlayNow = { actions.playSongs(list, index) },
            onPlayNext = { actions.playNext(song) },
            onAddToQueue = { actions.addToQueue(song) },
            onRadio = { actions.openRadio(song) },
            favorite = song.id in liked,
            onToggleFavorite = { actions.toggleLike(song.id) },
            onOpenDetails = { actions.openSongDetails(song) },
        )
    }
}

/** One online song inside a Home shelf. Mirrors the AlbumCard look with YouTube's thumbnail. */
@Composable
private fun OnlineSongCard(
    song: Song,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusLarge)
    Column(
        Modifier
            .width(Dimens.artworkCard)
            .clickableScale(pressedScale = 0.97f, onLongClick = onLongPress, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(Dimens.artworkCard)
                .shadow(8.dp, shape)
                .clip(shape)
                .focusRing(shape),
        ) {
            ArtworkImage(
                uri = song.artworkUri,
                sizePx = ArtworkSize.Row,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
                seed = song.artist,
            )
            if (current) {
                EqualizerBars(
                    playing = playing,
                    color = Color.White,
                    size = 20.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Dimens.sm),
                )
            }
        }
        Spacer(Modifier.height(Dimens.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AppText(song.title, AppTheme.type.label, color = if (current) colors.accent else colors.textPrimary)
                AppText(song.artist, AppTheme.type.caption, color = colors.textSecondary)
            }
            if (onMore != null) {
                // F-109: a visible, 48dp "more" target; long-press still works as a shortcut.
                IconAction(
                    icon = R.drawable.ic_more,
                    description = stringResource(R.string.cd_more_options, song.title),
                    onClick = onMore,
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

/** F-130: a big feed card with the shared song-action menu on long-press. */
@Composable
private fun BigShelfCard(
    song: Song,
    list: List<Song>,
    index: Int,
    current: Boolean,
    playing: Boolean,
    liked: Set<Long>,
    actions: MusicActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.width(BIG_CARD_WIDTH)) {
        com.dopavin.ui.components.FeedBigCard(
            song = song,
            current = current,
            playing = playing,
            onClick = { actions.playSongs(list, index) },
            onLongPress = { menuOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
        SongActionsMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            song = song,
            onPlayNow = { actions.playSongs(list, index) },
            onPlayNext = { actions.playNext(song) },
            onAddToQueue = { actions.addToQueue(song) },
            onRadio = { actions.openRadio(song) },
            favorite = song.id in liked,
            onToggleFavorite = { actions.toggleLike(song.id) },
            onOpenDetails = { actions.openSongDetails(song) },
        )
    }
}

/** Shown while nothing is subscribed: one quiet card that explains how and opens Subscriptions. */
@Composable
private fun SubscriptionsTip(onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusLarge)
    Row(
        Modifier
            .padding(horizontal = Dimens.screenEdge)
            .padding(top = Dimens.xl)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised)
            .clickableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(Dimens.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_person),
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(Dimens.iconControl),
        )
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            AppText(stringResource(R.string.home_subs_tip_title), AppTheme.type.bodyStrong)
            AppText(
                stringResource(R.string.home_subs_tip_body),
                AppTheme.type.bodySmall,
                color = colors.textSecondary,
                maxLines = 3,
            )
        }
    }
}

/** Round artist avatar (artwork of their newest play) with the name underneath. */
@Composable
private fun TopArtistBubble(song: Song, avatar: String?, onClick: () -> Unit) {
    val label = stringResource(R.string.cd_open_artist, song.artist)
    Column(
        Modifier
            .width(92.dp)
            .clickableScale(pressedScale = 0.95f, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            uri = avatar?.let(android.net.Uri::parse) ?: song.artworkUri,
            sizePx = ArtworkSize.Cell,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier
                .size(88.dp)
                .border(2.dp, AppTheme.colors.accent.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
                .padding(4.dp)
                .clip(androidx.compose.foundation.shape.CircleShape),
            seed = song.artist,
        )
        Spacer(Modifier.height(Dimens.xs))
        AppText(
            song.artist,
            AppTheme.type.caption,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


/** The real app logo: the launcher's mipmap background + foreground, rounded; nothing redrawn. */
@Composable
internal fun AppLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f)),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.mipmap.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        // The adaptive foreground has a safe-zone margin; scale it up so the mark fills the tile.
        androidx.compose.foundation.Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                },
        )
    }
}

/** Logo, greeting + library line, and two glass buttons (search, settings). */
@Composable
private fun HomeHeader(subtitle: String, onSearch: () -> Unit, onSettings: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .statusBarsPadding()
            .padding(start = Dimens.screenEdge, end = Dimens.sm, top = Dimens.md, bottom = Dimens.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(44.dp)
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            AppText(
                text = greetingText(),
                style = AppTheme.type.title,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
            AppText(text = subtitle, style = AppTheme.type.bodySmall, color = colors.textSecondary, maxLines = 1)
        }
        // Search and settings share one pill (glass, or a solid pill when glass is off).
        GlassIconPill(fallback = colors.surfaceRaised) {
            IconAction(
                icon = R.drawable.ic_search,
                description = stringResource(R.string.cd_search),
                onClick = onSearch,
            )
            IconAction(
                icon = R.drawable.ic_settings,
                description = stringResource(R.string.cd_settings),
                onClick = onSettings,
            )
        }
    }
}

private class Shortcut(val label: Int, val icon: Int, val onClick: () -> Unit)

/** Round shortcuts (icon disc + label) into screens that already exist. */
@Composable
private fun ShortcutRow(likedCount: Int, hasDownloads: Boolean, actions: MusicActions) {
    val colors = AppTheme.colors
    val shortcuts = buildList {
        add(
            Shortcut(R.string.tab_liked, R.drawable.ic_favorite, actions::openFavourites),
        )
        add(Shortcut(R.string.personal_mix_title, R.drawable.ic_shuffle, actions::openPersonalMix))
        add(Shortcut(R.string.lib_playlists, R.drawable.ic_queue, actions::openPlaylists))
        if (hasDownloads) {
            add(Shortcut(R.string.lib_downloads, R.drawable.ic_download, actions::openDownloads))
        }
        add(Shortcut(R.string.home_history, R.drawable.ic_history, actions::openHistory))
        add(Shortcut(R.string.lib_subscriptions, R.drawable.ic_person, actions::openSubscriptions))
    }
    Row(
        Modifier
            .padding(horizontal = Dimens.sm)
            .padding(top = Dimens.xl)
            .fillMaxWidth(),
    ) {
        shortcuts.forEach { item ->
            val label = stringResource(item.label)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.radiusMedium))
                    .clickableScale(pressedScale = 0.95f, role = androidx.compose.ui.semantics.Role.Button, onClick = item.onClick)
                    .padding(vertical = Dimens.xs)
                    .semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .glassSurface(androidx.compose.foundation.shape.CircleShape, fallback = colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(Dimens.iconControl),
                    )
                }
                Spacer(Modifier.height(Dimens.xs))
                AppText(
                    label,
                    AppTheme.type.caption,
                    color = colors.textSecondary,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** A tall poster card: artwork fills the card, title over a gradient, small play / now-playing badge. */
@Composable
private fun PosterCard(
    song: Song,
    list: List<Song>,
    index: Int,
    current: Boolean,
    playing: Boolean,
    liked: Set<Long>,
    actions: MusicActions,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusXLarge)
    var menuOpen by remember { mutableStateOf(false) }
    val description = stringResource(R.string.quick_pick_description, song.title, song.artist)
    Box(
        Modifier
            .width(142.dp)
            .height(196.dp)
            .shadow(8.dp, shape)
            .clip(shape)
            .focusRing(shape)
            .clickableScale(
                pressedScale = 0.97f,
                onLongClick = { menuOpen = true },
                onClick = { actions.playSongs(list, index) },
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        ArtworkImage(
            uri = song.artworkUri,
            sizePx = ArtworkSize.Cell,
            shape = shape,
            modifier = Modifier.fillMaxSize(),
            seed = song.artist,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(Dimens.sm)
                .size(32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            if (current) {
                EqualizerBars(playing = playing, color = colors.accent, size = 16.dp)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(Dimens.md),
        ) {
            AppText(
                song.title,
                AppTheme.type.label,
                color = if (current) colors.accent else Color.White,
                maxLines = 2,
            )
            AppText(song.artist, AppTheme.type.caption, color = Color.White.copy(alpha = 0.78f))
        }
    }
    SongActionsMenu(
        expanded = menuOpen,
        onDismiss = { menuOpen = false },
        song = song,
        onPlayNow = { actions.playSongs(list, index) },
        onPlayNext = { actions.playNext(song) },
        onAddToQueue = { actions.addToQueue(song) },
        onRadio = { actions.openRadio(song) },
        favorite = song.id in liked,
        onToggleFavorite = { actions.toggleLike(song.id) },
        onOpenDetails = { actions.openSongDetails(song) },
    )
}

/** Moods in two horizontally scrolling rows of glass pills. */
@Composable
private fun MoodGrid(actions: MusicActions) {
    androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
        rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        modifier = Modifier.height(100.dp),
        contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        gridItems(Mood.entries, key = { it.name }) { mood ->
            val label = stringResource(moodLabelRes(mood))
            Box(
                Modifier
                    .heightIn(min = Dimens.touchTarget)
                    .clip(RoundedCornerShape(50))
                    .glassSurface(RoundedCornerShape(50), fallback = AppTheme.colors.surfaceRaised)
                    .clickableScale { actions.openMood(mood) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = label,
                    style = AppTheme.type.label,
                    modifier = Modifier.padding(horizontal = Dimens.xl),
                )
            }
        }
    }
}

/**
 * Trending as three horizontally swipeable columns of five ranked songs: 1-5, 6-10, 11-15.
 * Each column is one screen wide, so a swipe moves to the next five; a short last column stays
 * top-aligned.
 */
@Composable
private fun TrendingColumns(
    songs: List<Song>,
    currentId: String?,
    playing: Boolean,
    liked: Set<Long>,
    actions: MusicActions,
) {
    if (songs.isEmpty()) return
    val state = rememberLazyListState()
    val pages = (songs.size + TRENDING_PER_COLUMN - 1) / TRENDING_PER_COLUMN
    LazyRow(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TRENDING_ROW_HEIGHT * TRENDING_PER_COLUMN),
        flingBehavior = rememberSnapFlingBehavior(state),
    ) {
        items(count = pages, key = { it }) { page ->
            Column(
                Modifier
                    .fillParentMaxWidth()
                    .padding(start = Dimens.screenEdge),
            ) {
                val first = page * TRENDING_PER_COLUMN
                val last = minOf(first + TRENDING_PER_COLUMN, songs.size)
                for (index in first until last) {
                    val song = songs[index]
                    RankedSongRow(
                        rank = index + 1,
                        song = song,
                        list = songs,
                        index = index,
                        current = song.id.toString() == currentId,
                        playing = playing,
                        liked = liked,
                        actions = actions,
                    )
                }
            }
        }
    }
}

/** One row of the ranked trending list: big rank number, artwork, title / artist, more. */
@Composable
private fun RankedSongRow(
    rank: Int,
    song: Song,
    list: List<Song>,
    index: Int,
    current: Boolean,
    playing: Boolean,
    liked: Set<Long>,
    actions: MusicActions,
) {
    val colors = AppTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val description = stringResource(R.string.quick_pick_description, song.title, song.artist)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TRENDING_ROW_HEIGHT)
            .clickableScale(
                pressedScale = 0.99f,
                onLongClick = { menuOpen = true },
                onClick = { actions.playSongs(list, index) },
            )
            .padding(end = Dimens.xs, top = Dimens.xs, bottom = Dimens.xs)
            .semantics(mergeDescendants = true) { contentDescription = "$rank. $description" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = rank.toString(),
            style = AppTheme.type.headline,
            color = if (rank == 1) colors.accent else colors.textSecondary,
            modifier = Modifier.width(34.dp),
        )
        ArtworkImage(
            uri = song.artworkUri,
            sizePx = ArtworkSize.Row,
            shape = RoundedCornerShape(Dimens.radiusMedium),
            modifier = Modifier.size(56.dp),
            seed = song.artist,
        )
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            AppText(
                song.title,
                AppTheme.type.bodyStrong,
                color = if (current) colors.accent else colors.textPrimary,
            )
            AppText(song.artist, AppTheme.type.bodySmall, color = colors.textSecondary)
        }
        if (current) {
            EqualizerBars(playing = playing, color = colors.accent, size = 18.dp, modifier = Modifier.padding(end = Dimens.sm))
        }
        IconAction(
            icon = R.drawable.ic_more,
            description = stringResource(R.string.cd_more_options, song.title),
            onClick = { menuOpen = true },
            tint = colors.textSecondary,
        )
    }
    SongActionsMenu(
        expanded = menuOpen,
        onDismiss = { menuOpen = false },
        song = song,
        onPlayNow = { actions.playSongs(list, index) },
        onPlayNext = { actions.playNext(song) },
        onAddToQueue = { actions.addToQueue(song) },
        onRadio = { actions.openRadio(song) },
        favorite = song.id in liked,
        onToggleFavorite = { actions.toggleLike(song.id) },
        onOpenDetails = { actions.openSongDetails(song) },
    )
}

/** The category tiles (they follow the phone's country and languages and what is played) as a two-column grid; each opens the online list. */
@Composable
private fun GenreGrid(categories: List<HomeCategorySpec>, actions: MusicActions) {
    Column(
        Modifier.padding(horizontal = Dimens.screenEdge),
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        categories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                pair.forEach { category ->
                    val label = categoryLabel(category)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(76.dp)
                            .clip(RoundedCornerShape(Dimens.radiusLarge))
                            .clickableScale(pressedScale = 0.97f) { actions.openOnlineList(label, category.query) }
                            .semantics { contentDescription = label },
                    ) {
                        CategoryArt(
                            kind = categoryArt(category.kind),
                            modifier = Modifier.matchParentSize(),
                        )
                        AppText(
                            text = label,
                            style = AppTheme.type.bodyStrong,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(Dimens.md),
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
