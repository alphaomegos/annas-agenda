package com.alphaomegos.annasagenda

/**
 * Half-typed values, flattened for saved instance state.
 *
 * A dialog that collects several numbers keeps them as text until the moment
 * they are read, because half of "17." is not a number and the user is not
 * finished typing it. That text is what has to survive turning the phone, and
 * saved state travels in a Bundle, which will not take a Map of anything.
 *
 * Flattened to alternating id and value, which keeps the order the fields are
 * drawn in. Values pass through untouched — empty ones included, since "this
 * field was cleared" is a different answer from "this field was never shown".
 *
 * This is the half worth testing. Getting it wrong does not fail to compile:
 * it loses what the user typed, and only when they turn the phone.
 */
fun typedFieldsToSavedStrings(fields: Map<String, String>): List<String> =
    fields.flatMap { (id, text) -> listOf(id, text) }

fun typedFieldsFromSavedStrings(flat: List<String>): Map<String, String> =
    flat.chunked(2)
        .filter { it.size == 2 }
        .associate { (id, text) -> id to text }
