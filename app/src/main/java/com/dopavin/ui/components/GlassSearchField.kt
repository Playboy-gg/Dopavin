package com.dopavin.ui.components
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.dopavin.R
import com.dopavin.ui.glass.glassSurface
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
@Composable
fun GlassSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val focusManager: FocusManager = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .height(Dimens.searchFieldHeight)
            .glassSurface(CircleShape, fallback = colors.surfaceHigh)
            .padding(start = Dimens.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(Dimens.iconControl),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = Dimens.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                AppText(hint, AppTheme.type.body, color = colors.textSecondary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = AppTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(); focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hint },
            )
        }
        if (query.isNotEmpty()) {
            IconAction(
                icon = R.drawable.ic_close,
                description = stringResource(R.string.cd_clear_text),
                onClick = { onQueryChange("") },
                tint = colors.textSecondary,
            )
        }
    }
}
