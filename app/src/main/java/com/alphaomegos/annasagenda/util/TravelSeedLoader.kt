package com.alphaomegos.annasagenda.util

import android.content.Context
import androidx.annotation.RawRes
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.TravelContinent
import com.alphaomegos.annasagenda.TravelCountrySeed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val travelSeedJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
private data class TravelCountrySeedJson(
    val countryId: String,
    val names: Map<String, String> = emptyMap(),
    val defaultContinent: String,
    val mapShapeId: String? = null,
    val centroidX: Float? = null,
    val centroidY: Float? = null,
    val aliases: List<String> = emptyList(),
)

fun loadTravelCountrySeeds(
    context: Context,
    @RawRes rawResId: Int = R.raw.travel_countries_seed,
): List<TravelCountrySeed> {
    val rawText = context.resources
        .openRawResource(rawResId)
        .bufferedReader()
        .use { it.readText() }

    val parsed = runCatching {
        travelSeedJson.decodeFromString<List<TravelCountrySeedJson>>(rawText)
    }.getOrElse {
        emptyList()
    }

    return parsed
        .mapNotNull { it.toDomainOrNull() }
        .distinctBy { it.countryId }
}

private fun TravelCountrySeedJson.toDomainOrNull(): TravelCountrySeed? {
    val cleanCountryId = countryId.trim().uppercase()
    if (cleanCountryId.isEmpty()) return null

    val continent = defaultContinent
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let { runCatching { TravelContinent.valueOf(it) }.getOrNull() }
        ?: return null

    val cleanNames = names
        .mapNotNull { (key, value) ->
            val cleanKey = key.trim()
            val cleanValue = value.trim()
            if (cleanKey.isEmpty() || cleanValue.isEmpty()) null else cleanKey to cleanValue
        }
        .toMap()

    val cleanAliases = aliases
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    return TravelCountrySeed(
        countryId = cleanCountryId,
        names = cleanNames,
        defaultContinent = continent,
        mapShapeId = mapShapeId?.trim()?.takeIf { it.isNotEmpty() },
        centroidX = centroidX,
        centroidY = centroidY,
        aliases = cleanAliases,
    )
}