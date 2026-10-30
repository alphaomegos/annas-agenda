package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.alphaomegos.annasagenda.support.firstThatFits

/**
 * A title between buttons (30.10): the first of [choices] — the words and their
 * style — that fits the width it is given, measured, not guessed. A narrow screen
 * (the folded Fold) gets a smaller, a two-line («\n» in the words) or a shorter
 * title rather than one wrapped anywhere; when nothing fits, the last choice is
 * cut with an ellipsis.
 */
@Composable
internal fun FittingText(
    choices: List<Pair<String, TextStyle>>,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val room = constraints.maxWidth
        val (text, style) = firstThatFits(choices) { (words, look) ->
            measurer.measure(words, look, maxLines = linesOf(words), softWrap = false).size.width <= room
        }
        Text(
            text = text,
            style = style,
            textAlign = textAlign,
            maxLines = linesOf(text),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// A title is one line, or two when it says so.
private fun linesOf(words: String): Int = if ('\n' in words) 2 else 1
