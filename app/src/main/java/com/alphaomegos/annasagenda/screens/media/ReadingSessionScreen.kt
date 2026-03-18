package com.alphaomegos.annasagenda.screens.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSessionScreen(
    vm: AppViewModel,
    bookId: Long,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    val active by vm.activeReading.collectAsState()

    val book = st.readingBooks.firstOrNull { it.id == bookId }
    val isActiveForThisBook = active?.bookId == bookId

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val showFinishDialog = remember { mutableStateOf(false) }

    LaunchedEffect(isActiveForThisBook) {
        while (isActiveForThisBook) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val startedAt = active?.startedAtEpochMillis ?: nowMs
    val elapsedSec = max(0L, (nowMs - startedAt) / 1000L)
    val elapsedMinCeil = ((elapsedSec + 59L) / 60L).toInt().coerceAtLeast(1)

    fun goBackCancel() {
        vm.cancelReading()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_session_title)) },
                navigationIcon = {
                    IconButton(onClick = { goBackCancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_menu_reading),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp)
                )

                Spacer(modifier = Modifier.size(20.dp))

                Text(
                    text = book?.title ?: stringResource(R.string.reading_session_unknown_book),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.size(10.dp))

                if (isActiveForThisBook) {
                    Text(
                        text = formatElapsed(elapsedSec),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.size(18.dp))
                    Button(onClick = { showFinishDialog.value = true }) {
                        Text(stringResource(R.string.reading_session_finish))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.reading_session_no_active),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(18.dp))
                    Button(onClick = { onBack() }) {
                        Text(stringResource(R.string.reading_session_back))
                    }
                }
            }
        }
    }

    if (showFinishDialog.value) {
        val totalPages = book?.totalPages ?: 0
        val rememberedStartPage = active?.startPage ?: (book?.currentPage ?: 0)

        var startText by rememberSaveable { mutableStateOf(rememberedStartPage.toString()) }
        var endText by rememberSaveable { mutableStateOf(rememberedStartPage.toString()) }
        var minutesText by rememberSaveable { mutableStateOf(elapsedMinCeil.toString()) }
        val errorText = remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showFinishDialog.value = false },
            title = { Text(stringResource(R.string.reading_session_finish_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.reading_session_stop_page_question),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_session_start_page_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_session_page_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_session_duration_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    if (totalPages > 0) {
                        Text(
                            text = stringResource(R.string.reading_session_total_pages_hint, totalPages),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (errorText.value != null) {
                        Text(
                            text = errorText.value!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val startPage = startText.toIntOrNull()
                    val endPage = endText.toIntOrNull()
                    val minutes = minutesText.toIntOrNull()

                    if (startPage == null || endPage == null || minutes == null || minutes <= 0) {
                        errorText.value = ctx.getString(R.string.reading_session_invalid_input)
                        return@TextButton
                    }

                    if (totalPages > 0) {
                        if (startPage !in 0..totalPages) {
                            errorText.value = ctx.getString(R.string.reading_session_invalid_page)
                            return@TextButton
                        }
                        if (endPage !in 0..totalPages) {
                            errorText.value = ctx.getString(R.string.reading_session_invalid_page)
                            return@TextButton
                        }
                    } else {
                        if (startPage < 0 || endPage < 0) {
                            errorText.value = ctx.getString(R.string.reading_session_invalid_page)
                            return@TextButton
                        }
                    }

                    val ok = vm.finishReading(
                        startPage = startPage,
                        endPage = endPage,
                        durationMinutes = minutes
                    )
                    if (ok) {
                        showFinishDialog.value = false
                        onBack()
                    } else {
                        errorText.value = ctx.getString(R.string.reading_session_save_failed)
                    }
                }) {
                    Text(stringResource(R.string.reading_session_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}