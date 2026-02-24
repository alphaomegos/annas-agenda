package com.alphaomegos.annasagenda.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.alphaomegos.annasagenda.Task
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun appLocale(): Locale {
    val cfg = LocalContext.current.resources.configuration
    return cfg.locales[0]
}

internal fun formatOneDecimal(v: Double): String {
    val r = (v * 10.0).roundToInt() / 10.0
    val s = String.format(Locale.US, "%.1f", r)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

internal fun formatSignedOneDecimal(v: Double): String {
    val r = (v * 10.0).roundToInt() / 10.0
    val sign = if (r > 0) "+" else ""
    val s = String.format(Locale.US, "%.1f", r)
    val trimmed = if (s.endsWith(".0")) s.dropLast(2) else s
    return sign + trimmed
}

internal fun parseOneDecimalOrNull(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return t.replace(',', '.').toDoubleOrNull()
}

internal fun orderedWeekDays(first: DayOfWeek): List<DayOfWeek> {
    val all = DayOfWeek.entries.toList()
    val idx = all.indexOf(first)
    return all.drop(idx) + all.take(idx)
}

internal fun parseDecimalOrNull(text: String): Double? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return t.replace(',', '.').toDoubleOrNull()
}

internal fun isSuppressedTemplateTaskOnItsDate(task: Task, suppressed: Set<String>): Boolean {
    val d = task.date ?: return false
    return task.originTaskId == null && suppressed.contains("T:${task.id}:${d.toEpochDay()}")
}