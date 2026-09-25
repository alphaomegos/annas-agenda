package com.alphaomegos.annasagenda

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/** Years, months or days, as a countdown says them. */
enum class RemainingUnit {
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
