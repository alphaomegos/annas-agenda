package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.TravelContinent
import com.alphaomegos.annasagenda.TravelCountryRecord
import com.alphaomegos.annasagenda.TravelCountrySeed
import com.alphaomegos.annasagenda.TravelVisit
import com.alphaomegos.annasagenda.util.loadTravelCountrySeeds
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private data class TravelTripUi(
    val year: Int,
    val month: Int,
    val cities: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryDetailsScreen(
    vm: AppViewModel,
    countryId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val state by vm.state.collectAsState()

    val locale = remember(configuration) {
        configuration.locales.get(0) ?: Locale.ENGLISH
    }
    val localeTag = remember(locale) { locale.toLanguageTag() }
    val normalizedCountryId = remember(countryId) { countryId.trim().uppercase() }

    val seedCountries = remember(context) {
        loadTravelCountrySeeds(context)
    }

    val seed = remember(seedCountries, normalizedCountryId) {
        seedCountries.firstOrNull { it.countryId == normalizedCountryId }
    }

    val record = remember(state.travelCountries, normalizedCountryId) {
        state.travelCountries.firstOrNull {
            it.countryId.trim().uppercase() == normalizedCountryId
        }
    }

    val displayName = remember(seed, record, localeTag, normalizedCountryId) {
        resolveTravelDisplayName(
            seed = seed,
            record = record,
            localeTag = localeTag
        ) ?: normalizedCountryId
    }

    val continent = remember(seed, record) {
        record?.continentOverride ?: seed?.defaultContinent
    }

    val trips = remember(record) {
        record.orEmptyTripsNewestFirst()
    }

    var isAddTripDialogOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddTripDialogOpen = true }
            ) {
                Text(stringResource(R.string.travel_add_trip))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    ListItem(
                        headlineContent = {
                            Text(text = displayName)
                        },
                        supportingContent = {
                            Text(
                                text = continent?.let { continentDisplayName(it) }
                                    ?: stringResource(R.string.travel_continent_unknown)
                            )
                        }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.travel_country_trips_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (trips.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.travel_country_empty),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(
                    items = trips,
                    key = { "${it.year}-${it.month}-${it.cities.joinToString("\u0000")}" }
                ) { trip ->
                    TravelTripCard(
                        trip = trip,
                        locale = locale
                    )
                }
            }
        }
    }

    if (isAddTripDialogOpen) {
        AddTravelTripDialog(
            onDismiss = { isAddTripDialogOpen = false },
            onConfirm = { year, month, cities ->
                val saved = vm.addTravelTrip(
                    countryId = normalizedCountryId,
                    year = year,
                    month = month,
                    cities = cities,
                )
                if (saved) {
                    isAddTripDialogOpen = false
                }
            }
        )
    }
}

@Composable
private fun TravelTripCard(
    trip: TravelTripUi,
    locale: Locale,
) {
    val headline = remember(trip.year, trip.month, locale) {
        val monthName = Month.of(trip.month)
            .getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
            }
        "$monthName ${trip.year}"
    }

    val supporting = if (trip.cities.isEmpty()) {
        stringResource(R.string.travel_trip_cities_none)
    } else {
        trip.cities.joinToString(", ")
    }

    Card {
        ListItem(
            headlineContent = { Text(headline) },
            supportingContent = { Text(supporting) }
        )
    }
}

@Composable
private fun AddTravelTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int, cities: List<String>) -> Unit,
) {
    var yearText by rememberSaveable { mutableStateOf("") }
    var monthText by rememberSaveable { mutableStateOf("") }
    var cityInput by rememberSaveable { mutableStateOf("") }
    var cities by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val year = yearText.toIntOrNull()
    val month = monthText.toIntOrNull()
    val isValid = year != null && year in 1..9999 && month != null && month in 1..12

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.travel_add_trip_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.travel_trip_year)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = monthText,
                    onValueChange = { monthText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.travel_trip_month)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        label = { Text(stringResource(R.string.travel_trip_city_input)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = {
                            val clean = cityInput.trim()
                            if (clean.isNotEmpty()) {
                                cities = cities + clean
                                cityInput = ""
                            }
                        },
                        modifier = Modifier.widthIn(min = 72.dp)
                    ) {
                        Text(stringResource(R.string.travel_trip_add_city))
                    }
                }

                Text(
                    text = stringResource(R.string.travel_trip_cities_label),
                    style = MaterialTheme.typography.titleSmall
                )

                if (cities.isEmpty()) {
                    Text(
                        text = stringResource(R.string.travel_trip_cities_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cities.forEachIndexed { index, city ->
                            AssistChip(
                                onClick = { },
                                label = { Text(city) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            cities = cities.filterIndexed { i, _ -> i != index }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(year!!, month!!, cities) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.travel_trip_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.travel_trip_cancel))
            }
        }
    )
}

@Composable
private fun continentDisplayName(continent: TravelContinent): String = when (continent) {
    TravelContinent.AFRICA -> stringResource(R.string.travel_continent_africa)
    TravelContinent.ANTARCTICA -> stringResource(R.string.travel_continent_antarctica)
    TravelContinent.ASIA -> stringResource(R.string.travel_continent_asia)
    TravelContinent.EUROPE -> stringResource(R.string.travel_continent_europe)
    TravelContinent.NORTH_AMERICA -> stringResource(R.string.travel_continent_north_america)
    TravelContinent.OCEANIA -> stringResource(R.string.travel_continent_oceania)
    TravelContinent.SOUTH_AMERICA -> stringResource(R.string.travel_continent_south_america)
}

private fun resolveTravelDisplayName(
    seed: TravelCountrySeed?,
    record: TravelCountryRecord?,
    localeTag: String,
): String? {
    val customName = record?.customName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    if (customName != null) return customName

    val names = seed?.names ?: return null

    val exact = names[localeTag]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (exact != null) return exact

    val exactIgnoreCase = names.entries
        .firstOrNull { it.key.equals(localeTag, ignoreCase = true) }
        ?.value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (exactIgnoreCase != null) return exactIgnoreCase

    val shortTag = localeTag.substringBefore('-')

    val short = names[shortTag]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (short != null) return short

    val shortIgnoreCase = names.entries
        .firstOrNull { it.key.equals(shortTag, ignoreCase = true) }
        ?.value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (shortIgnoreCase != null) return shortIgnoreCase

    val english = names["en"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (english != null) return english

    return names.values
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
}

private fun TravelCountryRecord?.orEmptyTripsNewestFirst(): List<TravelTripUi> {
    return this?.trips
        ?.sortedWith(compareByDescending<TravelVisit> { it.year }.thenByDescending { it.month })
        ?.map { trip ->
            TravelTripUi(
                year = trip.year,
                month = trip.month,
                cities = trip.cities
            )
        }
        .orEmpty()
}