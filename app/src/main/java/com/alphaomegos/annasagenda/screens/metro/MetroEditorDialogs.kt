package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.MetroHintTarget
import com.alphaomegos.annasagenda.model.MetroLine
import com.alphaomegos.annasagenda.model.MetroScheme
import com.alphaomegos.annasagenda.support.metroStationSuggestions

/**
 * Line colours to choose from: the usual ones of a metro map. Twenty-one, in
 * three rows of seven — seven is what fits across the folded phone (05.10).
 */
internal val MetroLineColors: List<Long> = listOf(
    0xFFE42313, 0xFF4FB04F, 0xFF0072BA, 0xFF1EBCEF, 0xFF915133, 0xFFF07E24, 0xFF943E90,
    0xFFFFD803, 0xFFADACAC, 0xFFBED12C, 0xFF88CDCF, 0xFFBAC8E8, 0xFFF9BCD1, 0xFFE94282,
    0xFF0A6F20, 0xFF00A099, 0xFF1A237E, 0xFFF6A600, 0xFFE95B0C, 0xFF40B280, 0xFF000000,
)

/** One line of text: a name, a city, an exit. Blank cannot be saved. */
@Composable
internal fun MetroTextDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Minutes for a ride or a transfer. "Default" clears what was set, so the
 * scheme's default applies again.
 */
@Composable
internal fun MetroMinutesDialog(
    title: String,
    initial: Int?,
    allowZero: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial?.toString().orEmpty()) }
    val value = text.trim().toIntOrNull()
    val ok = value != null && value >= (if (allowZero) 0 else 1) && value <= 240
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.metro_minutes_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = ok) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.metro_use_default)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** The scheme's defaults for rides and transfers nobody timed. */
@Composable
internal fun MetroDefaultsDialog(
    segment: Int,
    transfer: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var segmentText by rememberSaveable { mutableStateOf(segment.toString()) }
    var transferText by rememberSaveable { mutableStateOf(transfer.toString()) }
    val s = segmentText.toIntOrNull()?.takeIf { it in 1..60 }
    val t = transferText.toIntOrNull()?.takeIf { it in 1..60 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_defaults)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = segmentText,
                    onValueChange = { segmentText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.metro_default_segment)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = transferText,
                    onValueChange = { transferText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.metro_default_transfer)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (s != null && t != null) onSave(s, t) }, enabled = s != null && t != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** What a line is: number, name, colour, ring, and its train. [line] null makes a new one. */
@Composable
internal fun MetroLineDialog(
    line: MetroLine?,
    onDismiss: () -> Unit,
    onSave: (label: String, name: String, color: Long, ring: Boolean, cars: Int, doors: Int) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(line?.label.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(line?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(line?.color ?: MetroLineColors.first()) }
    var ring by rememberSaveable { mutableStateOf(line?.ring ?: false) }
    var cars by rememberSaveable { mutableStateOf((line?.carCount ?: 8).toString()) }
    var doors by rememberSaveable { mutableStateOf((line?.doorsPerCar ?: 4).toString()) }
    val carCount = cars.toIntOrNull()?.takeIf { it in 1..20 }
    val doorCount = doors.toIntOrNull()?.takeIf { it in 1..8 }
    val ok = label.isNotBlank() && carCount != null && doorCount != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (line == null) R.string.metro_add_line else R.string.metro_edit_line)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(6) },
                    label = { Text(stringResource(R.string.metro_line_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.metro_line_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Text(stringResource(R.string.metro_line_color), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetroLineColors.chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(c), CircleShape)
                                        .then(
                                            if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            else Modifier
                                        )
                                        .clickable { color = c },
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ring = !ring }) {
                    Checkbox(checked = ring, onCheckedChange = { ring = it })
                    Text(stringResource(R.string.metro_line_ring))
                }
                OutlinedTextField(
                    value = cars,
                    onValueChange = { cars = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.metro_line_cars)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = doors,
                    onValueChange = { doors = it.filter(Char::isDigit).take(1) },
                    label = { Text(stringResource(R.string.metro_line_doors)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (ok) onSave(label, name, color, ring, carCount!!, doorCount!!) },
                enabled = ok,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * A transfer from [fromStationId]: type the other station's name, pick it
 * from the stations of other lines so called.
 */
@Composable
internal fun MetroTransferDialog(
    scheme: MetroScheme,
    fromStationId: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val ownLine = scheme.stations.firstOrNull { it.id == fromStationId }?.lineId
    val lines = remember(scheme) { scheme.lines.associateBy { it.id } }
    val candidates = remember(scheme, typed) {
        val names = metroStationSuggestions(scheme, typed, limit = 20).map { it.name }.toSet() +
            scheme.stations.filter { it.name.equals(typed.trim(), ignoreCase = true) }.map { it.name }
        scheme.stations.filter { it.name in names && it.lineId != ownLine && it.id != fromStationId }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_add_transfer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.metro_station_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    candidates.forEach { st ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(st.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            lines[st.lineId]?.let { LineDot(it) }
                            Text(st.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Something to aim a hint at: a transfer to a station, or an exit. */
internal data class MetroHintChoice(val target: MetroHintTarget, val label: String)

/**
 * Where to sit, for getting off at a station: the side the train comes
 * from, what the place is good for, the car and the door.
 */
@Composable
internal fun MetroHintDialog(
    sides: List<Pair<Long, String>>,
    targets: List<MetroHintChoice>,
    carCount: Int,
    doorsPerCar: Int,
    initialFrom: Long?,
    initialTarget: Int?,
    initialCar: Int?,
    initialDoor: Int?,
    onDismiss: () -> Unit,
    onSave: (fromStationId: Long, target: MetroHintTarget, car: Int, door: Int) -> Unit,
) {
    var from by rememberSaveable { mutableStateOf(initialFrom ?: sides.firstOrNull()?.first) }
    var targetIndex by rememberSaveable { mutableStateOf(initialTarget ?: 0) }
    var car by rememberSaveable { mutableStateOf(initialCar?.toString().orEmpty()) }
    var door by rememberSaveable { mutableStateOf(initialDoor?.toString().orEmpty()) }
    val carValue = car.toIntOrNull()?.takeIf { it in 1..carCount }
    val doorValue = door.toIntOrNull()?.takeIf { it in 1..doorsPerCar }
    val target = targets.getOrNull(targetIndex)
    val ok = from != null && target != null && carValue != null && doorValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_add_hint)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(Modifier.selectableGroup()) {
                    sides.forEach { (id, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { from = id },
                        ) {
                            RadioButton(selected = from == id, onClick = { from = id })
                            Text(stringResource(R.string.metro_hint_from, name))
                        }
                    }
                }
                Column(Modifier.selectableGroup()) {
                    targets.forEachIndexed { i, choice ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { targetIndex = i },
                        ) {
                            RadioButton(selected = targetIndex == i, onClick = { targetIndex = i })
                            Text(choice.label)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = car,
                        onValueChange = { car = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.metro_hint_car_of, carCount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = door,
                        onValueChange = { door = it.filter(Char::isDigit).take(1) },
                        label = { Text(stringResource(R.string.metro_hint_door_of, doorsPerCar)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.metro_hint_counting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (ok) onSave(from!!, target!!.target, carValue!!, doorValue!!) },
                enabled = ok,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Closing for a while: with the day it is expected back, or not knowing it. */
@Composable
internal fun MetroCloseDialog(
    onDismiss: () -> Unit,
    onPickDate: () -> Unit,
    onNoDate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_close)) },
        text = { Text(stringResource(R.string.metro_close_when)) },
        confirmButton = {
            TextButton(onClick = onPickDate) { Text(stringResource(R.string.metro_close_pick_date)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onNoDate) { Text(stringResource(R.string.metro_close_no_date)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/**
 * A new station on a track, and where: at the start, or after one of the
 * stations already there — at the end unless told otherwise (05.10:
 * inserting is done here, not from a station).
 */
@Composable
internal fun MetroAddStationDialog(
    trackNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, index: Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    // The place the new station takes: 0 is the start, the track's length the end.
    var at by rememberSaveable { mutableStateOf(trackNames.size) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_add_station)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.metro_station_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (trackNames.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .selectableGroup(),
                    ) {
                        (0..trackNames.size).forEach { i ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { at = i },
                            ) {
                                RadioButton(selected = at == i, onClick = { at = i })
                                Text(
                                    if (i == 0) stringResource(R.string.metro_add_at_start)
                                    else stringResource(R.string.metro_add_after, trackNames[i - 1])
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, at) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
