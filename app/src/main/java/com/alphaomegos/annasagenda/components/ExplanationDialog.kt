package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alphaomegos.annasagenda.R

/**
 * A few paragraphs behind an (i), with one way out.
 *
 * Scrollable, because this holds prose and prose is the one thing in this app
 * whose height nobody controls: the same explanation is a third longer in
 * Russian than in English, and longer again on a phone held at a large font
 * size. A dialog that fits in one language and cuts its last line off in
 * another is worse than no explanation.
 *
 * One button, and it is the only way to dismiss on purpose. Somebody reading
 * an explanation has not decided anything, so there is nothing to confirm and
 * nothing to cancel.
 */
@Composable
internal fun ExplanationDialog(
    titleRes: Int,
    textRes: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}
