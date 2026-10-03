package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.WidgetStyle
import com.alphaomegos.annasagenda.WidgetTextColor
import kotlin.math.roundToInt

/**
 * How the home-screen widget looks (03.10): how see-through its backing is,
 * the colour of its words, and the colour of a ticked box. Written on OK;
 * what is picked survives turning the phone. The widget redraws by itself.
 */
@Composable
internal fun WidgetStyleDialog(
    initial: WidgetStyle,
    onDismiss: () -> Unit,
    onSave: (WidgetStyle) -> Unit,
) {
    var percent by rememberSaveable { mutableIntStateOf(initial.backgroundPercent) }
    var textName by rememberSaveable { mutableStateOf(initial.textColor.name) }
    val text = WidgetTextColor.entries.firstOrNull { it.name == textName } ?: WidgetTextColor.SYSTEM
    var check by rememberSaveable { mutableStateOf(initial.checkColorArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_style_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.widget_style_background, percent),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = percent.toFloat(),
                    // Rounded: the slider's snapping can hand back 69.9999 for 70.
                    onValueChange = { percent = it.roundToInt() },
                    valueRange = 0f..100f,
                    steps = 9,
                )

                Text(stringResource(R.string.widget_style_text), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    listOf(
                        WidgetTextColor.SYSTEM to R.string.widget_text_system,
                        WidgetTextColor.WHITE to R.string.widget_text_white,
                        WidgetTextColor.BLACK to R.string.widget_text_black,
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { textName = value.name },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = text == value, onClick = { textName = value.name })
                            Text(stringResource(label))
                        }
                    }
                }

                Text(stringResource(R.string.widget_style_checks), style = MaterialTheme.typography.titleSmall)
                // The empty circle first: the theme's colour.
                ColorPickerRow(selected = check, onSelect = { check = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(WidgetStyle(backgroundPercent = percent, textColor = text, checkColorArgb = check))
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
