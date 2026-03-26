package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.RepeatRule
import com.alphaomegos.annasagenda.Subtask
import com.alphaomegos.annasagenda.Task
import com.alphaomegos.annasagenda.dialogs.RepeatPickerDialog
import com.alphaomegos.annasagenda.util.isSuppressedTemplateTaskOnItsDate
import java.time.LocalDate
import com.alphaomegos.annasagenda.ManualCounter

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
    val setTaskLinkedManualCounter: (Long, Long?) -> Unit,
    val setTaskRepeatRule: (Long, RepeatRule?) -> Unit,
    val setSubtaskRepeatRule: (Long, RepeatRule?) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTasksBlock(
    vm: AppViewModel,
    state: AppState,
    date: LocalDate?,
    includeDoneTasks: Boolean = true,
    visibleTaskIds: Set<Long>? = null,
) {
    val tasks = remember(
        state.tasks,
        state.suppressedRecurrences,
        date,
        includeDoneTasks,
        visibleTaskIds
    ) {
        state.tasks
            .filter { it.date == date }
            .filter { includeDoneTasks || !it.isDone }
            .filter { visibleTaskIds == null || it.id in visibleTaskIds }
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
            setTaskLinkedManualCounter = { taskId, counterId -> vm.setTaskLinkedManualCounter(taskId, counterId) },
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

    val moveTaskId = remember { mutableStateOf<Long?>(null) }
    val showMoveTaskDatePicker = remember { mutableStateOf(false) }

    val copyTaskId = remember { mutableStateOf<Long?>(null) }
    val showCopyTaskDatePicker = remember { mutableStateOf(false) }

    val copySubtaskId = remember { mutableStateOf<Long?>(null) }
    val showCopySubtaskDatePicker = remember { mutableStateOf(false) }

    val moveSubtaskId = remember { mutableStateOf<Long?>(null) }

    val addSubtaskToTaskId = remember { mutableStateOf<Long?>(null) }
    val newSubtaskText = remember { mutableStateOf("") }
    val newSubtaskColor = remember { mutableStateOf<Long?>(null) }

    var editTaskId by remember { mutableStateOf<Long?>(null) }
    val editTaskText = remember { mutableStateOf("") }
    val editTaskRepeatRule = remember { mutableStateOf<RepeatRule?>(null) }
    val showTaskRepeatPicker = remember { mutableStateOf(false) }
    val showCounterPicker = remember { mutableStateOf(false) }
    var editSubtaskId by remember { mutableStateOf<Long?>(null) }
    val editSubtaskText = remember { mutableStateOf("") }
    val editSubtaskRepeatRule = remember { mutableStateOf<RepeatRule?>(null) }
    val showSubtaskRepeatPicker = remember { mutableStateOf(false) }

    tasks.forEach { task ->
        val subtasks = subtasksByTaskId[task.id].orEmpty()
        val isTaskExpanded = expandedTaskIds.contains(task.id)

        DateTaskRow(
            task = task,
            subtasks = subtasks,
            isExpanded = isTaskExpanded,
            onToggleExpand = {
                expandedTaskIds =
                    if (isTaskExpanded) expandedTaskIds - task.id else expandedTaskIds + task.id
            },
            onToggleDone = { actions.toggleTaskDone(task.id) },
            onCycleColor = {
                actions.setTaskColor(task.id, nextPaletteColor(task.colorArgb))
            },
            onEdit = {
                editTaskId = task.id
                editTaskText.value = task.description
                editTaskRepeatRule.value = task.repeatRule
                showTaskRepeatPicker.value = false
            },
            onMoveUp = { actions.moveTaskUp(task.id) },
            onMoveDown = { actions.moveTaskDown(task.id) },
            onMove = { moveTaskId.value = task.id },
            onCopy = { copyTaskId.value = task.id },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (isTaskExpanded) {
                TextButton(onClick = {
                    addSubtaskToTaskId.value = task.id
                    newSubtaskText.value = ""
                    newSubtaskColor.value = task.colorArgb
                }) {
                    Text(stringResource(R.string.add_subtask))
                }
            }
        }

        if (subtasks.isNotEmpty() && isTaskExpanded) {
            subtasks.forEach { st ->
                DateSubtaskRow(
                    subtask = st,
                    onToggleDone = { actions.toggleSubtaskDone(st.id) },
                    onCycleColor = {
                        actions.setSubtaskColor(st.id, nextPaletteColor(st.colorArgb))
                    },
                    onEdit = {
                        editSubtaskId = st.id
                        editSubtaskText.value = st.description
                        editSubtaskRepeatRule.value = st.repeatRule
                        showSubtaskRepeatPicker.value = false
                    },
                    onMoveUp = { actions.moveSubtaskUp(st.id) },
                    onMoveDown = { actions.moveSubtaskDown(st.id) },
                    onMove = { moveSubtaskId.value = st.id },
                    onCopy = { copySubtaskId.value = st.id },
                )

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }

    // Move task: choose date (Today / Tomorrow / Someday / Pick date)

    MoveTaskDialogs(
        taskId = moveTaskId.value,
        showDatePicker = showMoveTaskDatePicker.value,
        currentTaskDate = state.tasks.firstOrNull { it.id == moveTaskId.value }?.date,
        onDismissAll = {
            showMoveTaskDatePicker.value = false
            moveTaskId.value = null
        },
        onShowDatePicker = { showMoveTaskDatePicker.value = true },
        onMoveToSomeday = { taskId ->
            actions.rescheduleTaskToDate(taskId, null)
        },
        onMoveToToday = { taskId ->
            actions.rescheduleTaskToDate(taskId, LocalDate.now())
        },
        onMoveToTomorrow = { taskId ->
            actions.rescheduleTaskToDate(taskId, LocalDate.now().plusDays(1))
        },
        onMoveToDate = { taskId, newDate ->
            actions.rescheduleTaskToDate(taskId, newDate)
        },
    )

    CopyTaskDialogs(
        taskId = copyTaskId.value,
        showDatePicker = showCopyTaskDatePicker.value,
        onDismissAll = {
            showCopyTaskDatePicker.value = false
            copyTaskId.value = null
        },
        onShowDatePicker = { showCopyTaskDatePicker.value = true },
        onCopyToToday = { taskId ->
            actions.copyTaskToDate(taskId, LocalDate.now())
        },
        onCopyToTomorrow = { taskId ->
            actions.copyTaskToDate(taskId, LocalDate.now().plusDays(1))
        },
        onCopyToDate = { taskId, newDate ->
            actions.copyTaskToDate(taskId, newDate)
        },
    )

    MoveSubtaskDialog(
        subtaskId = moveSubtaskId.value,
        state = state,
        onDismiss = { moveSubtaskId.value = null },
        onMoveToTask = { subtaskId, targetTaskId ->
            actions.moveSubtaskToTask(subtaskId, targetTaskId)
        },
    )

    AddSubtaskDialog(
        taskId = addSubtaskToTaskId.value,
        text = newSubtaskText.value,
        color = newSubtaskColor.value,
        onTextChange = { newSubtaskText.value = it },
        onColorChange = { newSubtaskColor.value = it },
        onDismiss = { addSubtaskToTaskId.value = null },
        onConfirm = { taskId, text, color ->
            actions.createSubtask(taskId, text, color)
        },
    )

    CopySubtaskDialogs(
        subtaskId = copySubtaskId.value,
        showDatePicker = showCopySubtaskDatePicker.value,
        onDismissAll = {
            showCopySubtaskDatePicker.value = false
            copySubtaskId.value = null
        },
        onShowDatePicker = { showCopySubtaskDatePicker.value = true },
        onCopyToToday = { subtaskId ->
            actions.copySubtaskToDate(subtaskId, LocalDate.now())
        },
        onCopyToTomorrow = { subtaskId ->
            actions.copySubtaskToDate(subtaskId, LocalDate.now().plusDays(1))
        },
        onCopyToDate = { subtaskId, newDate ->
            actions.copySubtaskToDate(subtaskId, newDate)
        },
    )

    EditTaskDialog(
        taskId = editTaskId,
        editingTask = tasks.firstOrNull { it.id == editTaskId },
        manualCounters = state.counters.filterIsInstance<ManualCounter>(),
        text = editTaskText.value,
        onTextChange = { editTaskText.value = it },
        onDismiss = {
            editTaskId = null
            showTaskRepeatPicker.value = false
            showCounterPicker.value = false
        },
        onShowRepeatPicker = { showTaskRepeatPicker.value = true },
        onShowCounterPicker = { showCounterPicker.value = true },
        onDelete = { taskId ->
            actions.deleteTask(taskId)
            editTaskId = null
            showTaskRepeatPicker.value = false
            showCounterPicker.value = false
        },
        onConfirm = { taskId, text ->
            actions.updateTaskDescription(taskId, text)
            editTaskId = null
            showTaskRepeatPicker.value = false
            showCounterPicker.value = false
        },
        onDetachCounter = { taskId ->
            actions.setTaskLinkedManualCounter(taskId, null)
        },
    )

    if (showCounterPicker.value && editTaskId != null) {
        TaskCounterPickerDialog(
            taskId = editTaskId,
            manualCounters = state.counters.filterIsInstance<ManualCounter>(),
            onDismiss = { showCounterPicker.value = false },
            onSelectCounter = { taskId, counterId ->
                actions.setTaskLinkedManualCounter(taskId, counterId)
            },
        )
    }

    EditSubtaskDialog(
        subtaskId = editSubtaskId,
        text = editSubtaskText.value,
        onTextChange = { editSubtaskText.value = it },
        onDismiss = {
            editSubtaskId = null
            showSubtaskRepeatPicker.value = false
        },
        onShowRepeatPicker = { showSubtaskRepeatPicker.value = true },
        onDelete = { subtaskId ->
            actions.deleteSubtask(subtaskId)
            editSubtaskId = null
            showSubtaskRepeatPicker.value = false
        },
        onConfirm = { subtaskId, text ->
            actions.updateSubtaskDescription(subtaskId, text)
            editSubtaskId = null
        },
    )

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
