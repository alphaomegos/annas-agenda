package com.alphaomegos.annasagenda.model

import java.time.LocalDate

/**
 * A workout of another sport (05.11, Eduard): the «Other» tab of «Sport» — a
 * snowboard day, a zumba class, an hour in the gym.
 *
 * [kind] is a standard kind's key, «std:snowboard», or the user's own words as
 * typed. A key rather than a name for the standard ones: they are shown in the
 * app's language, and a snowboard day written down in Russian is a snowboard
 * day in English too.
 *
 * Both numbers are what the user typed — the minutes, and the kilocalories a
 * watch or a machine said. Null is «not written down», not nought, and neither
 * is worked out from the other: an hour of yoga and an hour of crossfit are
 * very different hours.
 */
data class SportWorkout(
    val id: Long,
    val date: LocalDate,
    val kind: String,
    val durationMinutes: Int? = null,
    val kcal: Int? = null,
)
