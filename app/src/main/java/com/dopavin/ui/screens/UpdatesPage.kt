package com.dopavin.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ReleaseInfo
import com.dopavin.data.UpdateCheck
import com.dopavin.data.UpdateStatus
import com.dopavin.data.UpdateStep
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.GradientButton
import com.dopavin.ui.components.LocalUpdate
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.ToggleRow
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
@Composable
fun UpdatesPage(actions: MusicActions, autoUpdateCheck: Boolean, match: (String) -> Boolean) {
    val update = LocalUpdate.current
    val context = LocalContext.current
    val installed = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val checkLabel = stringResource(R.string.update_check_now)
    val autoLabel = stringResource(R.string.update_auto)
    val fullPage = match("")
    val release = update.release
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.screenEdge)) {
        if (fullPage) {
            if (update.status == UpdateStatus.Available && release != null) {
                AvailableBlock(update.step, update.progress, update.errorRes, release, installed, actions)
            } else {
                IdleBlock(update.status, installed)
            }
            Spacer(Modifier.height(Dimens.lg))
        }
        val checking = update.status == UpdateStatus.Checking
        val busy = update.step == UpdateStep.Downloading
        if (match(checkLabel) && !busy) {
            TonalButton(
                text = stringResource(if (checking) R.string.update_checking else R.string.update_check_now),
                icon = R.drawable.ic_download,
                onClick = { if (!checking) actions.checkForUpdates() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.sm))
        }
    }
    if (match(autoLabel)) {
        ToggleRow(
            title = autoLabel,
            subtitle = stringResource(if (autoUpdateCheck) R.string.update_auto_on else R.string.update_auto_off),
            checked = autoUpdateCheck,
            onCheckedChange = actions::setAutoUpdateCheck,
        )
    }
}
@Composable
private fun IdleBlock(status: UpdateStatus, installed: String) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Dimens.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Hero(R.drawable.ic_check.takeIf { status == UpdateStatus.UpToDate } ?: R.drawable.ic_download)
        Spacer(Modifier.height(Dimens.lg))
        AppText(
            stringResource(
                when (status) {
                    UpdateStatus.Checking -> R.string.update_checking
                    UpdateStatus.UpToDate -> R.string.update_up_to_date
                    UpdateStatus.Failed -> R.string.update_failed
                    else -> R.string.update_idle
                },
            ),
            AppTheme.type.title,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
        if (installed.isNotEmpty()) {
            AppText(
                stringResource(R.string.update_installed_version, installed),
                AppTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Dimens.xs),
            )
        }
    }
}
@Composable
private fun AvailableBlock(
    step: UpdateStep,
    progress: Float,
    errorRes: Int,
    release: ReleaseInfo,
    installed: String,
    actions: MusicActions,
) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Dimens.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Hero(R.drawable.ic_download)
        Spacer(Modifier.height(Dimens.lg))
        AppText(
            stringResource(R.string.update_version_line, release.tag),
            AppTheme.type.title,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        val meta = buildList {
            if (installed.isNotEmpty()) add(stringResource(R.string.update_installed_version, installed))
            if (release.sizeBytes > 0) add(sizeText(release.sizeBytes))
        }.joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            AppText(meta, AppTheme.type.caption, color = colors.textSecondary, modifier = Modifier.padding(top = Dimens.xs))
        }
        Spacer(Modifier.height(Dimens.xl))
        when (step) {
            UpdateStep.None, UpdateStep.Failed -> {
                if (step == UpdateStep.Failed && errorRes != 0) {
                    AppText(
                        stringResource(errorRes),
                        AppTheme.type.body,
                        color = colors.error,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        modifier = Modifier.padding(bottom = Dimens.md),
                    )
                }
                val hasApk = release.apkUrl != null
                GradientButton(
                    text = stringResource(
                        when {
                            !hasApk -> R.string.update_open_page
                            step == UpdateStep.Failed -> R.string.update_retry
                            else -> R.string.update_download
                        },
                    ),
                    icon = R.drawable.ic_download,
                    onClick = actions::startUpdateDownload,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            UpdateStep.Downloading -> {
                val percent = (progress * 100).toInt().coerceIn(0, 100)
                val desc = stringResource(R.string.update_downloading_cd, percent)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(colors.textSecondary.copy(alpha = 0.2f))
                        .semantics { contentDescription = desc },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (progress > 0f) progress.coerceIn(0.02f, 1f) else 0.02f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
                AppText(
                    stringResource(if (progress > 0f) R.string.update_downloading_percent else R.string.update_downloading, percent),
                    AppTheme.type.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Dimens.sm, bottom = Dimens.md),
                )
                TonalButton(
                    text = stringResource(R.string.update_cancel),
                    icon = R.drawable.ic_close,
                    onClick = actions::cancelUpdateDownload,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            UpdateStep.Ready -> {
                AppText(
                    stringResource(R.string.update_ready),
                    AppTheme.type.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.padding(bottom = Dimens.md),
                )
                GradientButton(
                    text = stringResource(R.string.update_install),
                    icon = R.drawable.ic_check,
                    onClick = actions::installUpdate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val lines = remember(release.notes) { UpdateCheck.plainNotes(release.notes) }
        Spacer(Modifier.height(Dimens.xl))
        AppText(
            stringResource(R.string.update_whats_new),
            AppTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.sm))
        if (lines.isEmpty()) {
            AppText(
                stringResource(R.string.update_no_notes),
                AppTheme.type.body,
                color = colors.textSecondary,
                maxLines = Int.MAX_VALUE,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                lines.forEach { line ->
                    Row(Modifier.fillMaxWidth()) {
                        if (line.bullet) {
                            AppText("•", AppTheme.type.body, color = colors.accent, modifier = Modifier.padding(end = Dimens.sm))
                        }
                        AppText(
                            text = line.text,
                            style = if (line.heading) AppTheme.type.bodyStrong.copy(fontWeight = FontWeight.Bold) else AppTheme.type.body,
                            maxLines = Int.MAX_VALUE,
                            modifier = Modifier.weight(1f).padding(top = if (line.heading) Dimens.sm else 0.dp),
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun Hero(icon: Int) {
    val colors = AppTheme.colors
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
    }
}
private fun sizeText(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    else String.format(java.util.Locale.getDefault(), "%d KB", (bytes / 1024).coerceAtLeast(1))
}
