package com.dopavin.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space at the bottom of the content area that the floating mini player covers. Scrolling lists add
 * it to their bottom padding so the last row can be scrolled clear of the mini player while the
 * list is otherwise visible behind it.
 */
val LocalBottomInset = compositionLocalOf<Dp> { 0.dp }
