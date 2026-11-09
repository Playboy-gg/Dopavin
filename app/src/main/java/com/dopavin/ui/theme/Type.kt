package com.dopavin.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dopavin.R

/** Plus Jakarta Sans (SIL Open Font License), bundled in res/font. */
val DopavinFont = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/** Type scale. All sizes in sp so they follow the user's font scale. */
@Immutable
data class AppType(
    /** Screen titles on Home / Library / Search. */
    val display: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold,
    ),
    /** Track title on Now Playing, collection titles. */
    val headline: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
    ),
    /** Section headers. */
    val title: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal,
    ),
    /** Row titles. */
    val bodyStrong: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
    ),
    /** Bottom navigation labels, alphabet index. */
    val caption: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
    ),
    /** Tabular figures so changing digits do not make the text jitter. */
    val numeric: TextStyle = TextStyle(
        fontFamily = DopavinFont,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
)

val LocalAppType = staticCompositionLocalOf { AppType() }
