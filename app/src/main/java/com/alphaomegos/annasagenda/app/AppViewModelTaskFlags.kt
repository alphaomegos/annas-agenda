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

/** Done flags kept in step between a task and its subtasks, and colours (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface TaskFlagsAndColorsActions : AppStateHost {
    /* ---------------------------
       Done flags sync (Task <-> Subtasks)
    ---------------------------- */

    fun setTaskLinkedManualCounter(taskId: Long, newCounterId: Long?) {
        stateFlow.update { cur ->
            val after = tasksAndCountersAfterRelinkingManualCounter(
                tasks = cur.tasks,
                counters = cur.counters,
                taskId = taskId,
                newCounterId = newCounterId,
            ) ?: return@update cur

            cur.copy(tasks = after.tasks, counters = after.counters)
        }
    }

    /**
     * Ticking a task and ticking a subtask, and rebuilding a task's flag from
     * the parts it has. All three move the linked counter, and all three live
     * in TaskDoneSupport so the counter cannot be left behind again.
     */
    fun toggleTaskDone(taskId: Long) {
        stateFlow.update { cur ->
            val after = stateAfterTogglingTask(cur.tasks, cur.subtasks, cur.counters, taskId)
            cur.copy(tasks = after.tasks, subtasks = after.subtasks, counters = after.counters)
        }
    }

    fun toggleSubtaskDone(subtaskId: Long) {
        stateFlow.update { cur ->
            val after = stateAfterTogglingSubtask(cur.tasks, cur.subtasks, cur.counters, subtaskId)
            cur.copy(tasks = after.tasks, subtasks = after.subtasks, counters = after.counters)
        }
    }

    /* ---------------------------
       Colors
    ---------------------------- */

    fun setTaskColor(taskId: Long, colorArgb: Long?) {
        val cur = stateFlow.value

        val newTasks = cur.tasks.map { t ->
            if (t.id == taskId) t.copy(colorArgb = colorArgb) else t
        }

        val hasSubs = cur.subtasks.any { it.taskId == taskId }
        val newSubs = if (!hasSubs) {
            cur.subtasks
        } else {
            cur.subtasks.map { s ->
                if (s.taskId == taskId) s.copy(colorArgb = colorArgb) else s
            }
        }

        stateFlow.value = cur.copy(tasks = newTasks, subtasks = newSubs)
    }

    fun setSubtaskColor(subtaskId: Long, colorArgb: Long?) {
        val updated = stateFlow.value.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(colorArgb = colorArgb) else s
        }
        stateFlow.value = stateFlow.value.copy(subtasks = updated)
    }

}
