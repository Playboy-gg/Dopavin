package com.dopavin.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ArtworkLoader
import com.dopavin.data.Contributor
import com.dopavin.data.ContributorRole
import com.dopavin.data.Contributors
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.SettingsRow
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/**
 * Settings > Contributors: every person in [Contributors.people] with a tap-through to their GitHub profile,
 * then the full list on GitHub and the project page. The only thing the app downloads here is each person's
 * public profile picture (once, then cached); a tap opens the profile in the browser.
 */
@Composable
fun ContributorsPage(match: (String) -> Boolean) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val noBrowser = stringResource(R.string.about_no_browser)

    AppText(
        stringResource(R.string.contributors_intro),
        AppTheme.type.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
    )
    Contributors.people.forEach { person ->
        val url = Contributors.profileUrl(person.githubLogin)
        if (url != null && match(person.name)) {
            ContributorRow(person) { openUrl(context, url, noBrowser) }
        }
    }
    val allLabel = stringResource(R.string.contributors_all)
    if (match(allLabel)) {
        SettingsRow(
            title = allLabel,
            subtitle = stringResource(R.string.contributors_all_body),
            onClick = { openUrl(context, Contributors.ALL_CONTRIBUTORS_URL, noBrowser) },
        )
    }
    val joinLabel = stringResource(R.string.contributors_join)
    if (match(joinLabel)) {
        SettingsRow(
            title = joinLabel,
            subtitle = stringResource(R.string.contributors_join_body),
            onClick = { openUrl(context, Contributors.REPO_URL, noBrowser) },
        )
    }
}

@Composable
private fun ContributorRow(person: Contributor, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val role = stringResource(
        when (person.role) {
            ContributorRole.Maintainer -> R.string.contributor_role_maintainer
            ContributorRole.Designer -> R.string.contributor_role_designer
            ContributorRole.Contributor -> R.string.contributor_role_contributor
        },
    )
    val label = stringResource(R.string.cd_open_github, person.name)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickableScale(pressedScale = 0.99f, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(person)
        Spacer(Modifier.width(Dimens.lg))
        Column(Modifier.weight(1f)) {
            AppText(person.name, AppTheme.type.bodyStrong, maxLines = 1)
            AppText(
                "@${person.githubLogin} · $role",
                AppTheme.type.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(Dimens.iconControl),
        )
    }
}

private const val AVATAR_PX = 160

/**
 * The person's GitHub profile picture. While it loads (or when there is no connection) the first letter of
 * the name is shown, so the row is never empty. The picture is cached on the phone by the artwork loader.
 */
@Composable
private fun Avatar(person: Contributor) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val uri = remember(person.githubLogin) { Contributors.avatarUrl(person.githubLogin, AVATAR_PX)?.let(Uri::parse) }
    val bitmap by produceState<Bitmap?>(initialValue = uri?.let { ArtworkLoader.peek(it, AVATAR_PX) }, uri) {
        if (value == null && uri != null) value = ArtworkLoader.load(context, uri, AVATAR_PX)
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.gradient),
        contentAlignment = Alignment.Center,
    ) {
        val picture = bitmap
        if (picture != null) {
            Image(
                bitmap = picture.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AppText(
                person.name.take(1).uppercase(),
                AppTheme.type.bodyStrong,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
