package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.alphaomegos.annasagenda.util.loadTravelCountrySeeds
import java.util.Locale

private data class TravelCountryRowUi(
    val countryId: String,
    val displayName: String,
    val continent: TravelContinent,
    val tripCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenCountry: (String) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val state by vm.state.collectAsState()

    val locale = remember(configuration) {
        configuration.locales.get(0) ?: Locale.ENGLISH
    }
    val localeTag = remember(locale) { locale.toLanguageTag() }

    val seedCountries = remember(context) {
        loadTravelCountrySeeds(context)
    }

    val countryRows = remember(seedCountries, state.travelCountries, localeTag) {
        buildTravelCountryRows(
            seedCountries = seedCountries,
            records = state.travelCountries,
            localeTag = localeTag,
        )
    }

    val visitedCount = remember(countryRows) {
        countryRows.count { it.tripCount > 0 }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.travel_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (countryRows.isEmpty()) {
            Text(
                text = stringResource(R.string.travel_seed_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(
                            R.string.travel_stats,
                            visitedCount,
                            countryRows.size
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(
                    items = countryRows,
                    key = { it.countryId }
                ) { row ->
                    TravelCountryRow(
                        row = row,
                        onClick = { onOpenCountry(row.countryId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TravelCountryRow(
    row: TravelCountryRowUi,
    onClick: () -> Unit,
) {    val continentText = continentDisplayName(row.continent)

    val subtitle = when (row.tripCount) {
        0 -> stringResource(
            R.string.travel_country_not_visited,
            continentText
        )

        1 -> stringResource(
            R.string.travel_country_visited_one,
            continentText
        )

        else -> stringResource(
            R.string.travel_country_visited_many,
            continentText,
            row.tripCount
        )
    }

    Card(
        onClick = onClick
    ) {
        ListItem(
            headlineContent = {
                Text(text = row.displayName)
            },
            supportingContent = {
                Text(text = subtitle)
            }
        )
    }
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

private fun buildTravelCountryRows(
    seedCountries: List<TravelCountrySeed>,
    records: List<TravelCountryRecord>,
    localeTag: String,
): List<TravelCountryRowUi> {
    val recordsById = records.associateBy { it.countryId.trim().uppercase() }
    val seedIds = seedCountries.map { it.countryId }.toSet()
    val sortingLocale = Locale.forLanguageTag(localeTag).takeIf { it.language.isNotEmpty() }
        ?: Locale.ENGLISH

    val seedRows = seedCountries.map { seed ->
        val record = recordsById[seed.countryId]

        TravelCountryRowUi(
            countryId = seed.countryId,
            displayName = resolveCountryDisplayName(
                seed = seed,
                record = record,
                localeTag = localeTag
            ),
            continent = record?.continentOverride ?: seed.defaultContinent,
            tripCount = record?.trips?.size ?: 0,
        )
    }

    val customRows = records
        .asSequence()
        .filter { it.isUserCreated }
        .filter { it.countryId.trim().uppercase() !in seedIds }
        .mapNotNull { record ->
            val continent = record.continentOverride ?: return@mapNotNull null
            val name = record.customName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            TravelCountryRowUi(
                countryId = record.countryId,
                displayName = name,
                continent = continent,
                tripCount = record.trips.size,
            )
        }
        .toList()

    return (seedRows + customRows)
        .sortedBy { it.displayName.lowercase(sortingLocale) }
}

private fun resolveCountryDisplayName(
    seed: TravelCountrySeed,
    record: TravelCountryRecord?,
    localeTag: String,
): String {
    val customName = record?.customName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    if (customName != null) return customName

    return localizedSeedName(
        names = seed.names,
        localeTag = localeTag
    ) ?: seed.countryId
}

private fun localizedSeedName(
    names: Map<String, String>,
    localeTag: String,
): String? {
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