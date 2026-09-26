package com.alphaomegos.annasagenda

/**
 * A meal's name with its portion pulled out of it.
 *
 * [amount] is in grams, or in millilitres for something poured — the two are
 * deliberately one number. Nothing downstream does arithmetic that could tell
 * them apart: a portion is only ever compared with another portion of the same
 * food, and the food already knows whether it is eaten or drunk.
 *
 * [unit] is how to write that number back: the short form of whatever the user
 * typed, in the script they typed it in. Somebody who writes "кг" gets "г"
 * back rather than "g", and somebody who writes "ml" gets "ml".
 */
data class ParsedFoodTitle(
    val name: String,
    val amount: Int?,
    val unit: String?,
)

/**
 * A unit as it can be written, what one of it is worth, and what to write back.
 *
 * Ordered longest-first because the alternation is tried in order: "г" is a
 * prefix of "гр", so matching the short one first would leave a stray "р" in
 * the name.
 */
private data class FoodUnit(val written: String, val grams: Int, val short: String)

private val foodUnits = listOf(
    FoodUnit("граммов", 1, "г"),
    FoodUnit("грамма", 1, "г"),
    FoodUnit("грамм", 1, "г"),
    FoodUnit("мл", 1, "мл"),
    FoodUnit("кг", 1000, "г"),
    FoodUnit("гр", 1, "г"),
    FoodUnit("ml", 1, "ml"),
    FoodUnit("kg", 1000, "g"),
    FoodUnit("л", 1000, "мл"),
    FoodUnit("г", 1, "г"),
    FoodUnit("g", 1, "g"),
    FoodUnit("l", 1000, "ml"),
)

private val separators = charArrayOf(',', ';', '-', '–', '—', '·')

private val amountPattern = Regex(
    """(\d+(?:[.,]\d+)?)\s*(""" +
        foodUnits.joinToString("|") { Regex.escape(it.written) } +
        """)(?![\p{L}\d])""",
    RegexOption.IGNORE_CASE,
)

/**
 * Reads "Помидоры, 500 г" as tomatoes and five hundred grams.
 *
 * The portion lives **inside the name** rather than in a field of its own, and
 * that is on purpose. A field would mean every meal already in the history has
 * no portion until it is typed again, and there are hundreds of them; parsing
 * means the suggestions work on the first day, on data written long before
 * anybody thought of this. It also means the user goes on typing what they
 * already type, and nothing about the saved format changes.
 *
 * The **last** amount in the title wins. "2 яйца, 100 г" is a hundred grams of
 * two eggs, not two grams of anything — and people write the portion at the
 * end.
 *
 * A number with no unit after it is not a portion. That is the rule keeping
 * "Борщ 2" and "Кофе №3" out of the arithmetic, and it is why the unit list is
 * a list rather than "any letters".
 *
 * Kilograms and litres are a thousand. A comma is a decimal point, because
 * that is how the person typing this writes numbers. The result is rounded to
 * a whole gram; a tenth of a gram is not a portion either.
 *
 * Whatever is left gets its dangling punctuation cleaned up, so "Помидоры,
 * 500 г" leaves "Помидоры" and not "Помидоры,". A title that is *only* a
 * portion keeps its original wording as the name, because a meal with no name
 * is worse than a meal named "500 г".
 */
fun parseFoodTitle(title: String): ParsedFoodTitle {
    val trimmed = title.trim()

    // The name is cleaned of dangling separators even when no portion is
    // found, so that "Борщ," and "Борщ" are the same food to everything
    // downstream -- and so that a half-deleted portion, "Помидоры, ", still
    // reads as the food it is still about.
    val plain = ParsedFoodTitle(
        name = trimmed.trim(*separators).trim(),
        amount = null,
        unit = null,
    )

    val match = amountPattern.findAll(trimmed).lastOrNull() ?: return plain
    val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return plain

    val written = match.groupValues[2].lowercase()
    val unit = foodUnits.first { it.written == written }

    val amount = Math.round(number * unit.grams).toInt()
    if (amount <= 0) return plain

    val name = joinAround(
        before = trimmed.take(match.range.first),
        after = trimmed.substring(match.range.last + 1),
    )

    return if (name.isBlank()) {
        ParsedFoodTitle(name = trimmed, amount = amount, unit = unit.short)
    } else {
        ParsedFoodTitle(name = name, amount = amount, unit = unit.short)
    }
}

/**
 * Closes the hole the portion left behind.
 *
 * The awkward one is a portion in the middle: "Творог 200 г, 5%" must come out
 * as "Творог, 5%" and not as "Творог , 5%" with an orphaned space, nor as
 * "Творог 5%" with the comma the user typed thrown away. So when what follows
 * begins with a separator, that separator is the join; otherwise the two
 * halves get a space between them.
 */
private fun joinAround(before: String, after: String): String {
    val head = before.trimEnd()
    val tail = after.trimStart()

    val joined = when {
        head.isEmpty() -> tail
        tail.isEmpty() -> head
        tail.first() in separators -> head.trimEnd(*separators).trimEnd() + tail
        else -> "$head $tail"
    }

    return joined.trim().trim(*separators).trim()
}

/** The same meal written out again with a different portion. */
fun foodTitleWithAmount(name: String, amount: Int, unit: String): String =
    "${name.trim()}, $amount $unit"
