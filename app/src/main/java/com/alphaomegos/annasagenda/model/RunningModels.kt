package com.alphaomegos.annasagenda

import java.time.LocalDate

/** Planned/actual running entry for a specific date. Values are stored as text to keep UX stable. */
data class RunningPlanEntry(
    val date: LocalDate,
    val distanceKmText: String = "",
    /** User-entered time as 4 digits: HHMM (e.g., "0123" -> displayed as 01:23). */
    val durationHhMmText: String = "",
    /** User-entered pace as 4 digits: MMSS (e.g., "0915" -> displayed as 09'15"). */
    val paceText: String = "",
    val taskId: Long? = null,
    val isBonus: Boolean = false,
)
/**
 * Which of the two things the running screen is doing.
 *
 * Written into the saved state by name, like every other enum here, so
 * reordering these cannot silently change what a payload means.
 */
enum class RunningMode {
    /** Building up to a race: days are planned ahead and become tasks. */
    PLAN,

    /** Between races: nothing is planned, runs are written down after. */
    BETWEEN,
}

/**
 * A run that actually happened.
 *
 * Deliberately numbers rather than the plan row's four-digit text fields. A
 * planned day is something half-typed that has to survive being half-typed;
 * a run that happened is a fact, parsed once on the way in. Everything
 * downstream — the pace, the calories, the week's total — does arithmetic,
 * and re-parsing "0915" at each of those places is how two of them end up
 * disagreeing.
 *
 * [durationMinutes] rather than seconds because that is the precision anybody
 * types, and [distanceKm] as a double because half-kilometres are normal.
 */
data class RunningWorkout(
    val id: Long,
    val date: LocalDate,
    val distanceKm: Double,
    val durationMinutes: Int,
    val note: String = "",
)
