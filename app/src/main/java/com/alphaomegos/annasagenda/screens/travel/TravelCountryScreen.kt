package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.components.ConfirmDialog
import com.alphaomegos.annasagenda.model.TRAVEL_USER_COUNTRY_PREFIX
import com.alphaomegos.annasagenda.model.TravelMapPoint
import com.alphaomegos.annasagenda.model.TravelTrip
import com.alphaomegos.annasagenda.support.TravelWorldMap
import com.alphaomegos.annasagenda.support.travelContinentOf
import com.alphaomegos.annasagenda.support.travelVisited
import com.alphaomegos.annasagenda.util.appLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A country's card (05.10): the piece of the map around it, its outline
 * marked and green when visited; its name and continent; its trips, newest
 * first — "Май 2018: Минск" — each to be changed or removed; and "Add a trip".
 * A country of the user's own can be renamed and removed too.
 *
 * Which dialog is open survives turning the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelCountryScreen(
    vm: AppViewModel,
    countryId: String,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val base = remember { vm.travelBase }
    val baseMap = remember(base) { base.associateBy { it.code } }
    val map by produceState<TravelWorldMap?>(null) { value = withContext(Dispatchers.Default) { vm.travelMap } }
    val records = state.travelCountries
    val record = records.firstOrNull { it.countryId == countryId }
    val names = rememberTravelNames(base, records)
    val locale = appLocale()
    val isUser = countryId.startsWith(TRAVEL_USER_COUNTRY_PREFIX)
    val visited = remember(records) { travelVisited(records) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { dialog = null }

    // The user's own country gone (removed here): nothing left to show. Not
    // navigated away from in the middle of composing, but just after.
    if (isUser && record == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(names.nameOf(countryId)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val m = map
            if (m != null) {
                val focus = remember(m, record?.customPoint, countryId) { cardFocus(m, countryId, record?.customPoint) }
                TravelMapView(
                    map = m,
                    visited = visited,
                    userDots = records.filter { it.isUserCountry && it.customPoint != null }
                        .map { TravelUserDot(it.countryId, it.customPoint!!.x, it.customPoint.y, it.trips.isNotEmpty()) },
                    highlight = countryId,
                    focus = focus,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            }

            val continent = travelContinentOf(countryId, baseMap, records)
            Text(
                continent?.let { stringResource(travelContinentRes(it)) }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { dialog = "continent" }.padding(vertical = 8.dp),
            )

            val trips = record?.trips.orEmpty().sortedWith(compareByDescending<TravelTrip> { it.year }.thenByDescending { it.month })
            trips.forEach { t ->
                val text = travelMonthName(t.month, locale) + " " + t.year +
                    (if (t.cities.isEmpty()) "" else ": " + t.cities.joinToString(", "))
                Text(text, modifier = Modifier.fillMaxWidth().clickable { dialog = "trip:${t.id}" }.padding(vertical = 8.dp))
            }

            Button(onClick = { dialog = "add" }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.travel_add_trip))
            }

            if (isUser) {
                HorizontalDivider(modifier = Modifier.padding(top = 24.dp))
                TextButton(onClick = { dialog = "rename" }) { Text(stringResource(R.string.travel_rename_country)) }
                TextButton(onClick = { dialog = "delete" }) {
                    Text(stringResource(R.string.travel_delete_country), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    val d = dialog ?: return
    val tripId = d.substringAfter(':', "").toLongOrNull()
    when (d.substringBefore(':')) {
        "add" -> TravelTripDialog(
            title = stringResource(R.string.travel_add_trip),
            initialYear = null,
            initialMonth = null,
            initialCities = emptyList(),
            onDismiss = close,
            onSave = { year, month, cities ->
                vm.addTravelTrip(countryId, year, month, cities)
                close()
            },
        )
        "trip" -> {
            val t = record?.trips?.firstOrNull { it.id == tripId } ?: return
            AlertDialog(
                onDismissRequest = close,
                title = { Text(travelMonthName(t.month, locale) + " " + t.year) },
                text = {
                    Column {
                        TextButton(onClick = { dialog = "edit:${t.id}" }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.travel_edit_trip))
                        }
                        TextButton(
                            onClick = {
                                vm.removeTravelTrip(t.id)
                                close()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.delete)) }
                    }
                },
                confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
            )
        }
        "edit" -> {
            val t = record?.trips?.firstOrNull { it.id == tripId } ?: return
            TravelTripDialog(
                title = stringResource(R.string.travel_edit_trip),
                initialYear = t.year,
                initialMonth = t.month,
                initialCities = t.cities,
                onDismiss = close,
                onSave = { year, month, cities ->
                    vm.editTravelTrip(t.id, year, month, cities)
                    close()
                },
            )
        }
        "continent" -> TravelContinentDialog(
            current = travelContinentOf(countryId, baseMap, records),
            defaultContinent = baseMap[countryId]?.continent,
            onDismiss = close,
            onPick = {
                vm.setTravelContinent(countryId, it)
                close()
            },
        )
        "rename" -> {
            var name by rememberSaveable { mutableStateOf(record?.customName.orEmpty()) }
            AlertDialog(
                onDismissRequest = close,
                title = { Text(stringResource(R.string.travel_rename_country)) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.travel_country_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.editTravelUserCountry(countryId, name, null)
                            close()
                        },
                        enabled = name.isNotBlank(),
                    ) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
            )
        }
        "delete" -> ConfirmDialog(
            titleRes = R.string.travel_delete_country,
            textRes = R.string.travel_delete_country_text,
            confirmLabelRes = R.string.delete,
            onConfirm = {
                close()
                vm.removeTravelUserCountry(countryId)
            },
            onDismiss = close,
        )
    }
}

/**
 * The part of the map a card shows: the country's outline with a margin,
 * at least a region's worth so a small country is seen among its
 * neighbours, kept at the card's shape; for the user's own country, the
 * same around its point.
 */
private fun cardFocus(
    map: TravelWorldMap,
    countryId: String,
    point: TravelMapPoint?,
): FloatArray {
    val b: FloatArray = map.shapes[countryId]?.bounds?.let { floatArrayOf(it[0].toFloat(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) }
        ?: point?.let { floatArrayOf(it.x, it.y, it.x, it.y) }
        ?: floatArrayOf(0f, 0f, map.width.toFloat(), map.height.toFloat())
    val minSide = map.width / 16f
    val cx = (b[0] + b[2]) / 2
    val cy = (b[1] + b[3]) / 2
    var w = maxOf((b[2] - b[0]) * 1.3f, minSide)
    var h = maxOf((b[3] - b[1]) * 1.3f, minSide / 2)
    // The card is about twice as wide as it is tall.
    if (w < h * 2) w = h * 2 else h = w / 2
    return floatArrayOf(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}
