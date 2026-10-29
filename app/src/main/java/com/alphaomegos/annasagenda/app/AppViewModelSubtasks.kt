package com.alphaomegos.annasagenda.app

import android.app.Application
import android.net.Uri
import com.alphaomegos.annasagenda.util.collectInternalCoverRefs
import com.alphaomegos.annasagenda.util.deleteInternalCoverIfAny
import com.alphaomegos.annasagenda.util.importCoverIntoInternalStorage
import com.alphaomegos.annasagenda.util.isInternalCoverRef
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*

/** Subtasks: adding, editing, moving, copying, deleting (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface SubtasksActions : AppStateHost {
    /* ---------------------------
       Subtasks
    ---------------------------- */

    /**
     * Adds a part to a task. The rule about a part joining a task that is
     * already finished lives in TaskDoneSupport, with the reason it matters.
     */
    fun createSubtask(taskId: Long, description: String, colorArgb: Long? = null): Long {
        val id = nextFreshId()
        val cur = stateFlow.value

        val after = stateAfterCreatingSubtask(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            counters = cur.counters,
            taskId = taskId,
            description = description,
            colorArgb = colorArgb,
            id = id,
        )

        stateFlow.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            counters = after.counters,
        )

        return after.createdId
    }

    fun updateSubtaskDescription(subtaskId: Long, description: String) {
        val clean = description.trim()
        if (clean.isBlank()) return
        val updated = stateFlow.value.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(description = clean) else s
        }
        stateFlow.value = stateFlow.value.copy(subtasks = updated)
    }

    /**
     * Deletes a subtask, which can finish the task it was under and move the
     * counter that task is linked to.
     *
     * This used to be three separate writes to the state with reads in
     * between — the shape two earlier bugs in this file had. It is one now,
     * and what it does is stated in TaskDeletionSupport.
     */
    fun deleteSubtask(subtaskId: Long) {
        stateFlow.update { cur ->
            val after = stateAfterDeletingSubtask(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                suppressedRecurrences = cur.suppressedRecurrences,
                counters = cur.counters,
                subtaskId = subtaskId,
            )
            cur.copy(
                tasks = after.tasks,
                subtasks = after.subtasks,
                suppressedRecurrences = after.suppressedRecurrences,
                counters = after.counters,
            )
        }
    }

    fun copySubtaskToDate(subtaskId: Long, targetDate: LocalDate) {
        val cur = stateFlow.value
        val after = stateAfterCopyingSubtask(cur.tasks, cur.subtasks, subtaskId, targetDate, ::nextFreshId)

        stateFlow.value = cur.copy(tasks = after.tasks, subtasks = after.subtasks)
    }

    /**
     * Moves a part to another task. Both tasks can change their minds about
     * being finished and both can move a counter, so it is one write; the
     * rules are in TaskDoneSupport.
     */
    fun moveSubtask(subtaskId: Long, targetTaskId: Long) {
        val cur = stateFlow.value

        val after = stateAfterMovingSubtask(
            cur.tasks, cur.subtasks, cur.counters, subtaskId, targetTaskId,
        )

        stateFlow.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            counters = after.counters,
        )
    }

    fun moveTaskUp(taskId: Long) {
        val cur = stateFlow.value
        val newTasks = moveTaskWithinDate(cur.tasks, taskId, step = -1, doneLast = cur.doneTasksLast)
        if (newTasks == cur.tasks) return
        stateFlow.value = cur.copy(tasks = newTasks)
    }
    fun moveTaskDown(taskId: Long) {
        val cur = stateFlow.value
        val newTasks = moveTaskWithinDate(cur.tasks, taskId, step = 1, doneLast = cur.doneTasksLast)
        if (newTasks == cur.tasks) return
        stateFlow.value = cur.copy(tasks = newTasks)
    }

    fun moveSubtaskUp(subtaskId: Long) {
        val cur = stateFlow.value
        val after = stateAfterReorderingSubtask(cur.tasks, cur.subtasks, cur.counters, subtaskId, step = -1, doneLast = cur.doneSubtasksLast)

        stateFlow.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            counters = after.counters,
        )
    }

    fun moveSubtaskDown(subtaskId: Long) {
        val cur = stateFlow.value
        val after = stateAfterReorderingSubtask(cur.tasks, cur.subtasks, cur.counters, subtaskId, step = 1, doneLast = cur.doneSubtasksLast)

        stateFlow.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            counters = after.counters,
        )
    }

}
