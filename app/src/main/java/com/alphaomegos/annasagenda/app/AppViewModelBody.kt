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

/** Anthropometry, its settings, and the calorimeter (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface BodyActions : AppStateHost {
    /* ---------------------------
   Anthropometry settings
---------------------------- */
    fun setAnthropometryEnabledFieldIds(ids: Set<String>) {
        val normalized = normalizeAnthropometryEnabledFieldIds(ids)

        val cur = stateFlow.value
        if (cur.anthropometryEnabledFieldIds == normalized) return

        stateFlow.value = cur.copy(anthropometryEnabledFieldIds = normalized)
    }

    /** 5.1 and 5.3: whether the forecast block and the list of measurements are shown. */
    fun setAnthropometryDisplay(showForecast: Boolean, showEntries: Boolean) {
        val cur = stateFlow.value
        if (cur.anthropometryShowForecast == showForecast && cur.anthropometryShowEntries == showEntries) return
        stateFlow.value = cur.copy(anthropometryShowForecast = showForecast, anthropometryShowEntries = showEntries)
    }

    /**
     * The chart's range, kept so that leaving the screen and coming back
     * shows the same one — asked for by Eduard on 02.10.
     */
    fun setAnthropometryRange(range: AnthropometryRange) {
        val cur = stateFlow.value
        if (cur.anthropometryRange == range) return
        stateFlow.value = cur.copy(anthropometryRange = range)
    }

    /** Picking custom dates also chooses the custom range. */
    fun setAnthropometryCustomRange(window: DateWindow) {
        val cur = stateFlow.value
        stateFlow.value = cur.copy(
            anthropometryRange = AnthropometryRange.CUSTOM,
            anthropometryCustomRange = window,
        )
    }

    /* ---------------------------
     Anthropometry
  ---------------------------- */

    fun saveAnthropometryForDate(
        date: LocalDate,
        valuesByFieldId: Map<String, Double?>
    ) {
        stateFlow.update { cur ->
            cur.copy(
                anthropometry = anthropometryAfterSavingDate(
                    entries = cur.anthropometry,
                    date = date,
                    valuesByFieldId = valuesByFieldId,
                )
            )
        }
    }

    /** 6.3: which marks the calendar puts on its days. */
    fun setStepsWidget(line: StepsWidgetLine, showRecord: Boolean, goal: Int) {
        stateFlow.update { cur ->
            cur.copy(stepsWidgetLine = line, stepsWidgetRecord = showRecord, stepsGoal = goal.coerceIn(STEPS_GOAL_RANGE))
        }
    }

    fun setStepsChartStyle(style: StepsChartStyle) {
        stateFlow.update { cur -> if (cur.stepsChartStyle == style) cur else cur.copy(stepsChartStyle = style) }
    }

    fun setCalendarShowSteps(show: Boolean) {
        stateFlow.update { cur -> if (cur.calendarShowSteps == show) cur else cur.copy(calendarShowSteps = show) }
    }

    fun setCalendarBadges(badges: Set<CalendarBadge>) {
        val cur = stateFlow.value
        if (cur.calendarBadges != badges) stateFlow.value = cur.copy(calendarBadges = badges)
    }

    /* ---------------------------
       Calorimeter
    ---------------------------- */

    /** 6.1: which of the three summaries the calorimeter shows. */
    fun setCalorimeterDisplay(dailyGoal: Boolean, weeklyGoal: Boolean, potentialLoss: Boolean) {
        val cur = stateFlow.value
        val next = cur.copy(
            calorimeterShowDailyGoal = dailyGoal,
            calorimeterShowWeeklyGoal = weeklyGoal,
            calorimeterShowPotentialLoss = potentialLoss,
        )
        if (next != cur) stateFlow.value = next
    }

    /** Daily summaries and reminders (agreed 02.10). Written as given, cleaned. */
    fun setNotificationSettings(settings: NotificationSettings) {
        val clean = settings.copy(
            summaryMinutes = settings.summaryMinutes.filter { it in 0 until 24 * 60 }.distinct().sorted(),
            reminderLeadMinutes = settings.reminderLeadMinutes?.takeIf { it in REMINDER_LEAD_CHOICES },
        )
        val cur = stateFlow.value
        if (cur.notifications != clean) stateFlow.value = cur.copy(notifications = clean)
    }

    /** How the home-screen widget looks; the widgets redraw from the same watch as the alarm. */
    fun setWidgetStyle(style: WidgetStyle) {
        val clean = normalizedWidgetStyle(style)
        val cur = stateFlow.value
        if (cur.widgetStyle != clean) stateFlow.value = cur.copy(widgetStyle = clean)
    }

    /** A period with no measurement: the last one before it as a level line, or nothing (29.10). */
    fun setAnthropometryCarryLast(on: Boolean) {
        stateFlow.update { cur -> cur.copy(anthropometryCarryLast = on) }
    }
}
