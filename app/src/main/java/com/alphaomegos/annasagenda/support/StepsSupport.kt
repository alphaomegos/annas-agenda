package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import java.time.LocalDate

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
