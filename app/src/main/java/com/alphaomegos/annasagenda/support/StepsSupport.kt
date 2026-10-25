package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Steps (19.10): the days kept, what a read from Health Connect changes in
 * them, and what the app works out from them — the distance and the energy.
 */

/** How many days back each read from Health Connect looks: as far as it shows a newly let-in app. */
const val STEPS_READ_DAYS = 30

/** One day per date, in date order; a typed-in day wins over a read one, nothing negative. */
fun normalizedStepDays(days: List<StepDay>): List<StepDay> =
    days.filter { it.steps >= 0 }
        .groupBy { it.date }
        .map { (_, same) -> same.lastOrNull { it.source == StepSource.MANUAL } ?: same.last() }
        .sortedBy { it.date }

/**
 * The days after a read from Health Connect: [read] is the total of each day
 * read, by date.
 *
 * A day the user typed in stays as typed. Any other day takes what was read
 * — the watch sends its steps to the phone in its own time, so yesterday can
 * still grow today. A day read as nothing is not kept, unless it was kept
 * before from a read: then Health Connect has lost it and so does the app.
 */
fun stepDaysAfterRead(days: List<StepDay>, read: Map<LocalDate, Int>): List<StepDay> {
    val byDate = normalizedStepDays(days).associateBy { it.date }.toMutableMap()
    read.forEach { (date, steps) ->
        val kept = byDate[date]
        when {
            kept?.source == StepSource.MANUAL -> Unit
            steps > 0 -> byDate[date] = StepDay(date, steps, StepSource.HEALTH_CONNECT)
            kept?.source == StepSource.HEALTH_CONNECT -> byDate.remove(date)
        }
    }
    return byDate.values.sortedBy { it.date }
}

/** The days with [date] typed in as [steps] (Eduard: any day can be written over by hand). */
fun stepDaysWithTyped(days: List<StepDay>, date: LocalDate, steps: Int): List<StepDay> =
    normalizedStepDays(days.filter { it.date != date } + StepDay(date, steps.coerceAtLeast(0), StepSource.MANUAL))

/** The days with [date]'s typed-in count taken back: the next read brings the day back from Health Connect. */
fun stepDaysWithoutTyped(days: List<StepDay>, date: LocalDate): List<StepDay> =
    normalizedStepDays(days.filterNot { it.date == date && it.source == StepSource.MANUAL })

/** The days of [window], newest first, as the list under the chart shows them. */
fun stepDaysIn(days: List<StepDay>, window: DateWindow?): List<StepDay> =
    days.filter { window == null || it.date in window.from..window.to }.sortedByDescending { it.date }

/**
 * The three best days of all, best first — gold, silver, bronze. Of two days
 * with the same count the earlier holds the place: it got there first.
 */
fun stepRecords(days: List<StepDay>): List<StepDay> =
    days.filter { it.steps > 0 }
        .sortedWith(compareByDescending<StepDay> { it.steps }.thenBy { it.date })
        .take(3)

/** 1, 2 or 3 for a record day — its medal — and null for any other. */
fun stepMedal(day: StepDay, records: List<StepDay>): Int? =
    records.indexOfFirst { it.date == day.date }.takeIf { it >= 0 }?.plus(1)

/** A height the app will take, in centimetres. */
val STEPS_HEIGHT_RANGE_CM = 100..250

/**
 * A step's length from the walker's height: 0.414 of it, the rule pedometers
 * use (0.415 for men, 0.413 for women — too close to ask anyone which).
 */
const val STRIDE_PER_HEIGHT = 0.414

/** The distance [steps] cover, in kilometres, for someone [heightCm] tall. */
fun stepsDistanceKm(steps: Int, heightCm: Int): Double = steps * heightCm * STRIDE_PER_HEIGHT / 100.0 / 1000.0

/**
 * The energy walking took beyond what the body spends anyway, per kilogram
 * per kilometre: half a kilocalorie, from the ACSM walking equation (0.1 ml
 * of oxygen per kilogram per metre, about 5 kcal a litre).
 */
const val WALKING_KCAL_PER_KG_KM = 0.5

/** The kilocalories [steps] burned, for someone [heightCm] tall weighing [weightKg]. */
fun stepsKcal(steps: Int, heightCm: Int, weightKg: Double): Double =
    WALKING_KCAL_PER_KG_KM * weightKg * stepsDistanceKm(steps, heightCm)

/**
 * The weight to count a day's walking with: the last one measured on or
 * before it, and for a day before the first measurement, the first one — the
 * nearest thing known. Null when no weight was ever measured.
 */
fun weightOn(date: LocalDate, anthropometry: List<AnthropometryEntry>): Double? {
    val weighed = anthropometry.filter { it.weightKg != null }.sortedBy { it.date }
    return (weighed.lastOrNull { !it.date.isAfter(date) } ?: weighed.firstOrNull())?.weightKg
}

/**
 * The stretch of days the Steps tab shows for [range]: as the anthropometry
 * chart's, except «all», which here runs from the first day with steps — or
 * just today, before there are any.
 */
fun stepsWindowFor(range: AnthropometryRange, today: LocalDate, custom: DateWindow?, days: List<StepDay>): DateWindow =
    if (range == AnthropometryRange.ALL) {
        DateWindow(days.minOfOrNull { it.date }?.takeIf { it.isBefore(today) } ?: today, today)
    } else {
        anthropometryWindowFor(range, today, custom)
    }

/**
 * Every day of [window], newest first, never past [today]: the list under the
 * chart shows the empty days too, so that any of them can be written in by
 * hand (Eduard, 19.10).
 */
fun stepsListDays(window: DateWindow, today: LocalDate): List<LocalDate> {
    val last = if (window.to.isAfter(today)) today else window.to
    if (last.isBefore(window.from)) return emptyList()
    return generateSequence(last) { it.minusDays(1) }.takeWhile { !it.isBefore(window.from) }.toList()
}

/**
 * What one read from Health Connect found (19.10), for the line under the
 * chart: when, how many of the days read had steps, from which apps — or, if
 * it failed, what stopped it. A read that finds nothing must not look like a
 * read that failed, nor like one that never happened.
 */
data class StepsReadReport(
    val at: LocalDateTime,
    val daysRead: Int,
    val daysWithSteps: Int,
    val sources: List<String>,
    val error: String? = null,
)

fun stepsReadReport(at: LocalDateTime, days: Map<LocalDate, Int>, sources: Set<String>): StepsReadReport =
    StepsReadReport(at, days.size, days.values.count { it > 0 }, sources.sorted())

/** The apps steps come from, as the user knows them. */
enum class StepsSourceKind { SAMSUNG_HEALTH, THIS_PHONE, FITBIT, GOOGLE_FIT, OTHER }

/**
 * Which app a Health Connect source is. The phone's own steps come as
 * «android» and, from Health Connect's June 2026 update, under a made-up
 * package name of its own (com.android.healthconnect.phone.…).
 */
fun stepsSourceKind(packageName: String): StepsSourceKind = when {
    packageName == "com.sec.android.app.shealth" -> StepsSourceKind.SAMSUNG_HEALTH
    packageName == "android" || packageName.startsWith("com.android.healthconnect.phone") -> StepsSourceKind.THIS_PHONE
    packageName == "com.fitbit.FitbitMobile" -> StepsSourceKind.FITBIT
    packageName == "com.google.android.apps.fitness" -> StepsSourceKind.GOOGLE_FIT
    else -> StepsSourceKind.OTHER
}

/** A count in thousands to one decimal, for the month's narrow cells: 12 345 is 12.3. */
fun stepsThousands(steps: Int): Double = Math.round(steps / 100.0) / 10.0

/**
 * The page of days the Steps tab shows (19.10, Eduard: «выбран месяц, под
 * графиком "октябрь 2026", листаешь влево — "сентябрь 2026"»): the calendar's
 * own periods, not the last so many days — the week from Monday, the month,
 * the quarter, the year — [page] 0 being the one with [today] in it, −1 the
 * one before, and so on back. «All» has no pages; one's own range pages by its
 * own length, so its pages meet with no day missing or shown twice.
 */
fun stepsPage(range: AnthropometryRange, today: LocalDate, custom: DateWindow?, days: List<StepDay>, page: Int): DateWindow {
    val back = minOf(page, 0).toLong()
    return when (range) {
        AnthropometryRange.ALL -> stepsWindowFor(range, today, custom, days)
        AnthropometryRange.WEEK -> {
            val monday = today.with(java.time.DayOfWeek.MONDAY).plusWeeks(back)
            DateWindow(monday, monday.plusDays(6))
        }
        AnthropometryRange.MONTH -> {
            val first = today.withDayOfMonth(1).plusMonths(back)
            DateWindow(first, first.plusMonths(1).minusDays(1))
        }
        AnthropometryRange.QUARTER -> {
            val first = today.withDayOfMonth(1).withMonth((today.monthValue - 1) / 3 * 3 + 1).plusMonths(3 * back)
            DateWindow(first, first.plusMonths(3).minusDays(1))
        }
        AnthropometryRange.YEAR -> {
            val first = today.withDayOfYear(1).plusYears(back)
            DateWindow(first, first.plusYears(1).minusDays(1))
        }
        AnthropometryRange.CUSTOM -> {
            val own = custom ?: DateWindow(today.minusDays(29), today)
            val length = java.time.temporal.ChronoUnit.DAYS.between(own.from, own.to) + 1
            DateWindow(own.from.plusDays(back * length), own.to.plusDays(back * length))
        }
    }
}

/** Back is worth a page while there are steps before it; forward, up to the page with today. */
fun canPageStepsBack(range: AnthropometryRange, window: DateWindow, days: List<StepDay>): Boolean =
    range != AnthropometryRange.ALL && days.any { it.date < window.from }

fun canPageStepsForward(range: AnthropometryRange, page: Int): Boolean =
    range != AnthropometryRange.ALL && page < 0

/** Every day of [window], oldest first: the chart's days, the ones still to come included, empty. */
fun stepsChartDays(window: DateWindow): List<LocalDate> =
    generateSequence(window.from) { it.plusDays(1) }.takeWhile { !it.isAfter(window.to) }.toList()

/** The quarter a page is, 1 to 4, for its title. */
fun stepsQuarterOf(window: DateWindow): Int = (window.from.monthValue - 1) / 3 + 1

/**
 * The top of the chart's scale for a tallest day of [top] steps: the next
 * round number — 1, 2 or 5 times a power of ten — so the lines across read
 * 0, a half, and a round top (19 002 gets 20 000; 8 304, 10 000).
 */
fun niceStepsCeiling(top: Int): Int {
    if (top <= 0) return 0
    var unit = 1L
    while (unit * 10 <= top) unit *= 10
    val ceiling = listOf(1L, 2L, 5L, 10L).map { it * unit }.first { it >= top }
    return ceiling.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
