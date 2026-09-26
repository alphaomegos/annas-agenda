package com.alphaomegos.annasagenda

/**
 * What the add-a-meal dialog is holding, and who owns the number.
 *
 * Two fields and one question between them: when the user changes the portion
 * in the name, should the kilocalories follow? Sometimes yes — that is the
 * whole feature. Sometimes no — they have just typed a number themselves, and
 * overwriting it would be the app arguing with them.
 *
 * [pricedFrom] is the answer. While it is set, the app owns the number and
 * re-prices it; once the user types in the kilocalorie field, it is theirs and
 * the app stops.
 */
data class FoodDraftState(
    val title: String,
    val kcalText: String,
    val pricedFrom: FoodSuggestion?,
)

/**
 * Tapping a suggestion fills both fields and takes ownership of the number.
 *
 * The portion goes back into the title the way it came out, so the user can
 * edit it in place — which is the gesture the re-pricing exists for.
 */
fun foodDraftAfterPickingSuggestion(suggestion: FoodSuggestion): FoodDraftState {
    val title =
        if (suggestion.amount != null && suggestion.unit != null) {
            foodTitleWithAmount(suggestion.name, suggestion.amount, suggestion.unit)
        } else {
            suggestion.name
        }

    return FoodDraftState(
        title = title,
        kcalText = suggestion.kcal.toString(),
        pricedFrom = suggestion,
    )
}

/**
 * The user edited the name — possibly only the portion inside it.
 *
 * Three cases.
 *
 * Still the same food, with a portion in it: the number is re-priced. Editing
 * 500 to 300 is what this is all for.
 *
 * Still the same food, with no readable portion: **the number is left alone
 * and the app keeps ownership.** This is the middle of deleting "500" one
 * digit at a time, and blanking the kilocalories on the way through, then
 * filling them back in, would be a flicker for every keystroke.
 *
 * A different food: the app lets go. Whatever it priced is no longer about
 * what is written there, so it stops touching the number — and the user is
 * anyway about to pick a different suggestion or type their own.
 */
fun foodDraftAfterTitleChange(state: FoodDraftState, newTitle: String): FoodDraftState {
    val priced = state.pricedFrom ?: return state.copy(title = newTitle)

    val parsed = parseFoodTitle(newTitle)
    if (!parsed.name.equals(priced.name, ignoreCase = true)) {
        return FoodDraftState(title = newTitle, kcalText = state.kcalText, pricedFrom = null)
    }

    val amount = parsed.amount
        ?: return state.copy(title = newTitle)

    val kcal = kcalForAmount(priced, amount)
        ?: return state.copy(title = newTitle)

    return state.copy(title = newTitle, kcalText = kcal.toString())
}

/**
 * The user typed a number themselves. It is theirs now.
 *
 * Irreversible within one meal on purpose: there is no way to tell "I have
 * taken over" from "I fixed a typo and would still like the app to help", and
 * of the two mistakes, silently overwriting a number somebody typed is much
 * the worse one. Picking a suggestion again hands it back.
 */
fun foodDraftAfterKcalTyped(state: FoodDraftState, typed: String): FoodDraftState =
    FoodDraftState(title = state.title, kcalText = typed, pricedFrom = null)
