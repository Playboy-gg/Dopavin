package com.dopavin.ui.theme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dopavin.R
val DopavinFont = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)
@Immutable
data class AppType(
    val display: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold,
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal,
    ),
    val bodyStrong: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = DopavinFont, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
    ),
    val numeric: TextStyle = TextStyle(
        fontFamily = DopavinFont,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
)
val LocalAppType = staticCompositionLocalOf { AppType() }
