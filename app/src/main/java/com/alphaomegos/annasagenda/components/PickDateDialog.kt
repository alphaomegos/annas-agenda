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
import java.time.ZoneOffset

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
 * happens in UTC, because that is what the picker means by them: Material3
 * documents both initialSelectedDateMillis and selectedDateMillis as UTC
 * milliseconds since the epoch, and the calendar it draws reads them that way.
 *
 * All four copies used to hand it midnight in the device's own zone instead.
 * East of Greenwich that is the previous day in UTC, so the dialog opened on
 * the wrong date — in Moscow, asking to move a task dated the 23rd offered the
 * 22nd preselected. West of it the shift lands on the way back out, and a
 * picked date is stored as the day before. Nothing about this depended on
 * which of the four copies you used; they were all wrong the same way.
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
    val initialMillis = remember(initialDate) {
        (initialDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
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
