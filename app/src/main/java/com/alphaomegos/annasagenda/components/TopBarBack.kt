package com.alphaomegos.annasagenda.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.LocalBackStyle
import com.alphaomegos.annasagenda.model.BackStyle

/**
 * Back in a top bar, one way everywhere (19.10).
 *
 * Until now half the screens drew an arrow there and half the word «Back»,
 * so one button looked like two. This draws whichever the user chose in
 * Settings — the arrow by default, the word for whoever prefers it — and the
 * arrow still says «Back» to TalkBack. The full-width Back at the foot of
 * some screens is a different control, within thumb's reach, and stays.
 */
@Composable
fun TopBarBack(onBack: () -> Unit) {
    when (LocalBackStyle.current) {
        BackStyle.ARROW -> IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        BackStyle.WORD -> TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
