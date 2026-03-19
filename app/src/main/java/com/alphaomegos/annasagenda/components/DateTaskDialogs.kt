package com.alphaomegos.annasagenda.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveTaskDialogs(
    taskId: Long?,
    showDatePicker: Boolean,
    currentTaskDate: LocalDate?,
    onDismissAll: () -> Unit,
    onShowDatePicker: () -> Unit,
    onMoveToSomeday: (Long) -> Unit,
    onMoveToToday: (Long) -> Unit,
    onMoveToTomorrow: (Long) -> Unit,
    onMoveToDate: (Long, LocalDate) -> Unit,
) {
    if (taskId == null) return

    if (!showDatePicker) {
        AlertDialog(
            onDismissRequest = onDismissAll,
            title = { Text(stringResource(R.string.move_task)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            onMoveToSomeday(taskId)
                            onDismissAll()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.move_to_someday)) }

                    TextButton(
                        onClick = {
                            onMoveToToday(taskId)
                            onDismissAll()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            onMoveToTomorrow(taskId)
                            onDismissAll()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = onShowDatePicker,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.reschedule)) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissAll) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    } else {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember(taskId, currentTaskDate) {
            val d = currentTaskDate ?: LocalDate.now()
            d.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = onDismissAll,
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        onMoveToDate(taskId, newDate)
                    }
                    onDismissAll()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissAll) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CopyTaskDialogs(
    taskId: Long?,
    showDatePicker: Boolean,
    onDismissAll: () -> Unit,
    onShowDatePicker: () -> Unit,
    onCopyToToday: (Long) -> Unit,
    onCopyToTomorrow: (Long) -> Unit,
    onCopyToDate: (Long, LocalDate) -> Unit,
) {
    if (taskId == null) return

    val ctx = LocalContext.current

    if (!showDatePicker) {
        AlertDialog(
            onDismissRequest = onDismissAll,
            title = { Text(stringResource(R.string.copy_task)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            onCopyToToday(taskId)
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.toast_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismissAll()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            onCopyToTomorrow(taskId)
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.toast_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismissAll()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = onShowDatePicker,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_date)) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissAll) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    } else {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember {
            LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = onDismissAll,
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        onCopyToDate(taskId, newDate)
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.toast_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDismissAll()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissAll) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CopySubtaskDialogs(
    subtaskId: Long?,
    showDatePicker: Boolean,
    onDismissAll: () -> Unit,
    onShowDatePicker: () -> Unit,
    onCopyToToday: (Long) -> Unit,
    onCopyToTomorrow: (Long) -> Unit,
    onCopyToDate: (Long, LocalDate) -> Unit,
) {
    if (subtaskId == null) return

    val ctx = LocalContext.current

    if (!showDatePicker) {
        AlertDialog(
            onDismissRequest = onDismissAll,
            title = { Text(stringResource(R.string.copy_subtask)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            onCopyToToday(subtaskId)
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.toast_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismissAll()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            onCopyToTomorrow(subtaskId)
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.toast_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismissAll()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = onShowDatePicker,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_date)) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissAll) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    } else {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember {
            LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = onDismissAll,
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        onCopyToDate(subtaskId, newDate)
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.toast_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDismissAll()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissAll) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
internal fun MoveSubtaskDialog(
    subtaskId: Long?,
    state: AppState,
    onDismiss: () -> Unit,
    onMoveToTask: (Long, Long) -> Unit,
) {
    if (subtaskId == null) return

    val sub = state.subtasks.firstOrNull { it.id == subtaskId }
    val currentTaskId = sub?.taskId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_subtask)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                val today = LocalDate.now()
                val targets = state.tasks
                    .filter { it.id != currentTaskId }
                    .filter { it.date != null && !it.date.isBefore(today) }
                    .sortedWith(
                        compareBy(
                            { it.date!!.toEpochDay() },
                            { it.order },
                            { it.id }
                        )
                    )

                items(targets, key = { it.id }) { t ->
                    val dText = t.date?.toString() ?: stringResource(R.string.someday_tag)
                    TextButton(
                        onClick = {
                            onMoveToTask(subtaskId, t.id)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("${t.description} • $dText") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun AddSubtaskDialog(
    taskId: Long?,
    text: String,
    color: Long?,
    onTextChange: (String) -> Unit,
    onColorChange: (Long?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, Long?) -> Unit,
) {
    if (taskId == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_subtask)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.task_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.subtask_color))
                    Spacer(modifier = Modifier.width(10.dp))
                    ColorPickerRow(
                        selected = color,
                        onSelect = onColorChange
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = text.trim()
                    if (clean.isNotBlank()) {
                        onConfirm(taskId, clean, color)
                    }
                    onDismiss()
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}