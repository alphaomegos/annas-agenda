package com.alphaomegos.annasagenda.support

import java.time.LocalDate
import java.time.YearMonth

/**
 * A month cell's height in whole pixels (25.10): what is left of [availablePx]
 * after the gaps between [rows] rows, shared out and rounded down — so the
 * rows and gaps, each laid out in whole pixels, never come to more than the
 * screen has.
 */
fun monthCellHeightPx(availablePx: Int, gapPx: Int, rows: Int): Int =
    ((availablePx - gapPx * (rows - 1)).coerceAtLeast(0)) / rows.coerceAtLeast(1)

/**
 * How a day's steps sit in its cell (25.10): beside the walking figure with
 * the first of [texts] that fits there, else under it with the first that
 * fits the cell's width — the last of them if none does. The figure is always
 * there: without it nobody could tell what the number was.
 */
fun stepsCellChoice(texts: List<String>, fitsBeside: (String) -> Boolean, fitsAlone: (String) -> Boolean): Pair<Boolean, String> {
    texts.firstOrNull(fitsBeside)?.let { return true to it }
    return false to (texts.firstOrNull(fitsAlone) ?: texts.last())
}

/**
 * The month's weeks whole (31.10, Eduard): the [leading] last days of the month
 * before ahead of the 1st, the month, and the next month's first days to the end of
 * its last week — the days around drawn paler, and opened like any other.
 */
fun monthGridDates(month: YearMonth, leading: Int): List<LocalDate> {
    val first = month.atDay(1).minusDays(leading.coerceAtLeast(0).toLong())
    val shown = leading.coerceAtLeast(0) + month.lengthOfMonth()
    return List((shown + 6) / 7 * 7) { first.plusDays(it.toLong()) }
}
