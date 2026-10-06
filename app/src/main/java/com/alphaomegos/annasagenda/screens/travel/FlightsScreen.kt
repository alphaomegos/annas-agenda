package com.alphaomegos.annasagenda.screens.travel

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.model.Flight
import com.alphaomegos.annasagenda.model.FlightBase
import com.alphaomegos.annasagenda.support.FlightJourney
import com.alphaomegos.annasagenda.support.FlightMapRoute
import com.alphaomegos.annasagenda.support.TravelWorldMap
import com.alphaomegos.annasagenda.support.flightArrivesAt
import com.alphaomegos.annasagenda.support.flightCityName
import com.alphaomegos.annasagenda.support.flightDepartsAt
import com.alphaomegos.annasagenda.support.flightFlag
import com.alphaomegos.annasagenda.support.flightIsPast
import com.alphaomegos.annasagenda.support.flightJourneys
import com.alphaomegos.annasagenda.support.flightMapRoutes
import com.alphaomegos.annasagenda.util.appLocale
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Flights (06.10), from the travel screen: the world map with every route
 * flown as a great-circle arc, and under it the log, upcoming or past, one
 * card a journey — as App in the Air showed it: the cities with their
 * flags, the codes, the times (actual ones coloured, the schedule struck
 * through), the changes on the way and the time it all took.
 *
 * A card tapped lights its routes on the map and opens the journey: each
 * flight to edit, and "split off" for a flight after the first. The menu
 * imports App in the Air's export and turns grouping on or off — asking
 * about the flights already in the log (decided 06.10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val staged by vm.stagedFlightImport.collectAsStateWithLifecycle()
    val base = remember { vm.flightBase }
    val prefs = state.flightPrefs
    val map by produceState<TravelWorldMap?>(null) { value = withContext(Dispatchers.Default) { vm.travelMap } }
    val routes by produceState(emptyList<FlightMapRoute>(), state.flights, map) {
        val m = map
        value = if (m == null) emptyList() else withContext(Dispatchers.Default) { flightMapRoutes(state.flights, base, m.width) }
    }
    val now = remember { Instant.now() }
    val journeys = remember(state.flights) { flightJourneys(state.flights, base) }
    val past = remember(journeys) { journeys.filter { flightIsPast(it.first, base, now) }.reversed() }
    val upcoming = remember(journeys) { journeys.filter { !flightIsPast(it.first, base, now) } }

    var tab by rememberSaveable { mutableStateOf(if (upcoming.isEmpty()) 1 else 0) }
    var menu by rememberSaveable { mutableStateOf(false) }
    // The journey opened, by its first flight; its routes are lit on the map.
    var openJourney by rememberSaveable { mutableStateOf<Long?>(null) }
    // Grouping about to be turned on or off, waiting for "and the earlier flights?".
    var groupingTo by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notRecognised = stringResource(R.string.flights_import_not_recognised)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }
                    .getOrNull()
            }
            val ok = text != null && withContext(Dispatchers.Default) { vm.stageFlightImport(text) }
            if (!ok) Toast.makeText(context, notRecognised, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.flights_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { vm.setFlightPrefs(prefs.copy(showMap = !prefs.showMap)) }) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = stringResource(if (prefs.showMap) R.string.flights_hide_map else R.string.flights_show_map),
                        )
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.flights_add))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.flights_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.flights_import)) },
                                onClick = {
                                    menu = false
                                    importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.flights_group)) },
                                trailingIcon = { Checkbox(checked = prefs.groupJourneys, onCheckedChange = null) },
                                onClick = {
                                    menu = false
                                    groupingTo = !prefs.groupJourneys
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            val m = map
            if (prefs.showMap && m != null) {
                val lit = remember(openJourney, journeys) {
                    journeys.firstOrNull { it.first.id == openJourney }?.flights?.map { it.id }?.toSet().orEmpty()
                }
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.97f).heightIn(max = 320.dp)) {
                    TravelMapView(
                        map = m,
                        visited = emptySet(),
                        userDots = emptyList(),
                        routes = routes,
                        litFlights = lit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.flights_upcoming)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.flights_past)) })
            }
            val shown = if (tab == 0) upcoming else past
            if (state.flights.isEmpty()) {
                Text(
                    stringResource(R.string.flights_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(shown, key = { it.first.id }) { journey ->
                    JourneyCard(
                        journey = journey,
                        base = base,
                        cityNames = state.flightCityNames,
                        modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                    ) { openJourney = journey.first.id }
                }
            }
        }
    }

    val opened = journeys.firstOrNull { it.first.id == openJourney }
    if (opened != null) {
        JourneyDialog(
            journey = opened,
            base = base,
            cityNames = state.flightCityNames,
            onDismiss = { openJourney = null },
            onEdit = { id ->
                openJourney = null
                onEdit(id)
            },
            onSplit = { id -> vm.splitFlight(id) },
        )
    }

    val to = groupingTo
    if (to != null) {
        AlertDialog(
            onDismissRequest = { groupingTo = null },
            title = { Text(stringResource(R.string.flights_regroup_title)) },
            text = { Text(stringResource(if (to) R.string.flights_regroup_text else R.string.flights_ungroup_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setFlightGrouping(to, earlierToo = true)
                    groupingTo = null
                }) { Text(stringResource(R.string.flights_all_of_them)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.setFlightGrouping(to, earlierToo = false)
                    groupingTo = null
                }) { Text(stringResource(R.string.flights_only_new)) }
            },
        )
    }

    val plan = staged
    if (plan != null) {
        val imported = stringResource(R.string.flights_imported, plan.flights.size)
        AlertDialog(
            onDismissRequest = { vm.cancelFlightImport() },
            title = { Text(stringResource(R.string.flights_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(
                            R.string.flights_import_found,
                            plan.flights.size + plan.alreadyThere, plan.flights.size, plan.alreadyThere,
                        ),
                    )
                    if (plan.unknownAirports.isNotEmpty()) {
                        Text(stringResource(R.string.flights_import_unknown, plan.unknownAirports.joinToString(", ")))
                    }
                    if (plan.problems.isNotEmpty()) {
                        Text(stringResource(R.string.flights_import_problems, plan.problems.size))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.confirmFlightImport()
                    tab = 1
                    Toast.makeText(context, imported, Toast.LENGTH_SHORT).show()
                }, enabled = plan.flights.isNotEmpty()) { Text(stringResource(R.string.flights_import_button)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelFlightImport() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/* ---------------- the card ---------------- */

private val LateRed = Color(0xFFD32F2F)
private val EarlyGreen = Color(0xFF2E7D32)

/** "6h 05m" / "6 ч 5 мин". */
@Composable
internal fun flightHoursMinutes(minutes: Long): String =
    stringResource(R.string.flights_hm, minutes / 60, minutes % 60)

private fun hm(t: LocalDateTime): String = "%02d:%02d".format(t.hour, t.minute)

/** "11 Jul 2024", "03 — 04 Jul 2024", "30 Jun — 01 Jul 2024". */
private fun journeyDates(from: LocalDateTime, to: LocalDateTime, locale: Locale): String {
    val full = DateTimeFormatter.ofPattern("dd MMM yyyy", locale)
    val a = from.toLocalDate(); val b = to.toLocalDate()
    return when {
        a == b -> a.format(full)
        a.year == b.year && a.month == b.month -> "%02d — %s".format(a.dayOfMonth, b.format(full))
        a.year == b.year -> a.format(DateTimeFormatter.ofPattern("dd MMM", locale)) + " — " + b.format(full)
        else -> a.format(full) + " — " + b.format(full)
    }
}

@Composable
private fun JourneyCard(
    journey: FlightJourney,
    base: FlightBase,
    cityNames: Map<String, String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val locale = appLocale()
    val first = journey.first
    val last = journey.last
    val total = run {
        val a = flightDepartsAt(first, base); val b = flightArrivesAt(last, base)
        if (a != null && b != null) Duration.between(a, b).toMinutes().takeIf { it >= 0 } else null
    }
    val dayShift = ChronoUnit.DAYS.between(
        (first.actualDeparture ?: first.departure).toLocalDate(),
        (last.actualArrival ?: last.arrival).toLocalDate(),
    )
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                journeyDates(first.actualDeparture ?: first.departure, last.actualArrival ?: last.arrival, locale) +
                    if (journey.flights.size > 1) " • " + pluralStringResource(R.plurals.flights_count, journey.flights.size, journey.flights.size) else "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (total != null) {
                Text(flightHoursMinutes(total), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                End(first.from, first.departure, first.actualDeparture, base, cityNames, alignEnd = false, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.9f)) {
                    val stops = journey.flights.drop(1).map { flightCityName(it.from, base, cityNames) }
                    if (stops.isNotEmpty()) {
                        Text(
                            stops.joinToString(" • "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    if (dayShift > 0) {
                        Text(
                            stringResource(R.string.flights_plus_days, dayShift.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                End(last.to, last.arrival, last.actualArrival, base, cityNames, alignEnd = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One end of a card: flag and city, the code big, the time — the actual one coloured, the schedule struck through. */
@Composable
private fun End(
    code: String,
    scheduled: LocalDateTime,
    actual: LocalDateTime?,
    base: FlightBase,
    cityNames: Map<String, String>,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val flag = flightFlag(base.airports[code]?.country.orEmpty())
    val city = flightCityName(code, base, cityNames)
    val align = if (alignEnd) Alignment.End else Alignment.Start
    Column(modifier = modifier, horizontalAlignment = align) {
        Text(
            if (alignEnd) "$city $flag".trim() else "$flag $city".trim(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val time = actual ?: scheduled
        val color = when {
            actual == null -> MaterialTheme.colorScheme.onSurface
            actual > scheduled -> LateRed
            else -> EarlyGreen
        }
        val codeText = @Composable { Text(code, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        val timeText = @Composable {
            Text(hm(time), color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (alignEnd) { timeText(); codeText() } else { codeText(); timeText() }
        }
        if (actual != null && actual != scheduled) {
            Text(
                hm(scheduled),
                style = MaterialTheme.typography.labelMedium,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    }
}

/* ---------------- the journey ---------------- */

@Composable
private fun JourneyDialog(
    journey: FlightJourney,
    base: FlightBase,
    cityNames: Map<String, String>,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onSplit: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.flights_journey)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                journey.flights.forEachIndexed { i, f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onEdit(f.id) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                "${flightCityName(f.from, base, cityNames)} → ${flightCityName(f.to, base, cityNames)}",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                legLine(f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (i > 0) {
                            TextButton(onClick = { onSplit(f.id) }) { Text(stringResource(R.string.flights_split)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** "SVO 14:30 → KRR 16:45 · SU 1104 · 32A". */
private fun legLine(f: Flight): String {
    val times = "${f.from} ${hm(f.actualDeparture ?: f.departure)} → ${f.to} ${hm(f.actualArrival ?: f.arrival)}"
    val number = listOfNotNull(f.airline, f.number).joinToString(" ")
    return listOf(times, number, f.aircraft.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
}
