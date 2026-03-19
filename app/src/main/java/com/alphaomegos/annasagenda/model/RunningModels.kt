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
)