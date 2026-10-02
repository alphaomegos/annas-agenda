package com.alphaomegos.annasagenda

import java.time.LocalDate

/**
 * Below this many characters a search matches most of the history and the
 * list is noise. One number for tasks and food: the reason is the same, and
 * two constants with one justification drift apart the first time somebody
 * tunes one of them.
 */
const val SUGGESTION_MIN_LENGTH = 2

/**
 * What has been typed, as the suggestions compare it — trimmed and lowercase —
 * or null when it is too short to search with.
 */
fun suggestionNeedle(typed: String): String? =
    typed.trim().lowercase().takeIf { it.length >= SUGGESTION_MIN_LENGTH }

/**
 * The one ordering every list of suggestions uses.
 *
 * 1. Text that **starts** with the needle beats text that merely contains it.
 *    The user types from the beginning, so the beginning is what they mean —
 *    "поч" offers "Починить колесо" before "Не забыть починить колесо", even
 *    if the second was written more often.
 * 2. Used more often first.
 * 3. Used more recently first. No date at all sorts as older than any date,
 *    which is what "Someday" means for a task.
 * 4. Alphabetically, ignoring case. Not a preference, determinism: without it
 *    two equally-used entries swap places between launches and the list
 *    flickers.
 *
 * [needle] is expected as [suggestionNeedle] returns it. What is grouped and
 * how each suggestion is built stays with its own list — tasks and meals group
 * differently, and that difference is real. Only the order is shared.
 */
fun <T> suggestionOrder(
    needle: String,
    text: (T) -> String,
    timesUsed: (T) -> Int,
    lastUsedOn: (T) -> LocalDate?,
): Comparator<T> =
    compareByDescending<T> { text(it).lowercase().startsWith(needle) }
        .thenByDescending { timesUsed(it) }
        .thenByDescending { lastUsedOn(it) ?: LocalDate.MIN }
        .thenBy { text(it).lowercase() }
