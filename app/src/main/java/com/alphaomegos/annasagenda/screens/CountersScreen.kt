package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.components.ConfirmDialog
import java.time.LocalDate
import kotlin.math.roundToInt
import com.alphaomegos.annasagenda.ManualCounter
import com.alphaomegos.annasagenda.DateRangeCounter
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.RemainingPart
import com.alphaomegos.annasagenda.RemainingUnit
import com.alphaomegos.annasagenda.remainingFractionOf
import com.alphaomegos.annasagenda.remainingUntil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountersScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val state by vm.counters.collectAsState()

    val showAddTypeDialog = rememberSaveable { mutableStateOf(false) }
    val showCreateManualDialog = rememberSaveable { mutableStateOf(false) }
    val showCreateDateDialog = rememberSaveable { mutableStateOf(false) }

    // The id rather than the counter: only the id can be put in saved state,
    // and holding the counter meant a rotation closed the dialog and took
    // whatever had been typed into it. Looking it up again also means an edit
    // that lands while the dialog is open is not shadowed by a stale copy.
    val editManualId = rememberSaveable { mutableStateOf<Long?>(null) }
    val editDateRangeId = rememberSaveable { mutableStateOf<Long?>(null) }

    // The bin sits on the card itself, beside the tap that opens the counter
    // for editing. A counter is a running total the user has been keeping —
    // there is no undo, and nothing else on the card is destructive.
    val pendingDeleteId = rememberSaveable { mutableStateOf<Long?>(null) }

    val editManual = state.counters
        .filterIsInstance<ManualCounter>()
        .firstOrNull { it.id == editManualId.value }

    val editDateRange = state.counters
        .filterIsInstance<DateRangeCounter>()
        .firstOrNull { it.id == editDateRangeId.value }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.counters_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTypeDialog.value = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        val counters = state.counters

        if (counters.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_counters_yet))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(counters, key = { it.id }) { c ->
                    when (c) {
                        is ManualCounter -> ManualCounterCard(
                            counter = c,
                            onClick = { editManualId.value = c.id },
                            onDelete = { pendingDeleteId.value = c.id }
                        )
                        is DateRangeCounter -> DateRangeCounterCard(
                            counter = c,
                            onClick = { editDateRangeId.value = c.id },
                            onDelete = { pendingDeleteId.value = c.id }
                        )
                    }
                }
            }
        }
    }

    if (showAddTypeDialog.value) {
        AlertDialog(
            onDismissRequest = { showAddTypeDialog.value = false },
            title = { Text(stringResource(R.string.add_counter)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showAddTypeDialog.value = false
                        showCreateDateDialog.value = true
                    }) { Text(stringResource(R.string.counter_type_date_range)) }

                    TextButton(onClick = {
                        showAddTypeDialog.value = false
                        showCreateManualDialog.value = true
                    }) { Text(stringResource(R.string.counter_type_manual)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTypeDialog.value = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showCreateManualDialog.value) {
        ManualCounterDialog(
            title = stringResource(R.string.create_manual_counter),
            initialTitle = "",
            initialBalance = 0,
            onDismiss = { showCreateManualDialog.value = false },
            onDelete = null,
            onSave = { t, b ->
                vm.addManualCounter(t, b)
                showCreateManualDialog.value = false
            }
        )
    }

    if (showCreateDateDialog.value) {
        DateRangeCounterDialog(
            title = stringResource(R.string.create_date_counter),
            initialTitle = "",
            initialStart = LocalDate.now(),
            initialEnd = LocalDate.now().plusDays(30),
            onDismiss = { showCreateDateDialog.value = false },
            onDelete = null,
            onSave = { t, s, e ->
                vm.addDateRangeCounter(t, s, e)
                showCreateDateDialog.value = false
            }
        )
    }

    pendingDeleteId.value?.let { counterId ->
        ConfirmDialog(
            titleRes = R.string.delete_counter_title,
            textRes = R.string.action_cannot_be_undone,
            confirmLabelRes = R.string.delete,
            onConfirm = {
                vm.deleteCounter(counterId)
                pendingDeleteId.value = null
                editManualId.value = null
                editDateRangeId.value = null
            },
            onDismiss = { pendingDeleteId.value = null },
        )
    }

    editManual?.let { c ->
        ManualCounterDialog(
            title = stringResource(R.string.edit_counter),
            initialTitle = c.title,
            initialBalance = c.balance,
            onDismiss = { editManualId.value = null },
            // Asked the same way as from the card; the edit dialog closes by
            // itself once the counter it was showing is gone.
            onDelete = { pendingDeleteId.value = c.id },
            onSave = { t, b ->
                vm.updateManualCounter(c.id, t, b)
                editManualId.value = null
            }
        )
    }

    editDateRange?.let { c ->
        DateRangeCounterDialog(
            title = stringResource(R.string.edit_counter),
            initialTitle = c.title,
            initialStart = c.startDate,
            initialEnd = c.endDate,
            onDismiss = { editDateRangeId.value = null },
            onDelete = { pendingDeleteId.value = c.id },
            onSave = { t, s, e ->
                vm.updateDateRangeCounter(c.id, t, s, e)
                editDateRangeId.value = null
            }
        )
    }
}

@Composable
private fun ManualCounterCard(
    counter: ManualCounter,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(counter.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.manual_remaining_fmt, counter.balance),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}

@Composable
private fun DateRangeCounterCard(
    counter: DateRangeCounter,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val today = LocalDate.now()

    val percentRemaining = remainingFractionOf(counter.startDate, counter.endDate, today)
    val percentInt = (percentRemaining * 100.0).roundToInt()

    val remainingLabel = remainingText(remainingUntil(today, counter.endDate))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(counter.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.date_remaining_fmt, remainingLabel, percentInt),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                progress = { percentRemaining.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = ProgressIndicatorDefaults.linearColor,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}

/**
 * The countdown as a phrase in the user's language.
 *
 * A plain loop rather than joinToString: pluralStringResource is a composable
 * and cannot be called from the lambda that joinToString takes.
 */
@Composable
private fun remainingText(parts: List<RemainingPart>): String {
    val words = ArrayList<String>(parts.size)

    for (part in parts) {
        words += pluralStringResource(pluralResFor(part.unit), part.amount, part.amount)
    }

    return words.joinToString(" ")
}

private fun pluralResFor(unit: RemainingUnit): Int = when (unit) {
    RemainingUnit.YEARS -> R.plurals.counter_remaining_years
    RemainingUnit.MONTHS -> R.plurals.counter_remaining_months
    RemainingUnit.DAYS -> R.plurals.counter_remaining_days
}

@Composable
private fun ManualCounterDialog(
    title: String,
    initialTitle: String,
    initialBalance: Int,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, Int) -> Unit,
) {
    var t by rememberSaveable { mutableStateOf(initialTitle) }
    var balanceText by rememberSaveable { mutableStateOf(initialBalance.toString()) }

    val parsedBalance = balanceText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    label = { Text(stringResource(R.string.counter_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val v = (parsedBalance ?: 0) - 1
                            balanceText = v.toString()
                        }
                    ) { Text("-") }

                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = { Text(stringResource(R.string.balance)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = {
                            val v = (parsedBalance ?: 0) + 1
                            balanceText = v.toString()
                        }
                    ) { Text("+") }
                }

                if (parsedBalance == null && balanceText.isNotBlank()) {
                    Text(stringResource(R.string.invalid_number), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(t, parsedBalance ?: 0) },
                enabled = t.trim().isNotEmpty() && (parsedBalance != null || balanceText.isBlank())
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    )
}

@Composable
private fun DateRangeCounterDialog(
    title: String,
    initialTitle: String,
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, LocalDate, LocalDate) -> Unit,
) {
    var t by rememberSaveable { mutableStateOf(initialTitle) }
    var startText by rememberSaveable { mutableStateOf(initialStart.toString()) } // yyyy-MM-dd
    var endText by rememberSaveable { mutableStateOf(initialEnd.toString()) }

    val start = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
    val end = runCatching { LocalDate.parse(endText.trim()) }.getOrNull()

    val datesOk = start != null && end != null && start.isBefore(end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    label = { Text(stringResource(R.string.counter_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text(stringResource(R.string.start_date_iso)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text(stringResource(R.string.end_date_iso)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!datesOk) {
                    Text(stringResource(R.string.invalid_date_range_hint), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(t, start!!, end!!) },
                enabled = t.trim().isNotEmpty() && datesOk
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    )
}