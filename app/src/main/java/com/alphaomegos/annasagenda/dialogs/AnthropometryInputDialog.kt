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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AnthropometryEntry
import com.alphaomegos.annasagenda.AnthropometryFieldIds
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.util.formatOneDecimal
import com.alphaomegos.annasagenda.util.parseOneDecimalOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class AnthropometryInputFieldDef(
    val id: String,
    val labelRes: Int,
    val getValue: (AnthropometryEntry) -> Double?
)

private val anthropometryInputFieldDefs = listOf(
    AnthropometryInputFieldDef(
        id = AnthropometryFieldIds.ARM,
        labelRes = R.string.anthro_arm_cm
    ) { it.armCm },

    AnthropometryInputFieldDef(
        id = AnthropometryFieldIds.CHEST,
        labelRes = R.string.anthro_chest_cm
    ) { it.chestCm },

    AnthropometryInputFieldDef(
        id = AnthropometryFieldIds.UNDER_CHEST,
        labelRes = R.string.anthro_under_chest_cm
    ) { it.underChestCm },

    AnthropometryInputFieldDef(
        id = AnthropometryFieldIds.WAIST,
        labelRes = R.string.anthro_waist_cm
    ) { it.waistCm },

    AnthropometryInputFieldDef(
        id = AnthropometryFieldIds.BELLY,
        labelRes = R.string.anthro_belly_cm
    ) { it.bellyCm },

    AnthropometryInputFieldDef(
        id = AnthropometryFieldIds.HIPS,
        labelRes = R.string.anthro_hips_cm
    ) { it.hipsCm },

    AnthropometryInputFieldDef(
        id = AnthropometryFieldIds.THIGH,
        labelRes = R.string.anthro_thigh_cm
    ) { it.thighCm },

    AnthropometryInputFieldDef(
        id = AnthropometryFieldIds.WEIGHT,
        labelRes = R.string.anthro_weight_kg
    ) { it.weightKg },
)

private fun fillAnthropometryFieldsFromEntry(
    entry: AnthropometryEntry?,
    fieldDefs: List<AnthropometryInputFieldDef>
): Map<String, String> {
    return fieldDefs.associate { field ->
        field.id to (field.getValue(entry ?: return@associate field.id to "")?.let { formatOneDecimal(it) } ?: "")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnthropometryDayInputDialog(
    date: LocalDate,
    initialEntry: AnthropometryEntry?,
    enabledFieldIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Double?>) -> Unit
) {
    val activeFieldDefs = remember(enabledFieldIds) {
        anthropometryInputFieldDefs
            .filter { it.id in enabledFieldIds }
            .ifEmpty { anthropometryInputFieldDefs }
    }

    var fields by remember(initialEntry, activeFieldDefs) {
        mutableStateOf(fillAnthropometryFieldsFromEntry(initialEntry, activeFieldDefs))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = date.toString(), style = MaterialTheme.typography.labelLarge)
                Text(text = stringResource(R.string.anthropometry_hint))

                activeFieldDefs.forEach { field ->
                    OutlinedTextField(
                        value = fields[field.id].orEmpty(),
                        onValueChange = { newText ->
                            fields = fields.toMutableMap().also { it[field.id] = newText }
                        },
                        label = { Text(stringResource(field.labelRes)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = activeFieldDefs.associate { field ->
                    field.id to parseOneDecimalOrNull(fields[field.id].orEmpty())
                }
                onSave(values)
            }) {
                Text(stringResource(R.string.anthropometry_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnthropometryInputDialog(
    entriesByDate: Map<LocalDate, AnthropometryEntry>,
    enabledFieldIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Map<String, Double?>) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }

    val activeFieldDefs = remember(enabledFieldIds) {
        anthropometryInputFieldDefs
            .filter { it.id in enabledFieldIds }
            .ifEmpty { anthropometryInputFieldDefs }
    }

    var date by remember { mutableStateOf(LocalDate.now()) }

    var fields by remember(date, activeFieldDefs, entriesByDate) {
        mutableStateOf(fillAnthropometryFieldsFromEntry(entriesByDate[date], activeFieldDefs))
    }

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

                activeFieldDefs.forEach { field ->
                    OutlinedTextField(
                        value = fields[field.id].orEmpty(),
                        onValueChange = { newText ->
                            fields = fields.toMutableMap().also { it[field.id] = newText }
                        },
                        label = { Text(stringResource(field.labelRes)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = activeFieldDefs.associate { field ->
                    field.id to parseOneDecimalOrNull(fields[field.id].orEmpty())
                }
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