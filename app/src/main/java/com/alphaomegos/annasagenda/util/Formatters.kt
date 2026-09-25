package com.alphaomegos.annasagenda.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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

/**
 * Two decimals, for the projected kilograms on the calorimeter and the
 * anthropometry screen.
 *
 * The locale is named for the same reason it is named above: this decides how
 * the number reads, and "whatever the machine is set to" is not a decision
 * anybody made. The two screens showed the same quantity two different ways
 * before this existed — one pinned to a dot, the other following the app
 * language — which is the one outcome that is wrong whichever answer is
 * right.
 */
internal fun formatTwoDecimals(v: Double): String =
    String.format(Locale.US, "%.2f", v)

internal fun formatSignedOneDecimal(v: Double): String {
    val r = (v * 10.0).roundToInt() / 10.0
    val sign = if (r > 0) "+" else ""
    val s = String.format(Locale.US, "%.1f", r)
    val trimmed = if (s.endsWith(".0")) s.dropLast(2) else s
    return sign + trimmed
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
