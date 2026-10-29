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

/** Tasks and their recurrences: the core the other parts lean on (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
// The menu's month badge asks for a month drawn; the drawing is here, so this part is the menu's too.
interface TasksActions : AppStateHost, MainMenuActions {
    private fun nextTaskOrderForDate(date: LocalDate?): Int =
        nextTaskOrderOn(stateFlow.value.tasks, date)

    private fun nextSubtaskOrderFor(taskId: Long): Int =
        nextSubtaskOrderIn(stateFlow.value.subtasks, taskId)

    /* ---------------------------
       Recurrence (generated instances)
    ---------------------------- */

    /**
     * Materialises recurring occurrences for the given range.
     *
     * The algorithm itself lives in RecurrenceSupport as a pure function, so it
     * can be covered by JVM tests; this only feeds it state and takes the
     * result back. Reading `cur` once is safe precisely because that function
     * touches nothing.
     */
    override fun ensureGeneratedInRange(start: LocalDate, end: LocalDate) {
        val cur = stateFlow.value

        val generated = generateRecurrencesInRange(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            start = start,
            end = end,
            nextId = idCounter,
        )

        idCounter = generated.nextId
        stateFlow.value = cur.copy(
            tasks = generated.tasks,
            subtasks = generated.subtasks,
        )
    }

    /**
     * Records the week boundary the rule was created under, so that switching
     * the app language later cannot move an "every N weeks" schedule.
     */
    private fun withRecordedWeekStart(rule: RepeatRule?): RepeatRule? = when {
        rule == null -> null
        rule.weekStart != null -> rule
        else -> rule.copy(weekStart = currentLocaleWeekStart())
    }

    fun setTaskRepeatRule(taskId: Long, rule: RepeatRule?) {
        val recorded = withRecordedWeekStart(rule)

        val st = stateFlow.value
        val updated = st.tasks.map { t ->
            if (t.id == taskId) t.copy(repeatRule = recorded) else t
        }
        stateFlow.value = st.copy(tasks = updated)
    }

    fun setSubtaskRepeatRule(subtaskId: Long, rule: RepeatRule?) {
        val recorded = withRecordedWeekStart(rule)

        val st = stateFlow.value
        val updated = st.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(repeatRule = recorded) else s
        }
        // No refreshHasSubtasks here: a repeat rule does not change which
        // tasks have parts, and the call that used to follow was a second
        // write to the state that could not say anything new.
        stateFlow.value = st.copy(subtasks = updated)
    }

    /* ---------------------------
       Tasks
    ---------------------------- */

    fun createTaskForDate(
        date: LocalDate?,
        time: LocalTime?,
        description: String,
        colorArgb: Long? = null,
        hasSubtasks: Boolean = false,
        repeatRule: RepeatRule? = null,
        linkedManualCounterId: Long? = null,
    ): Long {
        val id = nextFreshId()
        val task = Task(
            id = id,
            order = nextTaskOrderForDate(date),
            date = date,
            time = time,
            description = description,
            colorArgb = colorArgb,
            hasSubtasks = hasSubtasks,
            linkedManualCounterId = linkedManualCounterId,
            repeatRule = withRecordedWeekStart(repeatRule),
        )
        stateFlow.value = stateFlow.value.copy(tasks = stateFlow.value.tasks + task)
        return id
    }

    /** Gives a task a time, changes it, or (null) takes it away — see tasksAfterSettingTime. */
    fun setTaskTime(taskId: Long, time: LocalTime?) {
        val cur = stateFlow.value
        val newTasks = tasksAfterSettingTime(cur.tasks, taskId, time)
        if (newTasks === cur.tasks) return
        stateFlow.value = cur.copy(tasks = newTasks)
    }

    fun updateTaskDescription(taskId: Long, description: String) {
        val clean = description.trim()
        if (clean.isBlank()) return
        val updated = stateFlow.value.tasks.map { t ->
            if (t.id == taskId) t.copy(description = clean) else t
        }
        stateFlow.value = stateFlow.value.copy(tasks = updated)
    }

    /**
     * Deletes a task. What that means depends on whether it is an occurrence,
     * a template that repeats, or neither; the three answers live in
     * TaskDeletionSupport, where a JVM test can state them.
     */
    fun deleteTask(taskId: Long) {
        stateFlow.update { cur ->
            val after = stateAfterDeletingTask(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                suppressedRecurrences = cur.suppressedRecurrences,
                runningPlanEntries = cur.runningPlanEntries,
                taskId = taskId,
            )
            cur.copy(
                tasks = after.tasks,
                subtasks = after.subtasks,
                suppressedRecurrences = after.suppressedRecurrences,
                runningPlanEntries = after.runningPlanEntries,
            )
        }
    }

    /**
     * Stops a repeat from a day onward. The rules live in SeriesDeletionSupport
     * so they can be stated as tests; this only hands the state over and takes
     * it back, in one update rather than in three.
     */
    fun deleteTaskSeriesFrom(templateTaskId: Long, fromDate: LocalDate = LocalDate.now()) {
        stateFlow.update { cur ->
            val after = tasksAfterDeletingTaskSeriesFrom(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                templateTaskId = templateTaskId,
                fromDate = fromDate,
            )
            cur.copy(tasks = after.tasks, subtasks = after.subtasks)
        }
    }

    /**
     * Renames a repeating task and sets or clears its time from [fromDate] on,
     * leaving earlier days as they were — the rule is in SeriesEditSupport.
     */
    fun editTaskSeriesFrom(
        templateTaskId: Long,
        description: String,
        time: LocalTime?,
        rule: RepeatRule?,
        fromDate: LocalDate = LocalDate.now(),
    ) {
        // Read and write rather than update {}: update's lambda is a
        // compare-and-set loop and may run more than once, and nextFreshId() is not
        // something to run twice for one edit. Nothing suspends in between.
        val cur = stateFlow.value
        val after = stateAfterEditingTaskSeriesFrom(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            templateTaskId = templateTaskId,
            fromDate = fromDate,
            description = description,
            time = time,
            newId = ::nextFreshId,
            weekStart = currentLocaleWeekStart(),
            rule = withRecordedWeekStart(rule),
        )
        if (after.tasks === cur.tasks) return

        stateFlow.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
        )
    }

    fun deleteSubtaskSeriesFrom(templateSubtaskId: Long, fromDate: LocalDate = LocalDate.now()) {
        stateFlow.update { cur ->
            val after = tasksAfterDeletingSubtaskSeriesFrom(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                templateSubtaskId = templateSubtaskId,
                fromDate = fromDate,
            )
            cur.copy(tasks = after.tasks, subtasks = after.subtasks)
        }
    }

    /**
     * Moves a task to another day, or off the calendar. The rules — including
     * what happens when the task being moved is an occurrence of a repeat —
     * live in RescheduleSupport, where a JVM test can state them.
     */
    /**
     * Moves one occurrence of a series and everything after it by the same
     * number of days — the rule is in SeriesEditSupport.
     */
    fun shiftTaskSeriesFrom(occurrenceId: Long, newDate: LocalDate) {
        // Read and write rather than update {}: nextFreshId() must not run twice.
        val cur = stateFlow.value
        val after = stateAfterShiftingSeriesFrom(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            occurrenceId = occurrenceId,
            newDate = newDate,
            newId = ::nextFreshId,
            weekStart = currentLocaleWeekStart(),
        )
        if (after.tasks === cur.tasks) return

        stateFlow.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
        )
    }

    fun rescheduleTaskToDate(taskId: Long, newDate: LocalDate?) {
        // Read and write rather than update {}: a series' first day moved
        // alone becomes a copy, and nextFreshId() must not run twice.
        val cur = stateFlow.value
        val after = stateAfterReschedulingTask(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            taskId = taskId,
            newDate = newDate,
            newId = ::nextFreshId,
        )
        stateFlow.value = cur.copy(
            tasks = withHasSubtasksRefreshed(after.tasks, after.subtasks),
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
        )
    }

    /**
     * Copies a task to another day, with everything under it. What a copy
     * keeps and what it deliberately leaves behind is stated in CopySupport.
     */
    fun copyTaskToDate(taskId: Long, targetDate: LocalDate) {
        // Read and write rather than update {}: update's lambda is a
        // compare-and-set loop and may run more than once, and nextFreshId() is not
        // something to run twice for one copy. Nothing suspends in between.
        val cur = stateFlow.value
        val after = stateAfterCopyingTask(cur.tasks, cur.subtasks, taskId, targetDate, ::nextFreshId)

        stateFlow.value = cur.copy(tasks = after.tasks, subtasks = after.subtasks)
    }

}
