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
@Stable
class ArtworkFlight {
    var miniRect by mutableStateOf<Rect?>(null)
    var playerRect by mutableStateOf<Rect?>(null)
    var playerScale by mutableFloatStateOf(1f)
    var sheet: () -> Float = { 1f }
    val land = androidx.compose.animation.core.Animatable(1f)
    var enabled: Boolean = true
    var coverVisible by mutableStateOf(true)
    var playerCoords: LayoutCoordinates? = null
    var playerHost: LayoutCoordinates? = null
    fun updatePlayerRect() {
        val cover = playerCoords ?: return
        val host = playerHost ?: return
        if (!cover.isAttached || !host.isAttached) return
        val origin = host.localPositionOf(cover, Offset.Zero)
        val rect = Rect(origin, cover.size.toSize())
        val w = host.size.width.toFloat()
        val h = host.size.height.toFloat()
        val ok = rect.width > 1f && rect.height > 1f &&
            rect.left >= -1f && rect.top >= -1f && rect.right <= w + 1f && rect.bottom <= h + 1f
        if (ok) {
            playerRect = rect
        } else {
            Log.w(TAG, "expanded rect rejected: $rect host=${w}x$h sheet=${sheet()}")
        }
    }
    fun settlePlayer() {
        val c = playerCoords ?: return
        if (!c.isAttached) return
        val rect = c.boundsInRoot()
        if (rect.width > 1f && rect.height > 1f) {
            playerRect = rect
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
fun Modifier.flightMini(flight: ArtworkFlight?): Modifier =
    if (flight == null) this else this
        .onGloballyPositioned {
            if (flight.sheet() >= 1f) {
                val r = it.boundsInRoot()
                if (r.width > 1f && r.height > 1f) {
                    flight.miniRect = r
                }
            }
        }
        .graphicsLayer {
            alpha = if (flight.flying) 0f else 1f
            val k = flight.land.value
            scaleX = k
            scaleY = k
        }
fun Modifier.flightPlayerHost(flight: ArtworkFlight?): Modifier =
    if (flight == null) this else this.onGloballyPositioned {
        flight.playerHost = it
        flight.updatePlayerRect()
    }
fun Modifier.flightPlayer(flight: ArtworkFlight?): Modifier =
    if (flight == null) this else this
        .onGloballyPositioned {
            flight.playerCoords = it
            flight.updatePlayerRect()
        }
        .graphicsLayer { alpha = if (flight.flying) 0f else 1f }
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
                modifier = Modifier.fillMaxSize(),
                seed = seed,
            )
        }
    }
}
private fun restRect(player: Rect, coverScale: Float): Rect {
    val halfW = player.width * coverScale / 2f
    val halfH = player.height * coverScale / 2f
    return Rect(player.center.x - halfW, player.center.y - halfH, player.center.x + halfW, player.center.y + halfH)
}
