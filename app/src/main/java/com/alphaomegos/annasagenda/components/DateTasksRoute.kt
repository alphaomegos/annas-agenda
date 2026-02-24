package com.alphaomegos.annasagenda.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.RepeatRule
import com.alphaomegos.annasagenda.Subtask
import com.alphaomegos.annasagenda.Task
import com.alphaomegos.annasagenda.dialogs.RepeatPickerDialog
import com.alphaomegos.annasagenda.util.isSuppressedTemplateTaskOnItsDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.ui.draw.clip


internal data class DateTasksActions(
    val toggleTaskDone: (Long) -> Unit,
    val toggleSubtaskDone: (Long) -> Unit,

    val moveTaskUp: (Long) -> Unit,
    val moveTaskDown: (Long) -> Unit,
    val rescheduleTaskToDate: (Long, LocalDate?) -> Unit,
    val copyTaskToDate: (Long, LocalDate) -> Unit,

    val setTaskColor: (Long, Long?) -> Unit,
    val setSubtaskColor: (Long, Long?) -> Unit,

    val moveSubtaskUp: (Long) -> Unit,
    val moveSubtaskDown: (Long) -> Unit,
    val moveSubtaskToTask: (Long, Long) -> Unit,

    val createSubtask: (taskId: Long, text: String, colorArgb: Long?) -> Unit,

    val copySubtaskToDate: (Long, LocalDate) -> Unit,

    val updateTaskDescription: (Long, String) -> Unit,
    val updateSubtaskDescription: (Long, String) -> Unit,

    val deleteTask: (Long) -> Unit,
    val deleteSubtask: (Long) -> Unit,

    val setTaskRepeatRule: (Long, RepeatRule?) -> Unit,
    val setSubtaskRepeatRule: (Long, RepeatRule?) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTasksBlock(
    vm: AppViewModel,
    state: AppState,
    date: LocalDate?
) {
    val tasks = remember(state.tasks, state.suppressedRecurrences, date) {
        state.tasks
            .filter { it.date == date }
            .filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressedRecurrences) }
            .sortedWith(compareBy({ it.order }, { it.id }))
    }
    if (tasks.isEmpty()) return

    val taskIds = remember(tasks) { tasks.map { it.id }.toHashSet() }

    val subtasksByTaskId: Map<Long, List<Subtask>> = remember(state.subtasks, taskIds) {
        state.subtasks
            .filter { it.taskId in taskIds }
            .sortedWith(compareBy({ it.order }, { it.id }))
            .groupBy { it.taskId }
    }

    val actions = remember(vm) {
        DateTasksActions(
            toggleTaskDone = { vm.toggleTaskDone(it) },
            toggleSubtaskDone = { vm.toggleSubtaskDone(it) },

            moveTaskUp = { vm.moveTaskUp(it) },
            moveTaskDown = { vm.moveTaskDown(it) },
            rescheduleTaskToDate = { taskId, newDate -> vm.rescheduleTaskToDate(taskId, newDate) },
            copyTaskToDate = { taskId, newDate -> vm.copyTaskToDate(taskId, newDate) },

            setTaskColor = { taskId, color -> vm.setTaskColor(taskId, color) },
            setSubtaskColor = { subId, color -> vm.setSubtaskColor(subId, color) },

            moveSubtaskUp = { vm.moveSubtaskUp(it) },
            moveSubtaskDown = { vm.moveSubtaskDown(it) },
            moveSubtaskToTask = { subId, taskId -> vm.moveSubtask(subId, taskId) },

            createSubtask = { taskId, text, color -> vm.createSubtask(taskId, text, colorArgb = color) },

            copySubtaskToDate = { subId, newDate -> vm.copySubtaskToDate(subId, newDate) },

            updateTaskDescription = { taskId, text -> vm.updateTaskDescription(taskId, text) },
            updateSubtaskDescription = { subId, text -> vm.updateSubtaskDescription(subId, text) },

            deleteTask = { vm.deleteTask(it) },
            deleteSubtask = { vm.deleteSubtask(it) },

            setTaskRepeatRule = { taskId, rule -> vm.setTaskRepeatRule(taskId, rule) },
            setSubtaskRepeatRule = { subId, rule -> vm.setSubtaskRepeatRule(subId, rule) },
        )
    }

    DateTasksBlockContent(
        state = state,
        date = date,
        tasks = tasks,
        subtasksByTaskId = subtasksByTaskId,
        actions = actions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTasksBlockContent(
    state: AppState,
    date: LocalDate?,
    tasks: List<Task>,
    subtasksByTaskId: Map<Long, List<Subtask>>,
    actions: DateTasksActions,
) {
    var expandedTaskIds by remember(date) { mutableStateOf<Set<Long>>(emptySet()) }

    var moveTaskId by remember { mutableStateOf<Long?>(null) }
    val showMoveTaskDatePicker = remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    var copyTaskId by remember { mutableStateOf<Long?>(null) }
    val showCopyTaskDatePicker = remember { mutableStateOf(false) }

    var copySubtaskId by remember { mutableStateOf<Long?>(null) }
    val showCopySubtaskDatePicker = remember { mutableStateOf(false) }

    val moveSubtaskId = remember { mutableStateOf<Long?>(null) }

    val addSubtaskToTaskId = remember { mutableStateOf<Long?>(null) }
    var newSubtaskText by remember { mutableStateOf("") }
    var newSubtaskColor by remember { mutableStateOf<Long?>(null) }

    var editTaskId by remember { mutableStateOf<Long?>(null) }
    val editTaskText = remember { mutableStateOf("") }
    val editTaskRepeatRule = remember { mutableStateOf<RepeatRule?>(null) }
    val showTaskRepeatPicker = remember { mutableStateOf(false) }

    var editSubtaskId by remember { mutableStateOf<Long?>(null) }
    val editSubtaskText = remember { mutableStateOf("") }
    val editSubtaskRepeatRule = remember { mutableStateOf<RepeatRule?>(null) }
    val showSubtaskRepeatPicker = remember { mutableStateOf(false) }

    val markerShape = remember { RoundedCornerShape(10.dp) }
    val markerAlpha = 0.18f

    tasks.forEach { task ->
        val subtasks = subtasksByTaskId[task.id].orEmpty()

        val taskBg = task.colorArgb
            ?.toInt()
            ?.let { Color(it).copy(alpha = markerAlpha) }
            ?: Color.Transparent

        val cycleTaskColor = {
            actions.setTaskColor(task.id, nextPaletteColor(task.colorArgb))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(taskBg, markerShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            val isTaskExpanded = expandedTaskIds.contains(task.id)

            TextButton(
                onClick = {
                    expandedTaskIds =
                        if (isTaskExpanded) expandedTaskIds - task.id else expandedTaskIds + task.id
                },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                modifier = Modifier.width(26.dp)
            ) {
                Text(if (isTaskExpanded) "▼" else "▶")
            }


            val totalSubs = subtasks.size
            val doneSubs = if (totalSubs == 0) 0 else subtasks.count { it.isDone }

            if (totalSubs == 0 || task.isDone) {
                Checkbox(
                    checked = task.isDone,
                    onCheckedChange = { actions.toggleTaskDone(task.id) }
                )
            } else {
                SubtaskProgressToggle(
                    done = doneSubs,
                    total = totalSubs,
                    onClick = { actions.toggleTaskDone(task.id) }
                )
            }


            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
                    .clickable { cycleTaskColor() }
            ) {
                val deco = if (task.isDone) TextDecoration.LineThrough else null
                Text(
                    text = task.description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editTaskId = task.id
                            editTaskText.value = task.description
                            editTaskRepeatRule.value = task.repeatRule
                            showTaskRepeatPicker.value = false
                        },
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = deco)
                )
            }

            TinyIconButton(
                onClick = { actions.moveTaskUp(task.id) },
                icon = Icons.Default.KeyboardArrowUp,
                cd = "Move task up"
            )
            TinyIconButton(
                onClick = { actions.moveTaskDown(task.id) },
                icon = Icons.Default.KeyboardArrowDown,
                cd = "Move task down"
            )
            TinyIconButton(
                onClick = { moveTaskId = task.id },
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                cd = "Move task"
            )
            TinyIconButton(
                onClick = { copyTaskId = task.id },
                icon = Icons.Default.ContentCopy,
                cd = stringResource(R.string.copy_task)
            )

            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(36.dp)
                    .clickable { cycleTaskColor() }
            )
        }
        val isTaskExpanded = expandedTaskIds.contains(task.id)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (isTaskExpanded) {
                TextButton(onClick = {
                    addSubtaskToTaskId.value = task.id
                    newSubtaskText = ""
                    newSubtaskColor = task.colorArgb
                }) {
                    Text(stringResource(R.string.add_subtask))
                }
            }
        }

        if (subtasks.isNotEmpty() && isTaskExpanded) {
            subtasks.forEach { st ->
                val subBg = st.colorArgb
                    ?.toInt()
                    ?.let { Color(it).copy(alpha = markerAlpha) }
                    ?: Color.Transparent

                val cycleSubtaskColor = {
                    actions.setSubtaskColor(st.id, nextPaletteColor(st.colorArgb))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp)
                        .background(subBg, markerShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = st.isDone,
                        onCheckedChange = { actions.toggleSubtaskDone(st.id) }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                            .clickable { cycleSubtaskColor() }
                    ) {
                        val stDeco = if (st.isDone) TextDecoration.LineThrough else null
                        Text(
                            text = st.description,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editSubtaskId = st.id
                                    editSubtaskText.value = st.description
                                    editSubtaskRepeatRule.value = st.repeatRule
                                    showSubtaskRepeatPicker.value = false
                                },
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = stDeco)
                        )
                    }

                    TinyIconButton(
                        onClick = { actions.moveSubtaskUp(st.id) },
                        icon = Icons.Default.KeyboardArrowUp,
                        cd = "Move subtask up"
                    )
                    TinyIconButton(
                        onClick = { actions.moveSubtaskDown(st.id) },
                        icon = Icons.Default.KeyboardArrowDown,
                        cd = "Move subtask down"
                    )
                    TinyIconButton(
                        onClick = { moveSubtaskId.value = st.id },
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        cd = "Move subtask"
                    )
                    TinyIconButton(
                        onClick = { copySubtaskId = st.id },
                        icon = Icons.Default.ContentCopy,
                        cd = stringResource(R.string.copy_subtask)
                    )

                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(36.dp)
                            .clickable { cycleSubtaskColor() }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }

    // Move task: choose date (Today / Tomorrow / Someday / Pick date)
    if (moveTaskId != null && !showMoveTaskDatePicker.value) {
        AlertDialog(
            onDismissRequest = { moveTaskId = null },
            title = { Text(stringResource(R.string.move_task)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            actions.rescheduleTaskToDate(moveTaskId!!, null)
                            moveTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.move_to_someday)) }

                    TextButton(
                        onClick = {
                            actions.rescheduleTaskToDate(moveTaskId!!, LocalDate.now())
                            moveTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            actions.rescheduleTaskToDate(moveTaskId!!, LocalDate.now().plusDays(1))
                            moveTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = { showMoveTaskDatePicker.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.reschedule)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (moveTaskId != null && showMoveTaskDatePicker.value) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember(moveTaskId, state.tasks) {
            val d = state.tasks.firstOrNull { it.id == moveTaskId }?.date ?: LocalDate.now()
            d.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showMoveTaskDatePicker.value = false
                moveTaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        actions.rescheduleTaskToDate(moveTaskId!!, newDate)
                    }
                    showMoveTaskDatePicker.value = false
                    moveTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMoveTaskDatePicker.value = false
                    moveTaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (copyTaskId != null && !showCopyTaskDatePicker.value) {
        AlertDialog(
            onDismissRequest = { copyTaskId = null },
            title = { Text(stringResource(R.string.copy_task)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            actions.copyTaskToDate(copyTaskId!!, LocalDate.now())
                            Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            copyTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            actions.copyTaskToDate(copyTaskId!!, LocalDate.now().plusDays(1))
                            Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            copyTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = { showCopyTaskDatePicker.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_date)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyTaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copyTaskId != null && showCopyTaskDatePicker.value) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember { LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showCopyTaskDatePicker.value = false
                copyTaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        actions.copyTaskToDate(copyTaskId!!, newDate)
                        Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                    showCopyTaskDatePicker.value = false
                    copyTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCopyTaskDatePicker.value = false
                    copyTaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }

    // Move subtask to another task
    if (moveSubtaskId.value != null) {
        val sub = state.subtasks.firstOrNull { it.id == moveSubtaskId.value }
        val currentTaskId = sub?.taskId

        AlertDialog(
            onDismissRequest = { moveSubtaskId.value = null },
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
                                actions.moveSubtaskToTask(moveSubtaskId.value!!, t.id)
                                moveSubtaskId.value = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${t.description} • $dText") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveSubtaskId.value = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Add subtask dialog
    if (addSubtaskToTaskId.value != null) {
        AlertDialog(
            onDismissRequest = { addSubtaskToTaskId.value = null },
            title = { Text(stringResource(R.string.add_subtask)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newSubtaskText,
                        onValueChange = { newSubtaskText = it },
                        label = { Text(stringResource(R.string.task_description_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.subtask_color))
                        Spacer(modifier = Modifier.width(10.dp))
                        ColorPickerRow(
                            selected = newSubtaskColor,
                            onSelect = { newSubtaskColor = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val txt = newSubtaskText.trim()
                        if (txt.isNotBlank()) {
                            actions.createSubtask(addSubtaskToTaskId.value!!, txt, newSubtaskColor)
                        }
                        addSubtaskToTaskId.value = null
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { addSubtaskToTaskId.value = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copySubtaskId != null && !showCopySubtaskDatePicker.value) {
        AlertDialog(
            onDismissRequest = { copySubtaskId = null },
            title = { Text(stringResource(R.string.copy_subtask)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            actions.copySubtaskToDate(copySubtaskId!!, LocalDate.now())
                            Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            copySubtaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            actions.copySubtaskToDate(copySubtaskId!!, LocalDate.now().plusDays(1))
                            Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            copySubtaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = { showCopySubtaskDatePicker.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_date)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { copySubtaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copySubtaskId != null && showCopySubtaskDatePicker.value) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember { LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showCopySubtaskDatePicker.value = false
                copySubtaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        actions.copySubtaskToDate(copySubtaskId!!, newDate)
                        Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                    showCopySubtaskDatePicker.value = false
                    copySubtaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCopySubtaskDatePicker.value = false
                    copySubtaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }

    // Edit task dialog
    if (editTaskId != null) {
        AlertDialog(
            onDismissRequest = { editTaskId = null; showTaskRepeatPicker.value = false },
            title = { Text(stringResource(R.string.task_description_label)) },
            text = {
                OutlinedTextField(
                    value = editTaskText.value,
                    onValueChange = { editTaskText.value = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.updateTaskDescription(editTaskId!!, editTaskText.value)
                    editTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showTaskRepeatPicker.value = true }) {
                        Text(stringResource(R.string.repeat))
                    }

                    TextButton(onClick = {
                        actions.deleteTask(editTaskId!!)
                        editTaskId = null
                        showTaskRepeatPicker.value = false
                    }) { Text(stringResource(R.string.remove)) }

                    TextButton(onClick = {
                        editTaskId = null
                        showTaskRepeatPicker.value = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    // Edit subtask dialog
    if (editSubtaskId != null) {
        AlertDialog(
            onDismissRequest = { editSubtaskId = null; showSubtaskRepeatPicker.value = false },
            title = { Text(stringResource(R.string.task_description_label)) },
            text = {
                OutlinedTextField(
                    value = editSubtaskText.value,
                    onValueChange = { editSubtaskText.value = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.updateSubtaskDescription(editSubtaskId!!, editSubtaskText.value)
                    editSubtaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSubtaskRepeatPicker.value = true }) {
                        Text(stringResource(R.string.repeat))
                    }

                    TextButton(onClick = {
                        actions.deleteSubtask(editSubtaskId!!)
                        editSubtaskId = null
                        showSubtaskRepeatPicker.value = false
                    }) { Text(stringResource(R.string.remove)) }

                    TextButton(onClick = {
                        editSubtaskId = null
                        showSubtaskRepeatPicker.value = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    if (showTaskRepeatPicker.value && editTaskId != null) {
        RepeatPickerDialog(
            initial = editTaskRepeatRule.value,
            onDismiss = { showTaskRepeatPicker.value = false },
            onConfirm = { rule ->
                editTaskRepeatRule.value = rule
                actions.setTaskRepeatRule(editTaskId!!, rule)
                showTaskRepeatPicker.value = false
            }
        )
    }

    if (showSubtaskRepeatPicker.value && editSubtaskId != null) {
        RepeatPickerDialog(
            initial = editSubtaskRepeatRule.value,
            onDismiss = { showSubtaskRepeatPicker.value = false },
            onConfirm = { rule ->
                editSubtaskRepeatRule.value = rule
                actions.setSubtaskRepeatRule(editSubtaskId!!, rule)
                showSubtaskRepeatPicker.value = false
            }
        )
    }
}

@Composable
private fun SubtaskProgressToggle(
    done: Int,
    total: Int,
    onClick: () -> Unit,
) {
    val progress = if (total <= 0) 0f else done.toFloat() / total.toFloat()

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
        progress = { progress },
        modifier = Modifier.matchParentSize(),
        color = ProgressIndicatorDefaults.circularColor,
        strokeWidth = 3.dp,
        trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
        strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
        )
        Text(
            text = "$done/$total",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
