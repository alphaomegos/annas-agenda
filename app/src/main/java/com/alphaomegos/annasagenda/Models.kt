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

data class AppState(
    val tasks: List<Task> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),

    // Keys like: "T:<originTaskId>:<epochDay>", "S:<originSubtaskId>:<epochDay>"
    val suppressedRecurrences: Set<String> = emptySet(),
)
