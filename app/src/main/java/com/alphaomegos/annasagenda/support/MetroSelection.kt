package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme

/**
 * Which scheme the metro screen uses: one of the user's, or one shipped in
 * the library (04.10, chosen in Settings).
 *
 * Kept in the state as text — "user:12", "library:moscow" — because the two
 * kinds are named differently: the user's schemes by id, the library's by
 * the name of their file, which stays the same from build to build.
 */
sealed interface MetroSelection {
    data class User(val schemeId: Long) : MetroSelection
    data class Library(val key: String) : MetroSelection
}

fun metroSelectionText(selection: MetroSelection): String = when (selection) {
    is MetroSelection.User -> "user:${selection.schemeId}"
    is MetroSelection.Library -> "library:${selection.key}"
}

/** Null for nothing chosen, and for text this build does not understand. */
fun metroSelectionOf(text: String?): MetroSelection? {
    if (text == null) return null
    val kind = text.substringBefore(':', missingDelimiterValue = "")
    val rest = text.substringAfter(':', missingDelimiterValue = "")
    return when (kind) {
        "user" -> rest.toLongOrNull()?.let { MetroSelection.User(it) }
        "library" -> rest.takeIf { it.isNotBlank() }?.let { MetroSelection.Library(it) }
        else -> null
    }
}

/**
 * The scheme to show. What was chosen, if it is still there; otherwise the
 * user's first scheme, then the library's first — a scheme deleted or a
 * library file gone from a later build does not leave the screen empty while
 * there is anything else to show.
 */
fun metroSchemeToShow(
    schemes: List<MetroScheme>,
    selection: String?,
    library: Map<String, MetroScheme>,
): MetroScheme? {
    val chosen = when (val s = metroSelectionOf(selection)) {
        is MetroSelection.User -> schemes.firstOrNull { it.id == s.schemeId }
        is MetroSelection.Library -> library[s.key]
        null -> null
    }
    return chosen ?: schemes.firstOrNull() ?: library.entries.sortedBy { it.key }.firstOrNull()?.value
}

/** An id for a new scheme of the user's: past all of theirs. */
fun metroNewSchemeId(schemes: List<MetroScheme>): Long = (schemes.maxOfOrNull { it.id } ?: 0L) + 1

/** [scheme] in place of the one with its id, or added at the end. */
fun metroSchemesAfterSaving(schemes: List<MetroScheme>, scheme: MetroScheme): List<MetroScheme> {
    val at = schemes.indexOfFirst { it.id == scheme.id }
    if (at < 0) return schemes + scheme
    if (schemes[at] == scheme) return schemes
    return schemes.toMutableList().apply { set(at, scheme) }
}

/**
 * The user's scheme gone, and the selection with it when it was the one
 * chosen: the selection does not point at nothing.
 */
fun metroSelectionAfterRemoving(selection: String?, schemeId: Long): String? =
    if (metroSelectionOf(selection) == MetroSelection.User(schemeId)) null else selection
