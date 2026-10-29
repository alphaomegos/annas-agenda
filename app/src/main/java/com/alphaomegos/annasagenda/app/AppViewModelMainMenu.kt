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

/** The main menu: its order, what it hides, its badges (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface MainMenuActions : AppStateHost {
    // The month a badge counts must be drawn first; the view model draws it (29.10).
    fun ensureGeneratedInRange(start: LocalDate, end: LocalDate)

    /* ---------------------------
   Main menu ordering / visibility
---------------------------- */
        fun setMainMenuOrder(ids: List<String>) {
        val normalized = normalizeMainMenuOrderIds(ids)

        val cur = stateFlow.value
        if (cur.mainMenuOrder == normalized) return
        stateFlow.value = cur.copy(mainMenuOrder = normalized)
    }

        fun hideMainMenuItem(id: String) {
            val normalizedId = normalizeMainMenuItemId(id) ?: return

        val cur = stateFlow.value
        if (normalizedId in cur.mainMenuHiddenIds) return

        stateFlow.value = cur.copy(
            mainMenuHiddenIds = cur.mainMenuHiddenIds + normalizedId
        )
    }

    fun showAllMainMenuItems() {
        val cur = stateFlow.value
        if (cur.mainMenuHiddenIds.isEmpty()) return

        stateFlow.value = cur.copy(mainMenuHiddenIds = emptySet())
    }

    /** Done tasks at the bottom of their day, or in their place (06.10). */
    fun setDoneTasksLast(value: Boolean) {
        val cur = stateFlow.value
        if (cur.doneTasksLast != value) stateFlow.value = cur.copy(doneTasksLast = value)
    }

    /** Done subtasks at the bottom of their task (16.10). */
    fun setDoneSubtasksLast(value: Boolean) {
        val cur = stateFlow.value
        if (cur.doneSubtasksLast != value) stateFlow.value = cur.copy(doneSubtasksLast = value)
    }

    fun setUndoneLampMuted(value: Boolean) {
        val cur = stateFlow.value
        if (cur.undoneLampMuted == value) return
        stateFlow.value = cur.copy(undoneLampMuted = value)
    }

    fun toggleUndoneLampMuted() {
        val cur = stateFlow.value
        stateFlow.value = cur.copy(undoneLampMuted = !cur.undoneLampMuted)
    }

    fun setUndoneHorizonDays(days: Int) {
        val normalized = normalizeUndoneHorizonDays(days)

        val cur = stateFlow.value
        if (cur.undoneHorizonDays == normalized) return
        stateFlow.value = cur.copy(undoneHorizonDays = normalized)
    }

    /**
     * Both of these go through [undoneDebt] so the lamp and the screen can
     * never disagree again: the lamp used to ignore tombstones and light red
     * for an occurrence the screen refused to show.
     */
    fun hasUndonePastTasks(today: LocalDate = LocalDate.now()): Boolean =
        !currentUndoneDebt(today).isEmpty

    fun undonePastTaskDates(today: LocalDate = LocalDate.now()): List<LocalDate> =
        currentUndoneDebt(today).dates

    private fun currentUndoneDebt(today: LocalDate): UndoneDebt {
        val cur = stateFlow.value
        return undoneDebt(
            tasks = cur.tasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            today = today,
            horizonDays = cur.undoneHorizonDays,
        )
    }

    /**
     * Materialises recurrences across the Undone horizon.
     *
     * Until this existed, a debt only showed up if the calendar had happened to
     * draw that month, because nothing else called ensureGeneratedInRange. The
     * list therefore reflected browsing history rather than what was left
     * undone. Callers run this before reading the debt.
     *
     * Bounded twice over: by the chosen horizon, and by the first day any
     * repeating template starts. With nothing repeating it walks no days at all.
     */
    fun ensureUndoneHorizonGenerated(today: LocalDate = LocalDate.now()) {
        val cur = stateFlow.value

        val start = undoneGenerationStart(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            today = today,
            horizonDays = cur.undoneHorizonDays,
        ) ?: return

        val end = today.minusDays(1)
        if (start.isAfter(end)) return

        ensureGeneratedInRange(start, end)
    }

    /** Generates the horizon, then reports what is owed. */
    fun prepareUndoneDebt(today: LocalDate = LocalDate.now()): UndoneDebt {
        ensureUndoneHorizonGenerated(today)
        return currentUndoneDebt(today)
    }


}
