package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.StepsWidgetLine
import com.alphaomegos.annasagenda.support.STEPS_GOAL_RANGE

/**
 * Settings → Steps widget (25.10, Eduard): its second line — yesterday, the
 * week's average, a green bar towards the day's goal, or none — the goal
 * itself, and whether the record shows.
 */
@Composable
internal fun StepsWidgetDialog(
    line: StepsWidgetLine,
    showRecord: Boolean,
    goal: Int,
    onSave: (StepsWidgetLine, Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(line) }
    var record by rememberSaveable { mutableStateOf(showRecord) }
    var goalText by rememberSaveable { mutableStateOf(goal.toString()) }
    val goalValue = goalText.toIntOrNull()
    val goalValid = goalValue != null && goalValue in STEPS_GOAL_RANGE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.steps_widget_menu)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StepsWidgetLine.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = picked == option, role = Role.RadioButton) { picked = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = picked == option, onClick = null)
                        Text(
                            stringResource(
                                when (option) {
                                    StepsWidgetLine.YESTERDAY -> R.string.steps_widget_line_yesterday
                                    StepsWidgetLine.WEEK_AVERAGE -> R.string.steps_widget_line_week
                                    StepsWidgetLine.GOAL_BAR -> R.string.steps_widget_line_goal
                                    StepsWidgetLine.NONE -> R.string.steps_widget_line_none
                                }
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (picked == StepsWidgetLine.GOAL_BAR) {
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text(stringResource(R.string.steps_widget_goal)) },
                        singleLine = true,
                        isError = !goalValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = record, role = Role.Switch) { record = it }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.steps_widget_show_record), modifier = Modifier.weight(1f))
                    Switch(checked = record, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked != StepsWidgetLine.GOAL_BAR || goalValid,
                onClick = { onSave(picked, record, goalValue?.takeIf { goalValid } ?: goal) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
