package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
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
import com.alphaomegos.annasagenda.components.WayHomeButton

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
    val userLocale = appLocale()
    // 0 all, 1 earned, 2 not yet.
    var filter by rememberSaveable { mutableStateOf(0) }
    // The achievement opened in its dialog.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.flights_achievements)) },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                        WayHomeButton()
                    }
                },
            )
        },
    ) { inner ->
        val s = stats ?: return@Scaffold
        val achievements = flightAchievements(s)
        val milestones = achievements.filter { keep(filter, it.taken) }
        achievements.firstOrNull { it.id == opened }?.let { a -> AchievementDialog(a, userLocale) { opened = null } }
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
                items(milestones, key = { "m:" + it.id }) { a -> Milestone(a, userLocale) { opened = a.id } }
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
private fun Milestone(a: FlightAchievement, userLocale: java.util.Locale, onOpen: () -> Unit) {
    val (title, text, _) = achievementRes(a.id)
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen)
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Every tile the same shape (06.10): a square picture, two lines of
            // name, two of what it takes, one of progress — kept even when empty.
            AchievementPicture(a.id, a.taken, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(
                stringResource(title),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).alpha(if (a.taken) 1f else 0.55f),
            )
            Text(
                stringResource(text),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (a.taken) 1f else 0.55f),
            )
            Text(
                if (a.taken) "" else stringResource(R.string.flights_progress, flightNumber(a.value, userLocale), flightNumber(a.goal, userLocale)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        // Earned more than once (06.10): "×2" in the corner.
        if (a.times > 1) {
            Text(
                stringResource(R.string.ach_times, flightNumber(a.times, userLocale)),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(AchievementGold, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * An achievement's picture, assets/achievements/<id>.webp or .png (06.10; made
 * elsewhere, see the README there), grey while not earned; the trophy
 * while the picture is not there yet.
 */
@Composable
private fun AchievementPicture(id: String, taken: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                // WebP as shipped, PNG as a picture arrives from the generator.
                listOf("webp", "png").firstNotNullOfOrNull { ext ->
                    runCatching {
                        context.assets.open("achievements/$id.$ext").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    }.getOrNull()
                }
            }.getOrNull()
        }
    }
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val p = picture
    if (p != null) {
        Image(
            bitmap = p,
            contentDescription = null,
            colorFilter = if (taken) null else grey,
            modifier = modifier.clip(RoundedCornerShape(12.dp)).alpha(if (taken) 1f else 0.5f),
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = if (taken) AchievementGold else MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxSize(0.5f),
            )
        }
    }
}

/** An achievement opened (06.10): the big picture, its name, what it takes, the joke, and how it stands. */
@Composable
private fun AchievementDialog(a: FlightAchievement, userLocale: java.util.Locale, onDismiss: () -> Unit) {
    val (title, text, joke) = achievementRes(a.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AchievementPicture(a.id, a.taken, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(stringResource(joke), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Text(
                    when {
                        a.times > 1 -> stringResource(R.string.ach_earned_times, flightNumber(a.times, userLocale))
                        a.taken -> stringResource(R.string.ach_earned)
                        else -> stringResource(R.string.ach_not_yet, flightNumber(a.value, userLocale), flightNumber(a.goal, userLocale))
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = if (a.taken) AchievementGold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
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
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun achievementRes(id: String): Triple<Int, Int, Int> = when (id) {
    "first_flight" -> Triple(R.string.ach_first_flight, R.string.ach_first_flight_text, R.string.ach_first_flight_joke)
    "flights_100" -> Triple(R.string.ach_flights_100, R.string.ach_flights_100_text, R.string.ach_flights_100_joke)
    "flights_1000" -> Triple(R.string.ach_flights_1000, R.string.ach_flights_1000_text, R.string.ach_flights_1000_joke)
    "around_the_earth" -> Triple(R.string.ach_around_the_earth, R.string.ach_around_the_earth_text, R.string.ach_around_the_earth_joke)
    "to_the_moon" -> Triple(R.string.ach_to_the_moon, R.string.ach_to_the_moon_text, R.string.ach_to_the_moon_joke)
    "day_in_the_air" -> Triple(R.string.ach_day_in_the_air, R.string.ach_day_in_the_air_text, R.string.ach_day_in_the_air_joke)
    "week_in_the_air" -> Triple(R.string.ach_week_in_the_air, R.string.ach_week_in_the_air_text, R.string.ach_week_in_the_air_joke)
    "month_in_the_air" -> Triple(R.string.ach_month_in_the_air, R.string.ach_month_in_the_air_text, R.string.ach_month_in_the_air_joke)
    "year_in_the_air" -> Triple(R.string.ach_year_in_the_air, R.string.ach_year_in_the_air_text, R.string.ach_year_in_the_air_joke)
    "countries_10" -> Triple(R.string.ach_countries_10, R.string.ach_countries_10_text, R.string.ach_countries_10_joke)
    "countries_50" -> Triple(R.string.ach_countries_50, R.string.ach_countries_50_text, R.string.ach_countries_50_joke)
    "airlines_10" -> Triple(R.string.ach_airlines_10, R.string.ach_airlines_10_text, R.string.ach_airlines_10_joke)
    "aircraft_10" -> Triple(R.string.ach_aircraft_10, R.string.ach_aircraft_10_text, R.string.ach_aircraft_10_joke)
    "airports_10" -> Triple(R.string.ach_airports_10, R.string.ach_airports_10_text, R.string.ach_airports_10_joke)
    "airports_100" -> Triple(R.string.ach_airports_100, R.string.ach_airports_100_text, R.string.ach_airports_100_joke)
    "airports_1000" -> Triple(R.string.ach_airports_1000, R.string.ach_airports_1000_text, R.string.ach_airports_1000_joke)
    "time_killer" -> Triple(R.string.ach_time_killer, R.string.ach_time_killer_text, R.string.ach_time_killer_joke)
    "time_traveller" -> Triple(R.string.ach_time_traveller, R.string.ach_time_traveller_text, R.string.ach_time_traveller_joke)
    else -> Triple(R.string.ach_flight_attendant, R.string.ach_flight_attendant_text, R.string.ach_flight_attendant_joke)
}
