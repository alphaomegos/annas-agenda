package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R

/**
 * Asks whether to keep a session that ended because its book stopped being
 * read.
 *
 * Shown above the whole navigation graph rather than on the screen that caused
 * it: moving a book off the Now shelf can be done from the library list or
 * from the book's own screen, and the user may well have moved on before they
 * answer. The question is saved with the rest of the state, so it is still
 * here after the process is reclaimed.
 *
 * There is no dismiss. Tapping outside would be a third answer nobody chose,
 * and the two real ones are one tap away each.
 *
 * The checkbox rides on Keep alone. Saying "always" to discarding would be
 * setting up a rule that silently throws work away, which is the thing this
 * dialog exists to stop.
 */
@Composable
fun PendingReadingSessionDialog(
    bookTitle: String,
    minutes: Int,
    pagesRead: Int,
    onKeep: (alwaysFromNowOn: Boolean) -> Unit,
    onDiscard: () -> Unit,
) {
    var always by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.reading_session_unfinished_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.reading_session_unfinished_text,
                        bookTitle,
                        pluralStringResource(R.plurals.reading_session_minutes, minutes, minutes),
                        pluralStringResource(R.plurals.reading_session_pages, pagesRead, pagesRead),
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = always,
                            role = Role.Checkbox,
                            onValueChange = { always = it },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The row carries the toggle, so the box must not: two
                    // targets for one choice reads as two controls to anything
                    // stepping through them.
                    Checkbox(checked = always, onCheckedChange = null)
                    Text(stringResource(R.string.reading_session_unfinished_always))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onKeep(always) }) {
                Text(stringResource(R.string.reading_session_unfinished_keep))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.reading_session_unfinished_discard))
            }
        },
    )
}
