package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.model.Flight
import com.alphaomegos.annasagenda.support.FLIGHT_EARTH_KM
import com.alphaomegos.annasagenda.support.FlightCount
import com.alphaomegos.annasagenda.support.FlightStats
import com.alphaomegos.annasagenda.support.flightAchievements
import com.alphaomegos.annasagenda.support.flightAirlineName
import com.alphaomegos.annasagenda.support.flightCityName
import com.alphaomegos.annasagenda.support.flightDistanceKm
import com.alphaomegos.annasagenda.support.flightFlag
import com.alphaomegos.annasagenda.support.flightMinutes
import com.alphaomegos.annasagenda.support.flightStats
import com.alphaomegos.annasagenda.util.appLocale
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "2 764 400": a big number with the language's grouping. */
internal fun flightNumber(n: Long, userLocale: Locale): String = String.format(userLocale, "%,d", n)

/**
 * My profile (06.10): the flight log's numbers — flights, kilometres (and
 * how many times round the Earth), hours in the air, countries, airports,
 * airlines, aircraft, turnarounds — the longest flights, the lists most
 * used first (five each, "show all" for the rest) and the years. The
 * achievements are a button away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FlightProfileScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAchievements: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val base = remember { vm.flightBase }
    val stats by produceState<FlightStats?>(null, state.flights) {
        value = withContext(Dispatchers.Default) { flightStats(state.flights, base, Instant.now()) }
    }
    val names = rememberTravelNames(vm.travelBase, state.travelCountries)
    val userLocale = appLocale()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.flights_profile)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { inner ->
        val s = stats
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (s == null) return@Column
            Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.flights == 0) {
                    Text(stringResource(R.string.flights_no_flights_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(flightNumber(s.flights.toLong(), userLocale), stringResource(R.string.flights_stat_flights))
                    Tile(
                        flightNumber(s.km, userLocale), stringResource(R.string.flights_stat_km),
                        stringResource(R.string.flights_stat_earths, String.format(userLocale, "%.1f", s.km.toDouble() / FLIGHT_EARTH_KM)),
                    )
                    Tile(
                        flightNumber(s.minutes / 60, userLocale), stringResource(R.string.flights_stat_hours),
                        stringResource(R.string.flights_stat_days, String.format(userLocale, "%.1f", s.minutes / 1440.0)),
                    )
                    Tile(s.countries.size.toString(), stringResource(R.string.flights_stat_countries))
                    Tile(s.airports.size.toString(), stringResource(R.string.flights_stat_airports))
                    Tile(s.airlines.size.toString(), stringResource(R.string.flights_stat_airlines))
                    Tile(s.aircraft.size.toString(), stringResource(R.string.flights_stat_aircraft))
                    Tile(s.turnarounds.toString(), stringResource(R.string.flights_stat_turnarounds))
                }

                val achievements = flightAchievements(s)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                        .clickable(onClick = onAchievements)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        stringResource(R.string.flights_achievements_count, achievements.count { it.taken }, achievements.size),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                s.longestByDistance?.let { f ->
                    Longest(stringResource(R.string.flights_longest_distance), f, flightNumber((flightDistanceKm(f, base) ?: 0.0).toLong(), userLocale) + " km", base, state.flightCityNames)
                }
                s.longestByTime?.let { f ->
                    Longest(stringResource(R.string.flights_longest_time), f, flightHoursMinutes(flightMinutes(f, base) ?: 0), base, state.flightCityNames)
                }

                TopList(stringResource(R.string.flights_top_countries), s.countries) {
                    (flightFlag(it) + " " + names.nameOf(it)).trim()
                }
                TopList(stringResource(R.string.flights_top_airports), s.airports) {
                    "$it · " + flightCityName(it, base, state.flightCityNames)
                }
                TopList(stringResource(R.string.flights_top_airlines), s.airlines) { flightAirlineName(it, base, state.flightAirlineNames) }
                TopList(stringResource(R.string.flights_top_aircraft), s.aircraft) { base.aircraft[it] ?: it }
                TopList(stringResource(R.string.flights_top_routes), s.routes) { it }
                TopList(stringResource(R.string.flights_top_turnarounds), s.turnaroundPlaces) {
                    "$it · " + flightCityName(it, base, state.flightCityNames)
                }

                if (s.years.isNotEmpty()) {
                    Section(stringResource(R.string.flights_by_year))
                    s.years.reversed().forEach { y ->
                        Text(
                            stringResource(
                                R.string.flights_year_line, y.year,
                                pluralStringResource(R.plurals.flights_count, y.flights, y.flights),
                                flightNumber(y.km, userLocale), flightHoursMinutes(y.minutes),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, extra: String? = null) {
    Column(
        modifier = Modifier
            .widthIn(min = 140.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (extra != null) Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Longest(title: String, f: Flight, value: String, base: com.alphaomegos.annasagenda.model.FlightBase, cityNames: Map<String, String>) {
    Section(title)
    Text(
        "${flightCityName(f.from, base, cityNames)} → ${flightCityName(f.to, base, cityNames)} · $value · ${f.departure.toLocalDate()}",
    )
}

/** A list, most used first: five, and "show all" for the rest. */
@Composable
private fun TopList(title: String, rows: List<FlightCount>, label: (String) -> String) {
    if (rows.isEmpty()) return
    var all by rememberSaveable(title) { mutableStateOf(false) }
    Section(title)
    (if (all) rows else rows.take(5)).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label(row.key), modifier = Modifier.weight(1f))
            Text(row.flights.toString(), fontWeight = FontWeight.SemiBold)
        }
    }
    if (rows.size > 5) {
        TextButton(onClick = { all = !all }) {
            Text(stringResource(if (all) R.string.flights_show_fewer else R.string.flights_show_all))
        }
    }
}
