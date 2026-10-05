package com.alphaomegos.annasagenda.data

import com.alphaomegos.annasagenda.model.MetroScheme
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * The metro schemes that ship with the app (04.10): one JSON file per city
 * in assets/metro/, named for the key that picks it — "moscow.json" is
 * "library:moscow". A file holds one scheme in the very shape the saved state
 * writes one (MetroSchemeDto), so a scheme the user built can be taken out of
 * a backup and shipped as it is.
 *
 * A library scheme is read-only; editing it makes the user's own copy.
 */
internal const val METRO_LIBRARY_DIR = "metro"

/** A library file read, or null when it cannot be — a broken file is left out, not fatal. */
internal fun decodeMetroLibraryScheme(raw: String, key: String): MetroScheme? = runCatching {
    appStateStoreJson.decodeFromString<MetroSchemeDto>(raw).toDomain().copy(librarySource = key)
}.getOrNull()

/** One scheme as a library file holds it. */
internal fun encodeMetroLibraryScheme(scheme: MetroScheme): String =
    appStateStoreJson.encodeToString(scheme.copy(librarySource = null).toDto())
