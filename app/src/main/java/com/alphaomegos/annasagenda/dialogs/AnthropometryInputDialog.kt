package com.alphaomegos.annasagenda.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AnthropometryEntry
import com.alphaomegos.annasagenda.AnthropometryFieldIds
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.formatMediumDate
import com.alphaomegos.annasagenda.parseAnthropometryInputs
import com.alphaomegos.annasagenda.typedFieldsFromSavedStrings
import com.alphaomegos.annasagenda.typedFieldsToSavedStrings
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.formatOneDecimal
import java.time.LocalDate

/**
 * What these dialogs have to carry across a rotation.
 *
 * Turning the phone tears the activity down and builds it again, so anything
 * held in a plain `remember` is gone. Here that is the half-typed numbers, the
 * red marks on the ones that could not be read, and the day they belong to —
 * in other words, everything the user has done since opening the dialog.
 *
 * Only Bundle-shaped values survive, hence the three savers: a map of text
 * becomes a flat list of strings, a set of ids becomes a list of them, and a
 * date becomes the day count it already is underneath.
 */
private val typedFieldsSaver = listSaver<Map<String, String>, String>(
    save = { typedFieldsToSavedStrings(it) },
    restore = { typedFieldsFromSavedStrings(it) },
)

private val fieldIdSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

private val localDateSaver = Saver<LocalDate, Long>(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) },
)

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

/**
 * One measurement field, shared by both dialogs so the keyboard and the error
 * state cannot drift apart between them.
 */
@Composable
private fun AnthropometryValueField(
    def: AnthropometryInputFieldDef,
    text: String,
    isError: Boolean,
    onTextChange: (String) -> Unit,
) {
    val supportingText: (@Composable () -> Unit)? =
        if (isError) {
            { Text(stringResource(R.string.anthropometry_invalid_value)) }
        } else {
            null
        }

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(stringResource(def.labelRes)) },
        singleLine = true,
        isError = isError,
        // A number field used to get the full text keyboard, which is how a
        // unit ends up typed into it in the first place.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = supportingText,
        modifier = Modifier.fillMaxWidth()
    )
}

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

    var fields by rememberSaveable(
        initialEntry,
        activeFieldDefs,
        stateSaver = typedFieldsSaver,
    ) {
        mutableStateOf(fillAnthropometryFieldsFromEntry(initialEntry, activeFieldDefs))
    }

    var invalidFieldIds by rememberSaveable(
        initialEntry,
        activeFieldDefs,
        stateSaver = fieldIdSetSaver,
    ) {
        mutableStateOf(emptySet<String>())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = formatMediumDate(date, appLocale()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(text = stringResource(R.string.anthropometry_hint))

                activeFieldDefs.forEach { field ->
                    AnthropometryValueField(
                        def = field,
                        text = fields[field.id].orEmpty(),
                        isError = field.id in invalidFieldIds,
                        onTextChange = { newText ->
                            fields = fields.toMutableMap().also { it[field.id] = newText }
                            invalidFieldIds = invalidFieldIds - field.id
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = parseAnthropometryInputs(
                    activeFieldDefs.associate { it.id to fields[it.id].orEmpty() }
                )

                // Refusing to save is the whole point: a field that cannot be
                // read must not be taken for "the user cleared this".
                if (parsed.isValid) {
                    onSave(parsed.values)
                } else {
                    invalidFieldIds = parsed.invalidFieldIds
                }
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
    val activeFieldDefs = remember(enabledFieldIds) {
        anthropometryInputFieldDefs
            .filter { it.id in enabledFieldIds }
            .ifEmpty { anthropometryInputFieldDefs }
    }

    var date by rememberSaveable(stateSaver = localDateSaver) {
        mutableStateOf(LocalDate.now())
    }

    var fields by rememberSaveable(
        date,
        activeFieldDefs,
        entriesByDate,
        stateSaver = typedFieldsSaver,
    ) {
        mutableStateOf(fillAnthropometryFieldsFromEntry(entriesByDate[date], activeFieldDefs))
    }

    var invalidFieldIds by rememberSaveable(
        date,
        activeFieldDefs,
        entriesByDate,
        stateSaver = fieldIdSetSaver,
    ) {
        mutableStateOf(emptySet<String>())
    }

    val showDatePicker = rememberSaveable { mutableStateOf(false) }

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
                    Text(
                        text = formatMediumDate(date, appLocale()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = { showDatePicker.value = true }) {
                        Text(stringResource(R.string.pick_date))
                    }
                }

                activeFieldDefs.forEach { field ->
                    AnthropometryValueField(
                        def = field,
                        text = fields[field.id].orEmpty(),
                        isError = field.id in invalidFieldIds,
                        onTextChange = { newText ->
                            fields = fields.toMutableMap().also { it[field.id] = newText }
                            invalidFieldIds = invalidFieldIds - field.id
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = parseAnthropometryInputs(
                    activeFieldDefs.associate { it.id to fields[it.id].orEmpty() }
                )

                if (parsed.isValid) {
                    onSave(date, parsed.values)
                } else {
                    invalidFieldIds = parsed.invalidFieldIds
                }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showDatePicker.value) {
        PickDateDialog(
            initialDate = date,
            onDismiss = { showDatePicker.value = false },
            onPicked = { picked -> date = picked },
        )
    }
}