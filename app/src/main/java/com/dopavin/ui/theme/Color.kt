package com.dopavin.ui.theme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
@Immutable
data class AppColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val controlOutline: Color,
    val accent: Color,
    val accentStart: Color,
    val accentEnd: Color,
    val onAccent: Color,
    val error: Color,
    val focusRing: Color,
    val isDark: Boolean,
) {
    val gradient: Brush get() = Brush.linearGradient(listOf(accentStart, accentEnd))
    val gradientHorizontal: Brush get() = Brush.horizontalGradient(listOf(accentStart, accentEnd))
}
val DarkColors = AppColors(
    canvas = Color(0xFF0B0B10),
    surface = Color(0xFF14141B),
    surfaceRaised = Color(0xFF1C1C25),
    surfaceHigh = Color(0xFF262632),
    textPrimary = Color(0xFFF6F4F1),
    textSecondary = Color(0xFFA7A5B3),
    divider = Color(0xFF2A2A36),
    controlOutline = Color(0xFF7C7A8C),
    accent = Color(0xFFFF6A55),
    accentStart = Color(0xFFFF8A4C),
    accentEnd = Color(0xFFFF3D81),
    onAccent = Color(0xFF1A0A10),
    error = Color(0xFFFF6B6B),
    focusRing = Color(0xFFFFB199),
    isDark = true,
)
val LightColors = AppColors(
    canvas = Color(0xFFFAF7F5),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF2EEEB),
    surfaceHigh = Color(0xFFE9E4E0),
    textPrimary = Color(0xFF17151B),
    textSecondary = Color(0xFF5E5B69),
    divider = Color(0xFFE2DCD7),
    controlOutline = Color(0xFF85828F),
    accent = Color(0xFFC7203F),
    accentStart = Color(0xFFCC4108),
    accentEnd = Color(0xFFC7203F),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    focusRing = Color(0xFFC7203F),
    isDark = false,
)
val LocalAppColors = staticCompositionLocalOf { DarkColors }
