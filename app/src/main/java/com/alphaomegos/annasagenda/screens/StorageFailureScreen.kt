package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.alphaomegos.annasagenda.components.ConfirmDialog

/**
 * Shown instead of the app when there is data on disk that this build must not
 * write over.
 *
 * Blocking the UI here is intentional: as long as this screen is up, autosave
 * has not been started, so whatever is on disk stays exactly as it is.
 *
 * Two different situations end up here and the advice differs. Unreadable data
 * is a loss to recover from, and starting empty is the way forward. Data from a
 * newer version is not damaged at all — the way forward is that newer version,
 * and starting empty would throw away something perfectly good, so it is
 * offered quietly rather than as the obvious next step.
 */
@Composable
fun StorageFailureScreen(
    failure: AppStateLoadResult.Failed,
    onContinueEmpty: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    when (failure) {
        is AppStateLoadResult.TooNew -> TooNewContent(
            failure = failure,
            onContinueEmpty = { confirming = true },
        )

        is AppStateLoadResult.Corrupted -> CorruptedContent(
            failure = failure,
            onContinueEmpty = { confirming = true },
        )
    }

    if (confirming) {
        ConfirmDialog(
            titleRes = R.string.storage_failure_confirm_title,
            textRes = R.string.storage_failure_confirm_text,
            confirmLabelRes = R.string.storage_failure_continue_empty,
            onConfirm = {
                confirming = false
                onContinueEmpty()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun TooNewContent(
    failure: AppStateLoadResult.TooNew,
    onContinueEmpty: () -> Unit,
) {
    FailureColumn {
        Text(
            text = stringResource(R.string.storage_too_new_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = stringResource(
                R.string.storage_too_new_text,
                failure.payloadVersion,
                failure.supportedVersion,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.storage_too_new_hint),
            style = MaterialTheme.typography.bodyMedium,
        )

        // Not a Button: this destroys data that is not damaged.
        TextButton(onClick = onContinueEmpty) {
            Text(stringResource(R.string.storage_failure_continue_empty))
        }
    }
}

@Composable
private fun CorruptedContent(
    failure: AppStateLoadResult.Corrupted,
    onContinueEmpty: () -> Unit,
) {
    FailureColumn {
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

        Button(onClick = onContinueEmpty) {
            Text(stringResource(R.string.storage_failure_continue_empty))
        }
    }
}

@Composable
private fun FailureColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}
