package com.alphaomegos.annasagenda

import java.time.LocalDate
import java.time.Month

/**
 * The twenty-ninth of July.
 *
 * Anna is the reason this app exists, and once a year the main menu says so.
 *
 * It is a whole function rather than two comparisons at the call site for the
 * ordinary reason: the date is the thing that can be wrong, and a wrong date
 * here is invisible for a year. `month` is compared as [Month] rather than as
 * a number because `monthValue` and a zero-based month look identical in a
 * diff and differ by one in a calendar — the mistake the Java date APIs spent
 * two decades teaching people to make.
 *
 * Year-agnostic on purpose. This is not an anniversary that counts upwards; it
 * is a day that comes round.
 */
fun isAnnaDay(date: LocalDate): Boolean =
    date.month == Month.JULY && date.dayOfMonth == 29
