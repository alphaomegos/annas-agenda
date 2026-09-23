package com.alphaomegos.annasagenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Recurrence generation, extracted from AppViewModel so it can be exercised by
 * plain JVM tests instead of requiring a device.
 *
 * Nothing here touches Android, the ViewModel or persisted state: the caller
 * hands in the current tasks, subtasks and tombstones, and gets back what the
 * new lists should be. Behaviour is deliberately identical to the version that
 * used to live inside the ViewModel.
 */

/** The lists produced by [generateRecurrencesInRange], plus the id counter. */
data class RecurrenceGenerationResult(
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val nextId: Long,
)

/**
 * Whether [date] is an occurrence of [rule] anchored at [anchor].
 *
 * The anchor day itself is never an occurrence — it is the template's own day.
 *
 * Where a week starts, for WEEKLY rules, comes from the rule itself. Rules
 * saved before that was recorded carry null and fall back to [defaultWeekStart],
 * which is the current locale — the old behaviour, kept so nothing shifts under
 * an existing schedule that has not been migrated yet.
 */
fun matchesRepeat(
    anchor: LocalDate,
    date: LocalDate,
    rule: RepeatRule,
    defaultWeekStart: DayOfWeek = currentLocaleWeekStart(),
): Boolean {
    if (!date.isAfter(anchor)) return false
    val interval = rule.interval.coerceAtLeast(1)

    return when (rule.freq) {
        RepeatFreq.DAILY -> {
            val days = ChronoUnit.DAYS.between(anchor, date)
            days % interval == 0L
        }

        RepeatFreq.WEEKLY -> {
            if (rule.weekDays.isNotEmpty() && date.dayOfWeek !in rule.weekDays) return false
            val weekStart = rule.weekStart ?: defaultWeekStart
            val weeks = ChronoUnit.WEEKS.between(
                startOfWeek(anchor, weekStart),
                startOfWeek(date, weekStart),
            )
            weeks % interval == 0L
        }

        RepeatFreq.MONTHLY -> {
            val dom = rule.dayOfMonth ?: anchor.dayOfMonth
            if (date.dayOfMonth != dom) return false
            val months =
                ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1))
            months % interval == 0L
        }
    }
}

/** The first day of the week containing [date], for a week starting on [weekStart]. */
fun startOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate {
    val shift = (date.dayOfWeek.value - weekStart.value + 7) % 7
    return date.minusDays(shift.toLong())
}

/** What the device currently considers the first day of the week. */
fun currentLocaleWeekStart(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

/** Tombstone key for a generated task occurrence. */
fun taskSuppressionKey(templateTaskId: Long, date: LocalDate): String =
    "T:$templateTaskId:${date.toEpochDay()}"

/** Tombstone key for a generated subtask occurrence. */
fun subtaskSuppressionKey(templateSubtaskId: Long, date: LocalDate): String =
    "S:$templateSubtaskId:${date.toEpochDay()}"

/**
 * Materialises every occurrence falling in [start]..[end] that is not already
 * present and not suppressed.
 *
 * Ids are handed out from [nextId]; the value to carry forward comes back in
 * [RecurrenceGenerationResult.nextId].
 *
 * Idempotent: running it twice over the same range produces lists equal to the
 * first run, which is what lets the calendar call it on every navigation.
 */
fun generateRecurrencesInRange(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
    start: LocalDate,
    end: LocalDate,
    nextId: Long,
    defaultWeekStart: DayOfWeek = currentLocaleWeekStart(),
): RecurrenceGenerationResult {
    val newTasks = tasks.toMutableList()
    val newSubtasks = subtasks.toMutableList()
    val subtasksByTask = subtasks.groupBy { it.taskId }

    var idCursor = nextId
    fun newId(): Long = idCursor++

    fun isSuppressed(key: String) = suppressedRecurrences.contains(key)

    fun nextTaskOrderIn(date: LocalDate): Int =
        (newTasks.filter { it.date == date }.maxOfOrNull { it.order } ?: -1) + 1

    fun cloneSubtaskIntoTask(templateSub: Subtask, newTaskId: Long): Subtask {
        val s = templateSub.copy(
            id = newId(),
            taskId = newTaskId,
            isDone = false,
            repeatRule = null,
            originSubtaskId = templateSub.id
        )
        newSubtasks.add(s)
        return s
    }

    fun findGeneratedTask(originTaskId: Long, targetDate: LocalDate): Task? =
        newTasks.firstOrNull { it.originTaskId == originTaskId && it.date == targetDate }

    fun cloneTaskForDate(templateTask: Task, targetDate: LocalDate): Task {
        val t = templateTask.copy(
            id = newId(),
            order = nextTaskOrderIn(targetDate),
            date = targetDate,
            isDone = false,
            repeatRule = null,
            originTaskId = templateTask.id
        )
        newTasks.add(t)
        return t
    }

    val dateTemplates = tasks.filter { it.originTaskId == null && it.date != null }

    for (t in dateTemplates) {
        val anchor = t.date ?: continue
        val subs = subtasksByTask[t.id].orEmpty().filter { it.originSubtaskId == null }

        val taskRule = t.repeatRule
        if (taskRule != null) {
            var d = start
            while (!d.isAfter(end)) {
                if (matchesRepeat(anchor, d, taskRule, defaultWeekStart)) {
                    if (!isSuppressed(taskSuppressionKey(t.id, d))) {
                        val existing = findGeneratedTask(t.id, d)
                        if (existing == null) {
                            val createdTask = cloneTaskForDate(t, d)
                            for (srcSub in subs) {
                                if (!isSuppressed(subtaskSuppressionKey(srcSub.id, d))) {
                                    cloneSubtaskIntoTask(srcSub, createdTask.id)
                                }
                            }
                            refreshHasSubtasksForTaskIn(createdTask.id, newTasks, newSubtasks)
                        }
                    }
                }
                d = d.plusDays(1)
            }
        }

        for (s in subs) {
            val rule = s.repeatRule ?: continue
            var d = start
            while (!d.isAfter(end)) {
                if (matchesRepeat(anchor, d, rule, defaultWeekStart)) {
                    // A deleted day stays deleted. This loop will happily build
                    // a carrier task for a repeating subtask, and it used to do
                    // so without asking whether the day's task had been deleted
                    // — so deleting an occurrence undid itself the next time the
                    // day was drawn, and there was no way to make it stick. The
                    // loop above has always honoured this tombstone.
                    val dayWasDeleted = isSuppressed(taskSuppressionKey(t.id, d))

                    if (!dayWasDeleted && !isSuppressed(subtaskSuppressionKey(s.id, d))) {
                        val taskForSub = findGeneratedTask(t.id, d) ?: cloneTaskForDate(t, d)
                        val alreadySub = newSubtasks.any {
                            it.taskId == taskForSub.id && it.originSubtaskId == s.id
                        }
                        if (!alreadySub) {
                            cloneSubtaskIntoTask(s, taskForSub.id)
                            refreshHasSubtasksForTaskIn(taskForSub.id, newTasks, newSubtasks)
                        }
                    }
                }
                d = d.plusDays(1)
            }
        }
    }

    return RecurrenceGenerationResult(
        tasks = newTasks,
        subtasks = newSubtasks,
        nextId = idCursor,
    )
}

private fun refreshHasSubtasksForTaskIn(
    taskId: Long,
    tasks: MutableList<Task>,
    subs: List<Subtask>,
) {
    val idx = tasks.indexOfFirst { it.id == taskId }
    if (idx >= 0) {
        val has = subs.any { it.taskId == taskId }
        tasks[idx] = tasks[idx].copy(hasSubtasks = has)
    }
}
