package com.dopavin.ui.components
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
fun Modifier.meltIntoPage(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Black,
                0.42f to Color.Black,
                0.55f to Color.Black.copy(alpha = 0.88f),
                0.67f to Color.Black.copy(alpha = 0.64f),
                0.78f to Color.Black.copy(alpha = 0.38f),
                0.88f to Color.Black.copy(alpha = 0.16f),
                0.95f to Color.Black.copy(alpha = 0.04f),
                1.00f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
@Composable
fun CoverHero(
    title: String,
    subtitle: String?,
    cover: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        Box(Modifier.fillMaxSize().meltIntoPage()) { cover() }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenEdge)
                .padding(bottom = Dimens.md),
        ) {
            AppText(
                text = title,
                style = AppTheme.type.display,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (!subtitle.isNullOrBlank()) {
                AppText(text = subtitle, style = AppTheme.type.caption, color = AppTheme.colors.textSecondary)
            }
            actions()
        }
    }
}
