package com.alphaomegos.annasagenda.app

import android.content.Context
import com.alphaomegos.annasagenda.model.FlightBase
import com.alphaomegos.annasagenda.support.parseFlightAirports
import com.alphaomegos.annasagenda.support.parseFlightCodes

/*
 * The flight section's shipped files (assets/flights/, see
 * tools/flights/README.md). An unreadable file gives an empty part of the
 * base rather than a crash: the flights are the user's and are still there,
 * shown by their codes.
 */

private fun readAsset(context: Context, name: String): String? = runCatching {
    context.assets.open("flights/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
}.getOrNull()

internal fun loadFlightBase(context: Context): FlightBase = FlightBase(
    airports = readAsset(context, "airports.tsv")?.let { parseFlightAirports(it).airports }.orEmpty(),
    airlines = readAsset(context, "airlines.tsv")?.let { parseFlightCodes(it).names }.orEmpty(),
    aircraft = readAsset(context, "aircraft.tsv")?.let { parseFlightCodes(it).names }.orEmpty(),
)
