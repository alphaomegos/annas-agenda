package com.alphaomegos.annasagenda.components

import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.model.ManualCounter
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.Task
import com.alphaomegos.annasagenda.support.formatTaskTime

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
    onShowTimePicker: () -> Unit,
    onClearTime: (Long) -> Unit,
    askFirst: Boolean = true,
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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

                // A time needs a day: Someday has no clock, so the row is not
                // offered there at all rather than offered and refused.
                val datedTask = editingTask?.takeIf { it.date != null }
                if (datedTask != null) {
                    val time = datedTask.time
                    if (time == null) {
                        OutlinedButton(
                            onClick = onShowTimePicker,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.set_task_time))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onShowTimePicker,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.task_time_fmt, formatTaskTime(time)))
                            }
                            TextButton(
                                onClick = { onClearTime(taskId) }
                            ) {
                                Text(stringResource(R.string.clear_task_time))
                            }
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
                    onClick = { if (askFirst) confirmingDelete = true else onDelete(taskId) },
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
    askFirst: Boolean = true,
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
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                    onClick = { if (askFirst) confirmingDelete = true else onDelete(subtaskId) },
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
/** One of the task dialog's tools in the new look (28.10): an icon over a short word. */
@Composable
private fun TaskTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/**
 * The task's dialog in the day screen's new look (28.10, Eduard): «Task
 * description» once, as the title; the text with no frame round it; the time
 * as a clock to tap — once set, «14:10» and a cross to take it off; and the
 * tools as icons with short words: repeat, counter, copy, move, delete.
 * «Summer 2026» keeps the dialog as it was (EditTaskDialog).
 */
@Composable
internal fun NewEditTaskDialog(
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
    onShowTimePicker: () -> Unit,
    onClearTime: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    onMove: (Long) -> Unit,
    askFirst: Boolean = true,
) {
    if (taskId == null) return
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val linkedTitle = manualCounters.firstOrNull { it.id == editingTask?.linkedManualCounterId }?.title
    val time = editingTask?.time
    // No title (28.10): the task's own words are the head of the dialog.
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The text and its time on one line (28.10).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = MaterialTheme.typography.titleMedium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = frameless(),
                        modifier = Modifier.weight(1f),
                    )
                    if (time == null) {
                        IconButton(onClick = onShowTimePicker) {
                            Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.set_task_time))
                        }
                    } else {
                        TextButton(onClick = onShowTimePicker) {
                            Text(formatTaskTime(time), style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { onClearTime(taskId) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_task_time))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (linkedTitle != null) {
                        Text(
                            stringResource(R.string.attached_counter_fmt, linkedTitle),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onDetachCounter(taskId) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.detach_counter))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TaskTool(Icons.Default.Repeat, stringResource(R.string.repeat), onClick = onShowRepeatPicker)
                    TaskTool(Icons.Default.PlusOne, stringResource(R.string.task_menu_counter), onClick = onShowCounterPicker)
                    TaskTool(Icons.Default.ContentCopy, stringResource(R.string.task_tool_copy)) { onCopy(taskId) }
                    TaskTool(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.task_tool_move)) { onMove(taskId) }
                    TaskTool(Icons.Default.Delete, stringResource(R.string.remove), MaterialTheme.colorScheme.error) { if (askFirst) confirmingDelete = true else onDelete(taskId) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(taskId, text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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

/** The task's dialog as the look has it (28.10): the new one, or «Summer 2026»'s. Same arguments. */
@Composable
internal fun TaskDialogCall(
    newLook: Boolean,
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
    onShowTimePicker: () -> Unit,
    onClearTime: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    onMove: (Long) -> Unit,
    askFirst: Boolean = true,
) {
    if (newLook) {
        NewEditTaskDialog(taskId, editingTask, manualCounters, text, onTextChange, onDismiss, onShowRepeatPicker, onShowCounterPicker, onDelete, onConfirm, onDetachCounter, onShowTimePicker, onClearTime, onCopy, onMove, askFirst = askFirst)
    } else {
        EditTaskDialog(taskId, editingTask, manualCounters, text, onTextChange, onDismiss, onShowRepeatPicker, onShowCounterPicker, onDelete, onConfirm, onDetachCounter, onShowTimePicker, onClearTime, askFirst = askFirst)
    }
}

/** A text field with no frame and no fill (28.10): the words alone, on the dialog's own surface. */
@Composable
private fun frameless() = TextFieldDefaults.colors(
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
)

/**
 * A subtask's dialog in the new look (28.10): as the task's — no title, the
 * words with no frame, the tools as icons with short words: repeat, copy,
 * move, delete. «Summer 2026» keeps EditSubtaskDialog.
 */
@Composable
internal fun NewEditSubtaskDialog(
    subtaskId: Long?,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowRepeatPicker: () -> Unit,
    onDelete: (Long) -> Unit,
    onConfirm: (Long, String) -> Unit,
    onCopy: (Long) -> Unit,
    onMove: (Long) -> Unit,
    askFirst: Boolean = true,
) {
    if (subtaskId == null) return
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = frameless(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TaskTool(Icons.Default.Repeat, stringResource(R.string.repeat), onClick = onShowRepeatPicker)
                    TaskTool(Icons.Default.ContentCopy, stringResource(R.string.task_tool_copy)) { onCopy(subtaskId) }
                    TaskTool(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.task_tool_move)) { onMove(subtaskId) }
                    TaskTool(Icons.Default.Delete, stringResource(R.string.remove), MaterialTheme.colorScheme.error) { if (askFirst) confirmingDelete = true else onDelete(subtaskId) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(subtaskId, text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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

/** A subtask's dialog as the look has it (28.10). */
@Composable
internal fun SubtaskDialogCall(
    newLook: Boolean,
    subtaskId: Long?,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowRepeatPicker: () -> Unit,
    onDelete: (Long) -> Unit,
    onConfirm: (Long, String) -> Unit,
    onCopy: (Long) -> Unit,
    onMove: (Long) -> Unit,
    askFirst: Boolean = true,
) {
    if (newLook) NewEditSubtaskDialog(subtaskId, text, onTextChange, onDismiss, onShowRepeatPicker, onDelete, onConfirm, onCopy, onMove, askFirst = askFirst)
    else EditSubtaskDialog(subtaskId, text, onTextChange, onDismiss, onShowRepeatPicker, onDelete, onConfirm, askFirst = askFirst)
}
