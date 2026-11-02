package com.dopavin.ui
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dopavin.ui.glass.GlassAmbient
import com.dopavin.ui.glass.ambientBackground
import com.dopavin.ui.glass.floatingtabbar.rememberFloatingTabBarScrollConnection
import com.dopavin.ui.glass.nav.GlassComponent
import com.dopavin.ui.glass.nav.GlassEffectConfig
import com.dopavin.ui.glass.nav.LocalAppBackdrop
import com.dopavin.ui.glass.nav.LocalGlassEffectConfig
import com.dopavin.ui.glass.nav.isGlassAllowed
import com.dopavin.ui.glass.LocalGlassBackdrop
import com.dopavin.ui.glass.LocalGlassStyle
import com.dopavin.ui.glass.toGlassStyle
import com.dopavin.ui.glass.backdrop.backdrops.layerBackdrop
import com.dopavin.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import com.dopavin.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import com.dopavin.ui.components.LocalBottomInset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopavin.R
import com.dopavin.data.Album
import com.dopavin.data.AppSettings
import com.dopavin.data.Artist
import com.dopavin.data.Groups
import com.dopavin.data.LyricsUi
import com.dopavin.data.Playlist
import com.dopavin.data.Song
import com.dopavin.data.Visible
import com.dopavin.ui.MusicViewModel.StorageUi
import com.dopavin.playback.EffectsState
import com.dopavin.playback.PlaybackUi
import com.dopavin.playback.SleepState
import com.dopavin.ui.components.ActionSheetLayer
import com.dopavin.ui.components.BottomNav
import com.dopavin.ui.components.LocalActions
import com.dopavin.ui.components.LocalDownloads
import com.dopavin.ui.components.LocalSheetHost
import com.dopavin.ui.components.SheetHost
import com.dopavin.ui.components.MiniPlayer
import com.dopavin.ui.components.flightPlayerHost
import com.dopavin.ui.components.albumCountText
import com.dopavin.ui.components.formatTime
import com.dopavin.ui.components.songCountText
import com.dopavin.ui.screens.CollectionScreen
import com.dopavin.ui.screens.CollectionUi
import com.dopavin.ui.screens.HomeScreen
import com.dopavin.ui.screens.SubscriptionsScreen
import com.dopavin.ui.screens.LibraryScreen
import com.dopavin.ui.screens.OnlineListScreen
import com.dopavin.ui.screens.SearchScreen
import com.dopavin.ui.screens.SuggestionsScreen
import com.dopavin.ui.screens.moodLabelRes
import com.dopavin.ui.screens.SongDetailsDialog
import com.dopavin.ui.screens.StorageScreen
import com.dopavin.ui.screens.PlaylistsScreen
import com.dopavin.ui.screens.PlaylistDetailScreen
import com.dopavin.ui.screens.NowPlayingScreen
import com.dopavin.ui.screens.EqualizerScreen
import com.dopavin.ui.screens.QueueSheet
import com.dopavin.ui.screens.SettingsScreen
import com.dopavin.ui.screens.SubscriptionsScreen
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import kotlinx.coroutines.launch
@Composable
fun MusicApp(
    viewModel: MusicViewModel,
) {
    val view by viewModel.view.collectAsStateWithLifecycle()
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searchUi by viewModel.searchUi.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val homeOnline by viewModel.home.collectAsStateWithLifecycle()
    val onlineList by viewModel.onlineList.collectAsStateWithLifecycle()
    val subs by viewModel.subs.collectAsStateWithLifecycle()
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val effects by viewModel.effects.collectAsStateWithLifecycle()
    val sleep by viewModel.sleep.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val detailSong by viewModel.detailSong.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistSongs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val queueOrigins by viewModel.origins.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val motion = AppTheme.motion
    val context = LocalContext.current
    val toast = remember { com.dopavin.ui.components.ToastState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { message ->
            val text = context.getString(message.res, *message.args.toTypedArray())
            toast.show(
                com.dopavin.ui.components.ToastData(text, message.actionLabel, message.onAction),
                com.dopavin.ui.components.toastDurationMs(message.actionLabel != null),
            )
        }
    }
    val backProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var backDirection by remember { mutableStateOf(1f) }
    val backScope = androidx.compose.runtime.rememberCoroutineScope()
    val viewNow by androidx.compose.runtime.rememberUpdatedState(view)
    var committedFrom by remember { mutableStateOf<Any?>(null) }
    val effBack = remember {
        androidx.compose.runtime.derivedStateOf {
            val c = committedFrom
            if (c != null && (viewNow.stack to viewNow.destination) != c) 0f else backProgress.value
        }
    }
    val sheet = remember { androidx.compose.animation.core.Animatable(1f) }
    val artFlight = remember { com.dopavin.ui.components.ArtworkFlight() }.also {
        it.sheet = { sheet.value }
        it.enabled = !motion.reduce
    }
    val miniDrop = remember { androidx.compose.animation.core.Animatable(0f) }
    var backSwap by remember { mutableStateOf(false) }
    val playerOpenNow by androidx.compose.runtime.rememberUpdatedState(view.stack.any { it == Route.NowPlaying })
    val playerGesture = !motion.reduce && view.route == Route.NowPlaying
    val pageAnimatesBack = !motion.reduce && !view.stack.contains(Route.NowPlaying) && view.route != Route.Queue
    val backBusy = remember { booleanArrayOf(false) }
    val finishPageBack: suspend () -> Unit = {
        val remaining = 1f - backProgress.value
        backProgress.animateTo(1f, com.dopavin.ui.motion.MotionSpecs.slide(com.dopavin.ui.motion.MotionMath.popDurationMs(remaining)))
        val from = viewNow.stack to viewNow.destination
        backSwap = true
        committedFrom = from
        viewModel.back()
        var waited = 0
        while ((viewNow.stack to viewNow.destination) == from && waited < 15) {
            androidx.compose.runtime.withFrameNanos { }
            waited++
        }
        backProgress.snapTo(0f)
        committedFrom = null
        androidx.compose.runtime.withFrameNanos { }
        backSwap = false
    }
    val requestBack: () -> Unit = {
        if (!pageAnimatesBack) {
            viewModel.back()
        } else if (!backBusy[0]) {
            backBusy[0] = true
            backScope.launch {
                try {
                    backDirection = 1f
                    finishPageBack()
                } finally {
                    backBusy[0] = false
                }
            }
        }
    }
    androidx.activity.compose.PredictiveBackHandler(
        enabled = (view.stack.size > 1 || view.destination != Destination.Home),
    ) { events ->
        try {
            events.collect { event ->
                backDirection = if (event.swipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT) -1f else 1f
                if (pageAnimatesBack) backProgress.snapTo(event.progress)
                if (playerGesture) sheet.snapTo(com.dopavin.ui.motion.MotionMath.heldPlayerOffset(event.progress))
            }
            if (playerGesture) {
                sheet.animateTo(1f, com.dopavin.ui.motion.MotionSpecs.slide(com.dopavin.ui.motion.MotionTokens.PlayerExitMs))
                viewModel.back()
            } else if (pageAnimatesBack) {
                finishPageBack()
            } else {
                viewModel.back()
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            backScope.launch { backProgress.animateTo(0f, com.dopavin.ui.motion.MotionSpecs.slide(com.dopavin.ui.motion.MotionTokens.GestureCancelMs)) }
            if (playerGesture) {
                backScope.launch { sheet.animateTo(0f, com.dopavin.ui.motion.MotionSpecs.cancelSpring()) }
            }
            throw e
        }
    }
    val backActive by remember { androidx.compose.runtime.derivedStateOf { effBack.value > 0f } }
    var keepBackButton by remember { mutableStateOf(false) }
    run {
        val remaining = view.stack.dropLast(1)
        val base = remaining.lastOrNull { it != Route.NowPlaying && it != Route.Queue } ?: Route.Root
        val prevIsPushed = view.stack.size > 1 && base != Route.Root
        if (committedFrom == null && !backActive) SideEffect { keepBackButton = prevIsPushed }
    }
    val barsPlaced by remember {
        androidx.compose.runtime.derivedStateOf { !(playerOpenNow && sheet.value < 0.7f) }
    }
    val playerLayerVisible by remember {
        androidx.compose.runtime.derivedStateOf { playerOpenNow || sheet.value < 1f }
    }
    LaunchedEffect(playerOpenNow) {
        when {
            motion.reduce -> sheet.snapTo(if (playerOpenNow) 0f else 1f)
            playerOpenNow -> sheet.animateTo(0f, com.dopavin.ui.motion.MotionSpecs.openSpring())
            else -> sheet.animateTo(1f, com.dopavin.ui.motion.MotionSpecs.slide(com.dopavin.ui.motion.MotionTokens.PlayerExitMs))
        }
    }
    val screenHPx = with(LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val onSheetDrag: (Float) -> Unit = { dy ->
        backScope.launch { sheet.snapTo((sheet.value + dy / screenHPx).coerceIn(0f, 1f)) }
    }
    val onSheetRelease: (Float) -> Unit = { v ->
        backScope.launch {
            val fling = com.dopavin.ui.motion.MotionTokens.SheetFlingPxPerSec
            val open = v < -fling || (sheet.value < com.dopavin.ui.motion.MotionTokens.SheetOpenAt && v < fling)
            when {
                open && motion.reduce -> {
                    sheet.snapTo(0f)
                    viewModel.openNowPlaying()
                }
                open -> {
                    sheet.animateTo(0f, com.dopavin.ui.motion.MotionSpecs.openSpring(), initialVelocity = v / screenHPx)
                    viewModel.openNowPlaying()
                }
                else -> sheet.animateTo(1f, com.dopavin.ui.motion.MotionSpecs.cancelSpring(), initialVelocity = v / screenHPx)
            }
        }
    }
    val playerReleaseJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val dismissPx = with(LocalDensity.current) { com.dopavin.ui.motion.MotionTokens.SheetDismissDp.dp.toPx() }
    val onPlayerDrag: (Float) -> Unit = { dy ->
        playerReleaseJob[0]?.cancel()
        backScope.launch { sheet.snapTo((sheet.value + dy / screenHPx).coerceIn(0f, 1f)) }
    }
    val onPlayerRelease: () -> Unit = {
        playerReleaseJob[0]?.cancel()
        playerReleaseJob[0] = backScope.launch {
            if (sheet.value * screenHPx > dismissPx) {
                val remaining = 1f - sheet.value
                sheet.animateTo(1f, com.dopavin.ui.motion.MotionSpecs.slide(com.dopavin.ui.motion.MotionMath.popDurationMs(remaining)))
                viewModel.back()
            } else {
                sheet.animateTo(0f, com.dopavin.ui.motion.MotionSpecs.cancelSpring())
            }
        }
    }
    val onPlayerCancel: () -> Unit = {
        playerReleaseJob[0]?.cancel()
        playerReleaseJob[0] = backScope.launch { sheet.animateTo(0f, com.dopavin.ui.motion.MotionSpecs.cancelSpring()) }
    }
    val playerWasOpen = remember { booleanArrayOf(false) }
    LaunchedEffect(playerOpenNow) { if (playerOpenNow) playerWasOpen[0] = true }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { sheet.value >= 1f }.collect { closed ->
            if (closed && playerWasOpen[0]) {
                playerWasOpen[0] = false
                if (!motion.reduce) {
                    artFlight.land.snapTo(1f)
                    artFlight.land.animateTo(
                        1f,
                        com.dopavin.ui.motion.MotionSpecs.landSpring(),
                        initialVelocity = com.dopavin.ui.motion.MotionTokens.CoverLandKick,
                    )
                }
            }
        }
    }
    val route = view.route
    val baseRoute = view.stack.lastOrNull { it != Route.NowPlaying && it != Route.Queue } ?: Route.Root
    val playerOpen = view.stack.any { it == Route.NowPlaying }
    val browsing = !imeOpen &&
        (baseRoute == Route.Root || baseRoute is Route.Album || baseRoute is Route.Artist)
    val miniVisible = browsing && playback.hasItem
    val reduceMotion = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val glassStyle = settings.toGlassStyle(reduceMotion)
    val glassConfig = GlassEffectConfig(
        globalEnabled = glassStyle.enabled,
        vibrancy = ((settings.glassSaturation / 100f) - 1f) / 0.5f,
        blurRadius = settings.glassBlur / 8f,
        lensHeight = (settings.glassLensHeight / 30f).coerceIn(0f, 1f),
        lensAmount = (settings.glassRefraction / 100f * 1.2f).coerceIn(0f, 1.2f),
        chromaticAberration = settings.glassDispersion,
        surfaceTintColor = colors.surface,
        surfaceOpacity = (settings.glassTint / 100f * 2.7f).coerceIn(0f, 1f),
        highlightOpacity = (settings.glassHighlight / 100f * 0.9167f).coerceIn(0f, 1f),
        textColor = colors.textPrimary,
    )
    val navGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val tabScroll = rememberFloatingTabBarScrollConnection()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val railMode = !navGlass && configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp
    val railInset = if (railMode && browsing) com.dopavin.ui.components.NavRailInset else 0.dp
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = (if (browsing && !railMode) Dimens.bottomNavHeight + Dimens.sm + navInset else 0.dp) +
        (if (browsing && playback.hasItem) Dimens.miniPlayerHeight + Dimens.md else 0.dp)
    val songTint = com.dopavin.ui.components.rememberArtworkTint(currentSong?.artworkUri)
    val ambientWash by androidx.compose.animation.animateColorAsState(
        targetValue = songTint ?: colors.accentEnd,
        animationSpec = tween(motion.ambient),
        label = "appAmbience",
    )
    val ambientBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val overlayBackdrop = rememberCombinedBackdrop(ambientBackdrop, contentBackdrop)
    val previousSong = if (!playback.shuffle && playback.currentIndex > 0) {
        playback.queue.getOrNull(playback.currentIndex - 1)?.mediaId?.toLongOrNull()?.let { groups.byId[it] }
    } else {
        null
    }
    val nextSong = if (!playback.shuffle && playback.currentIndex >= 0) {
        playback.queue.getOrNull(playback.currentIndex + 1)?.mediaId?.toLongOrNull()?.let { groups.byId[it] }
    } else {
        null
    }
    var sleepDialog by remember { mutableStateOf(false) }
    LaunchedEffect(artFlight) {
        androidx.compose.runtime.snapshotFlow { sheet.value <= 0f }.collect { open -> if (open) artFlight.settlePlayer() }
    }
    val sheetHost = remember { SheetHost() }
    val floatingBack = remember { com.dopavin.ui.components.FloatingBackState() }
    val dialogHost = remember { com.dopavin.ui.components.DialogHost() }
    CompositionLocalProvider(
        LocalActions provides viewModel,
        LocalDownloads provides downloads,
        com.dopavin.ui.components.LocalUpdate provides update,
        com.dopavin.ui.components.LocalAccounts provides accounts,
        LocalSheetHost provides sheetHost,
        com.dopavin.ui.components.LocalDialogHost provides dialogHost,
        LocalGlassStyle provides glassStyle,
        LocalGlassEffectConfig provides glassConfig,
        LocalAppBackdrop provides ambientBackdrop,
        com.dopavin.ui.components.LocalFloatingBack provides floatingBack,
        com.dopavin.ui.components.LocalArtworkFlight provides artFlight,
        androidx.compose.ui.platform.LocalHapticFeedback provides
            (if (settings.haptics) androidx.compose.ui.platform.LocalHapticFeedback.current else NoHaptics),
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        if (glassStyle.enabled || settings.ambience) {
            GlassAmbient(ambientBackdrop, if (settings.ambience) ambientWash else null)
        }
        CompositionLocalProvider(LocalGlassBackdrop provides ambientBackdrop) {
        Box(Modifier.fillMaxSize().nestedScroll(tabScroll).layerBackdrop(contentBackdrop)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = railInset)
                .imePadding(),
        ) {
            val previousKey: Any? = when {
                view.stack.size > 1 -> {
                    val remaining = view.stack.dropLast(1)
                    val base = remaining.lastOrNull { it != Route.NowPlaying && it != Route.Queue } ?: Route.Root
                    if (base == Route.Root) view.destination else base
                }
                view.destination != Destination.Home -> Destination.Home
                else -> null
            }
            val screenKey: Any = if (baseRoute == Route.Root) view.destination else baseRoute
            val renderScreen: @Composable (Any) -> Unit = { key ->
                saveableStateHolder.SaveableStateProvider(key.toString()) {
                    when (key) {
                        is Destination -> RootContent(
                            key, view, visible, results, searchUi, online, playback,
                            currentSong, homeOnline, liked, groups.byId, playlists, downloads, viewModel,
                        )
                        is Route -> RouteContent(
                            key, groups, playback, settings, effects, onlineList, subs,
                            currentSong, lyrics, artist, view, visible, groups.byId, downloads,
                            suggestions, liked, detailSong, playlists, playlistSongs,
                            storage, recap, favourites, viewModel,
                        )
                        else -> Unit
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                    var mainKey by remember { mutableStateOf(screenKey) }
                    var fadeFor by remember { mutableStateOf<Any?>(null) }
                    val fade = remember { androidx.compose.animation.core.Animatable(1f) }
                    val changing = mainKey != screenKey
                    val instant = backSwap || motion.reduce
                    if (changing && instant) SideEffect { mainKey = screenKey }
                    LaunchedEffect(screenKey) {
                        if (mainKey != screenKey && !instant) {
                            fadeFor = screenKey
                            fade.snapTo(0f)
                            fade.animateTo(1f, tween(motion.medium))
                            mainKey = screenKey
                            fadeFor = null
                        }
                    }
                    val crossfading = changing && !instant
                    val under: Any? =
                        if (backActive && previousKey != null && previousKey != screenKey && pageAnimatesBack) previousKey else null
                    val layers = buildList<Pair<Any, PageRole>> {
                        if (crossfading) {
                            add(mainKey to PageRole.Leaving)
                            add(screenKey to PageRole.Entering)
                        } else {
                            if (under != null) add(under to PageRole.Under)
                            add(screenKey to PageRole.Main)
                        }
                    }
                    layers.forEach { (pageKey, role) ->
                        key(pageKey) {
                            val roleModifier: Modifier = when (role) {
                                PageRole.Under -> Modifier
                                    .graphicsLayer {
                                        translationX = -backDirection * (1f - effBack.value) * size.width *
                                            com.dopavin.ui.motion.MotionTokens.ParallaxFraction
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            Color.Black.copy(
                                                alpha = com.dopavin.ui.motion.MotionTokens.ParallaxDim * (1f - effBack.value),
                                            ),
                                        )
                                    }
                                PageRole.Main -> Modifier
                                    .graphicsLayer { translationX = backDirection * effBack.value * size.width }
                                    .then(if (backActive) Modifier.ambientBackground(if (settings.ambience) ambientWash else null) else Modifier)
                                    .then(
                                        if (backActive) {
                                            Modifier.drawBehind {
                                                val w = 24.dp.toPx()
                                                val x = if (backDirection > 0f) -w else size.width
                                                drawRect(
                                                    brush = Brush.horizontalGradient(
                                                        if (backDirection > 0f) listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f))
                                                        else listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                                                        startX = x,
                                                        endX = x + w,
                                                    ),
                                                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                                                    size = androidx.compose.ui.geometry.Size(w, size.height),
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    )
                                PageRole.Leaving -> Modifier.graphicsLayer {
                                    alpha = 1f - (if (fadeFor == screenKey) fade.value else 0f)
                                }
                                PageRole.Entering -> Modifier.graphicsLayer {
                                    alpha = if (fadeFor == screenKey) fade.value else 0f
                                }
                            }
                            CompositionLocalProvider(
                                com.dopavin.ui.components.LocalFloatingBackEnabled provides
                                    (role == PageRole.Main || role == PageRole.Entering),
                            ) {
                                Box(Modifier.fillMaxSize().then(roleModifier)) {
                                    renderScreen(pageKey)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (playerLayerVisible) {
            Box(
                Modifier.graphicsLayer {
                    val o = sheet.value
                    if (o > 0f) {
                        translationY = o * size.height
                        val s = com.dopavin.ui.motion.MotionMath.playerScale(o)
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            com.dopavin.ui.motion.MotionMath.playerCornerDp(o).dp,
                        )
                        clip = true
                    }
                }
                    .flightPlayerHost(artFlight),
            ) {
            NowPlayingScreen(
                playback = playback,
                song = currentSong,
                previousSong = previousSong,
                nextSong = nextSong,
                sleep = sleep,
                liked = currentSong?.id in liked,
                keepScreenOn = settings.keepScreenOn,
                ambience = settings.ambience,
                lyrics = lyrics,
                lyricsOnline = settings.lyricsOnline,
                lyricStrip = settings.lyricStrip,
                skipSilence = settings.skipSilence,
                songsById = groups.byId,
                likedIds = liked,
                queueOrigins = queueOrigins,
                subscribed = currentSong?.channelUrl?.let { url -> subs.channels.any { it.url == url } } == true,
                onOpenSleep = { sleepDialog = true },
                actions = viewModel,
                onDragSheet = onPlayerDrag,
                onReleaseSheet = onPlayerRelease,
                onCancelSheet = onPlayerCancel,
            )
            }
        }
        }
        }
        CompositionLocalProvider(
            LocalGlassBackdrop provides overlayBackdrop,
            LocalAppBackdrop provides overlayBackdrop,
        ) {
            if (!playerOpen) {
                val pagePx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
                com.dopavin.ui.components.FloatingBackLayer(
                    floatingBack,
                    intercept = { requestBack() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            translationX = if (keepBackButton) 0f else backDirection * effBack.value * pagePx
                            alpha = if (!keepBackButton && committedFrom != null && effBack.value == 0f) 0f else 1f
                        },
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .placedWhen { barsPlaced }
                    .graphicsLayer { alpha = ((sheet.value - 0.7f) / 0.3f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val bottomBar = browsing && !railMode
                if (bottomBar && navGlass) {
                    BottomNav(
                        selected = view.destination,
                        onSelect = viewModel::setDestination,
                        scrollConnection = tabScroll,
                        accessory = if (miniVisible) {
                            { isInline, _, accessoryModifier ->
                                MiniPlayer(
                                    playback = playback,
                                    song = currentSong,
                                    position = viewModel::position,
                                    onOpen = viewModel::openNowPlaying,
                                    onTogglePlay = viewModel::togglePlayPause,
                                    onNext = viewModel::next,
                                    onPrevious = viewModel::previous,
                                    modifier = accessoryModifier,
                                    bare = true,
                                    compact = isInline,
                                    drop = { miniDrop.value },
                                    onSheetDrag = onSheetDrag,
                                    onSheetRelease = onSheetRelease,
                                )
                            }
                        } else {
                            null
                        },
                    )
                } else {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = miniVisible,
                        modifier = Modifier.padding(start = railInset),
                        enter = slideInVertically(tween(motion.medium)) { it } + fadeIn(tween(motion.medium)),
                        exit = slideOutVertically(tween(motion.medium)) { it } + fadeOut(tween(motion.medium)),
                    ) {
                        MiniPlayer(
                            playback = playback,
                            song = currentSong,
                            position = viewModel::position,
                            onOpen = viewModel::openNowPlaying,
                            onTogglePlay = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                            drop = { miniDrop.value },
                            onSheetDrag = onSheetDrag,
                            onSheetRelease = onSheetRelease,
                        )
                    }
                    if (bottomBar) {
                        com.dopavin.ui.components.SolidBottomNav(
                            selected = view.destination,
                            onSelect = viewModel::setDestination,
                        )
                    }
                }
            }
        com.dopavin.ui.components.ArtworkFlightLayer(
            artFlight,
            currentSong?.artworkUri,
            currentSong?.album ?: "",
        )
        if (railMode && browsing) {
            com.dopavin.ui.components.NavRail(
                selected = view.destination,
                onSelect = viewModel::setDestination,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .placedWhen { barsPlaced }
                    .graphicsLayer { alpha = ((sheet.value - 0.7f) / 0.3f).coerceIn(0f, 1f) },
            )
        }
        if (route == Route.Queue) {
            val queueLookup = remember(playback.queue, groups.byId) { groups.byId + viewModel.onlineSongsById() }
            QueueSheet(playback, queueLookup, liked, queueOrigins, viewModel)
        }
        ActionSheetLayer(sheetHost)
        detailSong?.let { SongDetailsDialog(it, viewModel) }
        if (sleepDialog) {
            com.dopavin.ui.components.SleepDialog(sleep, viewModel, onDismiss = { sleepDialog = false })
        }
        com.dopavin.ui.components.DialogLayer(dialogHost)
        }
        CompositionLocalProvider(LocalGlassBackdrop provides overlayBackdrop) {
            com.dopavin.ui.components.ToastHost(
                state = toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (bottomInset > 0.dp) {
                            Modifier.padding(bottom = bottomInset + Dimens.sm)
                        } else {
                            Modifier.navigationBarsPadding().padding(bottom = Dimens.md)
                        },
                    ),
            )
        }
    }
    }
}
@Composable
private fun RootContent(
    destination: Destination,
    view: ViewState,
    visible: Visible,
    results: Visible,
    searchUi: SearchUi,
    online: OnlineUi,
    playback: PlaybackUi,
    currentSong: Song?,
    homeOnline: HomeUi,
    liked: Set<Long>,
    songsById: Map<Long, Song>,
    playlists: List<Playlist>,
    downloads: DownloadsUi,
    actions: MusicActions,
) {
    when (destination) {
        Destination.Home -> HomeScreen(view, visible, playback, currentSong, homeOnline, liked, actions)
        Destination.Search -> SearchScreen(view, visible, results, searchUi, online, playback, currentSong, liked, actions)
        Destination.Library -> com.dopavin.ui.screens.LibraryHubScreen(actions)
    }
}
@Composable
private fun RouteContent(
    route: Route,
    groups: Groups,
    playback: PlaybackUi,
    settings: AppSettings,
    effects: EffectsState,
    onlineList: OnlineListUi,
    subs: SubsUi,
    currentSong: Song?,
    lyrics: LyricsUi,
    artist: ArtistUi,
    view: ViewState,
    visible: Visible,
    songsById: Map<Long, Song>,
    downloads: DownloadsUi,
    suggestions: SuggestionsUi,
    liked: Set<Long>,
    detailSong: Song?,
    playlists: List<Playlist>,
    playlistSongs: List<Song>,
    storage: StorageUi,
    recap: List<com.dopavin.data.ListenEntry>,
    favourites: List<Song>,
    actions: MusicActions,
) {
    when (route) {
        Route.Root, Route.NowPlaying, Route.Queue -> Unit
        is Route.Album -> CollectionScreen(
            collection = albumCollection(groups.albumsByKey[route.key]),
            playback = playback,
            liked = liked,
            actions = actions,
        )
        is Route.Artist -> CollectionScreen(
            collection = artistCollection(groups.artistsByKey[route.key]),
            playback = playback,
            liked = liked,
            actions = actions,
        )
        Route.Settings -> SettingsScreen(settings, effects, actions)
        is Route.SettingsPage -> SettingsScreen(settings, effects, actions, categoryName = route.name)
        Route.Equalizer -> EqualizerScreen(effects, actions)
        is Route.OnlineList -> OnlineListScreen(route, playback, onlineList, liked, actions)
        Route.Subscriptions -> SubscriptionsScreen(subs, playback, liked, actions)
        is Route.ArtistProfile -> com.dopavin.ui.screens.ArtistProfileScreen(
            artist, artist.url.isNotBlank() && subs.channels.any { it.url == artist.url }, playback, liked, actions,
        )
        Route.Downloads -> LibraryScreen(
            view, visible, playback, liked, songsById, playlists, downloads, actions,
        )
        Route.Favourites -> LibraryScreen(
            view, visible, playback, liked, songsById, playlists, downloads, actions, favourites = favourites, isFavourites = true,
        )
        is Route.MoodQueue -> SuggestionsScreen(
            title = stringResource(moodLabelRes(route.mood)),
            subtitle = stringResource(R.string.suggestions_subtitle),
            state = suggestions,
            playback = playback,
            liked = liked,
            actions = actions,
        )
        Route.PersonalMix -> SuggestionsScreen(
            title = stringResource(R.string.personal_mix_title),
            subtitle = stringResource(R.string.suggestions_subtitle),
            state = suggestions,
            playback = playback,
            liked = liked,
            actions = actions,
        )
        is Route.SongRadio -> SuggestionsScreen(
            title = stringResource(R.string.song_radio_title, route.title),
            subtitle = stringResource(R.string.suggestions_subtitle),
            state = suggestions,
            playback = playback,
            liked = liked,
            actions = actions,
        )
        Route.Playlists -> PlaylistsScreen(playlists, actions)
        is Route.PlaylistDetail -> PlaylistDetailScreen(
            route.name, playlists.firstOrNull { it.name == route.name }?.cover, playback, playlistSongs, liked, actions,
        )
        Route.Storage -> StorageScreen(storage, actions)
        Route.Recap -> com.dopavin.ui.screens.RecapScreen(recap, actions)
        Route.CrashLog -> com.dopavin.ui.screens.CrashLogScreen(actions)
        Route.Driving -> com.dopavin.ui.screens.DrivingScreen(playback, currentSong, lyrics, settings.lyricsOnline, actions)
    }
}
@Composable
private fun albumCollection(album: Album?): CollectionUi? {
    if (album == null) return null
    val artist = album.artist ?: stringResource(R.string.various_artists)
    val subtitle = stringResource(
        R.string.album_subtitle,
        artist,
        songCountText(album.songs.size),
        formatTime(album.totalDurationMs),
    )
    return CollectionUi(
        title = album.title,
        subtitle = subtitle,
        artworkUri = album.artworkUri,
        songs = album.songs,
        isAlbum = true,
    )
}
@Composable
private fun artistCollection(artist: Artist?): CollectionUi? {
    if (artist == null) return null
    val subtitle = stringResource(
        R.string.artist_subtitle,
        albumCountText(artist.albumCount),
        songCountText(artist.songs.size),
    )
    return CollectionUi(
        title = artist.name,
        subtitle = subtitle,
        artworkUri = artist.songs.first().artworkUri,
        songs = artist.songs,
        isAlbum = false,
    )
}
private enum class PageRole { Under, Main, Leaving, Entering }
private fun Modifier.placedWhen(placed: () -> Boolean): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, p.height) { if (placed()) p.place(0, 0) }
}
private object NoHaptics : androidx.compose.ui.hapticfeedback.HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) = Unit
}
