package com.dopavin.ui.screens
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.dopavin.CrashReporter
import com.dopavin.R
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.ScreenHeader
import com.dopavin.ui.components.SettingsRow
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import java.text.DateFormat
import java.util.Date
@Composable
fun CrashLogScreen(actions: MusicActions) {
    val context = LocalContext.current
    val entries = remember { mutableStateListOf<CrashReporter.Entry>().also { it.addAll(CrashReporter.history(context)) } }
    var openFile by remember { mutableStateOf<String?>(null) }
    val colors = AppTheme.colors
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(onBack = actions::back, title = stringResource(R.string.crash_log_title))
        AppText(
            text = stringResource(R.string.crash_log_native),
            style = AppTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs),
        )
        if (entries.isEmpty()) {
            AppText(
                text = stringResource(R.string.crash_log_empty),
                style = AppTheme.type.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
            )
        } else {
            Row(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm)) {
                TonalButton(
                    text = stringResource(R.string.crash_log_clear),
                    onClick = {
                        CrashReporter.clearHistory(context)
                        entries.clear()
                    },
                )
            }
        }
        entries.toList().forEach { entry ->
            val key = entry.file.name
            SettingsRow(
                title = entry.headline.ifBlank { key },
                subtitle = dateFormat.format(Date(entry.time)),
                onClick = { openFile = if (openFile == key) null else key },
                chevron = false,
            )
            if (openFile == key) {
                Column(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm)) {
                    SelectionContainer {
                        AppText(
                            text = entry.text,
                            style = AppTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = Dimens.sm),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    ) {
                        TonalButton(
                            text = stringResource(R.string.crash_copy),
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Dopavin crash report", entry.text))
                            },
                        )
                        TonalButton(
                            text = stringResource(R.string.crash_share),
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, entry.text)
                                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                        )
                        TonalButton(
                            text = stringResource(R.string.crash_log_delete),
                            onClick = {
                                CrashReporter.deleteHistory(entry)
                                entries.remove(entry)
                                openFile = null
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(Dimens.xl))
    }
}
