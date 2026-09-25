package com.alphaomegos.annasagenda

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A date as this app writes dates, in the language the app is set to.
 *
 * The locale is a parameter rather than `Locale.getDefault()`, for the same
 * reason the numbers name theirs: the app has its own language setting and the
 * phone has another, and only one of those is the user's answer about this
 * app.
 *
 * Two lengths, because a chart axis and a dialog heading have different room.
 * Neither is a hand-written pattern: "d MMMM yyyy" is right in one language
 * and wrong in the next, and getting that right across four of them is exactly
 * what [FormatStyle] already knows.
 *
 * Here rather than beside the other formatters because that file holds a
 * composable, which puts it out of reach of a test that runs from a terminal.
 */
fun formatMediumDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

fun formatShortDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(date)
