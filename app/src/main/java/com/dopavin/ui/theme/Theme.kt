package com.dopavin.ui.theme
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppType
        @Composable @ReadOnlyComposable get() = LocalAppType.current
    val motion: MotionSpec
        @Composable @ReadOnlyComposable get() = LocalMotion.current
}
@Composable
fun MusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    accentTint: Int = 0,
    artworkTint: Color? = null,
    content: @Composable () -> Unit,
) {
    val baseColors = when {
        !darkTheme -> LightColors
        pureBlack -> DarkColors.copy(canvas = Color.Black, surface = Color(0xFF050507))
        else -> DarkColors
    }
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val derived = com.dopavin.data.AccentTint.baseFor(accentTint, artworkTint?.toArgb())
        ?.let { com.dopavin.data.AccentTint.derive(it, darkTheme) }
    val fade = androidx.compose.animation.core.tween<Color>(if (reduceMotion) 0 else 450)
    val accent by androidx.compose.animation.animateColorAsState(derived?.let { Color(it.accent) } ?: baseColors.accent, fade, label = "tintAccent")
    val accentStart by androidx.compose.animation.animateColorAsState(derived?.let { Color(it.start) } ?: baseColors.accentStart, fade, label = "tintStart")
    val accentEnd by androidx.compose.animation.animateColorAsState(derived?.let { Color(it.end) } ?: baseColors.accentEnd, fade, label = "tintEnd")
    val onAccent by androidx.compose.animation.animateColorAsState(derived?.let { Color(it.onAccent) } ?: baseColors.onAccent, fade, label = "tintOn")
    val focusRing by androidx.compose.animation.animateColorAsState(derived?.let { Color(it.focusRing) } ?: baseColors.focusRing, fade, label = "tintFocus")
    val colors = baseColors.copy(accent = accent, accentStart = accentStart, accentEnd = accentEnd, onAccent = onAccent, focusRing = focusRing)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.surfaceRaised,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.controlOutline,
            outlineVariant = colors.divider,
            error = colors.error,
            inverseSurface = colors.textPrimary,
            inverseOnSurface = colors.canvas,
            inversePrimary = colors.accent,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.surfaceRaised,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.controlOutline,
            outlineVariant = colors.divider,
            error = colors.error,
            inverseSurface = colors.textPrimary,
            inverseOnSurface = colors.canvas,
            inversePrimary = colors.accent,
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppType provides AppType(),
        LocalMotion provides MotionSpec(reduceMotion),
    ) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
                content()
            }
        }
    }
}
