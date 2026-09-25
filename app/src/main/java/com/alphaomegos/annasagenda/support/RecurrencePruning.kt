package com.alphaomegos.annasagenda

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * How much of a day's list a prune pass was able to drop.
 *
 * Kept as a value rather than logged, so a test can state the numbers and the
 * caller can decide whether the pass was worth writing back.
 */
data class RecurrencePruneReport(
    /** Dates that held at least one generated occurrence and were considered. */
    val datesExamined: Int = 0,
    /** Dates whose generated occurrences were dropped, all of them together. */
    val datesPruned: Int = 0,
    val tasksPruned: Int = 0,
    val subtasksPruned: Int = 0,
    /** Generated occurrences that stayed, because dropping them would show. */
    val tasksKept: Int = 0,
) {
    val pruned: Boolean get() = tasksPruned > 0 || subtasksPruned > 0
}

data class RecurrencePruneResult(
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val report: RecurrencePruneReport,
)

/**
 * Drops generated occurrences that [generateRecurrencesInRange] would put back
 * exactly as they were.
 *
 * Every occurrence of every repeating task is written into the saved state the
 * first time a screen draws its day, and stays there for good. Browsing a year
 * ahead in the calendar is enough to materialise a year of them; nothing ever
 * takes them out again. The store therefore grows with how much the user has
 * scrolled, not with what they have actually planned.
 *
 * Most of that is derivable: an untouched occurrence is a copy of its template
 * on a date the template's rule already names, so the generator can rebuild it
 * on demand. An occurrence the user has touched — ticked off, renamed, dragged
 * up the day, given a time — is not derivable and has to stay.
 *
 * Rather than enumerate "touched" (and get it wrong the day a field is added
 * to Task), this asks the generator directly: take the day's generated
 * occurrences out, generate that one day again, and keep the removal only if
 * what comes back is the same day. Anything the generator cannot reproduce —
 * including the [Task.order] it recomputes from scratch — fails that check and
 * is left alone. A new field on Task is covered the moment it joins the
 * signature below, and until then the worst case is that a date is judged
 * unchanged too readily, which is why the signature lists fields explicitly
 * instead of comparing whatever `copy` happened to carry over.
 *
 * Dates are independent: generation for one day reads only the templates and
 * the tasks already on that same day, so pruning one date cannot change the
 * verdict for another.
 *
 * Nothing here touches templates, tombstones, or anything outside the day
 * being examined. Occurrences a running-plan entry points at are never dropped
 * — regeneration would hand them new ids and quietly break the link.
 *
 * [isPrunableDate] narrows which dates are eligible at all; the caller uses it
 * to keep, say, the past out of reach.
 */
fun pruneRedundantGeneratedOccurrences(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
    runningPlanEntries: List<RunningPlanEntry> = emptyList(),
    isPrunableDate: (LocalDate) -> Boolean = { true },
    weekStart: DayOfWeek = currentLocaleWeekStart(),
): RecurrencePruneResult {
    val generatedByDate = tasks
        .filter { it.originTaskId != null && it.date != null }
        .groupBy { it.date!! }

    if (generatedByDate.isEmpty()) {
        return RecurrencePruneResult(tasks, subtasks, RecurrencePruneReport())
    }

    val pinnedTaskIds = runningPlanEntries.mapNotNull { it.taskId }.toSet()

    // One cursor for every trial generation. The ids it hands out are thrown
    // away with the trial, so they only have to avoid colliding with the state
    // being generated from.
    val trialNextId = maxOf(
        tasks.maxOfOrNull { it.id } ?: 0L,
        subtasks.maxOfOrNull { it.id } ?: 0L,
    ) + 1

    val removedTaskIds = mutableSetOf<Long>()
    var datesExamined = 0
    var datesPruned = 0

    for ((date, dayGenerated) in generatedByDate) {
        if (!isPrunableDate(date)) continue

        datesExamined++

        if (dayGenerated.any { it.id in pinnedTaskIds }) continue

        val victimIds = dayGenerated.mapTo(mutableSetOf()) { it.id }
        val keptTasks = tasks.filterNot { it.id in victimIds }
        val keptSubtasks = subtasks.filterNot { it.taskId in victimIds }

        val regenerated = generateRecurrencesInRange(
            tasks = keptTasks,
            subtasks = keptSubtasks,
            suppressedRecurrences = suppressedRecurrences,
            start = date,
            end = date,
            nextId = trialNextId,
            defaultWeekStart = weekStart,
        )

        val before = daySignature(date, tasks, subtasks) ?: continue
        val after = daySignature(date, regenerated.tasks, regenerated.subtasks) ?: continue

        if (before != after) continue

        removedTaskIds += victimIds
        datesPruned++
    }

    if (removedTaskIds.isEmpty()) {
        return RecurrencePruneResult(
            tasks = tasks,
            subtasks = subtasks,
            report = RecurrencePruneReport(
                datesExamined = datesExamined,
                tasksKept = tasks.count { it.originTaskId != null },
            ),
        )
    }

    val prunedTasks = tasks.filterNot { it.id in removedTaskIds }
    val prunedSubtasks = subtasks.filterNot { it.taskId in removedTaskIds }

    return RecurrencePruneResult(
        tasks = prunedTasks,
        subtasks = prunedSubtasks,
        report = RecurrencePruneReport(
            datesExamined = datesExamined,
            datesPruned = datesPruned,
            tasksPruned = tasks.size - prunedTasks.size,
            subtasksPruned = subtasks.size - prunedSubtasks.size,
            tasksKept = prunedTasks.count { it.originTaskId != null },
        ),
    )
}

/**
 * Everything about one day that the user can see, with ids left out.
 *
 * Ids are left out because regeneration necessarily invents new ones; that is
 * the one difference a rebuilt day is allowed to have. Everything else has to
 * match, [Task.order] included, or the day would come back in a different
 * order than the user left it in.
 *
 * Null when the day cannot be compared this way at all: two tasks sharing an
 * order are separated by id, which is exactly the thing that does not survive.
 * Such a day is never pruned.
 */
private fun daySignature(
    date: LocalDate,
    tasks: List<Task>,
    subtasks: List<Subtask>,
): Set<TaskSignature>? {
    val onDate = tasks.filter { it.date == date }

    if (onDate.map { it.order }.toSet().size != onDate.size) return null

    val subtasksByTask = subtasks.groupBy { it.taskId }
    val signatures = mutableSetOf<TaskSignature>()

    for (t in onDate) {
        val subs = subtasksByTask[t.id].orEmpty()
        if (subs.map { it.order }.toSet().size != subs.size) return null

        signatures += TaskSignature(
            order = t.order,
            time = t.time,
            description = t.description,
            colorArgb = t.colorArgb,
            hasSubtasks = t.hasSubtasks,
            isDone = t.isDone,
            linkedManualCounterId = t.linkedManualCounterId,
            repeatRule = t.repeatRule,
            originTaskId = t.originTaskId,
            subtasks = subs.map { s ->
                SubtaskSignature(
                    order = s.order,
                    description = s.description,
                    colorArgb = s.colorArgb,
                    isDone = s.isDone,
                    repeatRule = s.repeatRule,
                    originSubtaskId = s.originSubtaskId,
                )
            }.toSet(),
        )
    }

    // Two tasks on the same day that are identical in every visible respect
    // would collapse into one entry here, and a day that lost one of them
    // would still compare equal. Refuse the day instead.
    if (signatures.size != onDate.size) return null

    return signatures
}

private data class TaskSignature(
    val order: Int,
    val time: java.time.LocalTime?,
    val description: String,
    val colorArgb: Long?,
    val hasSubtasks: Boolean,
    val isDone: Boolean,
    val linkedManualCounterId: Long?,
    val repeatRule: RepeatRule?,
    val originTaskId: Long?,
    val subtasks: Set<SubtaskSignature>,
)

private data class SubtaskSignature(
    val order: Int,
    val description: String,
    val colorArgb: Long?,
    val isDone: Boolean,
    val repeatRule: RepeatRule?,
    val originSubtaskId: Long?,
)
