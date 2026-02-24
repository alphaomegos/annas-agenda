package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
internal fun ColorPickerRow(selected: Long?, onSelect: (Long?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorDot(
            colorArgb = null,
            onClick = { onSelect(null) },
            size = 22.dp,
            isSelected = selected == null
        )
        Palette.forEach { c ->
            ColorDot(
                colorArgb = c,
                onClick = { onSelect(c) },
                size = 22.dp,
                isSelected = selected == c
            )
        }
    }
}

@Composable
internal fun ColorDot(
    colorArgb: Long?,
    onClick: () -> Unit,
    size: Dp = 16.dp,
    isSelected: Boolean = false
) {
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val fill = colorArgb?.toInt()?.let { Color(it) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .clickable { onClick() }
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size * 0.45f)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

private val Palette: List<Long> = listOf(
    0xFFE53935L, // red
    0xFFFB8C00L, // orange
    0xFFFDD835L, // yellow
    0xFF43A047L, // green
    0xFF00897BL, // teal
    0xFF1E88E5L, // blue
    0xFF3949ABL, // indigo
    0xFFD81B60L, // pink
)

internal fun nextPaletteColor(current: Long?): Long? {
    if (Palette.isEmpty()) return null
    if (current == null) return Palette.first()
    val idx = Palette.indexOf(current)
    return if (idx == -1) Palette.first() else Palette[(idx + 1) % Palette.size]
}