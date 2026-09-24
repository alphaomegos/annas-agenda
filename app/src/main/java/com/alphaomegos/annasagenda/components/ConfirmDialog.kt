package com.alphaomegos.annasagenda.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alphaomegos.annasagenda.R

/**
 * "Are you sure?" — a title, a sentence, and two buttons.
 *
 * Six of these were written out by hand across five files, identical apart
 * from the three strings. They also disagreed about where the button labels
 * come from: some used the app's own ok and cancel, others android.R's. That
 * is not cosmetic here — this app has its own language switcher, and the
 * platform strings follow the device's language instead of the one the user
 * chose in the app, so a Russian device showed "Cancel" in Russian inside an
 * English app. They all use the app's strings now.
 *
 * The caller decides whether the dialog exists: this draws itself whenever it
 * is composed.
 */
@Composable
internal fun ConfirmDialog(
    titleRes: Int,
    textRes: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabelRes: Int = R.string.ok,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(textRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
