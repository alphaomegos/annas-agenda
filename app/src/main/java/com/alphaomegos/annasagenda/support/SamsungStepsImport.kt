package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * Steps from a Samsung Health export (19.10): Samsung Health → Settings →
 * Download personal data puts a folder of CSV files in Download/Samsung
 * Health; the days are in com.samsung.shealth.step_daily_trend.<stamp>.csv.
 *
 * As Eduard's export of 25.10.2026 has it: a first line naming the table
 * (behind a byte-order mark), then the header, then a row per day and
 * source, each ending in a comma. source_type -2 is «all devices» — the
 * total the Samsung Health app itself shows, phone and watch merged; the
 * others (0 the phone, 24 the watch, 1…) are the parts, and adding them up
 * would count a step twice. day_time is the day, «2015-07-05 00:00:00.000»
 * there; older exports wrote it as epoch milliseconds of that day's UTC
 * midnight, and both are read.
 */

/** Samsung's «all devices» source: the day's total as the app shows it. */
const val SAMSUNG_SOURCE_ALL = "-2"

/**
 * Each day's total from a step_daily_trend export, by date; empty when [text]
 * is not one (no header with day_time and count). A day with an «all devices»
 * row takes it; a day without one — not in Eduard's export, but exports
 * differ by version — takes its largest part rather than their sum.
 */
fun parseSamsungStepDailyTrend(text: String): Map<LocalDate, Int> {
    val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trimEnd('\r') }.toList()
    val headerAt = lines.indexOfFirst { line -> columnsOf(line).let { "day_time" in it && "count" in it } }
    if (headerAt < 0) return emptyMap()
    val columns = columnsOf(lines[headerAt])
    val dayAt = columns.indexOf("day_time")
    val countAt = columns.indexOf("count")
    val sourceAt = columns.indexOf("source_type")

    val all = HashMap<LocalDate, Int>()
    val largestPart = HashMap<LocalDate, Int>()
    for (line in lines.drop(headerAt + 1)) {
        if (line.isBlank()) continue
        val cells = line.split(',')
        val day = cells.getOrNull(dayAt)?.let(::samsungDay) ?: continue
        val count = cells.getOrNull(countAt)?.trim()?.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 } ?: continue
        if (cells.getOrNull(sourceAt)?.trim() == SAMSUNG_SOURCE_ALL) {
            all[day] = maxOf(all[day] ?: 0, count)
        } else {
            largestPart[day] = maxOf(largestPart[day] ?: 0, count)
        }
    }
    return (largestPart.keys + all.keys).associateWith { all[it] ?: largestPart.getValue(it) }
}

/** A header's column names, without the table prefix some exports put on each. */
private fun columnsOf(line: String): List<String> =
    line.split(',').map { it.trim().substringAfterLast("com.samsung.shealth.step_daily_trend.") }

private fun samsungDay(cell: String): LocalDate? {
    val value = cell.trim()
    if (value.isEmpty()) return null
    value.toLongOrNull()?.let { millis -> return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate() }
    return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
}

/** What an import did: the days it brought, and what became of them. */
data class StepsImportOutcome(
    val days: List<StepDay>,
    val inFile: Int,
    val added: Int,
    val updated: Int,
    val leftAlone: Int,
    val first: LocalDate?,
    val last: LocalDate?,
)

/**
 * The kept days with an export's days laid in (19.10): an empty day takes the
 * export's count, a day an earlier import brought takes the newer one; a day
 * Health Connect gave or the user typed in is left as it is — those are the
 * live count and the user's word. Days of nothing are not brought.
 */
fun stepDaysAfterImport(days: List<StepDay>, imported: Map<LocalDate, Int>): StepsImportOutcome {
    val byDate = normalizedStepDays(days).associateBy { it.date }.toMutableMap()
    var added = 0
    var updated = 0
    var leftAlone = 0
    val brought = imported.filterValues { it > 0 }
    brought.forEach { (date, steps) ->
        val kept = byDate[date]
        when (kept?.source) {
            null -> { byDate[date] = StepDay(date, steps, StepSource.SAMSUNG_IMPORT); added++ }
            StepSource.SAMSUNG_IMPORT -> { if (kept.steps != steps) { byDate[date] = kept.copy(steps = steps); updated++ } }
            StepSource.HEALTH_CONNECT, StepSource.MANUAL -> leftAlone++
        }
    }
    return StepsImportOutcome(
        days = byDate.values.sortedBy { it.date },
        inFile = brought.size,
        added = added,
        updated = updated,
        leftAlone = leftAlone,
        first = brought.keys.minOrNull(),
        last = brought.keys.maxOrNull(),
    )
}

/** How an import ended, for the dialog that follows it. */
sealed interface StepsImportResult {
    data class Done(val outcome: StepsImportOutcome) : StepsImportResult
    /** The file read, but held no step days: some other file was picked. */
    data object NotAnExport : StepsImportResult
    data class Failed(val reason: String) : StepsImportResult
}
