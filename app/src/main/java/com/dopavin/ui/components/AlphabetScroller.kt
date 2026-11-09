package com.dopavin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/** Display text of a section letter: digits are grouped as "0-9" (stored as '0'). */
fun sectionLabel(letter: Char): String = if (letter == '0') "0-9" else letter.toString()

/**
 * Fast-scroll index along the end edge. Touch or drag on it to jump to a letter; a bubble shows the
 * letter being selected. Hidden from accessibility (TalkBack users scroll the list normally).
 */
@Composable
fun AlphabetScroller(
    letters: List<Char>,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.size < 2) return
    val colors = AppTheme.colors
    var active by remember { mutableStateOf<Char?>(null) }

    fun pick(y: Float, height: Int) {
        if (height <= 0) return
        val index = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
        val letter = letters[index]
        if (active != letter) {
            active = letter
            onSelect(letter)
        }
    }

    Box(modifier.fillMaxSize()) {
        active?.let { letter ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.gradient),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = sectionLabel(letter),
                    style = AppTheme.type.headline,
                    color = colors.onAccent,
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.82f)
                .width(Dimens.alphabetWidth)
                .pointerInput(letters) {
                    detectTapGestures(
                        onPress = { offset ->
                            pick(offset.y, size.height)
                            tryAwaitRelease()
                            active = null
                        },
                    )
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> pick(offset.y, size.height) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            pick(change.position.y, size.height)
                        },
                    )
                }
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                AppText(
                    text = if (letter == '0') "0" else letter.toString(),
                    style = AppTheme.type.caption,
                    color = if (active == letter) colors.accent else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}
