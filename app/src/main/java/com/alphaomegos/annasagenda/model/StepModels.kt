package com.alphaomegos.annasagenda.model

import java.time.LocalDate

/**
 * Where a day's step count came from (19.10). Stored by name.
 *
 * The app keeps its own copy of every day (Eduard: «сохраняем всё в наше
 * приложение»): Health Connect shows an app thirty days back from the moment
 * it was let in, so the history beyond that lives here, and goes into the
 * backups with everything else.
 */
enum class StepSource {
    /** Read from Health Connect: Samsung Health (the phone and the watch), the phone itself, any app writing steps there. */
    HEALTH_CONNECT,

    /** Typed in by the user. Nothing read later replaces it; only the user does. */
    MANUAL,

    /** Brought in from a Samsung Health export — kept for the import planned next (19.10). */
    SAMSUNG_IMPORT,
}

/** The steps of one day, by the phone's own calendar. */
data class StepDay(
    val date: LocalDate,
    val steps: Int,
    val source: StepSource,
)
