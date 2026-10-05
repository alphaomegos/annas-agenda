package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.TravelContinent
import com.alphaomegos.annasagenda.support.TRAVEL_YEARS
import com.alphaomegos.annasagenda.util.appLocale
import java.time.LocalDate

/**
 * A trip (05.10): the year and month, required, and the cities one by one —
 * typed, then "+"; a city still in the field when saving is taken too.
 * Everything typed survives turning the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TravelTripDialog(
    title: String,
    initialYear: Int?,
    initialMonth: Int?,
    initialCities: List<String>,
    onDismiss: () -> Unit,
    onSave: (year: Int, month: Int, cities: List<String>) -> Unit,
) {
    val locale = appLocale()
    var yearText by rememberSaveable { mutableStateOf((initialYear ?: LocalDate.now().year).toString()) }
    var month by rememberSaveable { mutableStateOf(initialMonth ?: 0) }
    var cities by rememberSaveable { mutableStateOf(initialCities) }
    var cityText by rememberSaveable { mutableStateOf("") }
    val year = yearText.toIntOrNull()?.takeIf { it in TRAVEL_YEARS }
    val ok = year != null && month in 1..12

    fun addTyped() {
        val c = cityText.trim()
        if (c.isNotEmpty()) cities = cities + c
        cityText = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.travel_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                // Twelve months in four rows of three.
                (1..12).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { m ->
                            FilterChip(
                                selected = month == m,
                                onClick = { month = m },
                                label = { Text(travelMonthName(m, locale).take(3)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = cityText,
                        onValueChange = { cityText = it },
                        label = { Text(stringResource(R.string.travel_city)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { addTyped() }, enabled = cityText.isNotBlank()) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = stringResource(R.string.travel_add_city))
                    }
                }
                cities.forEachIndexed { i, city ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(city, modifier = Modifier.weight(1f))
                        IconButton(onClick = { cities = cities.filterIndexed { k, _ -> k != i } }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.travel_remove_city))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    addTyped()
                    if (year != null && month in 1..12) onSave(year, month, cities)
                },
                enabled = ok,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Which continent a country is counted on. [defaultContinent] is the base's,
 * offered first as "as in the list"; null for a country of the user's own,
 * which has no default.
 */
@Composable
internal fun TravelContinentDialog(
    current: TravelContinent?,
    defaultContinent: TravelContinent?,
    onDismiss: () -> Unit,
    onPick: (TravelContinent?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.travel_continent)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                TravelContinent.entries.forEach { c ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(if (c == defaultContinent) null else c) },
                    ) {
                        RadioButton(selected = c == current, onClick = { onPick(if (c == defaultContinent) null else c) })
                        Text(stringResource(travelContinentRes(c)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Terra incognito, first half: the name and the continent. The place is
 * picked next, with a tap on the map.
 */
@Composable
internal fun TravelUserCountryDialog(
    onDismiss: () -> Unit,
    onNext: (name: String, continent: TravelContinent) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var continentName by rememberSaveable { mutableStateOf(TravelContinent.EUROPE.name) }
    val continent = TravelContinent.entries.first { it.name == continentName }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.travel_terra_incognito)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.travel_terra_incognito_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.travel_country_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.selectableGroup().padding(top = 8.dp)) {
                    TravelContinent.entries.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { continentName = c.name },
                        ) {
                            RadioButton(selected = c == continent, onClick = { continentName = c.name })
                            Text(stringResource(travelContinentRes(c)))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onNext(name, continent) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.travel_place_next))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
