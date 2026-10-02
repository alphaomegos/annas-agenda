package com.alphaomegos.annasagenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** What editing a series touches: the two lists and the tombstones. */
data class SeriesEditResult(
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val suppressedRecurrences: Set<String>,
)

/**
 * How far ahead to look for the next day a series lands on. Five years covers
 * "every twelve months on the 31st" with room to spare; a rule that names no
 * day in that time has no future to edit.
 */
private const val SERIES_LOOKAHEAD_DAYS = 366L * 5

/**
 * Renames a repeating task and sets (or clears) its time **from [fromDate]
 * on**, leaving every earlier day as it was.
 *
 * The difficulty is that a template is not only a rule — it is also the task
 * on its own first day. Rewriting it in place would rewrite that first day
 * too, which may be months back. So there are two cases:
 *
 * **The series starts on or after [fromDate]** — nothing earlier exists, and
 * the template and its occurrences are simply edited where they stand.
 *
 * **It started before** — the series is split:
 * - The old template keeps its day and its wording, and loses its rule
 *   (and its subtasks theirs): it is history now, like the days after it.
 * - A new template takes over on the first day the rule names from
 *   [fromDate] on, with the same rule. That day is itself one of the old
 *   series' days, so the interval's phase and the day of the month carry over
 *   by construction (a test asks the generator, for every kind of rule). The
 *   day of the month and the start of the week are written onto the rule all
 *   the same, so it stops depending on its first day and on the phone's
 *   language at all.
 * - Occurrences already on the calendar from [fromDate] on are **moved over,
 *   not deleted**: same ids, same ticks, same subtasks, only re-attached to the
 *   new template. That is the difference from "delete from today and make a
 *   new one", which would quietly lose every day already ticked ahead and
 *   bring back every day already deleted.
 * - Days deleted from the old series stay deleted in the new one (the
 *   tombstones move with it).
 * - If an occurrence already stands on the new template's own first day, the
 *   template is hidden there — the occurrence is that day — using the same
 *   tombstone that deleting a template's first day writes, which every screen
 *   already honours.
 *
 * Either way, an occurrence's wording is changed only if it still had the
 * template's wording, and its time only if it still had the template's time:
 * an occurrence the user renamed by hand keeps its own name.
 *
 * Returns the input unchanged for anything that is not a repeating template,
 * for a blank description, and for a rule with no day left from [fromDate].
 *
 * Known limit: a subtask with a rule of its own *and an interval above one*
 * counts its days from the template's first day, and the new first day is
 * chosen by the task's rule, not the subtask's — such a subtask may shift
 * phase. Nothing in the app creates that shape on purpose.
 */
fun stateAfterEditingTaskSeriesFrom(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
    templateTaskId: Long,
    fromDate: LocalDate,
    description: String,
    time: LocalTime?,
    newId: () -> Long,
    weekStart: DayOfWeek,
): SeriesEditResult {
    val unchanged = SeriesEditResult(tasks, subtasks, suppressedRecurrences)

    val template = tasks.firstOrNull {
        it.id == templateTaskId && it.originTaskId == null && it.repeatRule != null
    } ?: return unchanged
    val rule = template.repeatRule ?: return unchanged

    val clean = description.trim()
    if (clean.isBlank()) return unchanged

    val anchor = template.date
    // A task without a day has no time (see tasksAfterSettingTime).
    val newTime = if (anchor == null) null else time

    // Explicit about whose fields these are: inside an extension a bare
    // `description` would mean the parameter above, not the task's own.
    fun edited(occurrence: Task): Task = occurrence.copy(
        description = if (occurrence.description == template.description) clean else occurrence.description,
        time = if (occurrence.time == template.time) newTime else occurrence.time,
    )

    // Nothing before fromDate: edit where it stands.
    if (anchor == null || !anchor.isBefore(fromDate)) {
        val edited = tasks.map { t ->
            when {
                t.id == template.id -> t.copy(description = clean, time = newTime)
                t.originTaskId == template.id -> edited(t)
                else -> t
            }
        }
        return unchanged.copy(tasks = edited)
    }

    val firstDay = generateSequence(fromDate) { it.plusDays(1) }
        .take(SERIES_LOOKAHEAD_DAYS.toInt())
        .firstOrNull { matchesRepeat(anchor, it, rule, weekStart) }
        ?: return unchanged

    fun pinned(r: RepeatRule): RepeatRule = r.copy(
        dayOfMonth = r.dayOfMonth ?: if (r.freq == RepeatFreq.MONTHLY) anchor.dayOfMonth else null,
        weekStart = r.weekStart ?: weekStart,
    )

    val templateSubs = subtasks.filter { it.taskId == template.id && it.originSubtaskId == null }

    val newTemplateId = newId()
    val subIdMap = templateSubs.associate { it.id to newId() }

    val occurrencesFromHere = tasks.filter { t ->
        val date = t.date
        t.originTaskId == template.id && date != null && !date.isBefore(fromDate)
    }
    val movedIds = occurrencesFromHere.mapTo(mutableSetOf()) { it.id }
    val occupied = occurrencesFromHere.any { it.date == firstDay }

    val newTemplate = template.copy(
        id = newTemplateId,
        order = occurrencesFromHere.firstOrNull { it.date == firstDay }?.order
            ?: nextTaskOrderOn(tasks, firstDay),
        date = firstDay,
        time = newTime,
        description = clean,
        isDone = false,
        repeatRule = pinned(rule),
        originTaskId = null,
    )

    val newTemplateSubs = templateSubs.map { s ->
        s.copy(
            id = subIdMap.getValue(s.id),
            taskId = newTemplateId,
            isDone = false,
            repeatRule = s.repeatRule?.let(::pinned),
        )
    }

    val newTasks = tasks.map { t ->
        when {
            t.id == template.id -> t.copy(repeatRule = null)
            t.id in movedIds -> edited(t).copy(originTaskId = newTemplateId)
            else -> t
        }
    } + newTemplate

    val newSubtasks = subtasks.map { s ->
        when {
            s.taskId == template.id && s.originSubtaskId == null -> s.copy(repeatRule = null)
            s.taskId in movedIds -> {
                val mapped = s.originSubtaskId?.let { subIdMap[it] }
                if (mapped != null) s.copy(originSubtaskId = mapped) else s
            }
            else -> s
        }
    } + newTemplateSubs

    val movedTombstones = suppressionsMovedToNewTemplate(
        suppressedRecurrences = suppressedRecurrences,
        taskIds = mapOf(template.id to newTemplateId),
        subtaskIds = subIdMap,
        fromDate = fromDate,
    )
    val tombstones =
        if (occupied) movedTombstones + taskSuppressionKey(newTemplateId, firstDay) else movedTombstones

    return SeriesEditResult(
        tasks = withHasSubtasksRefreshed(newTasks, newSubtasks),
        subtasks = newSubtasks,
        suppressedRecurrences = tombstones,
    )
}
