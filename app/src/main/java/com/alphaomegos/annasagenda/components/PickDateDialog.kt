package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Pick a date", once.
 *
 * Four screens had their own copy of this: open at some date, convert the
 * picked milliseconds back to a LocalDate, hand it over, close. Identical
 * apart from which date they open at and what they do with the answer — and
 * one of them, the new-task screen, offers "no date" beside Cancel, which is
 * what [extraDismissAction] is for.
 *
 * The conversion between a LocalDate and the milliseconds the picker speaks
 * lives here now. It was the same in all four copies, and it is also where the
 * open question of UTC versus the device's zone will have to be answered —
 * one place rather than four to remember.
 *
 * [onDismiss] is called after [onPicked], so a caller that clears its own
 * "show the picker" flag in it needs to do nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickDateDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
    extraDismissAction: (@Composable () -> Unit)? = null,
) {
    val zone = remember { ZoneId.systemDefault() }
    val initialMillis = remember(initialDate, zone) {
        (initialDate ?: LocalDate.now()).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        onPicked(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())
                    }
                    onDismiss()
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (extraDismissAction != null) {
                    extraDismissAction()
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
