package com.alphaomegos.annasagenda.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AnthropometryEntry
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.util.parseDecimalOrNull
import com.alphaomegos.annasagenda.util.formatOneDecimal
import com.alphaomegos.annasagenda.util.parseOneDecimalOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue



@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnthropometryDayInputDialog(
    date: LocalDate,
    initialEntry: AnthropometryEntry?,
    onDismiss: () -> Unit,
    onSave: (List<Double?>) -> Unit
) {
    fun init(v: Double?) = v?.let {
        val s = String.format(Locale.US, "%.1f", it)
        if (s.endsWith(".0")) s.dropLast(2) else s
    } ?: ""

    var t0 by remember { mutableStateOf(init(initialEntry?.armCm)) }
    var t1 by remember { mutableStateOf(init(initialEntry?.chestCm)) }
    var t2 by remember { mutableStateOf(init(initialEntry?.underChestCm)) }
    var t3 by remember { mutableStateOf(init(initialEntry?.waistCm)) }
    var t4 by remember { mutableStateOf(init(initialEntry?.bellyCm)) }
    var t5 by remember { mutableStateOf(init(initialEntry?.hipsCm)) }
    var t6 by remember { mutableStateOf(init(initialEntry?.thighCm)) }
    var t7 by remember { mutableStateOf(init(initialEntry?.weightKg)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = date.toString(), style = MaterialTheme.typography.labelLarge)
                Text(text = stringResource(R.string.anthropometry_hint))

                OutlinedTextField(
                    t0,
                    { t0 = it },
                    label = { Text(stringResource(R.string.anthro_arm_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t1,
                    { t1 = it },
                    label = { Text(stringResource(R.string.anthro_chest_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t2,
                    { t2 = it },
                    label = { Text(stringResource(R.string.anthro_under_chest_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t3,
                    { t3 = it },
                    label = { Text(stringResource(R.string.anthro_waist_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t4,
                    { t4 = it },
                    label = { Text(stringResource(R.string.anthro_belly_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t5,
                    { t5 = it },
                    label = { Text(stringResource(R.string.anthro_hips_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t6,
                    { t6 = it },
                    label = { Text(stringResource(R.string.anthro_thigh_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t7,
                    { t7 = it },
                    label = { Text(stringResource(R.string.anthro_weight_kg)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = listOf(t0, t1, t2, t3, t4, t5, t6, t7).map { parseDecimalOrNull(it) }
                onSave(values)
            }) { Text(stringResource(R.string.anthropometry_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnthropometryInputDialog(
    entriesByDate: Map<LocalDate, AnthropometryEntry>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, List<Double?>) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }

    var date by remember { mutableStateOf(LocalDate.now()) }

    fun fillFromEntry(e: AnthropometryEntry?): List<String> = listOf(
        e?.armCm?.let { formatOneDecimal(it) } ?: "",
        e?.chestCm?.let { formatOneDecimal(it) } ?: "",
        e?.underChestCm?.let { formatOneDecimal(it) } ?: "",
        e?.waistCm?.let { formatOneDecimal(it) } ?: "",
        e?.bellyCm?.let { formatOneDecimal(it) } ?: "",
        e?.hipsCm?.let { formatOneDecimal(it) } ?: "",
        e?.thighCm?.let { formatOneDecimal(it) } ?: "",
        e?.weightKg?.let { formatOneDecimal(it) } ?: "",
    )

    var fields by remember { mutableStateOf(fillFromEntry(entriesByDate[date])) }

    val showDatePicker = remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = date.toString(), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { showDatePicker.value = true }) {
                        Text(stringResource(R.string.pick_date))
                    }
                }

                OutlinedTextField(
                    value = fields[0],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[0] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_arm_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[1],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[1] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_chest_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[2],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[2] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_under_chest_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[3],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[3] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_waist_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[4],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[4] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_belly_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[5],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[5] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_hips_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[6],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[6] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_thigh_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[7],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[7] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_weight_kg)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = fields.map { parseOneDecimalOrNull(it) }
                onSave(date, values)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showDatePicker.value) {
        val initialMillis = remember(date) {
            date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        fields = fillFromEntry(entriesByDate[date])
                    }
                    showDatePicker.value = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}