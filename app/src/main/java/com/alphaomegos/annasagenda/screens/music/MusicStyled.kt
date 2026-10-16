package com.alphaomegos.annasagenda.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.model.MetroStyle
import com.alphaomegos.annasagenda.screens.metro.LocalMetroStyle
import com.alphaomegos.annasagenda.screens.metro.LocalMetroStyleDetails
import com.alphaomegos.annasagenda.screens.metro.MetroTextButton
import com.alphaomegos.annasagenda.screens.metro.Win95Grey
import com.alphaomegos.annasagenda.screens.metro.metroFrame
import com.alphaomegos.annasagenda.support.musicTextBar

/** Whether the old program's own look is drawn: a style that has one, with its details on. */
@Composable
internal fun musicStyled(): Boolean = LocalMetroStyle.current != MetroStyle.MODERN && LocalMetroStyleDetails.current

/**
 * A button of the player in the section's style (16.10): today's icon;
 * Windows' grey bevelled square, pressed in while its mode is on ([on]);
 * DOS' and the terminal's "[ > ]", in [glyph]. A mode that is off ([lit]
 * false) is drawn faint.
 */
@Composable
internal fun MusicControl(
    icon: ImageVector,
    glyph: String,
    label: String,
    onClick: () -> Unit,
    lit: Boolean = true,
    on: Boolean = false,
    big: Boolean = false,
) {
    val style = LocalMetroStyle.current
    val colors = MaterialTheme.colorScheme
    when {
        !musicStyled() -> IconButton(onClick = onClick, modifier = if (big) Modifier.size(64.dp) else Modifier) {
            Icon(
                icon,
                contentDescription = label,
                tint = when {
                    !lit -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                    big || on -> colors.primary
                    else -> colors.onSurface
                },
                modifier = Modifier.size(if (big) 56.dp else 28.dp),
            )
        }
        style == MetroStyle.WIN95 -> Box(
            modifier = Modifier
                .padding(2.dp)
                .size(if (big) 48.dp else 40.dp)
                .background(Win95Grey)
                .metroFrame(style, details = true, sunken = on)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (lit) Color.Black else Color(0xFF808080), modifier = Modifier.size(if (big) 30.dp else 22.dp))
        }
        else -> MetroTextButton(
            onClick = onClick,
            modifier = Modifier
                .alpha(if (lit) 1f else 0.5f)
                .semantics { contentDescription = label },
        ) {
            Text(glyph)
        }
    }
}

/**
 * How far into the track, as DOS or a terminal would draw it (16.10):
 * "[█████░░░░░]" or "[#####.....]"; a tap along it goes there.
 */
@Composable
internal fun MusicTextProgress(fraction: Float, enabled: Boolean, onSeek: (Float) -> Unit) {
    val dos = LocalMetroStyle.current == MetroStyle.DOS
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { at -> onSeek((at.x / size.width).coerceIn(0f, 1f)) }
                }
            },
    ) {
        Text(
            musicTextBar(fraction, 24, if (dos) '█' else '#', if (dos) '░' else '.'),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
