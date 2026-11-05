package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.SportWorkout
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * «Other» in «Sport» (05.11, Eduard): workouts of other sports written down on
 * any day — the kind from a list that puts the most used first, the minutes and
 * the kilocalories as typed. Their kilocalories go into the calorimeter's day
 * spending and the weight forecast, beside the steps and the runs.
 */

/**
 * The standard kinds' keys, in the order offered before anything has been used.
 * The keys are stored («std:snowboard»), so one is never renamed: a new kind is
 * added, and the name shown is a string resource in each language.
 */
val STANDARD_SPORT_KINDS: List<String> = listOf(
    "snowboard", "alpine_skiing", "cross_country_skiing", "skating",
    "zumba", "dance", "strength", "crossfit",
    "yoga", "pilates", "swimming", "cycling",
    "nordic_walking", "tennis", "football", "martial_arts",
    "climbing", "rowing",
)

/** What a standard kind's key is stored behind; the user's own words are stored as they are. */
const val SPORT_STANDARD_PREFIX = "std:"

fun standardSportKind(key: String): String = SPORT_STANDARD_PREFIX + key

/** A standard kind's key, or null for the user's own words. */
fun standardSportKey(kind: String): String? =
    if (kind.startsWith(SPORT_STANDARD_PREFIX)) kind.removePrefix(SPORT_STANDARD_PREFIX) else null

/**
 * A kind's name: a standard one's from [labels] — key to the name in the app's
 * language — the user's own as typed. A standard key this build does not know,
 * a newer build's, shows as the key rather than as nothing.
 */
fun sportKindLabel(kind: String, labels: Map<String, String>): String {
    val key = standardSportKey(kind) ?: return kind
    return labels[key] ?: key
}

/**
 * What was typed, as a kind: a standard kind when the words are its name in the
 * app's language, a kind used before when they are its words — in any case
 * either way, so «зумба» is the list's Zumba and «скалолазание» last week's
 * «Скалолазание» — and otherwise the words themselves, spaces tidied. Null
 * when nothing was typed.
 */
fun sportKindFromInput(text: String, labels: Map<String, String>, used: Collection<String> = emptyList()): String? {
    val words = text.trim().replace(Regex("\\s+"), " ")
    if (words.isEmpty()) return null
    labels.entries.firstOrNull { it.value.trim().equals(words, ignoreCase = true) }?.let { return standardSportKind(it.key) }
    used.firstOrNull { standardSportKey(it) == null && it.equals(words, ignoreCase = true) }?.let { return it }
    return words
}

/**
 * The kinds offered for [query]: every standard kind and every kind used, the
 * most used first (05.11, Eduard) — then the standard order, then the user's own
 * by name — narrowed to the names that contain the query, in any case.
 */
fun sportKindSuggestions(workouts: List<SportWorkout>, labels: Map<String, String>, query: String = ""): List<String> {
    val uses = workouts.groupingBy { it.kind }.eachCount()
    val standard = STANDARD_SPORT_KINDS.map(::standardSportKind)
    val others = uses.keys.filterNot { it in standard }.sortedBy { sportKindLabel(it, labels).lowercase() }
    val q = query.trim()
    return (standard + others)
        .sortedByDescending { uses[it] ?: 0 }
        .filter { q.isEmpty() || sportKindLabel(it, labels).contains(q, ignoreCase = true) }
}

/** The most kilocalories one workout takes: past it a typo, not a day. */
const val SPORT_KCAL_MAX = 20_000

/**
 * Kilocalories as typed: a whole number, 1 to [SPORT_KCAL_MAX]. Spaces do not
 * count; a separator before groups of three digits groups them («1 200»,
 * «1,200»), any other is a decimal point and rounds. Null for nothing, or for
 * nothing usable.
 */
fun parseSportKcal(raw: String): Int? {
    val clean = raw.filterNot { it.isWhitespace() }
    if (clean.isEmpty()) return null
    val grouped = Regex("\\d{1,3}([.,]\\d{3})+").matches(clean)
    val value = (if (grouped) clean.filter { it.isDigit() } else clean.replace(',', '.')).toDoubleOrNull() ?: return null
    if (value.isNaN() || value.isInfinite()) return null
    return value.roundToInt().takeIf { it in 1..SPORT_KCAL_MAX }
}

/** Minutes as the duration field takes them back: «45», «1:30». */
fun sportDurationInput(minutes: Int?): String = when {
    minutes == null -> ""
    minutes < 60 -> minutes.toString()
    else -> "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}

/**
 * A workout from the dialog's fields: null without a kind, or with neither
 * number. Each number is optional — a class with no watch on still lasted an
 * hour, and a watch's kilocalories are worth having without the time — but a
 * workout with neither says nothing. The minutes are typed like a run's: h:mm,
 * or minutes.
 */
fun sportWorkoutFromInput(id: Long, date: LocalDate, kind: String?, durationText: String, kcalText: String): SportWorkout? {
    val k = kind?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val minutes = parseRunningDurationToMinutes(durationText)?.takeIf { it > 0 }
    val kcal = parseSportKcal(kcalText)
    if (minutes == null && kcal == null) return null
    return SportWorkout(id = id, date = date, kind = k, durationMinutes = minutes, kcal = kcal)
}

/** The workouts with [workout] in: in place of the one with its id, or added; in date order, ties by id. */
fun sportWorkoutsAfterSaving(workouts: List<SportWorkout>, workout: SportWorkout): List<SportWorkout> =
    (workouts.filterNot { it.id == workout.id } + workout).sortedWith(compareBy({ it.date }, { it.id }))

/** Null when there was no such workout, so the caller can leave the state alone. */
fun sportWorkoutsAfterDeleting(workouts: List<SportWorkout>, id: Long): List<SportWorkout>? =
    if (workouts.none { it.id == id }) null else workouts.filterNot { it.id == id }

/** How the list is ordered, and which way first: the newest, the most and the longest first; kinds A to Z. */
enum class SportSort(val descendingFirst: Boolean) { DATE(true), KCAL(true), DURATION(true), KIND(false) }

/** The period over the list: all of it, the last 7 or 30 days, the last year, or one's own dates. */
enum class SportPeriod { ALL, DAYS_7, DAYS_30, YEAR, CUSTOM }

/**
 * The first and last day [period] shows on [today], null for no bound. The last
 * days have no end: a workout dated ahead by mistake is shown, to be found and
 * mended, rather than hidden.
 */
fun sportPeriodWindow(
    period: SportPeriod,
    today: LocalDate,
    customFrom: LocalDate? = null,
    customTo: LocalDate? = null,
): Pair<LocalDate?, LocalDate?> = when (period) {
    SportPeriod.ALL -> null to null
    SportPeriod.DAYS_7 -> today.minusDays(6) to null
    SportPeriod.DAYS_30 -> today.minusDays(29) to null
    SportPeriod.YEAR -> today.minusYears(1).plusDays(1) to null
    SportPeriod.CUSTOM -> customFrom to customTo
}

/**
 * The workouts the list shows: of [kind] when one is chosen, from [from] to [to]
 * where given, both included; in [sort] order, [descending] or not. A workout
 * without the number sorted by goes last either way — an unknown is not a
 * nought — and ties go newest first.
 */
fun sportWorkoutsShown(
    workouts: List<SportWorkout>,
    labels: Map<String, String>,
    kind: String? = null,
    from: LocalDate? = null,
    to: LocalDate? = null,
    sort: SportSort = SportSort.DATE,
    descending: Boolean = sort.descendingFirst,
): List<SportWorkout> {
    val picked = workouts.filter { w ->
        (kind == null || w.kind == kind) &&
            (from == null || !w.date.isBefore(from)) &&
            (to == null || !w.date.isAfter(to))
    }
    val newestFirst: Comparator<SportWorkout> = compareByDescending<SportWorkout> { it.date }.thenByDescending { it.id }
    return when (sort) {
        SportSort.DATE -> picked.sortedWith(if (descending) newestFirst else newestFirst.reversed())
        SportSort.KCAL -> sortedByNumber(picked, descending, newestFirst) { it.kcal }
        SportSort.DURATION -> sortedByNumber(picked, descending, newestFirst) { it.durationMinutes }
        SportSort.KIND -> {
            val byName = compareBy<SportWorkout> { sportKindLabel(it.kind, labels).lowercase() }
            picked.sortedWith((if (descending) byName.reversed() else byName).then(newestFirst))
        }
    }
}

private fun sortedByNumber(
    workouts: List<SportWorkout>,
    descending: Boolean,
    ties: Comparator<SportWorkout>,
    number: (SportWorkout) -> Int?,
): List<SportWorkout> {
    val (known, unknown) = workouts.partition { number(it) != null }
    val byValue = compareBy<SportWorkout> { number(it) }
    return known.sortedWith((if (descending) byValue.reversed() else byValue).then(ties)) + unknown.sortedWith(ties)
}

/** What the workouts shown add up to: how many, their minutes and their kilocalories, unknowns adding nothing. */
data class SportTotals(val workouts: Int, val minutes: Int, val kcal: Int)

fun sportTotals(workouts: List<SportWorkout>): SportTotals =
    SportTotals(workouts.size, workouts.sumOf { it.durationMinutes ?: 0 }, workouts.sumOf { it.kcal ?: 0 })

/** The kilocalories of [date]'s workouts as written down: what the day's spending and the forecast add. */
fun sportKcalOn(workouts: List<SportWorkout>, date: LocalDate): Int =
    workouts.filter { it.date == date }.sumOf { it.kcal ?: 0 }
