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
 * **A new [rule]** (null or the same rule: keep it) changes which days the
 * series lands on, and then the days already on the calendar from [fromDate]
 * on split in two: those the new rule also names are moved over as above,
 * ticks and all; those it does not name go, as "delete from today" would take
 * them — moving a weekly call from Thursday to Friday means the Thursdays
 * ahead are gone. The new series starts on the first day from [fromDate] the
 * new rule's shape allows (a Friday for "Fridays", the given day of the month
 * for a monthly one, [fromDate] itself for a daily one), and counts its
 * interval from there.
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
    rule: RepeatRule? = null,
): SeriesEditResult {
    val unchanged = SeriesEditResult(tasks, subtasks, suppressedRecurrences)

    val template = tasks.firstOrNull {
        it.id == templateTaskId && it.originTaskId == null && it.repeatRule != null
    } ?: return unchanged
    val oldRule = template.repeatRule ?: return unchanged
    // Compared by the days it names, not field by field: the repeat picker
    // hands back a rule without a week start, so a rule the user opened and
    // confirmed untouched would otherwise look new and split the series.
    val ruleChanged = rule != null && !rule.namesTheSameDaysAs(oldRule)
    val effectiveRule = if (rule != null && ruleChanged) rule else oldRule

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

    // No day at all: nothing is generated from it, so there is nothing to
    // split — the wording and the rule are simply replaced.
    if (anchor == null) {
        val edited = tasks.map { t ->
            if (t.id == template.id) t.copy(description = clean, repeatRule = effectiveRule) else t
        }
        return unchanged.copy(tasks = edited)
    }

    // Nothing before fromDate and the same rule: edit where it stands.
    if (!anchor.isBefore(fromDate) && !ruleChanged) {
        val edited = tasks.map { t ->
            when {
                t.id == template.id -> t.copy(description = clean, time = newTime)
                t.originTaskId == template.id -> edited(t)
                else -> t
            }
        }
        return unchanged.copy(tasks = edited)
    }

    // Nothing before fromDate but a new rule: still no history to keep, so
    // the template itself moves to where the new rule starts.
    if (!anchor.isBefore(fromDate)) {
        val start = firstDayOfShape(effectiveRule, anchor) ?: return unchanged
        val newRule = pinnedRule(effectiveRule, start, weekStart)

        val ownOccurrences = tasks.filter { it.originTaskId == template.id }
        val onStart = ownOccurrences.firstOrNull { it.date == start }
        val dropped = ownOccurrences
            .filter { t -> t.date == start || !matchesRepeat(start, t.date ?: start, newRule, weekStart) }
            .mapTo(mutableSetOf()) { it.id }

        val newTasks = tasks
            .filterNot { it.id in dropped }
            .map { t ->
                when {
                    t.id == template.id -> t.copy(
                        date = start,
                        description = clean,
                        time = newTime,
                        repeatRule = newRule,
                        isDone = if (start == anchor) t.isDone else onStart?.isDone ?: false,
                    )
                    t.originTaskId == template.id -> edited(t)
                    else -> t
                }
            }
        val newSubtasks = subtasks.filterNot { it.taskId in dropped }

        return SeriesEditResult(
            tasks = withHasSubtasksRefreshed(newTasks, newSubtasks),
            subtasks = newSubtasks,
            suppressedRecurrences = suppressedRecurrences,
        )
    }

    // Started before fromDate: split. With the same rule the new first day is
    // the old series' next day, which keeps its phase; with a new rule it is
    // where the new rule's shape first allows.
    val firstDay = (
        if (ruleChanged) {
            firstDayOfShape(effectiveRule, fromDate)
        } else {
            generateSequence(fromDate) { it.plusDays(1) }
                .take(SERIES_LOOKAHEAD_DAYS.toInt())
                .firstOrNull { matchesRepeat(anchor, it, oldRule, weekStart) }
        }
    ) ?: return unchanged

    fun pinned(r: RepeatRule): RepeatRule = pinnedRule(r, firstDay, weekStart)
    val newSeriesRule = pinned(effectiveRule)

    val templateSubs = subtasks.filter { it.taskId == template.id && it.originSubtaskId == null }

    val newTemplateId = newId()
    val subIdMap = templateSubs.associate { it.id to newId() }

    val occurrencesAll = tasks.filter { t ->
        val date = t.date
        t.originTaskId == template.id && date != null && !date.isBefore(fromDate)
    }
    // With the same rule every one of them is a day of the new series. With a
    // new rule only those it names are; the rest go.
    val (occurrencesFromHere, droppedOccurrences) = if (ruleChanged) {
        occurrencesAll.partition { t ->
            val date = t.date!!
            date == firstDay || matchesRepeat(firstDay, date, newSeriesRule, weekStart)
        }
    } else {
        occurrencesAll to emptyList()
    }
    val movedIds = occurrencesFromHere.mapTo(mutableSetOf()) { it.id }
    val droppedIds = droppedOccurrences.mapTo(mutableSetOf()) { it.id }
    val occupied = occurrencesFromHere.any { it.date == firstDay }

    val newTemplate = template.copy(
        id = newTemplateId,
        order = occurrencesFromHere.firstOrNull { it.date == firstDay }?.order
            ?: nextTaskOrderOn(tasks, firstDay),
        date = firstDay,
        time = newTime,
        description = clean,
        isDone = false,
        repeatRule = newSeriesRule,
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

    val newTasks = tasks.filterNot { it.id in droppedIds }.map { t ->
        when {
            t.id == template.id -> t.copy(repeatRule = null)
            t.id in movedIds -> edited(t).copy(originTaskId = newTemplateId)
            else -> t
        }
    } + newTemplate

    val newSubtasks = subtasks.filterNot { it.taskId in droppedIds }.map { s ->
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

/**
 * The first day from [from] on that a rule's shape allows, ignoring its
 * interval: a day of the week for a weekly rule, a day of the month for a
 * monthly one, [from] itself for a daily one. A new series starts there and
 * counts its interval from it. Null when no such day comes within the
 * look-ahead (the 31st, asked of a calendar that has none for five years,
 * cannot happen — but the answer is honest if it does).
 */
fun firstDayOfShape(rule: RepeatRule, from: LocalDate): LocalDate? =
    generateSequence(from) { it.plusDays(1) }
        .take(SERIES_LOOKAHEAD_DAYS.toInt())
        .firstOrNull { day ->
            when (rule.freq) {
                RepeatFreq.DAILY -> true
                RepeatFreq.WEEKLY -> rule.weekDays.isEmpty() || day.dayOfWeek in rule.weekDays
                RepeatFreq.MONTHLY -> day.dayOfMonth == (rule.dayOfMonth ?: from.dayOfMonth)
            }
        }

/**
 * [rule] with what it would otherwise take from its first day written down:
 * the day of the month (from [firstDay]) and the start of the week.
 */
private fun pinnedRule(rule: RepeatRule, firstDay: LocalDate, weekStart: DayOfWeek): RepeatRule =
    rule.copy(
        dayOfMonth = rule.dayOfMonth ?: if (rule.freq == RepeatFreq.MONTHLY) firstDay.dayOfMonth else null,
        weekStart = rule.weekStart ?: weekStart,
    )

/**
 * Whether two rules name the same days: same frequency and interval, and the
 * same week days for a weekly rule or the same day of the month for a
 * monthly one. The week start is left out — it only places the boundary of
 * "every N weeks", and the series keeps its old rule whenever this says yes.
 */
fun RepeatRule.namesTheSameDaysAs(other: RepeatRule): Boolean =
    freq == other.freq &&
        interval.coerceAtLeast(1) == other.interval.coerceAtLeast(1) &&
        (freq != RepeatFreq.WEEKLY || weekDays == other.weekDays) &&
        (freq != RepeatFreq.MONTHLY || dayOfMonth == other.dayOfMonth)
