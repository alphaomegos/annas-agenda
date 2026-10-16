package com.alphaomegos.annasagenda.screens.backups

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.app.SidecarBackupAccounts
import com.alphaomegos.annasagenda.app.SidecarBackupRestorers
import com.alphaomegos.annasagenda.app.SidecarBackupResult
import com.alphaomegos.annasagenda.app.SidecarBackupWorker
import com.alphaomegos.annasagenda.app.SidecarConnections
import com.alphaomegos.annasagenda.app.SidecarResult
import com.alphaomegos.annasagenda.app.planSidecarAutoBackup
import com.alphaomegos.annasagenda.app.sidecarSignIn
import com.alphaomegos.annasagenda.app.sidecarSignOut
import com.alphaomegos.annasagenda.components.SidecarAccountDialog
import com.alphaomegos.annasagenda.model.SidecarBackup
import com.alphaomegos.annasagenda.model.SidecarBackupSlot
import com.alphaomegos.annasagenda.model.SidecarBackupWhenFull
import com.alphaomegos.annasagenda.support.ImportMessage
import com.alphaomegos.annasagenda.support.ImportOutcome
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.importMessageFor
import com.alphaomegos.annasagenda.support.sidecarBackupCounts
import com.alphaomegos.annasagenda.support.sidecarBackupManualFull
import com.alphaomegos.annasagenda.support.sidecarBackupMoment
import com.alphaomegos.annasagenda.support.sidecarBackupsShown
import com.alphaomegos.annasagenda.util.BackupImportPayload
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.readBackupImportPayload
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * This app's backups in the oursidecar account (16.10, Eduard): from the
 * main menu's "⋮". Who is signed in decides what is here — a way to sign
 * in, an account that keeps no backups, a phone whose token was revoked —
 * and then: whether backups are encrypted (asked once, with what each
 * choice means), the weekly one, what a fourth by hand does, "make one
 * now", and the list, each with "restore" and "delete".
 *
 * A backup by hand goes up in the background (SidecarBackupWorker); its
 * end is told here when it is the one asked for from here. Restoring is
 * here, on the screen: it replaces everything, and is waited for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidecarBackupsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = appLocale()
    val zone = remember { ZoneId.systemDefault() }
    val scope = rememberCoroutineScope()
    val connection = remember(context) { SidecarConnections.of(context) }
    val account = remember(context) { SidecarBackupAccounts.of(context) }
    val restorer = remember(context) { SidecarBackupRestorers.of(context) }
    val sidecar by connection.status.collectAsStateWithLifecycle()
    val status by account.status.collectAsStateWithLifecycle()
    val progress by remember(context) { SidecarBackupWorker.progress(context) }.collectAsStateWithLifecycle(initialValue = null)
    val outcome by remember(context) { SidecarBackupWorker.outcome(context) }.collectAsStateWithLifecycle(initialValue = null)

    var accountOpen by rememberSaveable { mutableStateOf(false) }
    var encryptionOpen by rememberSaveable { mutableStateOf(false) }
    var encryptPasswordOpen by rememberSaveable { mutableStateOf(false) }
    var makeOpen by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var replaceOpen by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreAsked by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordFor by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordWasWrong by rememberSaveable { mutableStateOf(false) }
    var watching by rememberSaveable { mutableStateOf<String?>(null) }
    var failure by rememberSaveable { mutableStateOf<SidecarBackupFailure?>(null) }
    var doneSaid by rememberSaveable { mutableStateOf(false) }
    // A restore in flight, and how much has come down: it belongs to this composition and ends with it.
    var restoring by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    val listing = status.listing
    val shown = listing?.let { sidecarBackupsShown(it) }.orEmpty()
    val byId: (String?) -> SidecarBackup? = { id -> listing?.backups?.firstOrNull { it.id == id } }
    val momentOf: (SidecarBackup) -> String = { sidecarBackupMoment(it.createdAtSeconds, zone, locale) }

    // The list asked for on entering, and whenever the phone is signed in anew.
    LaunchedEffect(status.signedIn) {
        if (status.signedIn) account.refresh()
    }
    // The end of the backup asked for from here: said once, and the list asked again.
    LaunchedEffect(outcome?.id, watching) {
        val o = outcome ?: return@LaunchedEffect
        if (o.id.toString() != watching) return@LaunchedEffect
        watching = null
        account.refresh()
        if (o.done) {
            doneSaid = true
        } else if (o.failure == SidecarBackupFailure.FULL) {
            replaceOpen = true
        } else {
            failure = o.failure
        }
    }

    fun startBackup(replace: String?) {
        failure = null
        doneSaid = false
        watching = SidecarBackupWorker.startManual(context, note.takeIf { it.isNotBlank() }, replace).toString()
        note = ""
    }

    fun restore(backup: SidecarBackup, password: String?) {
        failure = null
        restoring = 0L to backup.size
        scope.launch {
            val fetched = restorer.fetch(backup, password) { got, of -> restoring = got to of }
            when (fetched) {
                is SidecarBackupResult.Failed -> {
                    restoring = null
                    if (fetched.failure == SidecarBackupFailure.WRONG_BACKUP_PASSWORD) {
                        passwordWasWrong = password != null
                        passwordFor = backup.id
                    } else {
                        failure = fetched.failure
                    }
                }
                is SidecarBackupResult.Ok -> {
                    restoring = -1L to backup.size
                    val payload = readBackupImportPayload(context, Uri.fromFile(fetched.value))
                    val imported = when (payload) {
                        is BackupImportPayload.ZipPackage -> vm.importBackupPackage(payload.appStateJson, payload.coverEntries)
                        is BackupImportPayload.LegacyJson -> vm.importBackupJson(payload.json)
                        null -> ImportOutcome.Failed
                    }
                    restoring = null
                    restorer.clear()
                    val message = when (importMessageFor(payloadWasReadable = payload != null, outcome = imported)) {
                        ImportMessage.IMPORTED -> context.getString(R.string.toast_imported)
                        ImportMessage.IMPORTED_WITHOUT_SOME_COVERS -> context.resources.getQuantityString(
                            R.plurals.toast_imported_without_some_covers,
                            imported.coversNotWritten,
                            imported.coversNotWritten,
                        )
                        ImportMessage.COULD_NOT_READ_FILE -> context.getString(R.string.toast_import_failed)
                        ImportMessage.NOT_A_BACKUP -> context.getString(R.string.toast_invalid_backup)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.sidecar_backups_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.sidecar_backups_why),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            when {
                sidecar.account == null && !status.signedIn -> item {
                    BackupNotice(stringResource(R.string.sidecar_backups_sign_in_first), stringResource(R.string.sidecar_backups_sign_in)) {
                        accountOpen = true
                    }
                }
                status.notEnabled -> item {
                    BackupNotice(stringResource(R.string.sidecar_backups_not_enabled), null) {}
                }
                !status.signedIn -> item {
                    BackupNotice(stringResource(R.string.sidecar_backups_sign_in_again), stringResource(R.string.sidecar_backups_sign_in)) {
                        accountOpen = true
                    }
                }
                else -> {
                    // Encryption: chosen once, with what each choice means; changed when liked.
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(stringResource(R.string.sidecar_backups_encryption), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(
                                    when (status.encrypt) {
                                        true -> R.string.sidecar_backups_encryption_on
                                        false -> R.string.sidecar_backups_encryption_off
                                        null -> R.string.sidecar_backups_encryption_unchosen
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { encryptionOpen = true }) {
                                Text(
                                    stringResource(
                                        if (status.encrypt == null) R.string.sidecar_backups_encryption_choose else R.string.sidecar_backups_encryption_change,
                                    ),
                                )
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        account.setAuto(!status.auto)
                                        planSidecarAutoBackup(context, account.status.value)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.sidecar_backups_auto_pref), modifier = Modifier.weight(1f))
                            Switch(
                                checked = status.auto,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        account.setAuto(on)
                                        planSidecarAutoBackup(context, account.status.value)
                                    }
                                },
                            )
                        }
                    }
                    if (listing != null) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp).selectableGroup()) {
                                Text(stringResource(R.string.sidecar_backups_when_full), style = MaterialTheme.typography.titleSmall)
                                listOf(
                                    SidecarBackupWhenFull.ASK to R.string.sidecar_backups_when_full_ask,
                                    SidecarBackupWhenFull.REPLACE_OLDEST to R.string.sidecar_backups_when_full_oldest,
                                ).forEach { (value, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { scope.launch { account.setWhenFull(value) } },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = listing.whenFull == value, onClick = { scope.launch { account.setWhenFull(value) } })
                                        Text(stringResource(label))
                                    }
                                }
                            }
                        }
                        item {
                            val (manual, auto) = sidecarBackupCounts(listing)
                            Text(
                                stringResource(
                                    R.string.sidecar_backups_counts,
                                    manual,
                                    listing.limits.manual,
                                    auto,
                                    listing.limits.auto,
                                    Formatter.formatShortFileSize(context, listing.limits.maxSize),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            val going = progress
                            if (going == null) {
                                Button(
                                    onClick = { makeOpen = true },
                                    enabled = status.encrypt != null && restoring == null,
                                ) {
                                    Text(stringResource(R.string.sidecar_backups_make))
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        when {
                                            going.waiting -> stringResource(R.string.sidecar_backups_waiting)
                                            going.of == 0 -> stringResource(R.string.sidecar_backups_preparing)
                                            else -> stringResource(R.string.sidecar_backups_uploading, going.sent, going.of)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { SidecarBackupWorker.cancelManual(context) }) {
                                        Text(stringResource(R.string.sidecar_backups_cancel))
                                    }
                                }
                            }
                            if (doneSaid) Text(stringResource(R.string.sidecar_backups_done), style = MaterialTheme.typography.bodyMedium)
                            failure?.let { Text(sidecarBackupFailureText(it), color = MaterialTheme.colorScheme.error) }
                            restoring?.let { (got, of) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        if (got < 0) {
                                            stringResource(R.string.sidecar_backups_opening)
                                        } else {
                                            stringResource(
                                                R.string.sidecar_backups_restoring,
                                                Formatter.formatShortFileSize(context, got),
                                                Formatter.formatShortFileSize(context, of),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item { HorizontalDivider() }
                    if (listing != null && shown.isEmpty()) {
                        item {
                            Text(stringResource(R.string.sidecar_backups_none), modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    items(shown, key = { it.id }) { backup ->
                        BackupRow(
                            title = stringResource(
                                if (backup.slot == SidecarBackupSlot.MANUAL) R.string.sidecar_backups_manual else R.string.sidecar_backups_auto,
                                momentOf(backup),
                            ),
                            details = listOfNotNull(
                                Formatter.formatShortFileSize(context, backup.size),
                                backup.deviceName,
                                backup.appVersion,
                                if (backup.encrypted) stringResource(R.string.sidecar_backups_encrypted) else null,
                            ).joinToString(" · "),
                            note = backup.note,
                            busy = restoring != null,
                            onRestore = { restoreAsked = backup.id },
                            onDelete = { deleting = backup.id },
                        )
                    }
                }
            }
        }
    }

    if (accountOpen) {
        SidecarAccountDialog(
            status = sidecar,
            nowMillis = System.currentTimeMillis(),
            onSignIn = { server, email, password -> sidecarSignIn(context, server, email, password) },
            onSignOut = { sidecarSignOut(context) },
            onRefresh = { connection.refreshAccount() },
            onChooseSpace = { id -> scope.launch { connection.chooseSpace(id) } },
            onDismiss = { accountOpen = false },
        )
    }

    if (encryptionOpen) {
        BackupEncryptionDialog(
            chosen = status.encrypt,
            onEncrypt = {
                encryptionOpen = false
                if (status.hasKey) {
                    scope.launch {
                        account.chooseEncryption(true)
                        planSidecarAutoBackup(context, account.status.value)
                    }
                } else {
                    encryptPasswordOpen = true
                }
            },
            onPlain = {
                encryptionOpen = false
                scope.launch {
                    account.chooseEncryption(false)
                    planSidecarAutoBackup(context, account.status.value)
                }
            },
            onDismiss = { encryptionOpen = false },
        )
    }

    if (encryptPasswordOpen) {
        BackupPasswordDialog(
            text = stringResource(R.string.sidecar_backups_encrypt_password_text),
            wrong = false,
            onPassword = { password ->
                // Checked against the server, as signing in is; the key is made from it here.
                when (val r = connection.signIn(sidecar.server, status.email ?: sidecar.account?.email.orEmpty(), password)) {
                    is SidecarResult.Ok -> {
                        account.chooseEncryption(true, password)
                        planSidecarAutoBackup(context, account.status.value)
                        encryptPasswordOpen = false
                        null
                    }
                    is SidecarResult.Failed -> r.failure
                }
            },
            onDismiss = { encryptPasswordOpen = false },
        )
    }

    if (makeOpen) {
        BackupMakeDialog(
            note = note,
            onNote = { note = it },
            onMake = {
                makeOpen = false
                if (listing != null && listing.whenFull == SidecarBackupWhenFull.ASK && sidecarBackupManualFull(listing)) {
                    replaceOpen = true
                } else {
                    startBackup(null)
                }
            },
            onDismiss = { makeOpen = false },
        )
    }

    if (replaceOpen && listing != null) {
        BackupReplaceDialog(
            listing = listing,
            momentOf = momentOf,
            onReplace = { id ->
                replaceOpen = false
                startBackup(id)
            },
            onDismiss = { replaceOpen = false },
        )
    }

    byId(deleting)?.let { backup ->
        BackupConfirmDialog(
            title = stringResource(R.string.sidecar_backups_delete_title),
            text = stringResource(R.string.sidecar_backups_delete_text, momentOf(backup)),
            confirm = stringResource(R.string.sidecar_backups_delete),
            onConfirm = {
                deleting = null
                scope.launch {
                    val r = account.delete(backup.id)
                    if (r is SidecarBackupResult.Failed) failure = r.failure
                }
            },
            onDismiss = { deleting = null },
        )
    }

    byId(restoreAsked)?.let { backup ->
        BackupConfirmDialog(
            title = stringResource(R.string.sidecar_backups_restore_title),
            text = stringResource(R.string.sidecar_backups_restore_text, momentOf(backup)),
            confirm = stringResource(R.string.sidecar_backups_restore),
            onConfirm = {
                restoreAsked = null
                restore(backup, null)
            },
            onDismiss = { restoreAsked = null },
        )
    }

    byId(passwordFor)?.let { backup ->
        BackupPasswordDialog(
            text = stringResource(R.string.sidecar_backups_password_text, momentOf(backup)),
            wrong = passwordWasWrong,
            onPassword = { password ->
                passwordFor = null
                restore(backup, password)
                null
            },
            onDismiss = {
                passwordFor = null
                passwordWasWrong = false
            },
        )
    }
}

/** A line of what the screen cannot go on without, and the button that mends it, if any. */
@Composable
private fun BackupNotice(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (action != null) {
            Button(onClick = onAction) { Text(action) }
        }
    }
}

/** One backup of the list: when and which kind, its size, phone, version, whether encrypted, its note. */
@Composable
private fun BackupRow(
    title: String,
    details: String,
    note: String?,
    busy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(details, style = MaterialTheme.typography.bodySmall)
        if (!note.isNullOrBlank()) Text(note, style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(onClick = onRestore, enabled = !busy) { Text(stringResource(R.string.sidecar_backups_restore)) }
            TextButton(onClick = onDelete, enabled = !busy) { Text(stringResource(R.string.sidecar_backups_delete)) }
        }
    }
}
