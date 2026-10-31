package com.dopavin.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.LyricsUi
import com.dopavin.data.Song
import com.dopavin.playback.EffectsState
import com.dopavin.playback.PlaybackUi
import com.dopavin.playback.SleepMode
import com.dopavin.playback.SleepState
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppSlider
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.BottomSheet
import com.dopavin.ui.components.SettingsRow
import com.dopavin.ui.components.ToggleRow
import com.dopavin.ui.components.formatTime
import com.dopavin.ui.components.rememberSystemVolume
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.ui.components.flightPlayer
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.glass.backdrop.backdrops.layerBackdrop
import com.dopavin.ui.components.PlayPauseButton
import com.dopavin.ui.components.SeekBar
import com.dopavin.ui.components.ToggleIconAction
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.components.focusRing
import com.dopavin.ui.components.rememberArtworkTint
import com.dopavin.ui.components.rememberPlaybackPosition
import com.dopavin.data.DownloadStatus
import com.dopavin.ui.components.LocalDownloads
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import androidx.compose.material3.CircularProgressIndicator


/**
 * S-06. Full-screen player. Background gradient follows the artwork colour; artwork breathes with
 * play / pause; swipe the artwork sideways for previous / next, swipe down anywhere to close.
 */
@Composable
fun NowPlayingScreen(
    playback: PlaybackUi,
    song: Song?,
    previousSong: Song?,
    nextSong: Song?,
    sleep: SleepState,
    liked: Boolean,
    keepScreenOn: Boolean,
    ambience: Boolean,
    lyrics: LyricsUi,
    lyricsOnline: Boolean,
    lyricStrip: Boolean,
    skipSilence: Boolean,
    songsById: Map<Long, Song>,
    likedIds: Set<Long>,
    queueOrigins: Map<String, com.dopavin.data.QueueOrigin>,
    subscribed: Boolean,
    onOpenSleep: () -> Unit,
    actions: MusicActions,
    /** Finger movement while swiping down (px, positive = down); the root turns it into sheet progress. */
    onDragSheet: (Float) -> Unit,
    /** Finger lifted: the root decides between closing and returning to open. */
    onReleaseSheet: () -> Unit,
    /** Pointer cancelled: the root returns the sheet to open. */
    onCancelSheet: () -> Unit,
) {
    val colors = AppTheme.colors
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(keepScreenOn) {
        hostView.keepScreenOn = keepScreenOn
        onDispose { hostView.keepScreenOn = false }
    }
    val motion = AppTheme.motion
    val density = LocalDensity.current

    val tint = rememberArtworkTint(song?.artworkUri)
    val top by animateColorAsState(
        targetValue = tint?.let { lerp(colors.canvas, it, if (colors.isDark) 0.62f else 0.45f) }
            ?: lerp(colors.canvas, colors.surfaceHigh, 0.5f),
        animationSpec = tween(motion.ambient),
        label = "playerTint",
    )

    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    // Lyrics / queue replace the artwork while active; tapping the same button again brings it back.
    var stage by remember { mutableStateOf(PlayerStage.Artwork) }
    // Lyrics / queue are showing: the cover must not fly over them when the player is dragged or closed (F-212).
    val coverFlight = com.dopavin.ui.components.LocalArtworkFlight.current
    androidx.compose.runtime.SideEffect { coverFlight?.coverVisible = stage == PlayerStage.Artwork }
    androidx.compose.runtime.DisposableEffect(coverFlight) { onDispose { coverFlight?.coverVisible = true } }

    // Swipe down to close: the finger drives the ROOT sheet (the same value Predictive Back and the cover flight
    // use), so the screen, the flying cover and the bars all follow one progress. Latest callbacks are read
    // through updated state because the pointer block below is keyed on the stage, not on them.
    val latestDrag by androidx.compose.runtime.rememberUpdatedState(onDragSheet)
    val latestRelease by androidx.compose.runtime.rememberUpdatedState(onReleaseSheet)
    val latestCancel by androidx.compose.runtime.rememberUpdatedState(onCancelSheet)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(sheet == null && stage == PlayerStage.Artwork) {
                if (sheet != null || stage != PlayerStage.Artwork) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { latestRelease() },
                    onDragCancel = { latestCancel() },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        latestDrag(delta)
                    },
                )
            },
    ) {
        // The player's own background lives in its own recorded layer so the glass buttons above it
        // (close, more) bend and tint what is really behind them, not the app behind the player.
        val playerBackdrop = com.dopavin.ui.glass.backdrop.backdrops.rememberLayerBackdrop()
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(playerBackdrop)
                .background(colors.canvas)
                .then(
                if (ambience) {
                    // Glow that starts at the top edge and fades downwards: a radial wash centred on the
                    // top middle plus a soft vertical fade, so the colour spreads from the top, not the middle.
                    val strength = if (colors.isDark) 1f else 0.8f
                    Modifier.drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(top.copy(alpha = 0.9f * strength), Color.Transparent),
                                startY = 0f,
                                endY = size.height * 0.55f,
                            ),
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(top.copy(alpha = 0.75f * strength), Color.Transparent),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.height * 0.9f,
                            ),
                        )
                    }
                } else {
                    Modifier
                },
                ),
        )
        androidx.compose.runtime.CompositionLocalProvider(com.dopavin.ui.glass.LocalGlassBackdrop provides playerBackdrop) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val wide = boxWidth > boxHeight
        val stageContent: @Composable (Modifier) -> Unit = { stageModifier ->
            when {
                stage == PlayerStage.Lyrics && song != null ->
                    LyricsPanel(song, playback, lyrics, lyricsOnline, actions, stageModifier)
                stage == PlayerStage.Queue ->
                    QueuePanel(playback, songsById, likedIds, queueOrigins, actions, stageModifier)
                else -> ArtworkStage(playback, song, previousSong, nextSong, actions, stageModifier)
            }
        }
        // The artwork and controls are recorded too (without the menu itself), so the More menu's glass bends
        // the real player behind it. Buttons inside keep sampling only the background layer: a glass
        // surface must never sample a layer that contains itself.
        val contentBackdrop = com.dopavin.ui.glass.backdrop.backdrops.rememberLayerBackdrop()
        val menuBackdrop = com.dopavin.ui.glass.backdrop.backdrops.rememberCombinedBackdrop(playerBackdrop, contentBackdrop)
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.xl),
                horizontalArrangement = Arrangement.spacedBy(Dimens.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    stageContent(Modifier.fillMaxSize())
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    CloseRow(actions, onMore = { sheet = PlayerSheet.More })
                    Spacer(Modifier.height(Dimens.md))
                    TrackInfo(playback, song, liked, actions)
                    com.dopavin.ui.components.LyricStrip(song, playback, lyrics, lyricStrip && stage != PlayerStage.Lyrics, lyricsOnline, actions, onClick = { stage = PlayerStage.Lyrics })
                    Spacer(Modifier.height(Dimens.md))
                    SeekSection(playback, song, actions)
                    Spacer(Modifier.height(Dimens.sm))
                    TransportRow(playback, actions)
                    VolumeRow()
                    PlayerActions(
                        sleep,
                        stage = stage,
                        onSound = actions::openEqualizer,
                        onSleep = onOpenSleep,
                        onQueue = { stage = if (stage == PlayerStage.Queue) PlayerStage.Artwork else PlayerStage.Queue },
                        onLyrics = { stage = if (stage == PlayerStage.Lyrics) PlayerStage.Artwork else PlayerStage.Lyrics },
                    )
                    Spacer(Modifier.height(Dimens.md))
                }
            }
        } else {
            // Short screens (small phones, big font scale): everything scrolls and the artwork gets
            // a fixed square, so the controls are never squeezed out of view.
            val compact = boxHeight < 640.dp
            val screenWidth = boxWidth
            val controls: @Composable () -> Unit = {
                TrackInfo(playback, song, liked, actions)
                com.dopavin.ui.components.LyricStrip(song, playback, lyrics, lyricStrip && stage != PlayerStage.Lyrics, lyricsOnline, actions, onClick = { stage = PlayerStage.Lyrics })
                Spacer(Modifier.height(Dimens.md))
                SeekSection(playback, song, actions)
                Spacer(Modifier.height(Dimens.sm))
                TransportRow(playback, actions)
                VolumeRow()
                Spacer(Modifier.height(Dimens.xs))
                PlayerActions(
                    sleep,
                    stage = stage,
                    onSound = actions::openEqualizer,
                    onSleep = onOpenSleep,
                    onQueue = { stage = if (stage == PlayerStage.Queue) PlayerStage.Artwork else PlayerStage.Queue },
                    onLyrics = { stage = if (stage == PlayerStage.Lyrics) PlayerStage.Artwork else PlayerStage.Lyrics },
                )
                Spacer(Modifier.height(Dimens.sm))
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = Dimens.xl),
            ) {
                CloseRow(actions, onMore = { sheet = PlayerSheet.More })
                if (compact) {
                    stageContent(
                        Modifier
                            .fillMaxWidth()
                            .height(screenWidth - Dimens.xl * 2)
                            .padding(vertical = Dimens.md),
                    )
                } else {
                    stageContent(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = Dimens.md),
                    )
                }
                controls()
            }
        }
        }

        when (sheet) {
            PlayerSheet.More -> androidx.compose.runtime.CompositionLocalProvider(com.dopavin.ui.glass.LocalGlassBackdrop provides menuBackdrop) {
                MoreSheet(playback, song, skipSilence, subscribed, actions, onClose = { sheet = null })
            }
            null -> Unit
        }
        }
    }
}

private enum class PlayerSheet { More }

private enum class PlayerStage { Artwork, Lyrics, Queue }

@Composable
private fun CloseRow(actions: MusicActions, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = R.drawable.ic_expand_more,
            description = stringResource(R.string.cd_close_player),
            onClick = actions::back,
            iconSize = 32.dp,
            glass = true,
        )
        AppText(
            text = stringResource(R.string.now_playing_title).uppercase(),
            style = AppTheme.type.caption,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconAction(
            icon = R.drawable.ic_more,
            description = stringResource(R.string.more_title),
            onClick = onMore,
            glass = true,
        )
    }
}

/**
 * F-107 / F-148: the More controls dropdown. Every row does something real:
 * speed chips, the skip-silence switch, the current audio output plus the system's own
 * sound/output settings, song radio, song details and an honest offline status.
 */
@Composable
internal fun MoreSheet(
    playback: PlaybackUi,
    song: Song?,
    skipSilence: Boolean,
    subscribed: Boolean,
    actions: MusicActions,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val outputLabel = remember(context) { outputDeviceLabel(context) }
    // F-148: a dropdown next to the More button instead of a bottom sheet (the tap that opened it
    // recorded the button's bounds in MenuAnchor).
    val anchor = remember { com.dopavin.ui.components.MenuAnchor.fresh() }
    com.dopavin.ui.components.AnchoredMenu(anchor = anchor, onDismiss = onClose, width = 320.dp) {
        Column(
            Modifier.padding(bottom = Dimens.xs),
        ) {
            AppText(
                text = stringResource(R.string.more_title),
                style = AppTheme.type.title,
                modifier = Modifier
                    .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm)
                    .semantics { heading() },
            )
            SpeedPicker(playback.speed, actions)
            ToggleRow(
                title = stringResource(R.string.more_silence),
                subtitle = stringResource(R.string.settings_skip_silence_body),
                checked = skipSilence,
                onCheckedChange = { actions.setSkipSilence(it) },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    AppText(
                        stringResource(R.string.more_output),
                        AppTheme.type.bodyStrong,
                    )
                    if (outputLabel.isNotEmpty()) {
                        AppText(
                            outputLabel,
                            AppTheme.type.bodySmall,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
            }
            SettingsRow(
                title = stringResource(R.string.more_play_on),
                onClick = {
                    onClose()
                    openOutputPicker(context)
                },
            )
            SettingsRow(
                title = stringResource(R.string.more_output_settings),
                onClick = {
                    runCatching {
                        val target = android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                        context.startActivity(target.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )
            SettingsRow(
                title = stringResource(R.string.driving_title),
                subtitle = stringResource(R.string.driving_body),
                onClick = {
                    onClose()
                    actions.openDriving()
                },
            )
            if (song != null && song.channelUrl != null) {
                SettingsRow(
                    title = if (subscribed) {
                        stringResource(R.string.more_unsubscribe, song.artist)
                    } else {
                        stringResource(R.string.more_subscribe, song.artist)
                    },
                    chevron = false,
                    onClick = {
                        onClose()
                        actions.toggleSubscription(song)
                    },
                )
            }
            if (song != null) {
                SettingsRow(
                    title = stringResource(R.string.menu_song_radio),
                    onClick = {
                        onClose()
                        actions.openRadio(song)
                    },
                )
                val shareContext = androidx.compose.ui.platform.LocalContext.current
                SettingsRow(
                    title = stringResource(R.string.menu_share),
                    onClick = {
                        onClose()
                        com.dopavin.ui.components.shareSong(shareContext, song)
                    },
                )
                SettingsRow(
                    title = stringResource(R.string.menu_details),
                    onClick = {
                        onClose()
                        actions.openSongDetails(song)
                    },
                )
            }
            if (song != null && song.videoId != null) {
                val downloads = LocalDownloads.current
                val saved = downloads.isSaved(song.videoId)
                Column(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg)) {
                    AppText(stringResource(R.string.more_saved_status), AppTheme.type.bodyStrong)
                    AppText(
                        if (saved) {
                            stringResource(R.string.more_saved_in_app, song.format.ifBlank { "audio" })
                        } else {
                            stringResource(R.string.more_offline_stream)
                        },
                        AppTheme.type.bodySmall,
                        color = AppTheme.colors.textSecondary,
                    )
                }
                if (saved) {
                    SettingsRow(
                        title = stringResource(R.string.menu_save_device),
                        subtitle = stringResource(R.string.menu_save_device_body),
                        onClick = {
                            onClose()
                            actions.saveToDevice(song)
                        },
                    )
                    SettingsRow(
                        title = stringResource(R.string.menu_delete_download),
                        onClick = {
                            onClose()
                            actions.deleteDownload(song)
                        },
                    )
                } else if (downloads.item(song.videoId) == null) {
                    SettingsRow(
                        title = stringResource(R.string.menu_download),
                        onClick = {
                            onClose()
                            actions.downloadSong(song)
                        },
                    )
                }
            }
        }
    }
}

/**
 * F-201: opens the system's own output switcher (Bluetooth, cast and speaker choices) as a panel over the app.
 * The panel action is not part of the public SDK and differs between phone makers, so when no activity answers
 * the general sound settings open instead.
 */
private fun openOutputPicker(context: android.content.Context) {
    val flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    val panel = android.content.Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
        .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
        .addFlags(flags)
    val opened = runCatching {
        if (panel.resolveActivity(context.packageManager) != null) {
            context.startActivity(panel)
            true
        } else {
            false
        }
    }.getOrDefault(false)
    if (!opened) {
        runCatching {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).addFlags(flags))
        }
    }
}

/** The current audio output as a short human label; empty when it cannot be read. */
private fun outputDeviceLabel(context: android.content.Context): String {
    val manager = context.getSystemService(android.content.Context.AUDIO_SERVICE)
        as? android.media.AudioManager ?: return ""
    val preference = intArrayOf(
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
        android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY,
        android.media.AudioDeviceInfo.TYPE_HDMI,
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    )
    val best = runCatching {
        manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in preference }
            .minBy { preference.indexOf(it.type) }
    }.getOrNull() ?: return ""
    return context.getString(
        when (best.type) {
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            -> R.string.output_bluetooth
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            -> R.string.output_wired
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
            android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY,
            -> R.string.output_usb
            android.media.AudioDeviceInfo.TYPE_HDMI -> R.string.output_hdmi
            else -> R.string.output_speaker
        },
    )
}

/** Big artwork that shrinks slightly while paused and can be swiped for previous / next. */
@Composable
private fun ArtworkStage(
    playback: PlaybackUi,
    song: Song?,
    previousSong: Song?,
    nextSong: Song?,
    actions: MusicActions,
    modifier: Modifier = Modifier,
) {
    val motion = AppTheme.motion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val scale by animateFloatAsState(
        targetValue = if (playback.isPlaying) 1f else 0.88f,
        animationSpec = if (motion.reduce) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "artworkScale",
    )
    val flight = com.dopavin.ui.components.LocalArtworkFlight.current
    androidx.compose.runtime.SideEffect { flight?.playerScale = scale }
    val shape = RoundedCornerShape(Dimens.radiusXLarge)
    val canNext = playback.hasNext
    val canPrevious = playback.hasPrevious

    // The new song is already showing in the neighbour slot, so once the id changes the strip
    // snaps back to centre without any visible jump.
    LaunchedEffect(song?.id) { offset.snapTo(0f) }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side: Dp = minOf(maxWidth, maxHeight, Dimens.artworkMax)
        val travel = with(density) { (side + 24.dp).toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(canNext, canPrevious, travel) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = offset.value
                            scope.launch {
                                when {
                                    v < -travel * 0.3f && canNext -> {
                                        offset.animateTo(-travel, tween(motion.short))
                                        actions.next()
                                        delay(SETTLE_MS)
                                        if (offset.value != 0f) offset.animateTo(0f)
                                    }
                                    v > travel * 0.3f && canPrevious -> {
                                        offset.animateTo(travel, tween(motion.short))
                                        actions.previous()
                                        delay(SETTLE_MS)
                                        if (offset.value != 0f) offset.animateTo(0f)
                                    }
                                    else -> offset.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            // Resist when there is no song in that direction.
                            val blocked = (delta < 0 && !canNext) || (delta > 0 && !canPrevious)
                            val step = if (blocked) delta * 0.3f else delta
                            scope.launch { offset.snapTo((offset.value + step).coerceIn(-travel, travel)) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ArtworkCard(previousSong, side, shape, visible = { offset.value > 1f }) { -travel + offset.value }
            Box(
                Modifier
                    .size(side)
                    .flightPlayer(flight)
                    .graphicsLayer {
                        translationX = offset.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(28.dp, shape),
            ) {
                ArtworkImage(
                    uri = song?.artworkUri,
                    sizePx = ArtworkSize.Player,
                    shape = shape,
                    modifier = Modifier.fillMaxSize(),
                    seed = song?.album ?: "",
                )
            }
            ArtworkCard(nextSong, side, shape, visible = { offset.value < -1f }) { travel + offset.value }
        }
    }
}

/** A neighbouring song's artwork, positioned by [translationX]; only drawn while it is in view. */
@Composable
private fun ArtworkCard(song: Song?, side: Dp, shape: Shape, visible: () -> Boolean, translationX: () -> Float) {
    if (song == null) return
    Box(
        Modifier
            .size(side)
            .graphicsLayer {
                this.translationX = translationX()
                // Only while swiping toward this neighbour; at rest nothing peeks in from the side.
                alpha = if (visible()) 1f else 0f
                scaleX = 0.88f
                scaleY = 0.88f
            }
            .shadow(16.dp, shape),
    ) {
        ArtworkImage(
            uri = song.artworkUri,
            sizePx = ArtworkSize.Player,
            shape = shape,
            modifier = Modifier.fillMaxSize(),
            seed = song.album,
        )
    }
}

/** How long to wait for the player to report the new song before easing the strip back. */
private const val SETTLE_MS = 900L

@Composable
private fun TrackInfo(playback: PlaybackUi, song: Song?, liked: Boolean, actions: MusicActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            com.dopavin.ui.components.MarqueeText(
                text = playback.title,
                style = AppTheme.type.headline,
            )
            Spacer(Modifier.height(Dimens.xs))
            AppText(
                text = playback.artist,
                style = AppTheme.type.body,
                color = AppTheme.colors.textSecondary,
                modifier = if (song != null && playback.artist.isNotBlank()) {
                    Modifier
                        .heightIn(min = Dimens.touchTarget)
                        .clickableScale(pressedScale = 0.97f) { actions.openArtistOf(song) }
                        .wrapContentHeight(Alignment.CenterVertically)
                } else {
                    Modifier
                },
            )
        }
        if (song != null) {
            DownloadButton(song, actions)
            ToggleIconAction(
                icon = if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                description = stringResource(if (liked) R.string.cd_unlike else R.string.cd_like),
                active = liked,
                onClick = { actions.toggleLike(song.id) },
                inactiveTint = AppTheme.colors.textPrimary,
            )
        }
    }
}

/** The quick download control next to the heart: download, progress (tap cancels) or saved. */
@Composable
private fun DownloadButton(song: Song, actions: MusicActions) {
    val videoId = song.videoId ?: return
    val downloads = LocalDownloads.current
    val colors = AppTheme.colors
    val item = downloads.item(videoId)
    val cancelLabel = stringResource(R.string.menu_cancel_download)
    when {
        downloads.isSaved(videoId) -> IconAction(
            icon = R.drawable.ic_check,
            description = stringResource(R.string.cd_downloaded),
            onClick = {},
            tint = colors.accent,
        )
        item != null && item.status != DownloadStatus.Failed -> Box(
            Modifier
                .size(Dimens.touchTarget)
                .clickableScale { actions.cancelDownload(videoId) }
                .semantics { contentDescription = cancelLabel },
            contentAlignment = Alignment.Center,
        ) {
            val progress = item.progress
            if (progress >= 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.accent,
                    trackColor = colors.divider,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.accent,
                    trackColor = colors.divider,
                )
            }
        }
        else -> IconAction(
            icon = R.drawable.ic_download,
            description = stringResource(R.string.cd_download, song.title),
            onClick = { actions.downloadSong(song) },
        )
    }
}

/** Owns the position polling so only the seek bar recomposes while playing. */
@Composable
private fun SeekSection(playback: PlaybackUi, song: Song?, actions: MusicActions) {
    val position = rememberPlaybackPosition(playback.mediaId, playback.isPlaying, actions::position)
    val duration = if (playback.durationMs > 0L) playback.durationMs else song?.durationMs ?: 0L
    SeekBar(
        positionMs = position.longValue,
        durationMs = duration,
        onSeek = { target ->
            actions.seekTo(target)
            position.longValue = target
        },
    )
}

@Composable
private fun TransportRow(playback: PlaybackUi, actions: MusicActions) {
    val repeatIcon = if (playback.repeatMode == Player.REPEAT_MODE_ONE) {
        R.drawable.ic_repeat_one
    } else {
        R.drawable.ic_repeat
    }
    val repeatDescription = stringResource(
        when (playback.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.string.cd_repeat_one
            Player.REPEAT_MODE_ALL -> R.string.cd_repeat_all
            else -> R.string.cd_repeat_off
        },
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIconAction(
            icon = R.drawable.ic_shuffle,
            description = stringResource(if (playback.shuffle) R.string.cd_shuffle_on else R.string.cd_shuffle_off),
            active = playback.shuffle,
            onClick = actions::toggleShuffle,
            glass = true,
        )
        IconAction(
            icon = R.drawable.ic_skip_previous,
            description = stringResource(R.string.cd_previous),
            onClick = actions::previous,
            size = 56.dp,
            iconSize = Dimens.iconTransport,
            glass = true,
        )
        PlayPauseButton(
            playing = playback.isPlaying,
            buffering = playback.isBuffering,
            onClick = actions::togglePlayPause,
            size = Dimens.playButton,
        )
        IconAction(
            icon = R.drawable.ic_skip_next,
            description = stringResource(R.string.cd_next),
            onClick = actions::next,
            enabled = playback.hasNext,
            size = 56.dp,
            iconSize = Dimens.iconTransport,
            glass = true,
        )
        ToggleIconAction(
            icon = repeatIcon,
            description = repeatDescription,
            active = playback.repeatMode != Player.REPEAT_MODE_OFF,
            onClick = actions::cycleRepeat,
            glass = true,
        )
    }
}

/** Speaker icons around a slider that follows the system music volume. */
@Composable
private fun VolumeRow() {
    val colors = AppTheme.colors
    val volume = rememberSystemVolume()
    val percent = volume.level * 100 / volume.max
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_volume_down),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(Dimens.iconControl),
        )
        AppSlider(
            value = volume.level.toFloat(),
            onValueChange = { volume.setLevel(it.toInt()) },
            valueRange = 0f..volume.max.toFloat(),
            description = stringResource(R.string.cd_volume),
            valueText = stringResource(R.string.volume_state, percent),
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_volume_up),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(Dimens.iconControl),
        )
    }
}

/** Lyrics, sound, sleep timer and queue as four equal icon-over-label buttons. "More" lives in the top bar. */
@Composable
private fun PlayerActions(
    sleep: SleepState,
    stage: PlayerStage,
    onSound: () -> Unit,
    onSleep: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
) {
    val sleepLabel = when (sleep.mode) {
        SleepMode.Timer -> formatTime(sleep.remainingMs)
        SleepMode.EndOfTrack -> stringResource(R.string.sleep_end_short)
        SleepMode.Off -> stringResource(R.string.player_sleep)
    }
    Row(Modifier.fillMaxWidth()) {
        PlayerAction(R.drawable.ic_lyrics, stringResource(R.string.player_lyrics), stage == PlayerStage.Lyrics, onLyrics, Modifier.weight(1f))
        PlayerAction(R.drawable.ic_equalizer, stringResource(R.string.player_sound), false, onSound, Modifier.weight(1f))
        PlayerAction(R.drawable.ic_timer, sleepLabel, sleep.mode != SleepMode.Off, onSleep, Modifier.weight(1f))
        PlayerAction(R.drawable.ic_queue, stringResource(R.string.player_queue), stage == PlayerStage.Queue, onQueue, Modifier.weight(1f))
    }
}

@Composable
private fun PlayerAction(
    @androidx.annotation.DrawableRes icon: Int,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val tint = if (active) colors.accent else colors.textPrimary
    Column(
        modifier
            .heightIn(min = Dimens.touchTarget)
            .clip(RoundedCornerShape(Dimens.radiusMedium))
            .clickableScale(onClick = onClick)
            .focusRing(RoundedCornerShape(Dimens.radiusMedium))
            .padding(vertical = Dimens.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconControl),
        )
        Spacer(Modifier.height(Dimens.xs))
        AppText(label, AppTheme.type.caption, color = tint)
    }
}
