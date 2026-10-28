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

/** How the Steps tab draws its days (19.10): a bar a day, or a line through them, as anthropometry does. */
enum class StepsChartStyle { BARS, LINE }

/**
 * A friend whose steps the app may show (25.10, «Steps together» through
 * oursidecar): the server's id for them, the name they go by there, whether
 * they share at the moment, and their days — the last thirty, at most.
 */
data class StepFriend(
    val id: String,
    val name: String,
    val sharing: Boolean,
    val days: Map<LocalDate, Int>,
)

/** An invitation to share steps, to this account ([incoming]) or from it. */
data class StepInvitation(
    val id: String,
    val name: String,
    val incoming: Boolean,
    val createdAt: String,
)

/** Sharing as the server has it for this account: on or off, and the name friends see. */
data class StepSharing(
    val enabled: Boolean,
    val displayName: String,
    val accountName: String,
    val customName: Boolean,
)

/** The steps widget's second line (25.10): yesterday, the week's average, a bar towards the day's goal, or nothing. */
enum class StepsWidgetLine { YESTERDAY, WEEK_AVERAGE, GOAL_BAR, NONE }

/**
 * The user's sex, as the energy formulas ask (28.10, the profile): one of the
 * two the Mifflin–St Jeor equation knows; unset until the user says.
 */
enum class ProfileSex { FEMALE, MALE }
