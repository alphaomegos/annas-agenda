package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.SidecarResult
import com.alphaomegos.annasagenda.app.SidecarStatus
import com.alphaomegos.annasagenda.support.SIDECAR_DEFAULT_SERVER
import com.alphaomegos.annasagenda.support.SidecarFailure
import com.alphaomegos.annasagenda.support.SidecarSignInState
import com.alphaomegos.annasagenda.support.formatMediumDate
import com.alphaomegos.annasagenda.support.sidecarServerUrl
import com.alphaomegos.annasagenda.util.appLocale
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/** What a failure says to the person (16.10). */
@Composable
internal fun sidecarFailureText(failure: SidecarFailure, retryMinutes: Long? = null): String = when (failure) {
    SidecarFailure.NO_NETWORK -> stringResource(R.string.sidecar_failure_no_network)
    SidecarFailure.WRONG_PASSWORD -> stringResource(R.string.sidecar_failure_wrong_password)
    SidecarFailure.TOO_MANY_ATTEMPTS -> stringResource(R.string.sidecar_failure_too_many, retryMinutes ?: 1L)
    SidecarFailure.SIGNED_OUT -> stringResource(R.string.sidecar_session_ended)
    SidecarFailure.SERVER -> stringResource(R.string.sidecar_failure_server)
    SidecarFailure.NOT_FOUND, SidecarFailure.UNEXPECTED -> stringResource(R.string.sidecar_failure_unexpected)
}

/** The server's address as a person reads it: without "https://". */
private fun shownServer(server: String): String = server.removePrefix("https://")

/**
 * The oursidecar account, from the icon in the main menu's bar (16.10).
 *
 * Nobody in: the email, the password and — folded away — the server. Somebody
 * whose session has ended: the same, their email already there. Somebody in:
 * who, until when, and whose music plays when the account has more than one
 * sidecar; signing out from here.
 *
 * The password lives only in this composition: it is never put into the
 * saved state, and turning the phone asks for it again.
 */
@Composable
internal fun SidecarAccountDialog(
    status: SidecarStatus,
    nowMillis: Long,
    onSignIn: suspend (server: String, email: String, password: String) -> SidecarResult<Unit>,
    onSignOut: suspend () -> Unit,
    onRefresh: suspend () -> Unit,
    onChooseSpace: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = status.signIn(nowMillis)
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var failure by rememberSaveable { mutableStateOf<SidecarFailure?>(null) }
    var retryMinutes by rememberSaveable { mutableStateOf<Long?>(null) }
    var serverInvalid by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf(status.account?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable { mutableStateOf(shownServer(status.server)) }
    var serverShown by rememberSaveable { mutableStateOf(status.server != SIDECAR_DEFAULT_SERVER) }

    // The sidecars asked again when the account is opened: one joined on the web since appears.
    LaunchedEffect(state == SidecarSignInState.SIGNED_IN) {
        if (state == SidecarSignInState.SIGNED_IN) onRefresh()
    }

    fun signIn() {
        if (working) return
        failure = null
        retryMinutes = null
        serverInvalid = sidecarServerUrl(server) == null
        if (serverInvalid) {
            serverShown = true
            return
        }
        working = true
        scope.launch {
            val result = onSignIn(server, email, password)
            working = false
            when (result) {
                is SidecarResult.Ok -> password = ""
                is SidecarResult.Failed -> {
                    failure = result.failure
                    retryMinutes = result.retryMinutes
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidecar_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state == SidecarSignInState.SIGNED_IN) {
                    SignedInPanel(status, onChooseSpace)
                } else {
                    Text(
                        stringResource(
                            if (state == SidecarSignInState.ATTENTION) R.string.sidecar_session_ended else R.string.sidecar_sign_in_why,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.sidecar_email)) },
                        singleLine = true,
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        keyboardActions = KeyboardActions(onDone = { signIn() }),
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (serverShown) {
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text(stringResource(R.string.sidecar_server)) },
                            singleLine = true,
                            enabled = !working,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        TextButton(onClick = { serverShown = true }, enabled = !working) {
                            Text(stringResource(R.string.sidecar_server_other))
                        }
                    }
                    if (state == SidecarSignInState.ATTENTION) {
                        TextButton(
                            onClick = {
                                working = true
                                scope.launch {
                                    onSignOut()
                                    working = false
                                    email = ""
                                    failure = null
                                }
                            },
                            enabled = !working,
                        ) {
                            Text(stringResource(R.string.sidecar_forget_account))
                        }
                    }
                }
                val said = failure
                val errorText = when {
                    serverInvalid -> stringResource(R.string.sidecar_server_invalid)
                    said != null -> sidecarFailureText(said, retryMinutes)
                    else -> null
                }
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp).align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            if (state == SidecarSignInState.SIGNED_IN) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            } else {
                TextButton(
                    onClick = { signIn() },
                    enabled = !working && email.contains('@') && password.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.sidecar_sign_in))
                }
            }
        },
        dismissButton = {
            if (state == SidecarSignInState.SIGNED_IN) {
                TextButton(
                    onClick = {
                        working = true
                        scope.launch {
                            onSignOut()
                            working = false
                            email = ""
                        }
                    },
                    enabled = !working,
                ) {
                    Text(stringResource(R.string.sidecar_sign_out))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Who is in, until when, and — with more than one sidecar — whose music plays. */
@Composable
private fun SignedInPanel(status: SidecarStatus, onChooseSpace: (String) -> Unit) {
    val locale = appLocale()
    Text(stringResource(R.string.sidecar_signed_in_as, status.account?.email.orEmpty()), style = MaterialTheme.typography.bodyLarge)
    status.sessionEndsAtMillis?.let { ends ->
        val day = Instant.ofEpochMilli(ends).atZone(ZoneId.systemDefault()).toLocalDate()
        Text(
            stringResource(R.string.sidecar_session_until, formatMediumDate(day, locale)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    when {
        status.spaces.isEmpty() -> Text(stringResource(R.string.sidecar_no_spaces), style = MaterialTheme.typography.bodyMedium)
        status.spaces.size == 1 -> Text(status.spaces.single().name, style = MaterialTheme.typography.titleMedium)
        else -> {
            Text(stringResource(R.string.sidecar_space), style = MaterialTheme.typography.titleSmall)
            val chosen = status.space?.id
            status.spaces.forEach { space ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChooseSpace(space.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = space.id == chosen, onClick = { onChooseSpace(space.id) })
                    Text(space.name)
                }
            }
        }
    }
}
