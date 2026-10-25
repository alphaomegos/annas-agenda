package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.StepShareName
import com.alphaomegos.annasagenda.app.StepsSharingResult
import com.alphaomegos.annasagenda.app.StepsTogether
import com.alphaomegos.annasagenda.components.TopBarBack
import com.alphaomegos.annasagenda.model.StepFriend
import com.alphaomegos.annasagenda.model.StepInvitation
import com.alphaomegos.annasagenda.support.StepsSharingFailure
import com.alphaomegos.annasagenda.support.stepsShareNameValid
import kotlinx.coroutines.launch

/**
 * The colours friends are drawn in (25.10): four of the Okabe–Ito palette,
 * which people with any common colour blindness still tell apart — orange,
 * sky blue, vermillion, reddish purple. The user's own steps keep the theme's
 * primary colour.
 */
internal val STEP_FRIEND_COLORS = listOf(Color(0xFFE69F00), Color(0xFF56B4E9), Color(0xFFD55E00), Color(0xFFCC79A7))

@Composable
internal fun stepsSharingFailureText(failure: StepsSharingFailure): String = stringResource(
    when (failure) {
        StepsSharingFailure.NO_NETWORK -> R.string.steps_together_failed_network
        StepsSharingFailure.SIGNED_OUT -> R.string.steps_together_signed_out
        StepsSharingFailure.SHARING_OFF -> R.string.steps_together_failed_sharing_off
        StepsSharingFailure.TOO_MANY_FRIENDS -> R.string.steps_together_failed_too_many
        StepsSharingFailure.TOO_MANY_INVITATIONS -> R.string.steps_together_failed_invitations
        StepsSharingFailure.GONE -> R.string.steps_together_failed_gone
        StepsSharingFailure.REJECTED -> R.string.steps_together_failed_rejected
        StepsSharingFailure.SERVER, StepsSharingFailure.UNEXPECTED -> R.string.steps_together_failed_other
    }
)

/**
 * Settings → Steps together (25.10): consent, the name friends see, the
 * invitations both ways, and the friends — all against oursidecar, through
 * StepsTogether, which syncs after every change so what is shown follows.
 * Full screen, over Settings, with Back.
 */
@Composable
internal fun StepsTogetherDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val together by StepsTogether.of(context).collectAsState()
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var failure by rememberSaveable { mutableStateOf<StepsSharingFailure?>(null) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var leaving by rememberSaveable { mutableStateOf<String?>(null) }
    val sentText = stringResource(R.string.steps_together_invite_sent)

    LaunchedEffect(Unit) { StepsTogether.sync(context) }

    fun <T> run(request: suspend (com.alphaomegos.annasagenda.app.StepsSharingClient, String, String) -> StepsSharingResult<T>, done: (T) -> Unit = {}) {
        scope.launch {
            failure = null
            message = null
            when (val r = StepsTogether.act(context, request)) {
                is StepsSharingResult.Ok -> done(r.value)
                is StepsSharingResult.Failed -> failure = r.failure
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    TopBarBack(onDismiss)
                    Text(stringResource(R.string.steps_together_menu), style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val sharing = together.sharing
                    if (!together.signedIn && sharing == null) {
                        item { Hint(stringResource(R.string.steps_together_signed_out)) }
                        item {
                            TextButton(onClick = { scope.launch { StepsTogether.sync(context) } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(stringResource(R.string.steps_together_refresh))
                            }
                        }
                        return@LazyColumn
                    }
                    (failure ?: together.failure)?.let { f -> item { Hint(stepsSharingFailureText(f), error = true) } }
                    message?.let { m -> item { Hint(m) } }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = sharing?.enabled == true, role = Role.Switch) { on ->
                                    run({ c, s, t -> c.setSharing(s, t, on) })
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.steps_together_consent), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = sharing?.enabled == true, onCheckedChange = null)
                        }
                    }
                    item { Hint(stringResource(R.string.steps_together_consent_hint)) }

                    if (sharing != null) {
                        item {
                            if (!editingName) {
                                TextButton(onClick = { name = sharing.displayName; editingName = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(stringResource(R.string.steps_together_name, sharing.displayName))
                                }
                            } else {
                                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it.take(40) },
                                        label = { Text(stringResource(R.string.steps_together_name_label)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = stepsShareNameValid(name),
                                            onClick = { run({ c, s, t -> c.setSharing(s, t, sharing.enabled, StepShareName.Own(name)) }) { editingName = false } },
                                        ) { Text(stringResource(R.string.save)) }
                                        TextButton(onClick = { run({ c, s, t -> c.setSharing(s, t, sharing.enabled, StepShareName.FromAccount) }) { editingName = false } }) {
                                            Text(stringResource(R.string.steps_together_name_account))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                label = { Text(stringResource(R.string.steps_together_invite_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(enabled = email.contains('@'), onClick = {
                                val to = email
                                run({ c, s, t -> c.invite(s, t, to) }) {
                                    email = ""
                                    message = sentText
                                }
                            }) { Text(stringResource(R.string.steps_together_invite)) }
                        }
                    }

                    val incoming = together.invitations.filter { it.incoming }
                    val outgoing = together.invitations.filter { !it.incoming }
                    if (incoming.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.steps_together_incoming)) }
                        items(incoming, key = { "in-" + it.id }) { inv ->
                            InvitationRow(inv) {
                                TextButton(onClick = { run({ c, s, t -> c.accept(s, t, inv.id) }) }) { Text(stringResource(R.string.steps_together_accept)) }
                                TextButton(onClick = { run({ c, s, t -> c.decline(s, t, inv.id) }) }) { Text(stringResource(R.string.steps_together_decline)) }
                            }
                        }
                    }
                    if (outgoing.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.steps_together_outgoing)) }
                        items(outgoing, key = { "out-" + it.id }) { inv ->
                            InvitationRow(inv) {
                                TextButton(onClick = { run({ c, s, t -> c.cancel(s, t, inv.id) }) }) { Text(stringResource(R.string.steps_together_take_back)) }
                            }
                        }
                    }
                    if (together.friends.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.steps_together_friends)) }
                        items(together.friends, key = { "f-" + it.id }) { friend ->
                            FriendRow(friend, STEP_FRIEND_COLORS[(together.prefs[friend.id]?.color ?: 0).coerceIn(0, 3)]) { leaving = friend.id }
                        }
                    }
                }
            }
        }
    }

    leaving?.let { id ->
        val friend = together.friends.firstOrNull { it.id == id }
        if (friend == null) {
            leaving = null
        } else {
            AlertDialog(
                onDismissRequest = { leaving = null },
                text = { Text(stringResource(R.string.steps_together_unfriend_confirm, friend.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        leaving = null
                        run({ c, s, t -> c.unfriend(s, t, id) })
                    }) { Text(stringResource(R.string.steps_together_unfriend)) }
                },
                dismissButton = { TextButton(onClick = { leaving = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun InvitationRow(invitation: StepInvitation, actions: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(invitation.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun FriendRow(friend: StepFriend, color: Color, onLeave: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(friend.name, style = MaterialTheme.typography.bodyLarge)
            if (!friend.sharing) {
                Text(stringResource(R.string.steps_together_not_sharing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onLeave) { Text(stringResource(R.string.steps_together_unfriend)) }
    }
}
