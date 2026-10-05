package com.alphaomegos.annasagenda.app

import android.content.Context
import com.alphaomegos.annasagenda.data.METRO_LIBRARY_DIR
import com.alphaomegos.annasagenda.data.decodeMetroLibraryScheme
import com.alphaomegos.annasagenda.model.MetroScheme

/**
 * Every scheme in assets/metro/, by key. Files that cannot be read are left
 * out; no files at all is an empty library, which is what a build without
 * any is.
 */
internal fun loadMetroLibrary(context: Context): Map<String, MetroScheme> {
    val names = runCatching { context.assets.list(METRO_LIBRARY_DIR) }.getOrNull().orEmpty()
    return names
        .filter { it.endsWith(".json") }
        .sorted()
        .mapNotNull { name ->
            val key = name.removeSuffix(".json")
            val raw = runCatching {
                context.assets.open("$METRO_LIBRARY_DIR/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            decodeMetroLibraryScheme(raw, key)?.let { key to it }
        }
        .toMap()
}
