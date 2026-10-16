package com.alphaomegos.annasagenda.screens.backups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.components.sidecarFailureText
import com.alphaomegos.annasagenda.model.SidecarBackup
import com.alphaomegos.annasagenda.model.SidecarBackupListing
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.SidecarFailure
import com.alphaomegos.annasagenda.support.sidecarBackupReplaceChoices
import kotlinx.coroutines.launch

/** What went wrong with a backup, as the screen says it. */
@Composable
internal fun sidecarBackupFailureText(failure: SidecarBackupFailure): String = when (failure) {
    SidecarBackupFailure.NO_NETWORK -> stringResource(R.string.sidecar_failure_no_network)
    SidecarBackupFailure.SIGNED_OUT -> stringResource(R.string.sidecar_backups_sign_in_again)
    SidecarBackupFailure.WRONG_PASSWORD -> stringResource(R.string.sidecar_failure_wrong_password)
    SidecarBackupFailure.TOO_MANY_ATTEMPTS -> stringResource(R.string.sidecar_failure_too_many, 1L)
    SidecarBackupFailure.NOT_ENABLED -> stringResource(R.string.sidecar_backups_not_enabled)
    SidecarBackupFailure.FULL -> stringResource(R.string.sidecar_backups_replace_title)
    SidecarBackupFailure.TOO_LARGE -> stringResource(R.string.sidecar_backups_failure_too_large)
    SidecarBackupFailure.GONE -> stringResource(R.string.sidecar_backups_failure_gone)
    SidecarBackupFailure.SERVER -> stringResource(R.string.sidecar_failure_server)
    SidecarBackupFailure.DAMAGED -> stringResource(R.string.sidecar_backups_failure_damaged)
    SidecarBackupFailure.WRONG_BACKUP_PASSWORD -> stringResource(R.string.sidecar_backups_password_wrong)
    SidecarBackupFailure.NO_KEY -> stringResource(R.string.sidecar_backups_failure_no_key)
    SidecarBackupFailure.NOT_CHOSEN -> stringResource(R.string.sidecar_backups_failure_not_chosen)
    SidecarBackupFailure.PARTS_MISSING,
    SidecarBackupFailure.BAD_REPLACE,
    SidecarBackupFailure.UNEXPECTED,
    -> stringResource(R.string.sidecar_failure_unexpected)
}

/**
 * Whether to encrypt (16.10, Eduard: the person's choice, with what each
 * means said): encrypted on the phone with the oursidecar password, or
 * kept on the server as it is.
 */
@Composable
internal fun BackupEncryptionDialog(
    chosen: Boolean?,
    onEncrypt: () -> Unit,
    onPlain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidecar_backups_encryption_choose)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.sidecar_backups_encryption_on), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.sidecar_backups_encryption_on_warning), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.sidecar_backups_encryption_off),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(stringResource(R.string.sidecar_backups_encryption_off_warning), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onEncrypt, enabled = chosen != true) { Text(stringResource(R.string.sidecar_backups_encrypt_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onPlain, enabled = chosen != false) { Text(stringResource(R.string.sidecar_backups_encrypt_no)) }
        },
    )
}

/**
 * The oursidecar password asked (16.10): to make the encryption key from,
 * or to open a backup made with an older one. [onPassword] answers null
 * when it is done with it, or what went wrong.
 *
 * The password lives only in this composition: never in the saved state.
 */
@Composable
internal fun BackupPasswordDialog(
    text: String,
    wrong: Boolean,
    onPassword: suspend (String) -> SidecarFailure?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var failure by rememberSaveable { mutableStateOf<SidecarFailure?>(null) }

    fun submit() {
        if (working || password.isEmpty()) return
        working = true
        failure = null
        scope.launch {
            failure = onPassword(password)
            working = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidecar_backups_password_title)) },
        text = {
            Column {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (wrong) Text(stringResource(R.string.sidecar_backups_password_wrong), color = MaterialTheme.colorScheme.error)
                failure?.let { Text(sidecarFailureText(it), color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.sidecar_password)) },
                    singleLine = true,
                    enabled = !working,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (showPassword) R.string.sidecar_password_hide else R.string.sidecar_password_show,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !working && password.isNotEmpty()) {
                Text(stringResource(R.string.sidecar_backups_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sidecar_backups_cancel)) }
        },
    )
}

/** "Make a backup now": what it is, and a note to go with it. */
@Composable
internal fun BackupMakeDialog(
    note: String,
    onNote: (String) -> Unit,
    onMake: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidecar_backups_make_title)) },
        text = {
            Column {
                Text(stringResource(R.string.sidecar_backups_make_text), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { onNote(it.take(200)) },
                    label = { Text(stringResource(R.string.sidecar_backups_note)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onMake() }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMake) { Text(stringResource(R.string.sidecar_backups_make_go)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sidecar_backups_cancel)) }
        },
    )
}

/** All three places by hand taken, and the server asking: which one the new backup takes the place of. */
@Composable
internal fun BackupReplaceDialog(
    listing: SidecarBackupListing,
    momentOf: (SidecarBackup) -> String,
    onReplace: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = sidecarBackupReplaceChoices(listing)
    var picked by rememberSaveable { mutableStateOf(choices.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidecar_backups_replace_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(stringResource(R.string.sidecar_backups_replace_text), style = MaterialTheme.typography.bodyMedium)
                choices.forEach { backup ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { picked = backup.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = picked == backup.id, onClick = { picked = backup.id })
                        Column {
                            Text(momentOf(backup))
                            backup.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picked?.let(onReplace) }, enabled = picked != null) {
                Text(stringResource(R.string.sidecar_backups_make_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sidecar_backups_cancel)) }
        },
    )
}

/** A yes-or-no about something that cannot be taken back. */
@Composable
internal fun BackupConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sidecar_backups_cancel)) } },
    )
}
