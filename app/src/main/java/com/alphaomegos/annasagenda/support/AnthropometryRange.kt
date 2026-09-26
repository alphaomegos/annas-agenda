package com.alphaomegos.annasagenda

import java.time.LocalDate

/**
 * How much of the history the chart is showing.
 *
 * Stored nowhere and sent nowhere, so the names are free to change — unlike
 * every other enum in this project, which is written into the saved state by
 * name and read back with `valueOf`.
 */
enum class AnthropometryRange {
    WEEK,
    MONTH,
    QUARTER,
    YEAR,
    ALL,
    CUSTOM,
}

/** Two dates, both ends included. */
data class DateWindow(val from: LocalDate, val to: LocalDate)

/**
 * The stretch of days a range means today.
 *
 * Every named range **ends today and counts backwards**, rather than meaning
 * the calendar week or the calendar month. The question this screen answers is
 * "how have I been doing lately", and on the 2nd of a month a calendar month
 * answers it with two days. So a week is the last seven days with today as the
 * seventh, a month is the same day last month plus one, and so on.
 *
 * [ALL] is the only range that does not know its own length: it runs from the
 * first measurement to the last, and with no measurements at all it collapses
 * onto today, which draws an empty chart rather than a chart of nothing from
 * the year 1970 to the year 1970.
 *
 * [CUSTOM] takes the two dates the user picked, in whichever order they picked
 * them — a date picker will happily hand back an end before a start, and the
 * chart should draw the range they obviously meant instead of nothing at all.
 * With no dates picked yet it falls back to a month, because a chart has to
 * show something while the dialog is still open.
 */
fun anthropometryWindowFor(
    range: AnthropometryRange,
    today: LocalDate,
    custom: DateWindow? = null,
    entries: List<AnthropometryEntry> = emptyList(),
): DateWindow = when (range) {
    AnthropometryRange.WEEK -> DateWindow(today.minusDays(6), today)
    AnthropometryRange.MONTH -> DateWindow(today.minusMonths(1).plusDays(1), today)
    AnthropometryRange.QUARTER -> DateWindow(today.minusMonths(3).plusDays(1), today)
    AnthropometryRange.YEAR -> DateWindow(today.minusYears(1).plusDays(1), today)

    AnthropometryRange.ALL -> {
        val dates = entries.map { it.date }
        DateWindow(dates.minOrNull() ?: today, dates.maxOrNull() ?: today)
    }

    AnthropometryRange.CUSTOM ->
        custom?.let { DateWindow(minOf(it.from, it.to), maxOf(it.from, it.to)) }
            ?: DateWindow(today.minusMonths(1).plusDays(1), today)
}

/**
 * The measurements inside a window, both ends included, in date order.
 *
 * Inclusive on purpose and tested for it: a range ending today that leaves out
 * today would hide the measurement the user has just typed in, which is the
 * one they opened the screen to look at.
 */
fun anthropometryEntriesIn(
    entries: List<AnthropometryEntry>,
    window: DateWindow,
): List<AnthropometryEntry> =
    entries
        .filter { it.date >= window.from && it.date <= window.to }
        .sortedBy { it.date }
