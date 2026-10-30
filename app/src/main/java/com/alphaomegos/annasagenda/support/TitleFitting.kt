package com.alphaomegos.annasagenda.support

/**
 * A title on a row of buttons (30.10): the first of [choices] — biggest first, then
 * smaller, then shorter — that [fits]; the last one when none does, to be cut with
 * an ellipsis rather than wrapped.
 */
fun <T> firstThatFits(choices: List<T>, fits: (T) -> Boolean): T {
    require(choices.isNotEmpty()) { "nothing to choose from" }
    return choices.firstOrNull(fits) ?: choices.last()
}
