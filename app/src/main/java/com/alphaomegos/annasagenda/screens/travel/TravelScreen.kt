package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.app.appExtraColors
import com.alphaomegos.annasagenda.model.TravelContinent
import com.alphaomegos.annasagenda.model.TravelMapPoint
import com.alphaomegos.annasagenda.model.TravelView
import com.alphaomegos.annasagenda.support.TravelWorldMap
import com.alphaomegos.annasagenda.support.travelAllCountryIds
import com.alphaomegos.annasagenda.support.travelByCities
import com.alphaomegos.annasagenda.support.travelByContinents
import com.alphaomegos.annasagenda.support.travelByCountries
import com.alphaomegos.annasagenda.support.travelByYears
import com.alphaomegos.annasagenda.support.travelSearch
import com.alphaomegos.annasagenda.support.travelVisited
import com.alphaomegos.annasagenda.util.appLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Travel (05.10): the world map with the visited countries green, and under
 * it the panel — the view (by years, countries, cities, continents), the
 * order turned round, search, "all" or "mine" in the view by countries, and
 * terra incognito, a country of the user's own put on the map with a tap.
 *
 * A country tapped on the map or in a list opens its card. The view, its
 * order and "all/mine" are kept in the state; what is typed in the search
 * survives turning the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenCountry: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val base = remember { vm.travelBase }
    val map by produceState<TravelWorldMap?>(null) { value = withContext(Dispatchers.Default) { vm.travelMap } }
    val records = state.travelCountries
    val prefs = state.travelView
    val names = rememberTravelNames(base, records)
    val collator = rememberTravelCollator()
    val locale = appLocale()
    val visited = remember(records) { travelVisited(records) }
    val userDots = remember(records) {
        records.filter { it.isUserCountry && it.customPoint != null }
            .map { TravelUserDot(it.countryId, it.customPoint!!.x, it.customPoint.y, it.trips.isNotEmpty()) }
    }

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var viewMenu by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    // A country of the user's own waiting for its place on the map: "CONTINENT|name".
    var placing by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_travel)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = stringResource(R.string.travel_terra_incognito))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding(),
        ) {
            val pending = placing
            if (pending != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.travel_place_on_map, pending.substringAfter('|')),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { placing = null }) { Text(stringResource(R.string.cancel)) }
                }
            }

            val m = map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.97f)
                    .heightIn(max = 320.dp),
            ) {
                if (m != null) {
                    TravelMapView(
                        map = m,
                        visited = visited,
                        userDots = userDots,
                        modifier = Modifier.fillMaxSize(),
                        onTap = { countryId, gx, gy ->
                            val p = placing
                            if (p != null) {
                                val continent = TravelContinent.entries.firstOrNull { it.name == p.substringBefore('|') }
                                    ?: TravelContinent.EUROPE
                                vm.addTravelUserCountry(p.substringAfter('|'), continent, TravelMapPoint(gx, gy))
                                placing = null
                            } else if (countryId != null) {
                                onOpenCountry(countryId)
                            }
                        },
                    )
                }
            }

            // The panel.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    TextButton(onClick = { viewMenu = true }) { Text(stringResource(travelViewRes(prefs.view))) }
                    DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                        TravelView.entries.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(stringResource(travelViewRes(v))) },
                                onClick = {
                                    vm.setTravelView(prefs.copy(view = v))
                                    viewMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                PanelIcon(
                    painterRes = R.drawable.ic_filter_sort,
                    checked = prefs.reversed,
                    description = stringResource(R.string.travel_reverse),
                ) { vm.setTravelView(prefs.copy(reversed = !prefs.reversed)) }
                PanelIcon(
                    painterRes = R.drawable.ic_filter_search,
                    checked = searching,
                    description = stringResource(R.string.travel_search),
                ) {
                    searching = !searching
                    if (!searching) query = ""
                }
                if (prefs.view == TravelView.COUNTRIES) {
                    PanelIcon(
                        vector = Icons.Default.Public,
                        checked = !prefs.onlyMine,
                        description = stringResource(R.string.travel_all_countries),
                    ) { vm.setTravelView(prefs.copy(onlyMine = false)) }
                    PanelIcon(
                        vector = Icons.Default.Flag,
                        checked = prefs.onlyMine,
                        description = stringResource(R.string.travel_my_countries),
                    ) { vm.setTravelView(prefs.copy(onlyMine = true)) }
                }
            }

            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.travel_search_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            val all = remember(base, records) { travelAllCountryIds(base, records) }
            // Words for the view by continents, looked up here: the list's builder is not a composable.
            val continentNames = TravelContinent.entries.associateWith { stringResource(travelContinentRes(it)) }
            val continentRows = if (prefs.view == TravelView.CONTINENTS) {
                travelByContinents(base, records, { continentNames.getValue(it) }, collator, prefs.reversed)
            } else {
                emptyList()
            }
            val visitedText = stringResource(R.string.travel_visited)
            val countriesText = continentRows.associate {
                it.continent to pluralStringResource(R.plurals.travel_countries, it.totalCountries, it.totalCountries)
            }
            val citiesText = continentRows.associate {
                it.continent to pluralStringResource(R.plurals.travel_cities, it.cities, it.cities)
            }
            val emptyHint = stringResource(R.string.travel_empty)
            val nothingFound = stringResource(R.string.travel_nothing_found)
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                if (searching && query.isNotBlank()) {
                    val found = travelSearch(all, records, names, collator, query)
                    if (found.isEmpty()) item { Hint(nothingFound) }
                    val rows = travelByCountries(found, records, names, collator, onlyMine = false, reversed = false)
                        .associateBy { it.countryId }
                    items(found, key = { "s:$it" }) { id ->
                        val row = rows.getValue(id)
                        CountryLine(names.nameOf(id), row.years, row.cities, visited = id in visited) { onOpenCountry(id) }
                    }
                    return@LazyColumn
                }

                if (visited.isEmpty() && !(prefs.view == TravelView.COUNTRIES && !prefs.onlyMine) &&
                    prefs.view != TravelView.CONTINENTS
                ) {
                    item { Hint(emptyHint) }
                }

                when (prefs.view) {
                    TravelView.YEARS -> travelByYears(records, names, collator, prefs.reversed).forEach { group ->
                        item(key = "y:${group.year}") {
                            Text(
                                group.year.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            )
                        }
                        items(group.entries, key = { "t:${it.tripId}" }) { e ->
                            val text = travelMonthName(e.month, locale) + ". " + names.nameOf(e.countryId) +
                                (if (e.cities.isEmpty()) "" else ": " + e.cities.joinToString(", "))
                            Line(text) { onOpenCountry(e.countryId) }
                        }
                    }

                    TravelView.COUNTRIES -> {
                        val rows = travelByCountries(all, records, names, collator, prefs.onlyMine, prefs.reversed)
                        items(rows, key = { "c:${it.countryId}" }) { row ->
                            CountryLine(names.nameOf(row.countryId), row.years, row.cities, visited = row.years.isNotEmpty()) {
                                onOpenCountry(row.countryId)
                            }
                        }
                    }

                    TravelView.CITIES -> {
                        val rows = travelByCities(records, collator, prefs.reversed)
                        items(rows, key = { "city:${it.name}" }) { row ->
                            Line(if (row.visits > 1) "${row.name} ${row.visits}" else row.name) {
                                row.countryIds.firstOrNull()?.let(onOpenCountry)
                            }
                        }
                    }

                    TravelView.CONTINENTS -> {
                        items(continentRows, key = { "k:${it.continent.name}" }) { row ->
                            val stats = if (row.continent == TravelContinent.ANTARCTICA && row.visitedCountries > 0) {
                                visitedText
                            } else {
                                "${row.visitedCountries} / ${countriesText.getValue(row.continent)}, " +
                                    citiesText.getValue(row.continent)
                            }
                            Line("${continentNames.getValue(row.continent)} ($stats)", dimmed = row.visitedCountries == 0) {}
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TravelUserCountryDialog(
            onDismiss = { creating = false },
            onNext = { name, continent ->
                creating = false
                placing = continent.name + "|" + name.trim()
            },
        )
    }
}

internal fun travelViewRes(view: TravelView): Int = when (view) {
    TravelView.YEARS -> R.string.travel_view_years
    TravelView.COUNTRIES -> R.string.travel_view_countries
    TravelView.CITIES -> R.string.travel_view_cities
    TravelView.CONTINENTS -> R.string.travel_view_continents
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun Line(text: String, dimmed: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (dimmed) 0.5f else 1f)
            .padding(vertical = 8.dp),
    )
}

/** "Австрия (2014, 2020): Вена, Грац" — a country with its years and cities; grey when never visited. */
@Composable
private fun CountryLine(name: String, years: List<Int>, cities: List<String>, visited: Boolean, onClick: () -> Unit) {
    val text = name +
        (if (years.isEmpty()) "" else " (" + years.joinToString(", ") + ")") +
        (if (cities.isEmpty()) "" else ": " + cities.joinToString(", "))
    Line(text, dimmed = !visited, onClick = onClick)
}

/** A button of the panel, in the media library's style: lit when on. */
@Composable
private fun PanelIcon(
    checked: Boolean,
    description: String,
    painterRes: Int? = null,
    vector: ImageVector? = null,
    onClick: () -> Unit,
) {
    val painter = if (painterRes != null) painterResource(painterRes) else rememberVectorPainter(vector!!)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) appExtraColors.gentleHighlight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = description,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxSize().alpha(if (checked) 1f else 0.5f),
        )
    }
}
