package com.alphaomegos.annasagenda

import java.text.DecimalFormat

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
