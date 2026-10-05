package com.alphaomegos.annasagenda.app

import android.content.Context
import com.alphaomegos.annasagenda.model.TravelBaseCountry
import com.alphaomegos.annasagenda.support.TravelWorldMap
import com.alphaomegos.annasagenda.support.parseTravelBase
import com.alphaomegos.annasagenda.support.parseTravelWorldMap

/*
 * The travel section's shipped files (assets/travel/, see
 * tools/travel/README.md). Unreadable files give an empty base or an empty
 * map rather than a crash: the trips are the user's and are still there.
 */

private fun readAsset(context: Context, name: String): String? = runCatching {
    context.assets.open("travel/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
}.getOrNull()

internal fun loadTravelBase(context: Context): List<TravelBaseCountry> =
    readAsset(context, "countries.tsv")?.let { parseTravelBase(it).countries }.orEmpty()

internal fun loadTravelMap(context: Context): TravelWorldMap =
    readAsset(context, "world_map.txt")?.let { parseTravelWorldMap(it).map } ?: TravelWorldMap(1, 1, emptyMap())
