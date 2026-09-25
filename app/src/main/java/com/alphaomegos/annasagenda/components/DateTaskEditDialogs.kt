package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.ManualCounter
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.Task

@Composable
internal fun EditTaskDialog(
    taskId: Long?,
    editingTask: Task?,
    manualCounters: List<ManualCounter>,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowRepeatPicker: () -> Unit,
    onShowCounterPicker: () -> Unit,
    onDelete: (Long) -> Unit,
    onConfirm: (Long, String) -> Unit,
    onDetachCounter: (Long) -> Unit,
) {
    if (taskId == null) return

    // Deleting used to happen on the tap. It is the only button in this dialog
    // that cannot be taken back, and it sits under the two that can.
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    val linkedId = editingTask?.linkedManualCounterId
    val linkedTitle = manualCounters.firstOrNull { it.id == linkedId }?.title

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_description_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.task_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (linkedId == null) {
                    OutlinedButton(
                        onClick = onShowCounterPicker,
                        enabled = manualCounters.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.attach_counter))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.attached_counter_fmt, linkedTitle ?: ""),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onDetachCounter(taskId) }
                        ) {
                            Text(stringResource(R.string.detach_counter))
                        }
                    }
                }

                OutlinedButton(
                    onClick = onShowRepeatPicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.repeat))
                }

                TextButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.remove))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(taskId, text) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (confirmingDelete) {
        ConfirmDialog(
            titleRes = R.string.delete_task_title,
            textRes = R.string.action_cannot_be_undone,
            confirmLabelRes = R.string.remove,
            onConfirm = {
                confirmingDelete = false
                onDelete(taskId)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
internal fun TaskCounterPickerDialog(
    taskId: Long?,
    manualCounters: List<ManualCounter>,
    onDismiss: () -> Unit,
    onSelectCounter: (Long, Long?) -> Unit,
) {
    if (taskId == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_counter)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                item {
                    TextButton(
                        onClick = {
                            onSelectCounter(taskId, null)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.no_counter)) }
                }

                if (manualCounters.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_counters_yet),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(manualCounters, key = { it.id }) { c ->
                        TextButton(
                            onClick = {
                                onSelectCounter(taskId, c.id)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(c.title) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
internal fun EditSubtaskDialog(
    subtaskId: Long?,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowRepeatPicker: () -> Unit,
    onDelete: (Long) -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    if (subtaskId == null) return

    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_description_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text(stringResource(R.string.task_description_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(subtaskId, text) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShowRepeatPicker) {
                    Text(stringResource(R.string.repeat))
                }

                // Red, like the same button in the task dialog above. It was
                // the only one of the three that could not be taken back, and
                // the only one that looked exactly like its neighbours.
                TextButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.remove))
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )

    if (confirmingDelete) {
        ConfirmDialog(
            titleRes = R.string.delete_subtask_title,
            textRes = R.string.action_cannot_be_undone,
            confirmLabelRes = R.string.remove,
            onConfirm = {
                confirmingDelete = false
                onDelete(subtaskId)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}