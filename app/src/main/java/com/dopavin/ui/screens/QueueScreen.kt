package com.dopavin.ui.screens
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.QueueOrigin
import com.dopavin.data.Song
import com.dopavin.data.UNKNOWN_ALBUM_KEY
import com.dopavin.playback.PlaybackUi
import com.dopavin.playback.QueueItem
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.ui.components.EqualizerBars
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.SongActionsMenu
import com.dopavin.ui.components.SongActionsOverflow
import com.dopavin.ui.components.BottomSheet
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.components.focusRing
import com.dopavin.ui.components.formatTime
import com.dopavin.ui.components.songCountText
import com.dopavin.ui.glass.glassSurface
import com.dopavin.ui.glass.backdrop.backdrops.layerBackdrop
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
private val QUEUE_ROW_HEIGHT = 72.dp
private const val QUEUE_ROW_LIFT = 1.05f
@Composable
fun QueueSheet(
    playback: PlaybackUi,
    songsById: Map<Long, Song>,
    liked: Set<Long>,
    origins: Map<String, QueueOrigin>,
    actions: MusicActions,
) {
    BottomSheet(onDismiss = actions::back, heightFraction = 0.88f) {
        QueuePanel(playback, songsById, liked, origins, actions, Modifier.weight(1f))
    }
}
@Composable
fun QueuePanel(
    playback: PlaybackUi,
    songsById: Map<Long, Song>,
    liked: Set<Long>,
    origins: Map<String, QueueOrigin>,
    actions: MusicActions,
    modifier: Modifier = Modifier,
) {
    val queue = playback.queue
    val listState = rememberLazyListState()
    val rowHeightPx = with(LocalDensity.current) { QUEUE_ROW_HEIGHT.toPx() }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var pendingIds by remember { mutableStateOf<List<String>?>(null) }
    var settling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var savingPlaylist by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (playback.currentIndex > 0) listState.scrollToItem(playback.currentIndex)
    }
    val queueIds = remember(queue) { queue.map { it.mediaId } }
    val reorderApplied = pendingIds != null && queueIds != pendingIds
    LaunchedEffect(reorderApplied) {
        if (reorderApplied) {
            dragFrom = null
            dragOffsetPx = 0f
            pendingIds = null
            settling = false
        }
    }
    val activeFrom = if (reorderApplied) null else dragFrom
    val dragTarget = if (queue.isEmpty()) {
        null
    } else {
        activeFrom?.let { from ->
            (from + (dragOffsetPx / rowHeightPx).roundToInt()).coerceIn(0, queue.lastIndex)
        }
    }
    val displacement: (Int) -> Float = { index ->
        val from = activeFrom
        val to = dragTarget
        if (from == null || to == null) {
            0f
        } else if (index == from) {
            dragOffsetPx
        } else if (index in minOf(from, to)..maxOf(from, to)) {
            if (to > from) -rowHeightPx else rowHeightPx
        } else {
            0f
        }
    }
    val queueSongs = queue.mapNotNull { it.mediaId.toLongOrNull()?.let { id -> songsById[id] } }
    val listBackdrop = com.dopavin.ui.glass.backdrop.backdrops.rememberLayerBackdrop()
    val glassBase = com.dopavin.ui.glass.LocalGlassBackdrop.current
    val overlayBackdrop = com.dopavin.ui.glass.backdrop.backdrops.rememberCombinedBackdrop(glassBase ?: listBackdrop, listBackdrop)
    val showOverlay = activeFrom != null && com.dopavin.ui.glass.LocalGlassStyle.current.enabled && glassBase != null
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AppText(
                    text = stringResource(R.string.queue_title),
                    style = AppTheme.type.title,
                    modifier = Modifier.semantics { heading() },
                )
                AppText(
                    text = songCountText(queue.size),
                    style = AppTheme.type.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            IconAction(
                icon = R.drawable.ic_queue,
                description = stringResource(R.string.queue_save),
                onClick = { savingPlaylist = true },
                tint = AppTheme.colors.textSecondary,
            )
            IconAction(
                icon = R.drawable.ic_close,
                description = stringResource(R.string.queue_clear),
                onClick = { confirmClear = true },
                tint = AppTheme.colors.textSecondary,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(listBackdrop, frozen = { !showOverlay }),
            ) {
                itemsIndexed(queue, key = { index, item -> "${item.mediaId}#$index" }) { index, item ->
                    val song = item.mediaId.toLongOrNull()?.let { songsById[it] }
                    val favorite = song != null && song.id in liked
                    QueueRow(
                        item = item,
                        song = song,
                        position = index + 1,
                        total = queue.size,
                        current = index == playback.currentIndex,
                        playing = playback.isPlaying,
                        originManual = origins[item.mediaId] == QueueOrigin.MANUAL,
                        favorite = favorite,
                        lifted = activeFrom == index,
                        hidden = showOverlay && activeFrom == index,
                        glassHeld = false,
                        reordering = activeFrom != null,
                        displacedY = displacement(index),
                        onLift = { if (!settling) { dragFrom = index; dragOffsetPx = 0f } },
                        onDrag = { deltaY -> if (!settling) dragOffsetPx += deltaY },
                        onDropped = {
                            val from = dragFrom
                            val to = dragTarget
                            if (from == null || to == null || settling) {
                            } else {
                                settling = true
                                scope.launch {
                                    val start = dragOffsetPx
                                    val end = (to - from) * rowHeightPx
                                    androidx.compose.animation.core.Animatable(start).animateTo(
                                        end,
                                        androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                                        ),
                                    ) { dragOffsetPx = value }
                                    if (to == from) {
                                        dragFrom = null
                                        dragOffsetPx = 0f
                                        settling = false
                                    } else {
                                        pendingIds = queueIds
                                        actions.moveInQueue(from, to)
                                        kotlinx.coroutines.delay(800)
                                        if (pendingIds != null) {
                                            dragFrom = null
                                            dragOffsetPx = 0f
                                            pendingIds = null
                                            settling = false
                                        }
                                    }
                                }
                            }
                        },
                        onClick = { actions.jumpTo(index) },
                        onRemove = { actions.removeFromQueue(index) },
                        onPlayNext = { actions.playNext(songOrDefault(item, song)) },
                        onToggleFavorite = song?.let { s -> ({ actions.toggleLike(s.id) }) },
                        onOpenDetails = song?.let { s -> ({ actions.openSongDetails(s) }) },
                        onMoveUp = if (index > 0) ({ actions.moveInQueue(index, index - 1) }) else null,
                        onMoveDown = if (index < queue.lastIndex) ({ actions.moveInQueue(index, index + 1) }) else null,
                    )
                }
            }
            val heldIndex = activeFrom
            if (showOverlay && heldIndex != null && heldIndex in queue.indices) {
                val item = queue[heldIndex]
                val song = item.mediaId.toLongOrNull()?.let { songsById[it] }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(QUEUE_ROW_HEIGHT)
                        .offset {
                            val top = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == heldIndex }?.offset
                                ?: (heldIndex * rowHeightPx).roundToInt()
                            androidx.compose.ui.unit.IntOffset(0, top + dragOffsetPx.roundToInt())
                        }
                        .zIndex(2f),
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(com.dopavin.ui.glass.LocalGlassBackdrop provides overlayBackdrop) {
                        QueueRow(
                            item = item,
                            song = song,
                            position = heldIndex + 1,
                            total = queue.size,
                            current = heldIndex == playback.currentIndex,
                            playing = playback.isPlaying,
                            originManual = origins[item.mediaId] == QueueOrigin.MANUAL,
                            favorite = song != null && song.id in liked,
                            lifted = true,
                            hidden = false,
                            glassHeld = true,
                            reordering = true,
                            displacedY = 0f,
                            onLift = {}, onDrag = {}, onDropped = {}, onClick = {}, onRemove = {}, onPlayNext = {},
                            onToggleFavorite = null, onOpenDetails = null, onMoveUp = null, onMoveDown = null,
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        com.dopavin.ui.components.GlassDialog(
            title = stringResource(R.string.queue_clear),
            body = stringResource(R.string.queue_clear_body),
            confirmLabel = stringResource(R.string.sel_confirm_delete),
            destructive = true,
            onConfirm = {
                actions.clearUpcomingQueue()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
    if (savingPlaylist) {
        PlaylistNameDialog(
            onDismiss = { savingPlaylist = false },
            onSave = { name ->
                savingPlaylist = false
                actions.createPlaylist(name, queueSongs)
            },
        )
    }
}
private fun songOrDefault(item: QueueItem, song: Song?): Song =
    song ?: Song(
        id = item.mediaId.toLongOrNull() ?: 0L,
        title = item.title,
        artist = item.artist,
        album = "",
        albumKey = UNKNOWN_ALBUM_KEY,
        durationMs = 0L,
        dateAdded = 0L,
        track = 0,
    )
@Composable
private fun QueueRow(
    item: QueueItem,
    song: Song?,
    position: Int,
    total: Int,
    current: Boolean,
    playing: Boolean,
    originManual: Boolean,
    favorite: Boolean,
    lifted: Boolean,
    hidden: Boolean,
    glassHeld: Boolean,
    reordering: Boolean,
    displacedY: Float,
    onLift: () -> Unit,
    onDrag: (Float) -> Unit,
    onDropped: () -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onOpenDetails: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val description = stringResource(R.string.queue_row_description, item.title, item.artist, position, total)
    var menuOpen by remember { mutableStateOf(false) }
    val swipeOffset = remember { Animatable(0f) }
    val rowScope = rememberCoroutineScope()
    val swipeThreshold = with(LocalDensity.current) { 112.dp.toPx() }
    val swipeText = stringResource(R.string.cd_remove_from_queue, item.title)
    val currentOnLift by rememberUpdatedState(onLift)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDropped by rememberUpdatedState(onDropped)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val shift = remember { Animatable(0f) }
    LaunchedEffect(displacedY, reordering, lifted) {
        if (lifted) return@LaunchedEffect
        if (reordering) {
            shift.animateTo(
                displacedY,
                androidx.compose.animation.core.spring(
                    dampingRatio = 0.9f,
                    stiffness = 380f,
                ),
            )
        } else {
            shift.snapTo(0f)
        }
    }
    val heldGlass = lifted && glassHeld
    val heldShape = remember { com.kyant.capsule.ContinuousRoundedRectangle(Dimens.radiusXLarge) }
    Box(Modifier.fillMaxWidth().zIndex(if (lifted) 1f else 0f)) {
        if (swipeOffset.value != 0f) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimens.radiusSmall))
                    .background(colors.error.copy(alpha = 0.28f))
                    .padding(horizontal = Dimens.screenEdge),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (swipeOffset.value > 0f) {
                    Arrangement.Start
                } else {
                    Arrangement.End
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = swipeText,
                    tint = colors.error,
                    modifier = Modifier.size(Dimens.iconControl),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(QUEUE_ROW_HEIGHT)
                .zIndex(if (lifted) 1f else 0f)
                .graphicsLayer {
                    translationY = if (lifted) displacedY else if (reordering) shift.value else 0f
                    translationX = swipeOffset.value
                    alpha = if (hidden) 0f else 1f
                    if (lifted) {
                        shadowElevation = 24f
                        scaleX = QUEUE_ROW_LIFT
                        scaleY = QUEUE_ROW_LIFT
                    }
                }
                .then(
                    if (heldGlass) {
                        Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .glassSurface(heldShape, floating = true)
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = description }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            if (abs(swipeOffset.value) < 1f) currentOnLift()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            currentOnDrag(amount.y)
                        },
                        onDragEnd = { currentOnDropped() },
                        onDragCancel = { currentOnDropped() },
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            rowScope.launch { swipeOffset.snapTo(swipeOffset.value + dragAmount) }
                        },
                        onDragEnd = {
                            rowScope.launch {
                                if (abs(swipeOffset.value) > swipeThreshold) {
                                    swipeOffset.snapTo(0f)
                                    currentOnRemove()
                                } else {
                                    swipeOffset.animateTo(0f)
                                }
                            }
                        },
                    )
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(QUEUE_ROW_HEIGHT)
                    .padding(end = Dimens.touchTarget, start = 0.dp)
                    .focusRing(RectangleShape)
                    .clickableScale(
                        pressedScale = if (lifted) 1f else 0.985f,
                        onClick = onClick,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .padding(start = Dimens.screenEdge)
                        .size(Dimens.artworkRow),
                ) {
                    ArtworkImage(
                        uri = song?.artworkUri,
                        sizePx = ArtworkSize.Row,
                        shape = RoundedCornerShape(Dimens.radiusSmall),
                        modifier = Modifier.fillMaxSize(),
                        seed = song?.album ?: item.title,
                    )
                    if (current) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(Dimens.radiusSmall))
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            EqualizerBars(playing = playing, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    AppText(
                        text = item.title,
                        style = type.bodyStrong,
                        color = if (current || lifted) colors.accent else colors.textPrimary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            text = item.artist,
                            style = type.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f, fill = false),
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!originManual) {
                            Spacer(Modifier.width(Dimens.sm))
                            OriginChip(label = stringResource(R.string.queue_auto_chip))
                        }
                    }
                }
                Spacer(Modifier.width(Dimens.sm))
                if (song != null) {
                    AppText(
                        text = formatTime(song.durationMs),
                        style = type.numeric,
                        color = colors.textSecondary,
                        overflow = TextOverflow.Clip,
                    )
                }
                Spacer(Modifier.width(Dimens.sm))
            }
        }
        SongActionsOverflow(
            songTitle = item.title,
            onOpen = { menuOpen = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Dimens.xs),
        )
        SongActionsMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            song = songOrDefault(item, song),
            onPlayNow = onClick,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onPlayNext = onPlayNext,
            onAddToQueue = null,
            favorite = if (song != null) favorite else null,
            onToggleFavorite = onToggleFavorite,
            onOpenDetails = onOpenDetails,
            onRemove = onRemove,
        )
    }
}
@Composable
private fun OriginChip(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppTheme.colors.surfaceHigh)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        AppText(
            text = label,
            style = AppTheme.type.caption,
            color = AppTheme.colors.textSecondary,
        )
    }
}
