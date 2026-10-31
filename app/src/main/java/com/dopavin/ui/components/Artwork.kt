package com.dopavin.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.dopavin.R
import com.dopavin.data.ArtworkLoader
import com.dopavin.data.ArtworkTint
import com.dopavin.ui.theme.AppTheme

private val placeholderPairs = listOf(
    Color(0xFF3A2C6B) to Color(0xFF8E3B8C),
    Color(0xFF0F4C5C) to Color(0xFF2A9D8F),
    Color(0xFF6B2C3A) to Color(0xFFE76F51),
    Color(0xFF1F3A5F) to Color(0xFF4D7EA8),
    Color(0xFF5C4410) to Color(0xFFE9A23B),
    Color(0xFF2D4A22) to Color(0xFF6FA861),
    Color(0xFF5B2A86) to Color(0xFFE05A9B),
    Color(0xFF16324F) to Color(0xFF48B5C4),
)

/** A stable gradient for items without artwork, chosen from [seed] so each album looks different. */
fun placeholderBrush(seed: String): Brush {
    val index = (seed.hashCode() and 0x7fffffff) % placeholderPairs.size
    val (a, b) = placeholderPairs[index]
    return Brush.linearGradient(listOf(a, b))
}

/**
 * Album art for an audio URI. While loading, or when there is none, a gradient placeholder with a
 * note glyph is shown. Decorative: the title next to it carries the meaning.
 */
@Composable
fun ArtworkImage(
    uri: Uri?,
    sizePx: Int,
    shape: Shape,
    modifier: Modifier = Modifier,
    seed: String = "",
) {
    val context = LocalContext.current
    val motion = AppTheme.motion
    var bitmap by remember(uri, sizePx) {
        mutableStateOf<Bitmap?>(if (uri != null) ArtworkLoader.peek(uri, sizePx) else null)
    }
    LaunchedEffect(uri, sizePx) {
        if (uri != null && bitmap == null) {
            bitmap = ArtworkLoader.load(context, uri, sizePx)
        }
    }
    val loaded = bitmap
    val alpha by animateFloatAsState(
        targetValue = if (loaded != null) 1f else 0f,
        animationSpec = tween(motion.medium),
        label = "artworkAlpha",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderBrush(seed)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize(0.45f),
        )
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha),
            )
        }
    }
}

/**
 * The tint colour of the artwork behind [uri], or null while unknown / when the art has no usable colour.
 * Keeps the previous colour while the next one is computed so backgrounds never flash.
 */
@Composable
fun rememberArtworkTint(uri: Uri?): Color? {
    val context = LocalContext.current
    var tint by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(uri) {
        if (uri == null) {
            tint = null
        } else {
            val value = ArtworkTint.peek(uri) ?: ArtworkTint.load(context, uri)
            tint = if (value == ArtworkTint.NONE) null else Color(value)
        }
    }
    return tint
}
