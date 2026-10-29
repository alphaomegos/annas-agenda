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

/** The day counters (26.10) (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface DayCountersActions : AppStateHost {
    /* ---------- day counters (26.10) ---------- */

    fun addDayTimer(title: String, startDate: LocalDate) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        stateFlow.value = stateWithAddedCounter(stateFlow.value, DayTimerCounter(id = nextFreshId(), title = cleanTitle, startDate = startDate))
    }

    fun updateDayTimer(counterId: Long, title: String, startDate: LocalDate, correction: Int) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        stateFlow.update { cur ->
            cur.copy(counters = cur.counters.map { c ->
                if (c is DayTimerCounter && c.id == counterId) c.copy(title = cleanTitle, startDate = startDate, correction = correction) else c
            })
        }
    }

    /** A hand correction of [delta] (the card's − and +). */
    fun adjustDayTimer(counterId: Long, delta: Int) {
        stateFlow.update { cur -> cur.copy(counters = countersWithDayTimerCorrection(cur.counters, counterId, delta)) }
    }

    /** «Start over»: from today, no correction. */
    fun restartDayTimer(counterId: Long) {
        stateFlow.update { cur -> cur.copy(counters = countersWithDayTimerRestarted(cur.counters, counterId, LocalDate.now())) }
    }

    fun addManualCounter(title: String, balance: Int) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        val cur = stateFlow.value
        val counter = ManualCounter(
            id = nextFreshId(),
            title = cleanTitle,
            balance = balance,
        )
        stateFlow.value = stateWithAddedCounter(cur, counter)
    }

    fun addDateRangeCounter(title: String, startDate: LocalDate, endDate: LocalDate) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        val cur = stateFlow.value
        val counter = DateRangeCounter(
            id = nextFreshId(),
            title = cleanTitle,
            startDate = startDate,
            endDate = endDate,
        )
        stateFlow.value = stateWithAddedCounter(cur, counter)
    }

    fun updateManualCounter(counterId: Long, title: String, balance: Int) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        val cur = stateFlow.value
        val updated = countersWithUpdatedManualCounter(
            counters = cur.counters,
            counterId = counterId,
            title = cleanTitle,
            balance = balance,
        )
        stateFlow.value = cur.copy(counters = updated)
    }

    fun updateDateRangeCounter(counterId: Long, title: String, startDate: LocalDate, endDate: LocalDate) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        val cur = stateFlow.value
        val updated = countersWithUpdatedDateRangeCounter(
            counters = cur.counters,
            counterId = counterId,
            title = cleanTitle,
            startDate = startDate,
            endDate = endDate,
        )
        stateFlow.value = cur.copy(counters = updated)
    }

    fun deleteCounter(counterId: Long) {
        val cur = stateFlow.value
        stateFlow.value = stateWithoutCounter(cur, counterId)
    }

}
