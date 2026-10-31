package com.dopavin.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.dp
import com.dopavin.data.AppSettings
import com.dopavin.ui.glass.backdrop.Backdrop
import com.dopavin.ui.glass.backdrop.backdrops.LayerBackdrop
import com.dopavin.ui.glass.backdrop.backdrops.layerBackdrop
import com.dopavin.ui.glass.backdrop.drawBackdrop
import com.dopavin.ui.glass.backdrop.effects.blur
import com.dopavin.ui.glass.backdrop.effects.colorControls
import com.dopavin.ui.glass.backdrop.effects.lens
import com.dopavin.ui.glass.backdrop.highlight.Highlight
import com.dopavin.ui.glass.backdrop.isRenderEffectSupported
import com.dopavin.ui.glass.backdrop.shadow.Shadow
import com.dopavin.ui.theme.AppTheme

/**
 * Resolved liquid-glass look, derived from [AppSettings]. [enabled] is false when the user turned
 * glass off, the device is older than Android 12 (no RenderEffect blur) or animations are reduced;
 * every glass surface then draws its solid fallback colour instead.
 */
@Immutable
data class GlassStyle(
    val enabled: Boolean,
    val blurDp: Float,
    val lensDp: Float,
    val refraction: Float,
    val tint: Float,
    val saturation: Float,
    val highlight: Float,
    val shadow: Float,
    val scale: Float,
    val dispersion: Boolean,
) {
    companion object {
        val Off = GlassStyle(false, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, false)
    }
}

fun AppSettings.toGlassStyle(reduceMotion: Boolean): GlassStyle = GlassStyle(
    enabled = glassEnabled && !reduceMotion && isRenderEffectSupported(),
    blurDp = glassBlur.toFloat(),
    lensDp = glassLensHeight.toFloat(),
    refraction = glassRefraction / 100f,
    tint = glassTint / 100f,
    saturation = glassSaturation / 100f,
    highlight = glassHighlight / 100f,
    shadow = glassShadow / 100f,
    scale = glassQuality / 100f,
    dispersion = glassDispersion,
)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.Off }

/** What glass surfaces at the current spot sample: the ambient backdrop, or ambient + screen content. */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Glass surface: blurs / refracts [LocalGlassBackdrop] inside [shape]. Draws [fallback] as a plain
 * fill when glass is off or unsupported, so callers can use it instead of `clip + background`.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    fallback: Color = AppTheme.colors.surfaceRaised,
    tint: Color = AppTheme.colors.surface,
    floating: Boolean = false,
    /** Sample this backdrop instead of [LocalGlassBackdrop] (used by the sliding nav indicator). */
    backdrop: Backdrop? = null,
    blurDp: Float? = null,
    tintAlpha: Float? = null,
    lensScale: Float = 1f,
): Modifier {
    val style = LocalGlassStyle.current
    val source = backdrop ?: LocalGlassBackdrop.current
    if (!style.enabled || source == null) {
        val base = if (floating) this.shadow(10.dp, shape) else this
        return base.clip(shape).background(fallback)
    }
    val scale = style.scale
    return this.drawBackdrop(
        backdrop = source,
        shape = { shape },
        effects = {
            if (style.saturation > 1.001f) colorControls(saturation = style.saturation)
            blur((blurDp ?: style.blurDp).dp.toPx() * scale)
            // The effects block also runs once before the first layout, when size is unspecified.
            if (size.isSpecified && size.minDimension > 0f) {
                val maxLens = size.minDimension / 2f
                val lensPx = minOf(style.lensDp * lensScale * density * scale, maxLens)
                lens(
                    refractionHeight = lensPx,
                    refractionAmount = style.refraction * lensScale * 40.dp.toPx() * scale,
                    depthEffect = true,
                    chromaticAberration = style.dispersion,
                )
            }
        },
        highlight = {
            if (style.highlight > 0f) Highlight(width = 1.dp, alpha = style.highlight) else null
        },
        shadow = {
            if (floating && style.shadow > 0f) {
                Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.18f), alpha = style.shadow)
            } else {
                null
            }
        },
        onDrawSurface = { drawRect(tint.copy(alpha = tintAlpha ?: style.tint)) },
        backdropScale = scale,
    )
}

/**
 * App-wide backdrop recorded into [backdrop]: the canvas plus, with ambience on, a wash in [wash]
 * (the playing song's artwork colour) that starts at the top edge and fades downwards. With
 * ambience off ([wash] null) it is just the plain canvas (no tint).
 */
@Composable
fun GlassAmbient(backdrop: LayerBackdrop, wash: Color?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
            .ambientBackground(wash),
    )
}

/**
 * The app's ambience as a plain background: canvas plus the artwork wash from the top edge. Used by
 * [GlassAmbient] and by a page that is moving during the back gesture, so the page keeps its ambience
 * while it slides over the page underneath.
 */
@Composable
fun Modifier.ambientBackground(wash: Color?): Modifier {
    val colors = AppTheme.colors
    return this
        .background(colors.canvas)
        .drawBehind {
            if (wash != null) {
                val strength = if (colors.isDark) 1f else 0.75f
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(wash.copy(alpha = 0.55f * strength), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.6f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(wash.copy(alpha = 0.5f * strength), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height * 0.85f,
                    ),
                )
            }
        }
}
