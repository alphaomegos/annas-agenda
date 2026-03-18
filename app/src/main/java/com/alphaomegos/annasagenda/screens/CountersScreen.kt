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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt
import com.alphaomegos.annasagenda.ManualCounter
import com.alphaomegos.annasagenda.DateRangeCounter
import com.alphaomegos.annasagenda.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountersScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()

    val showAddTypeDialog = remember { mutableStateOf(false) }
    val showCreateManualDialog = remember { mutableStateOf(false) }
    val showCreateDateDialog = remember { mutableStateOf(false) }

    val editManual = remember { mutableStateOf<ManualCounter?>(null) }
    val editDateRange = remember { mutableStateOf<DateRangeCounter?>(null) }

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
                            onClick = { editManual.value = c },
                            onDelete = { vm.deleteCounter(c.id) }
                        )
                        is DateRangeCounter -> DateRangeCounterCard(
                            counter = c,
                            onClick = { editDateRange.value = c },
                            onDelete = { vm.deleteCounter(c.id) }
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

    editManual.value?.let { c ->
        ManualCounterDialog(
            title = stringResource(R.string.edit_counter),
            initialTitle = c.title,
            initialBalance = c.balance,
            onDismiss = { editManual.value = null },
            onDelete = {
                vm.deleteCounter(c.id)
                editManual.value = null
            },
            onSave = { t, b ->
                vm.updateManualCounter(c.id, t, b)
                editManual.value = null
            }
        )
    }

    editDateRange.value?.let { c ->
        DateRangeCounterDialog(
            title = stringResource(R.string.edit_counter),
            initialTitle = c.title,
            initialStart = c.startDate,
            initialEnd = c.endDate,
            onDismiss = { editDateRange.value = null },
            onDelete = {
                vm.deleteCounter(c.id)
                editDateRange.value = null
            },
            onSave = { t, s, e ->
                vm.updateDateRangeCounter(c.id, t, s, e)
                editDateRange.value = null
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
                Icon(Icons.Default.Delete, contentDescription = null)
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
    val totalDays = max(1L, ChronoUnit.DAYS.between(counter.startDate, counter.endDate))
    val remainingDaysRaw = ChronoUnit.DAYS.between(today, counter.endDate)
    val remainingDays = remainingDaysRaw.coerceIn(0L, totalDays)

    val percentRemaining = (remainingDays.toDouble() / totalDays.toDouble()).coerceIn(0.0, 1.0)
    val percentInt = (percentRemaining * 100.0).roundToInt()

    val remainingText = buildRemainingText(today, counter.endDate)

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
                    stringResource(R.string.date_remaining_fmt, remainingText, percentInt),
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
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }
    }
}

private fun buildRemainingText(today: LocalDate, end: LocalDate): String {
    if (!today.isBefore(end)) return "0"
    val p = Period.between(today, end)
    val parts = mutableListOf<String>()
    if (p.years != 0) parts += "${p.years}y"
    if (p.months != 0) parts += "${p.months}m"
    if (p.days != 0) parts += "${p.days}d"
    if (parts.isEmpty()) return "0"
    return parts.joinToString(" ")
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