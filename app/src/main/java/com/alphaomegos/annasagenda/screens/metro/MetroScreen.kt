package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.model.MetroClosure
import com.alphaomegos.annasagenda.model.MetroHint
import com.alphaomegos.annasagenda.model.MetroHintTarget
import com.alphaomegos.annasagenda.model.MetroLine
import com.alphaomegos.annasagenda.model.MetroScheme
import com.alphaomegos.annasagenda.support.MetroClosable
import com.alphaomegos.annasagenda.support.MetroClosureDue
import com.alphaomegos.annasagenda.support.MetroRoute
import com.alphaomegos.annasagenda.support.MetroRouteStep
import com.alphaomegos.annasagenda.support.formatShortDate
import com.alphaomegos.annasagenda.support.metroClosedStations
import com.alphaomegos.annasagenda.support.metroClosureSet
import com.alphaomegos.annasagenda.support.metroClosuresDue
import com.alphaomegos.annasagenda.support.metroRoute
import com.alphaomegos.annasagenda.support.metroShown
import com.alphaomegos.annasagenda.support.metroStationSuggestions
import com.alphaomegos.annasagenda.support.metroStationsForName
import com.alphaomegos.annasagenda.util.appLocale
import java.time.LocalDate

/**
 * The metro (04.10): "from" and "to", and the way between them on the scheme
 * chosen in Settings.
 *
 * On the day a closed station, transfer or exit was expected to open, and
 * every day after until answered, the screen asks whether it has (decided
 * 04.10). "Later" puts the question off until the screen is next opened.
 *
 * Everything typed, and which question was put off, survives turning the
 * phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onEditScheme: (Long) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shown = remember(state.metroSchemes, state.metroSelection) {
        metroShown(state.metroSchemes, state.metroSelection, vm.metroLibrary)
    }
    val scheme = shown?.scheme
    // A library scheme is read-only: its pencil makes the user's own copy first.
    val libraryKey = shown?.libraryKey
    val ownScheme = scheme != null && libraryKey == null

    var fromText by rememberSaveable { mutableStateOf("") }
    var toText by rememberSaveable { mutableStateOf("") }
    // Which field the list of names is open under: "from", "to", or none.
    var typingIn by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var putOff by rememberSaveable { mutableStateOf(listOf<String>()) }
    var movingDateOf by rememberSaveable { mutableStateOf<String?>(null) }

    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(scheme?.city ?: stringResource(R.string.menu_metro)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    if (scheme != null) {
                        IconButton(onClick = {
                            val id = if (libraryKey == null) scheme.id else vm.copyMetroLibraryScheme(libraryKey)
                            if (id != null) onEditScheme(id)
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.metro_edit))
                        }
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (scheme == null) {
                Text(stringResource(R.string.metro_no_scheme))
                Button(onClick = { creating = true }) { Text(stringResource(R.string.metro_create_scheme)) }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StationField(
                        label = stringResource(R.string.metro_from),
                        value = fromText,
                        onValueChange = {
                            fromText = it
                            typingIn = "from"
                        },
                    )
                    if (typingIn == "from") {
                        Suggestions(scheme, fromText) {
                            fromText = it
                            typingIn = null
                        }
                    }
                    StationField(
                        label = stringResource(R.string.metro_to),
                        value = toText,
                        onValueChange = {
                            toText = it
                            typingIn = "to"
                        },
                    )
                    if (typingIn == "to") {
                        Suggestions(scheme, toText) {
                            toText = it
                            typingIn = null
                        }
                    }
                }
                IconButton(onClick = {
                    val was = fromText
                    fromText = toText
                    toText = was
                    typingIn = null
                }) {
                    Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.metro_swap))
                }
            }

            if (fromText.isNotBlank() && toText.isNotBlank()) {
                HorizontalDivider()
                RouteOrWhyNot(scheme, fromText, toText)
            }
        }
    }

    if (creating) {
        CreateSchemeDialog(
            onDismiss = { creating = false },
            onCreate = { city ->
                val id = vm.createMetroScheme(city)
                if (id != null) {
                    creating = false
                    onEditScheme(id)
                }
            },
        )
    }

    // The question about an opening day, one at a time.
    val due = if (scheme != null && ownScheme) {
        metroClosuresDue(scheme, today).firstOrNull { closableKey(it.target) !in putOff }
    } else {
        null
    }
    if (scheme != null && due != null) {
        val key = closableKey(due.target)
        if (movingDateOf == key) {
            PickDateDialog(
                initialDate = today.plusDays(7),
                onDismiss = { movingDateOf = null },
                onPicked = { day ->
                    vm.editMetroScheme(scheme.id) { metroClosureSet(it, due.target, MetroClosure(day)) }
                    movingDateOf = null
                },
            )
        } else {
            ReopenDialog(
                scheme = scheme,
                due = due,
                onOpened = { vm.editMetroScheme(scheme.id) { metroClosureSet(it, due.target, null) } },
                onMoveDate = { movingDateOf = key },
                onLater = { putOff = putOff + key },
            )
        }
    }
}

private fun closableKey(target: MetroClosable): String = when (target) {
    is MetroClosable.Station -> "s${target.id}"
    is MetroClosable.Transfer -> "t${target.id}"
    is MetroClosable.Exit -> "e${target.id}"
}

@Composable
private fun StationField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The names the typed text could stand for, each with the colours of its lines. */
@Composable
private fun Suggestions(scheme: MetroScheme, typed: String, onPick: (String) -> Unit) {
    val choices = remember(scheme, typed) { metroStationSuggestions(scheme, typed) }
    val lines = remember(scheme) { scheme.lines.associateBy { it.id } }
    choices.forEach { choice ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(choice.name) }
                .alpha(if (choice.allClosed) 0.45f else 1f)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            choice.lineIds.forEach { id -> lines[id]?.let { LineDot(it, size = 20) } }
            Text(choice.name, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/**
 * A line as people see it on the map: its number in a circle of its colour
 * (05.10 — no word "line" beside it). A longer number ("11А", "D1") widens
 * the circle into a pill; the number is dark on a light colour, light on a
 * dark one.
 */
@Composable
internal fun LineDot(line: MetroLine, size: Int = 22) {
    val background = Color(line.color)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = size.dp, minHeight = size.dp)
            .background(background, RoundedCornerShape(percent = 50))
            .padding(horizontal = (size / 6).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line.label,
            color = if (background.luminance() > 0.55f) Color.Black else Color.White,
            fontSize = (size * 0.48f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The route, or the reason there is none. */
@Composable
private fun RouteOrWhyNot(scheme: MetroScheme, fromText: String, toText: String) {
    val locale = appLocale()
    val fromIds = remember(scheme, fromText) { metroStationsForName(scheme, fromText) }
    val toIds = remember(scheme, toText) { metroStationsForName(scheme, toText) }

    @Composable
    fun closedMessage(ids: Set<Long>): String? {
        if (ids.isEmpty() || metroClosedStations(scheme, ids).size != ids.size) return null
        val station = scheme.stations.first { it.id in ids }
        val day = scheme.stations.filter { it.id in ids }.mapNotNull { it.closure?.expectedOpening }.maxOrNull()
        return if (day == null) stringResource(R.string.metro_station_closed, station.name)
        else stringResource(R.string.metro_station_closed_until, station.name, formatShortDate(day, locale))
    }

    val problem = when {
        fromIds.isEmpty() -> stringResource(R.string.metro_unknown_station, fromText.trim())
        toIds.isEmpty() -> stringResource(R.string.metro_unknown_station, toText.trim())
        else -> closedMessage(fromIds) ?: closedMessage(toIds)
    }
    if (problem != null) {
        Text(problem, color = MaterialTheme.colorScheme.error)
        return
    }

    val route = remember(scheme, fromIds, toIds) { metroRoute(scheme, fromIds, toIds) }
    when {
        route == null -> Text(stringResource(R.string.metro_no_route), color = MaterialTheme.colorScheme.error)
        route.steps.isEmpty() -> Text(stringResource(R.string.metro_same_station))
        else -> RouteSteps(scheme, route)
    }
}

@Composable
private fun RouteSteps(scheme: MetroScheme, route: MetroRoute) {
    val stations = remember(scheme) { scheme.stations.associateBy { it.id } }
    val lines = remember(scheme) { scheme.lines.associateBy { it.id } }
    fun nameOf(id: Long) = stations[id]?.name.orEmpty()

    val transfers = if (route.transfers == 0) stringResource(R.string.metro_no_transfers)
    else pluralStringResource(R.plurals.metro_transfers, route.transfers, route.transfers)
    Text(
        stringResource(R.string.metro_minutes, route.minutes) + " · " + transfers,
        style = MaterialTheme.typography.titleMedium,
    )

    route.steps.forEach { step ->
        when (step) {
            is MetroRouteStep.Ride -> {
                val line = lines[step.lineId] ?: return@forEach
                RideStep(scheme, line, step, ::nameOf)
            }
            is MetroRouteStep.Change -> {
                val fork = step.fromStationId == step.toStationId
                val text = if (fork) {
                    stringResource(R.string.metro_change_fork, nameOf(step.toStationId))
                } else {
                    stringResource(R.string.metro_change_to, nameOf(step.toStationId))
                }
                val toLine = stations[step.toStationId]?.lineId?.let { lines[it] }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text + " · " + stringResource(R.string.metro_minutes, step.minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!fork && toLine != null) LineDot(toLine, size = 20)
                }
            }
        }
    }
}

@Composable
private fun RideStep(scheme: MetroScheme, line: MetroLine, ride: MetroRouteStep.Ride, nameOf: (Long) -> String) {
    val exits = remember(scheme) { scheme.exits.associateBy { it.id } }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineDot(line, size = 24)
            if (line.name.isNotBlank()) Text(line.name, style = MaterialTheme.typography.titleSmall)
        }
        val towards = ride.towardsStationIds.ifEmpty { listOf(ride.stationIds[1]) }
        val towardsText = towards.map { stringResource(R.string.metro_quoted, nameOf(it)) }.joinToString(", ")
        Text(nameOf(ride.stationIds.first()) + ", " + stringResource(R.string.metro_towards, towardsText))
        val count = ride.stationIds.size - 1
        Text(
            pluralStringResource(R.plurals.metro_ride_stations, count, count, nameOf(ride.stationIds.last())) +
                " · " + stringResource(R.string.metro_minutes, ride.minutes),
        )
        ride.passedClosedStationIds.forEach {
            Text(
                stringResource(R.string.metro_passes_closed, nameOf(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val transferHints = ride.hints.filter { it.target is MetroHintTarget.Transfer }
        if (transferHints.isNotEmpty()) {
            Text(
                stringResource(R.string.metro_hint_transfer, transferHints.map { carDoor(line, it) }.joinToString("; ")),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ride.hints.forEach { hint ->
            val target = hint.target
            if (target is MetroHintTarget.Exit) {
                Text(
                    stringResource(R.string.metro_hint_exit, carDoor(line, hint), exits[target.exitId]?.name.orEmpty()),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** "car 3, door 2", or "last car, door 4" when it is the last one. */
@Composable
private fun carDoor(line: MetroLine, hint: MetroHint): String =
    if (hint.car == line.carCount && line.carCount > 1) stringResource(R.string.metro_last_car_door, hint.door)
    else stringResource(R.string.metro_car_door, hint.car, hint.door)

@Composable
private fun CreateSchemeDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var city by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_create_scheme)) },
        text = {
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text(stringResource(R.string.metro_city)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(city) }, enabled = city.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ReopenDialog(
    scheme: MetroScheme,
    due: MetroClosureDue,
    onOpened: () -> Unit,
    onMoveDate: () -> Unit,
    onLater: () -> Unit,
) {
    val locale = appLocale()
    val day = formatShortDate(due.expectedOpening, locale)
    fun station(id: Long) = scheme.stations.firstOrNull { it.id == id }?.name.orEmpty()
    val text = when (val t = due.target) {
        is MetroClosable.Station -> stringResource(R.string.metro_reopen_station, station(t.id), day)
        is MetroClosable.Transfer -> {
            val tr = scheme.transfers.firstOrNull { it.id == t.id }
            stringResource(R.string.metro_reopen_transfer, station(tr?.aStationId ?: -1), station(tr?.bStationId ?: -1), day)
        }
        is MetroClosable.Exit -> {
            val exit = scheme.exits.firstOrNull { it.id == t.id }
            stringResource(R.string.metro_reopen_exit, exit?.name.orEmpty(), station(exit?.stationId ?: -1), day)
        }
    }
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.metro_reopen_title)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onOpened) { Text(stringResource(R.string.metro_reopen_yes)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLater) { Text(stringResource(R.string.metro_reopen_later)) }
                TextButton(onClick = onMoveDate) { Text(stringResource(R.string.metro_reopen_move)) }
            }
        },
    )
}
