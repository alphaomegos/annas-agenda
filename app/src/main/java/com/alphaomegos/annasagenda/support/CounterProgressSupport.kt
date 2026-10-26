package com.alphaomegos.annasagenda.support

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/** Years, months or days, as a countdown says them. */
enum class RemainingUnit {
    WEEKS,
    YEARS,
    MONTHS,
    DAYS,
}

/** One term of a countdown: "2 years", "3 days". */
data class RemainingPart(
    val unit: RemainingUnit,
    val amount: Int,
)

/**
 * How long is left until [end], broken into the terms a countdown reads out.
 *
 * Terms that are zero are left out, so 1 year and 5 days is two terms rather
 * than three — but a countdown that has run out still says something, because
 * a card showing nothing at all reads as broken rather than as finished.
 *
 * The months are calendar months, not thirty-day blocks: [Period] does the
 * arithmetic, so "one month" from the 31st of January lands on the 28th of
 * February and the leftover days are counted from there.
 *
 * The words are not here. The screen turns each term into a phrase through
 * the plural resources, which is the whole reason this returns numbers and
 * units instead of a string: the string used to be built here, with "y", "m"
 * and "d" written into it, so a Russian card counted down in English.
 */
fun remainingUntil(today: LocalDate, end: LocalDate): List<RemainingPart> {
    if (!today.isBefore(end)) return listOf(RemainingPart(RemainingUnit.DAYS, 0))

    val period = Period.between(today, end)

    val parts = buildList {
        if (period.years != 0) add(RemainingPart(RemainingUnit.YEARS, period.years))
        if (period.months != 0) add(RemainingPart(RemainingUnit.MONTHS, period.months))
        if (period.days != 0) add(RemainingPart(RemainingUnit.DAYS, period.days))
    }

    // A day apart is a day; there is no way through here with nothing to say.
    // Kept so that a future change to the arithmetic cannot produce a blank
    // card without anybody noticing.
    return parts.ifEmpty { listOf(RemainingPart(RemainingUnit.DAYS, 0)) }
}

/**
 * How much of the range is still ahead, from 0 to 1, for the progress bar.
 *
 * A range that is empty or backwards counts as one day long rather than as
 * zero, because the alternative is a division by zero on a card the user can
 * create from the counter editor.
 */
fun remainingFractionOf(start: LocalDate, end: LocalDate, today: LocalDate): Double {
    val totalDays = maxOf(1L, ChronoUnit.DAYS.between(start, end))
    val remainingDays = ChronoUnit.DAYS.between(today, end).coerceIn(0L, totalDays)

    return (remainingDays.toDouble() / totalDays.toDouble()).coerceIn(0.0, 1.0)
}

/** A day counter's number (26.10): the days since its start, the start day being 1, plus the correction. */
fun dayTimerValue(counter: DayTimerCounter, today: LocalDate): Int {
    val days = if (today.isBefore(counter.startDate)) 0L else java.time.temporal.ChronoUnit.DAYS.between(counter.startDate, today) + 1
    return (days + counter.correction).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/**
 * How long ago a day counter started, as the calendar has it: years, months,
 * weeks and days, the empty ones left out (26.10: «Прошло 2 месяца 1 неделя
 * 2 дня»); the correction does not touch it. Nothing yet on its first day.
 */
fun dayTimerElapsedParts(start: LocalDate, today: LocalDate): List<RemainingPart> {
    if (!today.isAfter(start)) return emptyList()
    val period = java.time.Period.between(start, today)
    return buildList {
        if (period.years != 0) add(RemainingPart(RemainingUnit.YEARS, period.years))
        if (period.months != 0) add(RemainingPart(RemainingUnit.MONTHS, period.months))
        if (period.days / 7 != 0) add(RemainingPart(RemainingUnit.WEEKS, period.days / 7))
        if (period.days % 7 != 0) add(RemainingPart(RemainingUnit.DAYS, period.days % 7))
    }
}

/** The counters with one day counter's correction moved by [delta]. */
fun countersWithDayTimerCorrection(counters: List<Counter>, counterId: Long, delta: Int): List<Counter> =
    counters.map { c -> if (c is DayTimerCounter && c.id == counterId) c.copy(correction = c.correction + delta) else c }

/** The counters with one day counter started over: from [today], no correction. */
fun countersWithDayTimerRestarted(counters: List<Counter>, counterId: Long, today: LocalDate): List<Counter> =
    counters.map { c -> if (c is DayTimerCounter && c.id == counterId) c.copy(startDate = today, correction = 0) else c }
