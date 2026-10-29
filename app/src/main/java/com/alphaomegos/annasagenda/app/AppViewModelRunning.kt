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

/** The running plan and the between-marathons workouts (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface RunningActions : AppStateHost {
    // What the running plan asks of the tasks (29.10): narrow hooks, the view model's own behind them.
    fun runningCreateTask(date: LocalDate?, time: LocalTime?, description: String): Long
    fun runningDeleteTask(taskId: Long)
    fun runningRetitleTask(taskId: Long, description: String)
    fun runningMinutesTitle(minutes: Int): String

    /* ---------------------------
       Running plan ("On the run")
    ---------------------------- */

    /**
     * One day's row of the running plan, after the user typed into it.
     *
     * What a row does when it is emptied, and whether the pace column is the
     * user's to fill, are rules rather than details; they live in
     * RunningPlanSupport where a test can say what they are. What is left here
     * is the part that cannot move: the plan's task has a title built from
     * translated strings, and creating one hands out an id.
     */
    fun updateRunningPlanEntry(
        date: LocalDate,
        distanceKmText: String? = null,
        durationHhMmText: String? = null,
        paceText: String? = null,
    ) {
        val approved = stateFlow.value.runningPlanApproved

        val edit = runningPlanEntriesAfterEdit(
            entries = stateFlow.value.runningPlanEntries,
            approved = approved,
            date = date,
            distanceKmText = distanceKmText,
            durationHhMmText = durationHhMmText,
            paceText = paceText,
        )

        // The row goes first, so that deleting its task afterwards finds
        // nothing still pointing at it. The other order works too, but only by
        // accident of what gets written back last.
        stateFlow.update { cur -> cur.copy(runningPlanEntries = edit.entries) }
        edit.orphanedTaskId?.let { runningDeleteTask(it) }

        if (!approved) return

        val after = stateFlow.value.runningPlanEntries.firstOrNull { it.date == date } ?: return
        val title = buildRunningPlanTaskTitle(
            entry = after,
            formatKmTitle = ::formatRunningTaskKmTitle,
            formatMinutesTitle = ::runningMinutesTitle,
        ) ?: return

        // Asking whether the task still exists, rather than whether the row
        // remembers an id: a row whose task was deleted used to be stuck,
        // renaming nothing and never getting a task back.
        val linkedTask = after.taskId?.let { id -> stateFlow.value.tasks.firstOrNull { it.id == id } }

        if (linkedTask != null) {
            runningRetitleTask(linkedTask.id, title)
            return
        }

        val newTaskId = runningCreateTask(date = after.date, time = null, description = title)

        stateFlow.update { cur ->
            cur.copy(
                runningPlanEntries = cur.runningPlanEntries
                    .map { entry -> if (entry.date == date) entry.copy(taskId = newTaskId) else entry }
                    .sortedBy { it.date }
            )
        }
    }

    fun addRunningPlanBonusEntry(date: LocalDate): Boolean {
        val st = stateFlow.value
        if (!st.runningPlanApproved) return false
        if (st.runningPlanEntries.any { it.date == date }) return false

        val updated = (st.runningPlanEntries + RunningPlanEntry(
            date = date,
            isBonus = true,
        )).sortedBy { it.date }

        stateFlow.value = st.copy(runningPlanEntries = updated)
        return true
    }


    fun approveRunningPlan() {
        // Does nothing the first time, since nothing is approved yet to prune.
        // It earns its place when a plan is approved a second time, where the
        // days it has already given up on should not come back as tasks.
        pruneRunningPlanNow()

        val cleaned = runningPlanEntriesCleanedForApproval(stateFlow.value.runningPlanEntries)

        val updated = cleaned.map { e0 ->
            var e = e0

            if (e.taskId == null) {
                val title = buildRunningPlanTaskTitle(
                    entry = e,
                    formatKmTitle = ::formatRunningTaskKmTitle,
                    formatMinutesTitle = ::runningMinutesTitle,
                )
                if (title != null) {
                    val id = runningCreateTask(
                        date = e.date,
                        time = null,
                        description = title,
                    )
                    e = e.copy(taskId = id)
                }
            }
            e
        }.sortedBy { it.date }

        stateFlow.value = stateFlow.value.copy(
            runningPlanApproved = true,
            runningPlanEntries = updated,
        )
    }

    /* ---------------------------
       Between marathons
    ---------------------------- */

    fun setRunningMode(mode: RunningMode) {
        stateFlow.update { cur -> if (cur.runningMode == mode) cur else cur.copy(runningMode = mode) }
    }

    /**
     * Writes down a run that happened. False when there was nothing to write
     * down, and the dialog says so rather than closing on nothing.
     *
     * The id is minted before the update lambda, not inside it: that lambda is
     * a compare-and-set loop and can run more than once.
     */
    fun addRunningWorkout(
        date: LocalDate,
        distanceText: String,
        durationText: String,
        note: String = "",
    ): Boolean {
        val workout = runningWorkoutFromInput(
            id = nextFreshId(),
            date = date,
            distanceText = distanceText,
            durationText = durationText,
            note = note,
        ) ?: return false

        stateFlow.value = stateFlow.value.copy(
            runningWorkouts = workoutsAfterAdding(stateFlow.value.runningWorkouts, workout)
        )

        return true
    }

    fun deleteRunningWorkout(id: Long) {
        stateFlow.update { cur ->
            val after = workoutsAfterDeleting(cur.runningWorkouts, id) ?: return@update cur
            cur.copy(runningWorkouts = after)
        }
    }

    fun resetRunningPlan() {
        val ids = stateFlow.value.runningPlanEntries.mapNotNull { it.taskId }
        ids.forEach { runningDeleteTask(it) }

        stateFlow.value = stateFlow.value.copy(
            runningPlanApproved = false,
            runningPlanEntries = emptyList(),
        )
    }

    fun pruneRunningPlanNow() {
        val st = stateFlow.value
        if (!st.runningPlanApproved) return
        if (st.runningPlanEntries.isEmpty()) return

        val expired = expiredRunningPlanEntries(st.runningPlanEntries, LocalDate.now())

        if (expired.isEmpty()) return

        expired.mapNotNull { it.taskId }.forEach { runningDeleteTask(it) }

        // Must read the state fresh. runningDeleteTask() above removed tasks and
        // subtasks; writing back the `st` snapshot taken before the loop put
        // every one of them straight back, leaving orphaned running tasks in
        // the calendar that no longer belonged to any plan entry.
        val expiredDates = expired.map { it.date }.toSet()
        stateFlow.update { cur ->
            cur.copy(
                runningPlanEntries = cur.runningPlanEntries.filterNot { it.date in expiredDates }
            )
        }
    }

}
