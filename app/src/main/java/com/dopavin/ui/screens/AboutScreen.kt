package com.dopavin.ui.screens
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
private data class OpenSourceRepo(val name: String, val note: String, val url: String)
private val OpenSourceRepos = listOf(
    OpenSourceRepo("NewPipe Extractor", "TeamNewPipe · GPL-3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    OpenSourceRepo("Backdrop (liquid glass)", "Kyant0 · Apache-2.0", "https://github.com/Kyant0/AndroidLiquidGlass"),
    OpenSourceRepo("Capsule (smooth shapes)", "Kyant0 · Apache-2.0", "https://github.com/Kyant0/Capsule"),
    OpenSourceRepo("Compose Floating Tab Bar", "Elyes Mansour · Apache-2.0", "https://github.com/elyesmansour/compose-floating-tab-bar"),
    OpenSourceRepo("Lucide icons", "Lucide · ISC", "https://github.com/lucide-icons/lucide"),
    OpenSourceRepo("Plus Jakarta Sans", "Tokotype · OFL-1.1", "https://github.com/tokotype/PlusJakartaSans"),
    OpenSourceRepo("LRCLIB (synced lyrics)", "tranxuanthang", "https://github.com/tranxuanthang/lrclib"),
    OpenSourceRepo("AndroidX Media3", "Google · Apache-2.0", "https://github.com/androidx/media"),
    OpenSourceRepo("Jetpack Compose and AndroidX", "Google · Apache-2.0", "https://github.com/androidx/androidx"),
    OpenSourceRepo("Kotlin", "JetBrains · Apache-2.0", "https://github.com/JetBrains/kotlin"),
    OpenSourceRepo("Kotlin coroutines", "JetBrains · Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
)
private fun openUrl(context: android.content.Context, url: String, noBrowser: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
    }
}
@Composable
fun AboutPage(actions: MusicActions, onOpenPrivacy: () -> Unit) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val sourceUrl = stringResource(R.string.about_source_url).trim()
    val noBrowser = stringResource(R.string.about_no_browser)
    var showLicences by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = Dimens.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val logoLabel = stringResource(R.string.about_logo_cd)
        Box(
            Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(28.dp))
                .semantics { contentDescription = logoLabel },
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().scale(1.5f),
            )
        }
        Spacer(Modifier.height(Dimens.lg))
        AppText(stringResource(R.string.app_name), AppTheme.type.display, maxLines = 1)
        AppText(
            stringResource(R.string.about_tagline),
            AppTheme.type.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.xs),
        )
        if (version.isNotEmpty()) {
            AppText(
                stringResource(R.string.about_version, version),
                AppTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Dimens.xs),
            )
        }
        if (sourceUrl.isNotEmpty()) {
            Spacer(Modifier.height(Dimens.xl))
            TonalButton(
                text = stringResource(R.string.about_view_source),
                icon = R.drawable.ic_code,
                accent = true,
                onClick = { openUrl(context, sourceUrl, noBrowser) },
            )
        }
        val profileUrl = stringResource(R.string.about_profile_url).trim()
        if (profileUrl.isNotEmpty()) {
            Spacer(Modifier.height(Dimens.sm))
            TonalButton(
                text = stringResource(R.string.about_profile),
                icon = R.drawable.ic_person,
                onClick = { openUrl(context, profileUrl, noBrowser) },
            )
        }
        Spacer(Modifier.height(Dimens.xl))
        AboutRow(R.drawable.ic_scale, stringResource(R.string.about_licences)) { showLicences = true }
        AboutRow(R.drawable.ic_shield_check, stringResource(R.string.about_privacy), onClick = onOpenPrivacy)
        AboutRow(R.drawable.ic_save, stringResource(R.string.settings_restore), destructive = true, chevron = false) {
            confirmRestore = true
        }
        Spacer(Modifier.height(Dimens.lg))
        AppText(
            stringResource(R.string.about_open_source),
            AppTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs),
        )
        OpenSourceRepos.forEach { repo ->
            AboutRow(R.drawable.ic_code, repo.name, subtitle = repo.note, chevron = false) {
                openUrl(context, repo.url, noBrowser)
            }
        }
        Spacer(Modifier.height(Dimens.xl))
        AppText(
            stringResource(R.string.about_made),
            AppTheme.type.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    if (showLicences) {
        com.dopavin.ui.components.GlassDialog(
            title = stringResource(R.string.about_licences),
            body = stringResource(R.string.settings_licences),
            dismissLabel = stringResource(R.string.about_close),
            onDismiss = { showLicences = false },
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = stringResource(R.string.settings_restore),
            body = stringResource(R.string.settings_restore_body),
            confirmLabel = stringResource(R.string.settings_restore_confirm),
            onConfirm = {
                confirmRestore = false
                actions.resetSettings()
            },
            onDismiss = { confirmRestore = false },
        )
    }
}
@Composable
private fun AboutRow(
    icon: Int,
    title: String,
    destructive: Boolean = false,
    chevron: Boolean = true,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val ink = if (destructive) colors.error else colors.textPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = Dimens.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(Dimens.iconControl),
        )
        Spacer(Modifier.size(Dimens.lg))
        Column(Modifier.weight(1f).padding(vertical = Dimens.sm)) {
            AppText(title, AppTheme.type.bodyStrong, color = ink, maxLines = 2)
            if (subtitle != null) {
                AppText(subtitle, AppTheme.type.caption, color = colors.textSecondary, maxLines = 2)
            }
        }
        if (chevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(Dimens.iconControl),
            )
        }
    }
}
