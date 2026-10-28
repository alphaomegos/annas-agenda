package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * Weight from a Samsung Health export (28.10, Eduard: he weighs himself in
 * both apps): com.samsung.health.weight.<stamp>.csv, laid out as the steps
 * file is — a line naming the table, the header, a row per weighing. The
 * columns may carry the table's name before them; the time is text or epoch
 * milliseconds, in UTC, with time_offset («UTC+0300») to make it local.
 */

/** Each day's weight from the export, the day's last weighing; empty when [text] is not one. */
fun parseSamsungWeight(text: String): Map<LocalDate, Double> {
    val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trimEnd('\r') }.toList()
    fun columns(line: String) = line.split(',').map { it.trim().substringAfterLast('.') }
    val headerAt = lines.indexOfFirst { l -> columns(l).let { "weight" in it && ("start_time" in it || "create_time" in it) } }
    if (headerAt < 0) return emptyMap()
    val cols = columns(lines[headerAt])
    val weightAt = cols.indexOf("weight")
    val timeAt = cols.indexOf("start_time").takeIf { it >= 0 } ?: cols.indexOf("create_time")
    val offsetAt = cols.indexOf("time_offset")
    val byDay = HashMap<LocalDate, Pair<LocalDateTime, Double>>()
    for (line in lines.drop(headerAt + 1)) {
        if (line.isBlank()) continue
        val cells = line.split(',')
        val kg = cells.getOrNull(weightAt)?.trim()?.toDoubleOrNull()?.takeIf { it in 20.0..400.0 } ?: continue
        val utc = cells.getOrNull(timeAt)?.let(::samsungTime) ?: continue
        val offset = cells.getOrNull(offsetAt)?.let(::samsungOffset) ?: ZoneOffset.UTC
        val local = utc.atOffset(ZoneOffset.UTC).withOffsetSameInstant(offset).toLocalDateTime()
        val kept = byDay[local.toLocalDate()]
        if (kept == null || !local.isBefore(kept.first)) byDay[local.toLocalDate()] = local to kg
    }
    return byDay.mapValues { it.value.second }
}

private fun samsungTime(cell: String): LocalDateTime? {
    val v = cell.trim()
    if (v.isEmpty()) return null
    v.toLongOrNull()?.let { return LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }
    return runCatching { LocalDateTime.parse(v.take(19).replace(' ', 'T')) }.getOrNull()
}

private fun samsungOffset(cell: String): ZoneOffset? {
    val m = Regex("""UTC([+-])(\d{2})(\d{2})""").find(cell.trim()) ?: return null
    val sign = if (m.groupValues[1] == "-") -1 else 1
    return runCatching { ZoneOffset.ofHoursMinutes(sign * m.groupValues[2].toInt(), sign * m.groupValues[3].toInt()) }.getOrNull()
}

/** The days the export and the app both weighed, differently: what the user is asked about. */
fun weightImportConflicts(entries: List<AnthropometryEntry>, read: Map<LocalDate, Double>): Int =
    entries.count { e -> val w = e.weightKg; w != null && read[e.date]?.let { it != w } == true }

data class WeightImportOutcome(
    val entries: List<AnthropometryEntry>,
    val inFile: Int,
    val added: Int,
    val replaced: Int,
    val kept: Int,
    val first: LocalDate?,
    val last: LocalDate?,
)

/**
 * The measurements with the export's weights laid in: a day without a weight
 * takes it (its other measurements stay); a day weighed in both keeps the
 * app's unless [preferImported] (Eduard: ask which).
 */
fun anthropometryAfterWeightImport(entries: List<AnthropometryEntry>, read: Map<LocalDate, Double>, preferImported: Boolean): WeightImportOutcome {
    val byDate = entries.associateBy { it.date }.toMutableMap()
    var added = 0
    var replaced = 0
    var kept = 0
    read.forEach { (date, kg) ->
        val e = byDate[date]
        when {
            e == null -> { byDate[date] = AnthropometryEntry(date = date, weightKg = kg); added++ }
            e.weightKg == null -> { byDate[date] = e.copy(weightKg = kg); added++ }
            e.weightKg == kg -> kept++
            preferImported -> { byDate[date] = e.copy(weightKg = kg); replaced++ }
            else -> kept++
        }
    }
    return WeightImportOutcome(byDate.values.sortedBy { it.date }, read.size, added, replaced, kept, read.keys.minOrNull(), read.keys.maxOrNull())
}

/** Where a weight import stands, for the screen's dialogs. */
sealed interface WeightImportState {
    data class Conflicts(val read: Map<LocalDate, Double>, val conflicts: Int) : WeightImportState
    data class Done(val outcome: WeightImportOutcome) : WeightImportState
    data object NotAnExport : WeightImportState
    data class Failed(val reason: String) : WeightImportState
}
