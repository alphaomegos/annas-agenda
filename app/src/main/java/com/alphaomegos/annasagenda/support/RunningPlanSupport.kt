package com.alphaomegos.annasagenda

import java.text.DecimalFormat
import java.time.LocalDate

fun parseRunningDurationToMinutes(raw: String): Int? {
    val digitsAll = raw.filter { it.isDigit() }
    if (digitsAll.isBlank()) return null

    if (digitsAll.length <= 2) {
        return digitsAll.toIntOrNull()?.coerceAtLeast(0)
    }

    val d = digitsAll.take(4).padStart(4, '0')

    val hh = d.substring(0, 2).toIntOrNull() ?: return null
    val mm = d.substring(2, 4).toIntOrNull() ?: return null

    return if (mm in 0..59) {
        (hh * 60 + mm).coerceAtLeast(0)
    } else {
        d.toIntOrNull()?.coerceAtLeast(0)
    }
}

fun parseRunningKm(raw: String): Double? {
    val clean = raw.trim().replace(',', '.')
    return clean.toDoubleOrNull()
}

fun formatRunningKmForTitle(raw: String): String {
    val km = parseRunningKm(raw) ?: return raw.trim()
    return DecimalFormat("0.#").format(km)
}

fun isRunningPlanEntryIncomplete(entry: RunningPlanEntry): Boolean {
    val km = parseRunningKm(entry.distanceKmText)
    val minutes = parseRunningDurationToMinutes(entry.durationHhMmText)
    val minutesOk = minutes != null && minutes > 0
    val paceDigits = entry.paceText.filter { it.isDigit() }
    val paceOk = paceDigits.length == 4
    return km == null || !minutesOk || !paceOk
}

fun buildRunningPlanTaskTitle(
    entry: RunningPlanEntry,
    formatKmTitle: (String) -> String,
    formatMinutesTitle: (Int) -> String,
): String? {
    val distRaw = entry.distanceKmText.trim()
    if (distRaw.isNotBlank()) {
        val kmTitle = formatRunningKmForTitle(distRaw)
        return formatKmTitle(kmTitle)
    }

    val minutes = parseRunningDurationToMinutes(entry.durationHhMmText) ?: return null
    return formatMinutesTitle(minutes.coerceAtLeast(1))
}
/**
 * Removes every reference to [taskId] from the plan.
 *
 * A plan row keeps the id of the task it created, forever, and nothing checked
 * that the task was still there. Deleting that task from the calendar left the
 * row pointing at a number — and ids are handed out as "one past the largest in
 * use", so after a restart that number belongs to whatever the user created
 * next. Resetting the plan then deleted a stranger's task, and editing the row
 * renamed one.
 */
fun runningPlanEntriesWithoutTask(
    entries: List<RunningPlanEntry>,
    taskId: Long,
): List<RunningPlanEntry> {
    if (entries.none { it.taskId == taskId }) return entries

    return entries.map { entry ->
        if (entry.taskId == taskId) entry.copy(taskId = null) else entry
    }
}

/** The plan after one row was typed into, and whatever that left behind. */
data class RunningPlanEdit(
    val entries: List<RunningPlanEntry>,
    /**
     * The task the row was holding, now that the row is gone. Nothing points
     * at it any more, so it has to be deleted rather than left in the
     * calendar as a run nobody planned.
     */
    val orphanedTaskId: Long? = null,
)

/**
 * One day's row of the running plan, after the user typed into it.
 *
 * A null argument means "not this field" rather than "clear this field", so
 * that a screen editing distance does not have to resend the other two.
 *
 * Pace is only taken before the plan is approved... the other way round:
 * before approval the pace column is not the user's to fill, so what they type
 * there is ignored and the row keeps whatever it had. After approval it is a
 * record of what actually happened, and it is theirs.
 *
 * Emptying a row means two different things either side of approval. Before,
 * the plan is still being written, so the row goes and takes its task with it.
 * After, the row is a day of the plan that happens to have nothing filled in
 * yet; it stays, and so does its task, until pruneRunningPlanNow decides the
 * day is long past.
 *
 * Rows come back in date order, because the screen draws them in the order it
 * is given.
 */
fun runningPlanEntriesAfterEdit(
    entries: List<RunningPlanEntry>,
    approved: Boolean,
    date: LocalDate,
    distanceKmText: String? = null,
    durationHhMmText: String? = null,
    paceText: String? = null,
): RunningPlanEdit {
    val existing = entries.firstOrNull { it.date == date }
    val base = existing ?: RunningPlanEntry(date = date)

    val updated = base.copy(
        distanceKmText = distanceKmText ?: base.distanceKmText,
        durationHhMmText = durationHhMmText ?: base.durationHhMmText,
        paceText = if (approved) (paceText ?: base.paceText) else base.paceText,
    )

    val nowEmpty = updated.distanceKmText.isBlank() &&
        updated.durationHhMmText.isBlank() &&
        updated.paceText.isBlank()

    if (nowEmpty && !approved) {
        if (existing == null) return RunningPlanEdit(entries)

        return RunningPlanEdit(
            entries = entries.filterNot { it.date == date },
            orphanedTaskId = updated.taskId,
        )
    }

    // An approved row that has been emptied stays, so that the plan keeps its
    // shape; an approved day that never existed is not conjured up by typing
    // nothing into it.
    if (nowEmpty && existing == null) return RunningPlanEdit(entries)

    val without = entries.filterNot { it.date == date }

    return RunningPlanEdit((without + updated).sortedBy { it.date })
}

/**
 * The plan as it is worth approving: trimmed, with the blank days left out,
 * in date order.
 *
 * Approving turns rows into tasks in the calendar, so a row that says nothing
 * must not become a task that says nothing. Whitespace counts as nothing —
 * a stray space in a distance field is not a plan for that day.
 */
fun runningPlanEntriesCleanedForApproval(
    entries: List<RunningPlanEntry>,
): List<RunningPlanEntry> =
    entries
        .map {
            it.copy(
                distanceKmText = it.distanceKmText.trim(),
                durationHhMmText = it.durationHhMmText.trim(),
                paceText = it.paceText.trim(),
            )
        }
        .filter {
            it.distanceKmText.isNotBlank() ||
                it.durationHhMmText.isNotBlank() ||
                it.paceText.isNotBlank()
        }
        .sortedBy { it.date }

/**
 * The days the plan has given up on: long enough past, and never filled in.
 *
 * "Long enough" is more than one whole day, so a run the user has not yet
 * written up in the evening, or the morning after, is still theirs to record.
 * Only from the day after that does the plan stop waiting.
 */
fun expiredRunningPlanEntries(
    entries: List<RunningPlanEntry>,
    today: LocalDate,
): List<RunningPlanEntry> =
    entries.filter { today.isAfter(it.date.plusDays(1)) && isRunningPlanEntryIncomplete(it) }
