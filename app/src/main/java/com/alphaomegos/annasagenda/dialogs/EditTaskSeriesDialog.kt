package com.alphaomegos.annasagenda.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.RepeatRule
import com.alphaomegos.annasagenda.components.PickTimeDialog
import com.alphaomegos.annasagenda.support.formatTaskTime
import com.alphaomegos.annasagenda.support.minuteOfDay
import com.alphaomegos.annasagenda.support.repeatRuleFromSavedStrings
import com.alphaomegos.annasagenda.support.repeatRuleToSavedStrings
import com.alphaomegos.annasagenda.support.timeFromMinuteOfDay
import java.time.LocalTime

/** A rule as five strings in a Bundle; see repeatRuleToSavedStrings. */
private val repeatRuleSaver = listSaver<RepeatRule, String>(
    save = { repeatRuleToSavedStrings(it) },
    restore = { repeatRuleFromSavedStrings(it) },
)

/**
 * The wording, the time and the rule of a repeating task, changed from today on.
 *
 * Says so in the dialog itself: "from today" is the whole point, and the
 * user deciding whether to rename a series wants to know that the Thursdays
 * already behind them will keep the old name.
 *
 * Nothing is written until OK — unlike the time in a single task's dialog,
 * which is written at once. Here one OK is one split of the series; picking a
 * time and then a new name should not split it twice.
 *
 * Everything typed or picked is saveable, so turning the phone keeps it.
 */
@Composable
internal fun EditTaskSeriesDialog(
    initialDescription: String,
    initialTime: LocalTime?,
    initialRule: RepeatRule,
    canHaveTime: Boolean,
    ruleLabel: @Composable (RepeatRule) -> String,
    onDismiss: () -> Unit,
    onSave: (description: String, time: LocalTime?, rule: RepeatRule) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialDescription) }
    // Minutes since midnight: a LocalTime does not go into a Bundle.
    var timeMinute by rememberSaveable { mutableStateOf(initialTime?.let { minuteOfDay(it) }) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val time = timeFromMinuteOfDay(timeMinute)
    var rule by rememberSaveable(stateSaver = repeatRuleSaver) { mutableStateOf(initialRule) }
    var pickingRule by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_series_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.edit_series_from_today_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (canHaveTime) {
                    if (time == null) {
                        OutlinedButton(
                            onClick = { picking = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.set_task_time))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { picking = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.task_time_fmt, formatTaskTime(time)))
                            }
                            TextButton(onClick = { timeMinute = null }) {
                                Text(stringResource(R.string.clear_task_time))
                            }
                        }
                    }
                }

                // The same picker the day screen uses. Switching repeating off
                // is not offered here: stopping a series is "Delete from
                // today", one button over on the card.
                OutlinedButton(
                    onClick = { pickingRule = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(ruleLabel(rule))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text, time, rule) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (pickingRule) {
        RepeatPickerDialog(
            initial = rule,
            onDismiss = { pickingRule = false },
            onConfirm = { picked ->
                if (picked != null) rule = picked
                pickingRule = false
            },
        )
    }

    if (picking) {
        PickTimeDialog(
            initialTime = time,
            onDismiss = { picking = false },
            onPicked = { picked -> timeMinute = minuteOfDay(picked) },
        )
    }
}
