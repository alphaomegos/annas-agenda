package com.alphaomegos.annasagenda.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.alphaomegos.annasagenda.R
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * "Pick a time", the counterpart of [PickDateDialog].
 *
 * Opens at [initialTime], or at the next full hour when there is none — the
 * time most often meant by somebody adding something to today.
 *
 * Fixed to the 24-hour clock, to match how the day shows times
 * ([com.alphaomegos.annasagenda.support.formatTaskTime]); both people using this app
 * live on it.
 *
 * Material3 1.3 has no time picker dialog of its own, so the picker sits in
 * an ordinary AlertDialog. The picker's state is saveable by itself, so a
 * rotation keeps the hand where it was put.
 *
 * [onDismiss] is called after [onPicked], as in [PickDateDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickTimeDialog(
    initialTime: LocalTime?,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit,
) {
    val start = remember(initialTime) {
        initialTime ?: LocalTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1)
    }
    val pickerState = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPicked(LocalTime.of(pickerState.hour, pickerState.minute))
                    onDismiss()
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = { TimePicker(state = pickerState) },
    )
}
