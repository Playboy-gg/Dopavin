package com.dopavin.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import com.dopavin.R
import com.dopavin.data.UpdateCheck
import com.dopavin.data.UpdateStep
import com.dopavin.data.UpdateUi
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.GlassDialog
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/**
 * "Update available": shown when the automatic check finds a newer release. It carries the whole update in
 * one place: the version, the size, the release notes and the one button that fits the download step
 * (Update now > progress > Install, or Try again after a failure). "Later" hides it until the next automatic
 * check; "Skip this version" hides it for that release for good. The same actions are on Settings > Updates.
 */
@Composable
fun UpdateDialog(update: UpdateUi, actions: MusicActions) {
    val release = update.release ?: return
    val context = LocalContext.current
    val installed = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    val versionLine = stringResource(R.string.update_version_line, release.tag)
    val installedLine = if (installed.isNotEmpty()) stringResource(R.string.update_installed_version, installed) else null
    val sizeLine = if (release.sizeBytes > 0) updateSizeText(release.sizeBytes) else null
    val notesTitle = stringResource(R.string.update_whats_new)
    val noNotes = stringResource(R.string.update_no_notes)
    val notes = remember(release.notes) { UpdateCheck.plainNotes(release.notes) }
    val error = if (update.step == UpdateStep.Failed && update.errorRes != 0) stringResource(update.errorRes) else null

    val body = buildString {
        append(versionLine)
        val meta = listOfNotNull(installedLine, sizeLine)
        if (meta.isNotEmpty()) append("\n").append(meta.joinToString("  ·  "))
        if (error != null) append("\n\n").append(error)
        append("\n\n").append(notesTitle).append("\n")
        if (notes.isEmpty()) {
            append(noNotes)
        } else {
            append(notes.joinToString("\n") { line -> (if (line.bullet) "• " else "") + line.text })
        }
    }

    val percent = (update.progress * 100).toInt().coerceIn(0, 100)
    val hasApk = release.apkUrl != null
    val confirmLabel = when (update.step) {
        UpdateStep.Ready -> stringResource(R.string.update_install)
        UpdateStep.Downloading ->
            stringResource(if (update.progress > 0f) R.string.update_downloading_percent else R.string.update_downloading, percent)
        UpdateStep.Failed -> stringResource(if (hasApk) R.string.update_retry else R.string.update_open_page)
        UpdateStep.None -> stringResource(if (hasApk) R.string.update_dialog_update else R.string.update_open_page)
    }
    val onConfirm: () -> Unit = when (update.step) {
        UpdateStep.Ready -> actions::installUpdate
        UpdateStep.Downloading -> ({})
        else -> actions::startUpdateDownload
    }

    GlassDialog(
        title = stringResource(R.string.update_dialog_title),
        onDismiss = actions::dismissUpdateDialog,
        dismissLabel = stringResource(R.string.update_later),
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        confirmEnabled = update.step != UpdateStep.Downloading,
        body = body,
    ) {
        // Skipping only makes sense before a download has started.
        if (update.step == UpdateStep.None || update.step == UpdateStep.Failed) {
            AppText(
                stringResource(R.string.update_skip_version),
                AppTheme.type.bodyStrong,
                color = AppTheme.colors.accent,
                modifier = Modifier
                    .clickableScale(pressedScale = 0.97f) { actions.skipUpdateVersion() }
                    .padding(vertical = Dimens.sm),
            )
        }
    }
}

/** "12.3 MB"; sizes below 1 MB are shown in KB. (Its own name: other screens have private helpers called sizeText.) */
private fun updateSizeText(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    else String.format(java.util.Locale.getDefault(), "%d KB", (bytes / 1024).coerceAtLeast(1))
}
