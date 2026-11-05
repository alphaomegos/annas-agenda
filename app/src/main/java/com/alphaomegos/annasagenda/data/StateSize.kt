package com.alphaomegos.annasagenda.data

import kotlinx.serialization.json.jsonObject

/**
 * How big the stored state is, and what weighs in it (05.11, the internals
 * review, point 7): the payload in UTF-8 bytes, and the top-level parts that
 * weigh most, heaviest first. Written once a start into the log — sizes only,
 * never content — so that whether keeping the state in parts would pay is
 * told by the user's own data before anything is changed.
 */
internal data class StateSize(val total: Int, val largest: List<Pair<String, Int>>)

internal fun stateSizeOf(raw: String, top: Int = 8): StateSize {
    val json = appStateStoreJson.parseToJsonElement(raw).jsonObject
    val parts = json.entries
        .map { (key, value) -> key to value.toString().toByteArray(Charsets.UTF_8).size }
        .sortedByDescending { it.second }
    return StateSize(total = raw.toByteArray(Charsets.UTF_8).size, largest = parts.take(top))
}

/** One line for the log: «state 812 KB; foodLog 301 KB, tasks 220 KB, …» — names and sizes. */
internal fun StateSize.logLine(): String =
    "state ${sizeText(total)}; " + largest.joinToString { (key, size) -> "$key ${sizeText(size)}" }

private fun sizeText(bytes: Int): String = if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"
