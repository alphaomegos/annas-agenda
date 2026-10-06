package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.components.ConfirmDialog
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.components.PickTimeDialog
import com.alphaomegos.annasagenda.components.TinyIconButton
import com.alphaomegos.annasagenda.model.Flight
import com.alphaomegos.annasagenda.support.flightAirlineName
import com.alphaomegos.annasagenda.support.flightAirportSuggestions
import com.alphaomegos.annasagenda.support.flightCityName
import com.alphaomegos.annasagenda.support.flightFlag
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A flight, new ([flightId] < 0) or kept (06.10): where from and to — typed
 * as a code, or found by city or airport name — when, by the schedule and,
 * if the user knows, in fact; the airline, number and aircraft by their
 * codes; class, seat, booking, a note. A city or an airline can be renamed
 * here, for every flight. Everything typed survives turning the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightEditScreen(
    vm: AppViewModel,
    flightId: Long,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val base = remember { vm.flightBase }
    val flight = remember(flightId) { vm.state.value.flights.firstOrNull { it.id == flightId } }
    val flown = remember(state.flights) { state.flights.flatMap { listOf(it.from, it.to) }.toSet() }

    var from by rememberSaveable(flightId) { mutableStateOf(flight?.from.orEmpty()) }
    var to by rememberSaveable(flightId) { mutableStateOf(flight?.to.orEmpty()) }
    // Dates and times as text ("2024-02-24", "14:05"): what the saved state can hold.
    var depDate by rememberSaveable(flightId) { mutableStateOf((flight?.departure?.toLocalDate() ?: LocalDate.now()).toString()) }
    var depTime by rememberSaveable(flightId) { mutableStateOf(flight?.departure?.toLocalTime()?.toString().orEmpty()) }
    var arrDate by rememberSaveable(flightId) { mutableStateOf(flight?.arrival?.toLocalDate()?.toString().orEmpty()) }
    var arrTime by rememberSaveable(flightId) { mutableStateOf(flight?.arrival?.toLocalTime()?.toString().orEmpty()) }
    var actDepDate by rememberSaveable(flightId) { mutableStateOf(flight?.actualDeparture?.toLocalDate()?.toString().orEmpty()) }
    var actDepTime by rememberSaveable(flightId) { mutableStateOf(flight?.actualDeparture?.toLocalTime()?.toString().orEmpty()) }
    var actArrDate by rememberSaveable(flightId) { mutableStateOf(flight?.actualArrival?.toLocalDate()?.toString().orEmpty()) }
    var actArrTime by rememberSaveable(flightId) { mutableStateOf(flight?.actualArrival?.toLocalTime()?.toString().orEmpty()) }
    var airline by rememberSaveable(flightId) { mutableStateOf(flight?.airline.orEmpty()) }
    var number by rememberSaveable(flightId) { mutableStateOf(flight?.number.orEmpty()) }
    var aircraft by rememberSaveable(flightId) { mutableStateOf(flight?.aircraft.orEmpty()) }
    var seatClass by rememberSaveable(flightId) { mutableStateOf(flight?.seatClass.orEmpty()) }
    var seat by rememberSaveable(flightId) { mutableStateOf(flight?.seat.orEmpty()) }
    var booking by rememberSaveable(flightId) { mutableStateOf(flight?.booking.orEmpty()) }
    var note by rememberSaveable(flightId) { mutableStateOf(flight?.note.orEmpty()) }
    // Which picker is open: "depDate", "depTime", "arrDate", … or none.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    // A rename open: "city:SVO" or "airline:SU".
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    fun at(date: String, time: String): LocalDateTime? {
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val t = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
        return LocalDateTime.of(d, t)
    }

    fun save() {
        val f = from.trim().uppercase()
        val t = to.trim().uppercase()
        val dep = at(depDate, depTime)
        val arr = at(arrDate.ifBlank { depDate }, arrTime)?.let { a ->
            // An arrival "before" the departure on the same day is the next day.
            if (arrDate.isBlank() && dep != null && a < dep) a.plusDays(1) else a
        }
        val code = Regex("[A-Z]{3}")
        if (!f.matches(code) || !t.matches(code) || dep == null || arr == null) {
            invalid = true
            return
        }
        // An offset the import knew holds only for the airports and times it came with.
        val same = flight != null && flight.from == f && flight.to == t && flight.departure == dep && flight.arrival == arr
        val edited = Flight(
            id = flight?.id ?: 0L,
            from = f, to = t, departure = dep, arrival = arr,
            departureOffsetMinutes = if (same) flight?.departureOffsetMinutes else null,
            arrivalOffsetMinutes = if (same) flight?.arrivalOffsetMinutes else null,
            actualDeparture = at(actDepDate.ifBlank { depDate }, actDepTime),
            actualArrival = at(actArrDate.ifBlank { arr.toLocalDate().toString() }, actArrTime),
            airline = airline.trim().uppercase().ifBlank { null },
            number = number.trim().ifBlank { null },
            aircraft = aircraft.trim().uppercase().ifBlank { null },
            seatClass = seatClass.trim().ifBlank { null },
            seat = seat.trim().ifBlank { null },
            booking = booking.trim().ifBlank { null },
            note = note.trim(),
            journeyId = flight?.journeyId,
            keptApart = flight?.keptApart ?: false,
        )
        if (flight == null) vm.addFlight(edited) else vm.editFlight(edited)
        onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (flight == null) R.string.flights_add else R.string.flights_edit)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) } },
                actions = { TextButton(onClick = { save() }) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AirportField(stringResource(R.string.flights_from), from, base, state.flightCityNames, flown, { from = it }) {
                renaming = "city:$it"
            }
            AirportField(stringResource(R.string.flights_to), to, base, state.flightCityNames, flown, { to = it }) {
                renaming = "city:$it"
            }

            WhenRow(stringResource(R.string.flights_departure), depDate, depTime, { picking = "depDate" }, { picking = "depTime" })
            WhenRow(
                stringResource(R.string.flights_arrival), arrDate.ifBlank { depDate }, arrTime,
                { picking = "arrDate" }, { picking = "arrTime" },
            )
            WhenRow(
                stringResource(R.string.flights_actual_departure), actDepDate.ifBlank { depDate }.takeIf { actDepTime.isNotBlank() }.orEmpty(), actDepTime,
                { picking = "actDepDate" }, { picking = "actDepTime" },
                onClear = { actDepDate = ""; actDepTime = "" }.takeIf { actDepTime.isNotBlank() },
            )
            WhenRow(
                stringResource(R.string.flights_actual_arrival), actArrDate.ifBlank { arrDate.ifBlank { depDate } }.takeIf { actArrTime.isNotBlank() }.orEmpty(), actArrTime,
                { picking = "actArrDate" }, { picking = "actArrTime" },
                onClear = { actArrDate = ""; actArrTime = "" }.takeIf { actArrTime.isNotBlank() },
            )

            CodeField(stringResource(R.string.flights_airline), airline) { airline = it }
            val airlineCode = airline.trim().uppercase()
            if (airlineCode.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Hint(flightAirlineName(airlineCode, base, state.flightAirlineNames), Modifier.weight(1f))
                    TinyIconButton(onClick = { renaming = "airline:$airlineCode" }, icon = Icons.Default.Edit, cd = stringResource(R.string.flights_rename_airline))
                }
            }
            TextLine(stringResource(R.string.flights_number), number, KeyboardCapitalization.Characters) { number = it }
            CodeField(stringResource(R.string.flights_aircraft), aircraft) { aircraft = it }
            base.aircraft[aircraft.trim().uppercase()]?.let { Hint(it) }
            TextLine(stringResource(R.string.flights_class), seatClass, KeyboardCapitalization.Sentences) { seatClass = it }
            TextLine(stringResource(R.string.flights_seat), seat, KeyboardCapitalization.Characters) { seat = it }
            TextLine(stringResource(R.string.flights_booking), booking, KeyboardCapitalization.Characters) { booking = it }
            TextLine(stringResource(R.string.flights_note), note, KeyboardCapitalization.Sentences, single = false) { note = it }

            if (invalid) Text(stringResource(R.string.flights_invalid), color = MaterialTheme.colorScheme.error)

            if (flight != null) {
                TextButton(onClick = { deleting = true }) {
                    Text(stringResource(R.string.flights_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    when (val p = picking) {
        null -> Unit
        "depDate", "arrDate", "actDepDate", "actArrDate" -> {
            val current = when (p) {
                "depDate" -> depDate
                "arrDate" -> arrDate.ifBlank { depDate }
                "actDepDate" -> actDepDate.ifBlank { depDate }
                else -> actArrDate.ifBlank { arrDate.ifBlank { depDate } }
            }
            PickDateDialog(
                initialDate = runCatching { LocalDate.parse(current) }.getOrNull(),
                onDismiss = { picking = null },
                onPicked = { d ->
                    when (p) {
                        "depDate" -> depDate = d.toString()
                        "arrDate" -> arrDate = d.toString()
                        "actDepDate" -> actDepDate = d.toString()
                        else -> actArrDate = d.toString()
                    }
                },
            )
        }
        else -> {
            val current = when (p) {
                "depTime" -> depTime
                "arrTime" -> arrTime
                "actDepTime" -> actDepTime.ifBlank { depTime }
                else -> actArrTime.ifBlank { arrTime }
            }
            PickTimeDialog(
                initialTime = runCatching { LocalTime.parse(current) }.getOrNull(),
                onDismiss = { picking = null },
                onPicked = { t ->
                    when (p) {
                        "depTime" -> depTime = t.toString()
                        "arrTime" -> arrTime = t.toString()
                        "actDepTime" -> actDepTime = t.toString()
                        else -> actArrTime = t.toString()
                    }
                },
            )
        }
    }

    if (deleting && flight != null) {
        ConfirmDialog(
            titleRes = R.string.flights_delete,
            textRes = R.string.flights_delete_text,
            onConfirm = {
                deleting = false
                vm.removeFlight(flight.id)
                onBack()
            },
            onDismiss = { deleting = false },
        )
    }

    val r = renaming
    if (r != null) {
        val kind = r.substringBefore(':')
        val code = r.substringAfter(':')
        val current = if (kind == "city") flightCityName(code, base, state.flightCityNames) else flightAirlineName(code, base, state.flightAirlineNames)
        RenameDialog(
            title = stringResource(if (kind == "city") R.string.flights_rename_city else R.string.flights_rename_airline),
            initial = current,
            onDismiss = { renaming = null },
            onSave = { name ->
                if (kind == "city") vm.renameFlightCity(code, name) else vm.renameFlightAirline(code, name)
                renaming = null
            },
        )
    }
}

/** An airport: typed as a code or found by city or name; under it, what the code is, and the city's pencil. */
@Composable
private fun AirportField(
    label: String,
    value: String,
    base: com.alphaomegos.annasagenda.model.FlightBase,
    cityNames: Map<String, String>,
    flown: Set<String>,
    onChange: (String) -> Unit,
    onRename: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    val code = value.trim().uppercase()
    val known = base.airports[code]
    if (known != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hint(
                (flightFlag(known.country) + " " + flightCityName(code, base, cityNames) + " · " + known.name).trim(),
                Modifier.weight(1f),
            )
            TinyIconButton(onClick = { onRename(code) }, icon = Icons.Default.Edit, cd = stringResource(R.string.flights_rename_city))
        }
    } else if (value.isNotBlank()) {
        val offered = remember(value, cityNames) { flightAirportSuggestions(base, cityNames, value, flown) }
        if (offered.isEmpty()) Hint(stringResource(R.string.flights_unknown_airport))
        offered.forEach { c ->
            val a = base.airports.getValue(c)
            Text(
                "$c — ${flightFlag(a.country)} ${flightCityName(c, base, cityNames)} · ${a.name}",
                modifier = Modifier.fillMaxWidth().clickable { onChange(c) }.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun WhenRow(
    label: String,
    date: String,
    time: String,
    onDate: () -> Unit,
    onTime: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val notSet = stringResource(R.string.flights_not_set)
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onDate) { Text(date.ifBlank { notSet }) }
        OutlinedButton(onClick = onTime) { Text(time.ifBlank { notSet }) }
        if (onClear != null) TextButton(onClick = onClear) { Text(stringResource(R.string.flights_clear)) }
    }
}

@Composable
private fun CodeField(label: String, value: String, onChange: (String) -> Unit) =
    TextLine(label, value, KeyboardCapitalization.Characters, onChange = onChange)

@Composable
private fun TextLine(
    label: String,
    value: String,
    capitalization: KeyboardCapitalization,
    single: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = single,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A city's or an airline's own name, for every flight; empty brings back the usual one. */
@Composable
private fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.flights_rename_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.flights_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
