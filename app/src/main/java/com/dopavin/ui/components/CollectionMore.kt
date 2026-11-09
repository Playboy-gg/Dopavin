package com.dopavin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dopavin.R

/**
 * The round "more" button of radio, mix and playlist pages (F-200). The page keeps Play and Shuffle in view;
 * everything else (save as playlist, download all, add all to queue, ...) lives in the anchored menu this opens.
 */
@Composable
fun CollectionMoreButton(
    title: String,
    actions: List<SheetAction>,
    modifier: Modifier = Modifier,
) {
    val host = LocalSheetHost.current
    IconAction(
        icon = R.drawable.ic_more,
        description = stringResource(R.string.cd_more_options, title),
        onClick = { host.show(SheetRequest(title = title, actions = actions)) },
        modifier = modifier,
        glass = true,
    )
}
