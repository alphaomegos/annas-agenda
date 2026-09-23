package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppStateLoadResult
import com.alphaomegos.annasagenda.R

/**
 * Shown instead of the app when persisted state exists but cannot be decoded.
 *
 * Blocking the UI here is intentional: as long as this screen is up, autosave
 * has not been started, so the unreadable payload on disk is still intact and
 * still recoverable.
 */
@Composable
fun StorageFailureScreen(
    failure: AppStateLoadResult.Corrupted,
    onContinueEmpty: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.storage_failure_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = stringResource(R.string.storage_failure_text),
            style = MaterialTheme.typography.bodyMedium,
        )

        val quarantinePath = failure.quarantineFile?.absolutePath
        Text(
            text = if (quarantinePath != null) {
                stringResource(R.string.storage_failure_saved_copy, quarantinePath)
            } else {
                stringResource(R.string.storage_failure_no_copy)
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = stringResource(R.string.storage_failure_restore_hint),
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = { confirming = true }) {
            Text(stringResource(R.string.storage_failure_continue_empty))
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.storage_failure_confirm_title)) },
            text = { Text(stringResource(R.string.storage_failure_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onContinueEmpty()
                    }
                ) {
                    Text(stringResource(R.string.storage_failure_continue_empty))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
