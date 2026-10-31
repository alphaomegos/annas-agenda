package com.alphaomegos.annasagenda.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import com.alphaomegos.annasagenda.app.AppIcons

/**
 * An import in a top bar (31.10, Eduard): the arrow into a tray (AppIcons.importData),
 * and a long press says what it brings in — «Import from Samsung Health» — since an
 * icon alone cannot name the source. TalkBack reads the same [label].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportIconButton(label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(AppIcons.importData, contentDescription = label)
        }
    }
}
