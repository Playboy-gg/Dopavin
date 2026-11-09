package com.dopavin.ui.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.dopavin.data.ArtworkSize
import com.dopavin.ui.motion.MotionMath
import com.dopavin.ui.motion.MotionTokens
import com.dopavin.ui.theme.Dimens

/**
 * Shared-element morph of the cover between the mini player and Now Playing. Both real covers report where
 * they sit (only while settled); while the sheet is between "held" and "closed" ONE flying cover is drawn at
 * the root, interpolated between the two rectangles by the sheet value, and the real ones are hidden.
 * Without both rectangles (first open since launch) nothing flies and the covers simply show as before.
 */
@Stable
class ArtworkFlight {
    var miniRect by mutableStateOf<Rect?>(null)
    var playerRect by mutableStateOf<Rect?>(null)
    /** Pause shrink of the real player cover (0.88 while paused), so the flight lands on what is really there. */
    var playerScale by mutableFloatStateOf(1f)
    /** 0 = Now Playing open, 1 = closed. Set once by the root. */
    var sheet: () -> Float = { 1f }

    /** Landing spring of the mini cover (scale, 1 = rest); the root kicks it when the cover has arrived. */
    val land = androidx.compose.animation.core.Animatable(1f)

    /** Off while Reduce Motion is on: nothing flies, nothing is hidden. Set by the root. */
    var enabled: Boolean = true

    /**
     * False while Now Playing shows lyrics or the queue: the big cover is not on screen then, so no cover
     * may fly in (back gesture, close) over the lyrics.
     */
    var coverVisible by mutableStateOf(true)

    /** Live handles: the big cover, and the full-screen box that holds Now Playing (it carries the sheet layer). */
    var playerCoords: LayoutCoordinates? = null
    var playerHost: LayoutCoordinates? = null

    /**
     * The big cover's resting rectangle in root coordinates, measured from the real layout.
     *
     * Coordinate facts (read from Compose's NodeCoordinator / NodeChain source): a LayoutCoordinates maps through
     * the graphicsLayer of EVERY coordinator it sits inside, in either modifier order, so `boundsInRoot()` of the
     * cover contains the sheet's translation/scale. [playerHost] is attached to the host's inner coordinator,
     * i.e. INSIDE the sheet's graphicsLayer, so `host.localPositionOf(cover)` is the cover's place without the
     * sheet transform, at any sheet value, including the first frame after the player is composed. The host fills
     * the root at (0,0), so that is also its place in root space.
     */
    fun updatePlayerRect() {
        val cover = playerCoords ?: return
        val host = playerHost ?: return
        if (!cover.isAttached || !host.isAttached) return
        val origin = host.localPositionOf(cover, Offset.Zero)
        val rect = Rect(origin, cover.size.toSize())
        // Reject anything that cannot be a cover on this screen instead of flying to nowhere.
        val w = host.size.width.toFloat()
        val h = host.size.height.toFloat()
        val ok = rect.width > 1f && rect.height > 1f &&
            rect.left >= -1f && rect.top >= -1f && rect.right <= w + 1f && rect.bottom <= h + 1f
        if (ok) {
            playerRect = rect
            Log.d(TAG, "expanded rect (host-relative) = $rect host=${w}x$h sheet=${sheet()}")
        } else {
            Log.w(TAG, "expanded rect rejected: $rect host=${w}x$h sheet=${sheet()}")
        }
    }

    /**
     * Second, independent reading: when the sheet is fully open nothing transforms the cover except its own
     * pause shrink, so `boundsInRoot()` is exact. The root calls this when the sheet reaches 0.
     */
    fun settlePlayer() {
        val c = playerCoords ?: return
        if (!c.isAttached) return
        val rect = c.boundsInRoot()
        if (rect.width > 1f && rect.height > 1f) {
            playerRect = rect
            Log.d(TAG, "expanded rect (settled, boundsInRoot) = $rect")
        }
    }

    val flying: Boolean
        get() {
            if (!enabled || !coverVisible) return false
            val s = sheet()
            return s > 0.002f && s < 0.998f && miniRect != null && playerRect != null
        }

    private companion object {
        const val TAG = "ArtworkFlight"
    }
}

val LocalArtworkFlight = staticCompositionLocalOf<ArtworkFlight?> { null }

/** The cover inside the mini player. */
fun Modifier.flightMini(flight: ArtworkFlight?): Modifier =
    if (flight == null) this else this
        .onGloballyPositioned {
            if (flight.sheet() >= 1f) {
                val r = it.boundsInRoot()
                if (r.width > 1f && r.height > 1f) {
                    flight.miniRect = r
                    Log.d("ArtworkFlight", "mini rect = $r")
                }
            }
        }
        .graphicsLayer {
            alpha = if (flight.flying) 0f else 1f
            val k = flight.land.value
            scaleX = k
            scaleY = k
        }

/** The full-screen box around Now Playing; apply it AFTER the sheet's graphicsLayer (inside the sheet transform). */
fun Modifier.flightPlayerHost(flight: ArtworkFlight?): Modifier =
    if (flight == null) this else this.onGloballyPositioned {
        flight.playerHost = it
        flight.updatePlayerRect()
    }

/** The big cover in Now Playing (apply before its own scale / swipe transforms). */
fun Modifier.flightPlayer(flight: ArtworkFlight?): Modifier =
    if (flight == null) this else this
        .onGloballyPositioned {
            flight.playerCoords = it
            flight.updatePlayerRect()
        }
        .graphicsLayer { alpha = if (flight.flying) 0f else 1f }

/**
 * Draws the flying cover. It leaves the big cover's own place (including the pause shrink) and reaches the mini
 * player's cover as the sheet goes 0 -> 1, travelling LINEARLY with the sheet value, so it covers less distance
 * than the sheet itself (the sheet goes the whole screen height, the cover only down to the mini player):
 * it trails the player, follows the finger in the same direction, and opening is exactly the reverse.
 */
@Composable
fun ArtworkFlightLayer(flight: ArtworkFlight, uri: Uri?, seed: String) {
    val show by remember(flight) { derivedStateOf { flight.flying } }
    if (!show) return
    val player = flight.playerRect ?: return
    val density = LocalDensity.current
    val playerCorner = with(density) { Dimens.radiusXLarge.toPx() }
    val miniCorner = with(density) { Dimens.radiusMedium.toPx() }
    val shadowPx = with(density) { 28.dp.toPx() }
    Box(
        Modifier
            .size(with(density) { player.width.toDp() }, with(density) { player.height.toDp() })
            .graphicsLayer {
                val q = MotionMath.flightFraction(flight.sheet())
                val r = lerp(restRect(player, flight.playerScale), flight.miniRect ?: player, q)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = r.left
                translationY = r.top
                val k = r.width / player.width
                scaleX = k
                scaleY = k
                shadowElevation = (1f - q) * shadowPx
                // The radius is applied before scaling, so divide by the scale to get the visible radius.
                shape = RoundedCornerShape(MotionMath.lerp(playerCorner, miniCorner, q) / k)
                clip = false
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val q = MotionMath.flightFraction(flight.sheet())
                    val r = lerp(restRect(player, flight.playerScale), flight.miniRect ?: player, q)
                    val k = r.width / player.width
                    shape = RoundedCornerShape(MotionMath.lerp(playerCorner, miniCorner, q) / k)
                    clip = true
                },
        ) {
            ArtworkImage(
                uri = uri,
                sizePx = ArtworkSize.Player,
                shape = RectangleShape,
                modifier = Modifier
                    .fillMaxSize(),
                seed = seed,
            )
        }
    }
}

/** The big cover as it really sits when Now Playing is fully open (its pause shrink is about its centre). */
private fun restRect(player: Rect, coverScale: Float): Rect {
    val halfW = player.width * coverScale / 2f
    val halfH = player.height * coverScale / 2f
    return Rect(player.center.x - halfW, player.center.y - halfH, player.center.x + halfW, player.center.y + halfH)
}
