package com.alphaomegos.annasagenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class RepeatFreq {
    DAILY,
    WEEKLY,
    MONTHLY,
}

data class RepeatRule(
    val freq: RepeatFreq,
    val interval: Int = 1,                      // every N days/weeks/months
    val weekDays: Set<DayOfWeek> = emptySet(),  // for WEEKLY
    val dayOfMonth: Int? = null,                // for MONTHLY (1..31)

    /**
     * Which day a week starts on, for WEEKLY rules with an interval above one.
     *
     * Recorded on the rule rather than read from the locale every time. It used
     * to come from Locale.getDefault(), so switching the app language moved the
     * week boundary and "every 2 weeks" landed on different days than before.
     *
     * Null means a rule saved before this was recorded; the caller falls back
     * to the current locale, which is exactly the old behaviour.
     */
    val weekStart: DayOfWeek? = null,
)

data class Task(
    val id: Long,
    val order: Int = 0,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val description: String,
    val colorArgb: Long? = null,
    val hasSubtasks: Boolean = false,
    val isDone: Boolean = false,
    val linkedManualCounterId: Long? = null,

    // Recurrence is defined only on template tasks (originTaskId == null).
    val repeatRule: RepeatRule? = null,

    // If not null -> this task instance was generated from a template task with this id.
    val originTaskId: Long? = null,
)

data class Subtask(
    val id: Long,
    val order: Int = 0,
    val taskId: Long,
    val description: String,
    val colorArgb: Long? = null,
    val isDone: Boolean = false,

    // Recurrence is defined only on template subtasks (originSubtaskId == null).
    val repeatRule: RepeatRule? = null,

    // If not null -> this subtask instance was generated from a template subtask with this id.
    val originSubtaskId: Long? = null,
)