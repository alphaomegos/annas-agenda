package com.alphaomegos.annasagenda

import java.time.LocalDate

/**
 * A meal the user has eaten before, offered back while they type its name.
 *
 * [amount] and [kcal] are the portion they last wrote down and what they said
 * it was worth. The pair is the point: with both, a different portion can be
 * priced without asking them anything.
 */
data class FoodSuggestion(
    val name: String,
    val amount: Int?,
    val unit: String?,
    val kcal: Int,
    val timesEaten: Int,
    val lastEatenOn: LocalDate?,
)

/** Below this, a search matches most of the log and the list is noise. */
const val FOOD_SUGGESTION_MIN_LENGTH = 2

/**
 * What the user has eaten before that looks like what they are typing.
 *
 * Grouped by the name **with the portion taken out of it**, which is the whole
 * trick: "Помидоры, 500 г" and "Помидоры, 300 г" are one food eaten twice
 * rather than two foods eaten once, and the list stays as short as the
 * kitchen is.
 *
 * The portion offered is the one from the most recent time, together with the
 * kilocalories written down that time — so the pair is always internally
 * consistent, which is what [kcalForAmount] needs to be allowed to scale it.
 * Taking the portion from one entry and the calories from another would
 * produce a number that was never true.
 *
 * Ordering is the same shape as the task suggestions, and for the same
 * reasons: a name that **starts** with what has been typed beats one that
 * merely contains it, then eaten more often, then eaten more recently, then
 * alphabetically — the last one for determinism, so the list does not
 * reshuffle itself between launches.
 */
fun foodSuggestionsFor(
    typed: String,
    log: List<FoodEntry>,
    limit: Int = 5,
): List<FoodSuggestion> {
    val needle = typed.trim().lowercase()
    if (needle.length < FOOD_SUGGESTION_MIN_LENGTH) return emptyList()

    val parsed = log
        .filter { it.title.isNotBlank() }
        .map { it to parseFoodTitle(it.title) }
        .filter { (_, p) -> p.name.isNotBlank() }

    // Matched on the name without the portion. Typing "500" should not offer
    // every meal that happened to weigh five hundred grams.
    val matching = parsed.filter { (_, p) -> needle in p.name.lowercase() }
    if (matching.isEmpty()) return emptyList()

    return matching
        .groupBy { (_, p) -> p.name.lowercase() }
        .map { (_, group) ->
            val (newestEntry, newestParse) = group.maxWithOrNull(
                compareBy({ it.first.date }, { it.first.id })
            )!!

            FoodSuggestion(
                name = newestParse.name,
                amount = newestParse.amount,
                unit = newestParse.unit,
                kcal = newestEntry.kcal,
                timesEaten = group.size,
                lastEatenOn = group.maxOf { it.first.date },
            )
        }
        .sortedWith(
            compareByDescending<FoodSuggestion> { it.name.lowercase().startsWith(needle) }
                .thenByDescending { it.timesEaten }
                .thenByDescending { it.lastEatenOn }
                .thenBy { it.name.lowercase() }
        )
        .take(limit)
}

/**
 * What a different portion of the same food is worth.
 *
 * Straight proportion, rounded to a whole kilocalorie. Null when there is
 * nothing to scale from — a suggestion whose portion was never written down
 * has no rate, and inventing one would be worse than leaving the number alone
 * for the user to type.
 *
 * Deliberately not clamped to anything. Somebody who types a ten-kilo portion
 * has made a typo, and a chart showing thirty thousand kilocalories tells them
 * so; a number silently held down to a plausible one does not.
 */
fun kcalForAmount(suggestion: FoodSuggestion, amount: Int): Int? {
    val from = suggestion.amount ?: return null
    if (from <= 0 || amount <= 0) return null

    return Math.round(suggestion.kcal.toDouble() * amount / from).toInt()
}

/**
 * One food by its exact name, or null if it has never been eaten.
 *
 * Exists so that "the app is pricing this meal" can be remembered as a name
 * rather than as the suggestion itself. A name is a string, and a string
 * survives the phone being turned — the suggestion is looked up again
 * afterwards from the same log it came from.
 */
fun foodSuggestionForName(name: String, log: List<FoodEntry>): FoodSuggestion? =
    foodSuggestionsFor(name, log, limit = Int.MAX_VALUE)
        .firstOrNull { it.name.equals(name, ignoreCase = true) }
