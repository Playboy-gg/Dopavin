package com.dopavin.ui.components
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
@Composable
fun ScreenHeader(
    onBack: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    FloatingBackButton(onBack)
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Spacer(Modifier.height(Dimens.touchTarget + Dimens.sm))
        Row(
            Modifier.fillMaxWidth().padding(start = Dimens.screenEdge, end = Dimens.sm, top = Dimens.sm, bottom = Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).semantics { heading() }) {
                AppText(text = title, style = AppTheme.type.display, maxLines = 2)
                if (subtitle != null) {
                    AppText(text = subtitle, style = AppTheme.type.bodySmall, color = AppTheme.colors.textSecondary)
                }
            }
            actions()
        }
    }
}
