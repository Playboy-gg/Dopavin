package com.dopavin.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.SpotifyImport
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.GlassDialog
import com.dopavin.ui.components.glassInk
import com.dopavin.ui.theme.AppTheme
@Composable
fun SpotifyImportDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    val valid = SpotifyImport.parseLink(link) != null
    val ink = glassInk()
    val colors = AppTheme.colors
    GlassDialog(
        title = stringResource(R.string.spotify_import_title),
        body = stringResource(R.string.spotify_import_body),
        confirmLabel = stringResource(R.string.spotify_import_start),
        confirmEnabled = valid,
        onConfirm = { onImport(link) },
        onDismiss = onDismiss,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(ink.copy(alpha = 0.10f))
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (link.isEmpty()) {
                AppText(stringResource(R.string.spotify_import_hint), AppTheme.type.body, color = ink.copy(alpha = 0.55f))
            }
            BasicTextField(
                value = link,
                onValueChange = { link = it },
                singleLine = true,
                textStyle = AppTheme.type.body.copy(color = ink),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (link.isNotBlank() && !valid) {
            AppText(stringResource(R.string.spotify_import_bad_link), AppTheme.type.bodySmall, color = colors.error)
        }
    }
}
