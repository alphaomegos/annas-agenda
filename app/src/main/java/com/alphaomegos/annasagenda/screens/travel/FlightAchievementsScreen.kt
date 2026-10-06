package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.support.FlightAchievement
import com.alphaomegos.annasagenda.support.FlightStats
import com.alphaomegos.annasagenda.support.flightAchievements
import com.alphaomegos.annasagenda.support.flightCountriesFlown
import com.alphaomegos.annasagenda.support.flightFlag
import com.alphaomegos.annasagenda.support.flightStats
import com.alphaomegos.annasagenda.util.appLocale
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AchievementGold = Color(0xFFF2A900)

/**
 * Achievements (06.10): the milestones, and every country of the base as a
 * badge — earned in colour, not yet in grey. A filter shows all, the
 * earned or the not yet. Eduard will name the real milestones; these hold
 * their place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightAchievementsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val base = remember { vm.flightBase }
    val stats by produceState<FlightStats?>(null, state.flights) {
        value = withContext(Dispatchers.Default) { flightStats(state.flights, base, Instant.now()) }
    }
    val names = rememberTravelNames(vm.travelBase, state.travelCountries)
    val collator = rememberTravelCollator()
    val locale = appLocale()
    // 0 all, 1 earned, 2 not yet.
    var filter by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.flights_achievements)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { inner ->
        val s = stats ?: return@Scaffold
        val milestones = flightAchievements(s).filter { keep(filter, it.taken) }
        val flown = flightCountriesFlown(s)
        val countries = remember(names, collator) { vm.travelBase.map { it.code }.sortedWith { a, b -> collator.compare(names.nameOf(a), names.nameOf(b)) } }
            .filter { keep(filter, it in flown) }
        val milestonesTitle = stringResource(R.string.flights_achievements_milestones)
        val countriesTitle = stringResource(R.string.flights_achievements_countries) + " · ${flown.size}"
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text(stringResource(R.string.flights_filter_all)) })
                    FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text(stringResource(R.string.flights_filter_taken)) })
                    FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text(stringResource(R.string.flights_filter_open)) })
                }
            }
            if (milestones.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Header(milestonesTitle) }
                items(milestones, key = { "m:" + it.id }) { a -> Milestone(a, locale) }
            }
            if (countries.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Header(countriesTitle) }
                items(countries, key = { "c:$it" }) { code -> CountryBadge(flightFlag(code), names.nameOf(code), code in flown) }
            }
        }
    }
}

private fun keep(filter: Int, taken: Boolean) = when (filter) {
    1 -> taken
    2 -> !taken
    else -> true
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Milestone(a: FlightAchievement, locale: java.util.Locale) {
    val (title, text) = achievementRes(a.id)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (a.taken) 1f else 0.45f)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = if (a.taken) AchievementGold else MaterialTheme.colorScheme.outline,
        )
        Text(stringResource(title), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(stringResource(text), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        if (!a.taken) {
            Text(
                stringResource(R.string.flights_progress, flightNumber(a.value, locale), flightNumber(a.goal, locale)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountryBadge(flag: String, name: String, taken: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (taken) 1f else 0.3f)
            .background(
                if (taken) TravelVisitedGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp),
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(flag, fontSize = 28.sp)
        Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun achievementRes(id: String): Pair<Int, Int> = when (id) {
    "first_flight" -> R.string.ach_first_flight to R.string.ach_first_flight_text
    "flights_100" -> R.string.ach_flights_100 to R.string.ach_flights_100_text
    "flights_500" -> R.string.ach_flights_500 to R.string.ach_flights_500_text
    "flights_1000" -> R.string.ach_flights_1000 to R.string.ach_flights_1000_text
    "around_the_earth" -> R.string.ach_around_the_earth to R.string.ach_around_the_earth_text
    "to_the_moon" -> R.string.ach_to_the_moon to R.string.ach_to_the_moon_text
    "day_in_the_air" -> R.string.ach_day_in_the_air to R.string.ach_day_in_the_air_text
    "week_in_the_air" -> R.string.ach_week_in_the_air to R.string.ach_week_in_the_air_text
    "countries_10" -> R.string.ach_countries_10 to R.string.ach_countries_10_text
    "countries_50" -> R.string.ach_countries_50 to R.string.ach_countries_50_text
    "airlines_10" -> R.string.ach_airlines_10 to R.string.ach_airlines_10_text
    else -> R.string.ach_turnarounds_10 to R.string.ach_turnarounds_10_text
}
