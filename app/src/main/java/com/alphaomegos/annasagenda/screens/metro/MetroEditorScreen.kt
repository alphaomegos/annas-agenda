package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.components.ConfirmDialog
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.model.MetroClosure
import com.alphaomegos.annasagenda.model.MetroHintTarget
import com.alphaomegos.annasagenda.model.MetroLine
import com.alphaomegos.annasagenda.model.MetroScheme
import com.alphaomegos.annasagenda.model.MetroStation
import com.alphaomegos.annasagenda.support.MetroClosable
import com.alphaomegos.annasagenda.support.formatShortDate
import com.alphaomegos.annasagenda.support.metroBranchAdded
import com.alphaomegos.annasagenda.support.metroBranchRemoved
import com.alphaomegos.annasagenda.support.metroClosureOf
import com.alphaomegos.annasagenda.support.metroClosureSet
import com.alphaomegos.annasagenda.support.metroExitAdded
import com.alphaomegos.annasagenda.support.metroExitRemoved
import com.alphaomegos.annasagenda.support.metroExitRenamed
import com.alphaomegos.annasagenda.support.metroHintRemoved
import com.alphaomegos.annasagenda.support.metroHintSet
import com.alphaomegos.annasagenda.support.metroLineAdded
import com.alphaomegos.annasagenda.support.metroLineEdited
import com.alphaomegos.annasagenda.support.metroLineMoved
import com.alphaomegos.annasagenda.support.metroLineRemoved
import com.alphaomegos.annasagenda.support.metroSchemeRenamed
import com.alphaomegos.annasagenda.support.metroSchemeWithDefaults
import com.alphaomegos.annasagenda.support.metroSegmentTimeSet
import com.alphaomegos.annasagenda.support.metroSidesOf
import com.alphaomegos.annasagenda.support.metroStationAdded
import com.alphaomegos.annasagenda.support.metroStationMoved
import com.alphaomegos.annasagenda.support.metroStationRemoved
import com.alphaomegos.annasagenda.support.metroStationRenamed
import com.alphaomegos.annasagenda.support.metroTrackPlace
import com.alphaomegos.annasagenda.support.metroTransferRemoved
import com.alphaomegos.annasagenda.support.metroTransferSet
import com.alphaomegos.annasagenda.util.appLocale

/*
 * The metro editor (04.10): the scheme, a line, a station — three screens,
 * each a step deeper, so "Back" goes up one level as one expects.
 *
 * Every change is one rule from support/MetroEditing.kt or MetroClosures.kt,
 * handed to the view model; the rules keep hints, ride times and the rest in
 * step with the line's shape. Which dialog is open is kept by name, so
 * turning the phone keeps it open.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetroEditorScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
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
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** "closed", or "closed until 20.10", or null when open. */
@Composable
private fun closedLabel(closure: MetroClosure?): String? {
    if (closure == null) return null
    val day = closure.expectedOpening ?: return stringResource(R.string.metro_closed_short)
    return stringResource(R.string.metro_closed_until_short, formatShortDate(day, appLocale()))
}

/** A row that opens something, with what it is set to underneath. */
@Composable
private fun EditorRow(title: String, summary: String? = null, dimmed: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (dimmed) 0.5f else 1f)
            .padding(vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A short list of things to do with one item. */
@Composable
private fun ActionsDialog(title: String, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                actions.forEach { (label, action) ->
                    TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/* ---------------- the scheme ---------------- */

@Composable
fun MetroSchemeEditorScreen(
    vm: AppViewModel,
    schemeId: Long,
    onBack: () -> Unit,
    onOpenLine: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = state.metroSchemes.firstOrNull { it.id == schemeId }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { dialog = null }

    MetroEditorScaffold(scheme?.city ?: stringResource(R.string.menu_metro), onBack) {
        if (scheme == null) return@MetroEditorScaffold

        EditorRow(stringResource(R.string.metro_city), scheme.city) { dialog = "city" }
        EditorRow(
            stringResource(R.string.metro_defaults),
            stringResource(R.string.metro_defaults_summary, scheme.defaultSegmentMinutes, scheme.defaultTransferMinutes),
        ) { dialog = "defaults" }

        SectionTitle(stringResource(R.string.metro_lines))
        scheme.lines.forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenLine(line.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LineDot(line, size = 16)
                    Text(lineTitle(line))
                }
                IconButton(onClick = { vm.editMetroScheme(schemeId) { metroLineMoved(it, line.id, -1) } }, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.metro_move_up))
                }
                IconButton(
                    onClick = { vm.editMetroScheme(schemeId) { metroLineMoved(it, line.id, +1) } },
                    enabled = index < scheme.lines.lastIndex,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.metro_move_down))
                }
            }
        }
        OutlinedButton(onClick = { dialog = "line" }) { Text(stringResource(R.string.metro_add_line)) }

        HorizontalDivider(modifier = Modifier.padding(top = 24.dp))
        TextButton(onClick = { dialog = "delete" }) {
            Text(stringResource(R.string.metro_delete_scheme), color = MaterialTheme.colorScheme.error)
        }
    }

    if (scheme == null) return
    when (dialog) {
        "city" -> MetroTextDialog(
            title = stringResource(R.string.metro_city),
            label = stringResource(R.string.metro_city),
            initial = scheme.city,
            onDismiss = close,
            onSave = {
                vm.editMetroScheme(schemeId) { s -> metroSchemeRenamed(s, it) }
                close()
            },
        )
        "defaults" -> MetroDefaultsDialog(
            segment = scheme.defaultSegmentMinutes,
            transfer = scheme.defaultTransferMinutes,
            onDismiss = close,
            onSave = { seg, tr ->
                vm.editMetroScheme(schemeId) { metroSchemeWithDefaults(it, seg, tr) }
                close()
            },
        )
        "line" -> MetroLineDialog(
            line = null,
            onDismiss = close,
            onSave = { label, name, color, ring, cars, doors ->
                vm.editMetroScheme(schemeId) { s ->
                    val added = metroLineAdded(s, label, name, color, ring)
                    val line = added.lines.lastOrNull()
                    if (added === s || line == null) s
                    else metroLineEdited(added, line.id, label, name, color, ring, cars, doors)
                }
                close()
            },
        )
        "delete" -> ConfirmDialog(
            titleRes = R.string.metro_delete_scheme,
            textRes = R.string.metro_delete_scheme_text,
            confirmLabelRes = R.string.delete,
            onConfirm = {
                close()
                vm.removeMetroScheme(schemeId)
                onBack()
            },
            onDismiss = close,
        )
    }
}

@Composable
private fun lineTitle(line: MetroLine): String =
    stringResource(R.string.metro_line, line.label) + if (line.name.isNotBlank()) " · ${line.name}" else ""

/* ---------------- a line ---------------- */

@Composable
fun MetroLineEditorScreen(
    vm: AppViewModel,
    schemeId: Long,
    lineId: Long,
    onBack: () -> Unit,
    onOpenStation: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = state.metroSchemes.firstOrNull { it.id == schemeId }
    val line = scheme?.lines?.firstOrNull { it.id == lineId }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { dialog = null }

    MetroEditorScaffold(line?.let { lineTitle(it) } ?: stringResource(R.string.menu_metro), onBack) {
        if (scheme == null || line == null) return@MetroEditorScaffold
        val stations = scheme.stations.associateBy { it.id }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineDot(line, size = 16)
            TextButton(onClick = { dialog = "edit" }) { Text(stringResource(R.string.metro_edit_line)) }
        }

        SectionTitle(stringResource(R.string.metro_stations))
        TrackRows(line.trunk, stations, schemeId, vm, onOpenStation)
        OutlinedButton(onClick = { dialog = "add:t" }) { Text(stringResource(R.string.metro_add_station)) }

        line.branches.forEach { branch ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.metro_branch_from, stations[branch.fromStationId]?.name.orEmpty()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(top = 16.dp),
                )
                IconButton(onClick = { dialog = "delbranch:${branch.id}" }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.metro_delete_branch))
                }
            }
            TrackRows(branch.stationIds, stations, schemeId, vm, onOpenStation)
            OutlinedButton(onClick = { dialog = "add:${branch.id}" }) { Text(stringResource(R.string.metro_add_station)) }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 24.dp))
        TextButton(onClick = { dialog = "delete" }) {
            Text(stringResource(R.string.metro_delete_line), color = MaterialTheme.colorScheme.error)
        }
    }

    if (scheme == null || line == null) return
    val d = dialog
    when {
        d == "edit" -> MetroLineDialog(
            line = line,
            onDismiss = close,
            onSave = { label, name, color, ring, cars, doors ->
                vm.editMetroScheme(schemeId) { metroLineEdited(it, lineId, label, name, color, ring, cars, doors) }
                close()
            },
        )
        d != null && d.startsWith("add:") -> {
            val branchId = d.removePrefix("add:").toLongOrNull()
            MetroTextDialog(
                title = stringResource(R.string.metro_add_station),
                label = stringResource(R.string.metro_station_name),
                initial = "",
                onDismiss = close,
                onSave = { name ->
                    vm.editMetroScheme(schemeId) { metroStationAdded(it, lineId, branchId, Int.MAX_VALUE, name) }
                    close()
                },
            )
        }
        d != null && d.startsWith("delbranch:") -> ConfirmDialog(
            titleRes = R.string.metro_delete_branch,
            textRes = R.string.metro_delete_branch_text,
            confirmLabelRes = R.string.delete,
            onConfirm = {
                d.removePrefix("delbranch:").toLongOrNull()?.let { id ->
                    vm.editMetroScheme(schemeId) { metroBranchRemoved(it, lineId, id) }
                }
                close()
            },
            onDismiss = close,
        )
        d == "delete" -> ConfirmDialog(
            titleRes = R.string.metro_delete_line,
            textRes = R.string.metro_delete_line_text,
            confirmLabelRes = R.string.delete,
            onConfirm = {
                close()
                vm.editMetroScheme(schemeId) { metroLineRemoved(it, lineId) }
                onBack()
            },
            onDismiss = close,
        )
    }
}

/** A track's stations in order, each to be opened or moved one place. */
@Composable
private fun TrackRows(
    ids: List<Long>,
    stations: Map<Long, MetroStation>,
    schemeId: Long,
    vm: AppViewModel,
    onOpenStation: (Long) -> Unit,
) {
    ids.forEachIndexed { index, id ->
        val station = stations[id] ?: return@forEachIndexed
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenStation(id) }
                    .alpha(if (station.closure != null) 0.5f else 1f)
                    .padding(vertical = 8.dp),
            ) {
                Text(station.name)
                closedLabel(station.closure)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { vm.editMetroScheme(schemeId) { metroStationMoved(it, id, -1) } }, enabled = index > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.metro_move_up))
            }
            IconButton(onClick = { vm.editMetroScheme(schemeId) { metroStationMoved(it, id, +1) } }, enabled = index < ids.lastIndex) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.metro_move_down))
            }
        }
    }
}

/* ---------------- a station ---------------- */

@Composable
fun MetroStationEditorScreen(
    vm: AppViewModel,
    schemeId: Long,
    stationId: Long,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = state.metroSchemes.firstOrNull { it.id == schemeId }
    val station = scheme?.stations?.firstOrNull { it.id == stationId }
    val line = station?.let { s -> scheme?.lines?.firstOrNull { it.id == s.lineId } }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { dialog = null }
    val edit = { f: (MetroScheme) -> MetroScheme -> vm.editMetroScheme(schemeId, f) }

    MetroEditorScaffold(station?.name ?: stringResource(R.string.menu_metro), onBack) {
        if (scheme == null || station == null || line == null) return@MetroEditorScaffold
        val names = scheme.stations.associate { it.id to it.name }
        val lines = scheme.lines.associateBy { it.id }
        val sides = metroSidesOf(line)[stationId].orEmpty().values.distinct()

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineDot(line, size = 16)
            Text(lineTitle(line), style = MaterialTheme.typography.titleSmall)
        }
        EditorRow(stringResource(R.string.metro_rename), station.name) { dialog = "rename" }
        EditorRow(
            if (station.closure == null) stringResource(R.string.metro_close) else stringResource(R.string.metro_open),
            closedLabel(station.closure),
        ) {
            if (station.closure == null) {
                dialog = "close:s$stationId"
            } else {
                edit { metroClosureSet(it, MetroClosable.Station(stationId), null) }
            }
        }
        if (station.closure != null) {
            EditorRow(stringResource(R.string.metro_change_date)) { dialog = "date:s$stationId" }
        }
        EditorRow(stringResource(R.string.metro_insert_before)) { dialog = "insert:0" }
        EditorRow(stringResource(R.string.metro_insert_after)) { dialog = "insert:1" }
        EditorRow(stringResource(R.string.metro_add_branch)) { dialog = "branch" }

        SectionTitle(stringResource(R.string.metro_ride_times))
        sides.forEach { n ->
            val set = scheme.segmentTimes.firstOrNull {
                (it.aStationId == stationId && it.bStationId == n) || (it.bStationId == stationId && it.aStationId == n)
            }?.minutes
            EditorRow(
                stringResource(R.string.metro_ride_time, names[n].orEmpty()),
                set?.let { stringResource(R.string.metro_minutes, it) }
                    ?: stringResource(R.string.metro_default_value, scheme.defaultSegmentMinutes),
            ) { dialog = "segment:$n" }
        }

        SectionTitle(stringResource(R.string.metro_transfers_title))
        scheme.transfers.filter { it.aStationId == stationId || it.bStationId == stationId }.forEach { t ->
            val other = if (t.aStationId == stationId) t.bStationId else t.aStationId
            val otherLine = scheme.stations.firstOrNull { it.id == other }?.lineId?.let { lines[it] }
            val minutes = t.minutes?.let { stringResource(R.string.metro_minutes, it) }
                ?: stringResource(R.string.metro_default_value, scheme.defaultTransferMinutes)
            EditorRow(
                stringResource(R.string.metro_transfer_to, names[other].orEmpty(), otherLine?.label.orEmpty()),
                listOfNotNull(minutes, closedLabel(t.closure)).joinToString(" · "),
                dimmed = t.closure != null,
            ) { dialog = "transfer:${t.id}" }
        }
        OutlinedButton(onClick = { dialog = "addtransfer" }) { Text(stringResource(R.string.metro_add_transfer)) }

        SectionTitle(stringResource(R.string.metro_exits))
        scheme.exits.filter { it.stationId == stationId }.forEach { e ->
            EditorRow(e.name, closedLabel(e.closure), dimmed = e.closure != null) { dialog = "exit:${e.id}" }
        }
        OutlinedButton(onClick = { dialog = "addexit" }) { Text(stringResource(R.string.metro_add_exit)) }

        SectionTitle(stringResource(R.string.metro_hints))
        scheme.hints.filter { it.stationId == stationId }.sortedWith(compareBy({ it.fromStationId }, { it.car }, { it.door })).forEach { h ->
            val what = when (val t = h.target) {
                is MetroHintTarget.Transfer -> stringResource(R.string.metro_hint_for_transfer_to, names[t.toStationId].orEmpty())
                is MetroHintTarget.Exit -> stringResource(
                    R.string.metro_hint_for_exit_name,
                    scheme.exits.firstOrNull { it.id == t.exitId }?.name.orEmpty(),
                )
            }
            val place = if (h.car == line.carCount && line.carCount > 1) stringResource(R.string.metro_last_car_door, h.door)
            else stringResource(R.string.metro_car_door, h.car, h.door)
            EditorRow(stringResource(R.string.metro_hint_from, names[h.fromStationId].orEmpty()), "$place — $what") {
                dialog = "hint:${h.id}"
            }
        }
        if (sides.isNotEmpty()) {
            OutlinedButton(onClick = { dialog = "addhint" }) { Text(stringResource(R.string.metro_add_hint)) }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 24.dp))
        TextButton(onClick = { dialog = "delete" }) {
            Text(stringResource(R.string.metro_delete_station), color = MaterialTheme.colorScheme.error)
        }
    }

    if (scheme == null || station == null || line == null) return
    val names = scheme.stations.associate { it.id to it.name }
    val d = dialog ?: return
    val arg = d.substringAfter(':', "")
    val argId = arg.toLongOrNull()

    when (d.substringBefore(':')) {
        "rename" -> MetroTextDialog(
            title = stringResource(R.string.metro_rename),
            label = stringResource(R.string.metro_station_name),
            initial = station.name,
            onDismiss = close,
            onSave = { name ->
                edit { metroStationRenamed(it, stationId, name) }
                close()
            },
        )
        "close" -> MetroCloseDialog(
            onDismiss = close,
            onPickDate = { dialog = "date:$arg" },
            onNoDate = {
                closableOf(arg)?.let { target -> edit { metroClosureSet(it, target, MetroClosure()) } }
                close()
            },
        )
        "date" -> {
            val target = closableOf(arg)
            PickDateDialog(
                initialDate = target?.let { metroClosureOf(scheme, it)?.expectedOpening },
                onDismiss = close,
                onPicked = { day ->
                    target?.let { t -> edit { metroClosureSet(it, t, MetroClosure(day)) } }
                    close()
                },
            )
        }
        "insert" -> {
            val place = metroTrackPlace(line, stationId)
            MetroTextDialog(
                title = stringResource(if (arg == "0") R.string.metro_insert_before else R.string.metro_insert_after),
                label = stringResource(R.string.metro_station_name),
                initial = "",
                onDismiss = close,
                onSave = { name ->
                    if (place != null) {
                        val at = place.index + (if (arg == "0") 0 else 1)
                        edit { metroStationAdded(it, line.id, place.branchId, at, name) }
                    }
                    close()
                },
            )
        }
        "branch" -> MetroTextDialog(
            title = stringResource(R.string.metro_add_branch),
            label = stringResource(R.string.metro_station_name),
            initial = "",
            onDismiss = close,
            onSave = { name ->
                edit { metroBranchAdded(it, line.id, stationId, name) }
                close()
            },
        )
        "segment" -> {
            val other = argId ?: return
            MetroMinutesDialog(
                title = stringResource(R.string.metro_ride_time, names[other].orEmpty()),
                initial = scheme.segmentTimes.firstOrNull {
                    (it.aStationId == stationId && it.bStationId == other) || (it.bStationId == stationId && it.aStationId == other)
                }?.minutes,
                allowZero = false,
                onDismiss = close,
                onSave = { minutes ->
                    edit { metroSegmentTimeSet(it, stationId, other, minutes) }
                    close()
                },
            )
        }
        "addtransfer" -> MetroTransferDialog(
            scheme = scheme,
            fromStationId = stationId,
            onDismiss = close,
            onPick = { other ->
                edit { metroTransferSet(it, stationId, other, null) }
                close()
            },
        )
        "transfer" -> {
            val t = scheme.transfers.firstOrNull { it.id == argId } ?: return
            ActionsDialog(
                title = names[if (t.aStationId == stationId) t.bStationId else t.aStationId].orEmpty(),
                actions = listOf(
                    act(stringResource(R.string.metro_minutes_label)) { dialog = "transfertime:${t.id}" },
                    act(if (t.closure == null) stringResource(R.string.metro_close) else stringResource(R.string.metro_open)) {
                        if (t.closure == null) {
                            dialog = "close:t${t.id}"
                        } else {
                            edit { metroClosureSet(it, MetroClosable.Transfer(t.id), null) }
                            close()
                        }
                    },
                    act(stringResource(R.string.delete)) {
                        edit { metroTransferRemoved(it, t.id) }
                        close()
                    },
                ),
                onDismiss = close,
            )
        }
        "transfertime" -> {
            val t = scheme.transfers.firstOrNull { it.id == argId } ?: return
            MetroMinutesDialog(
                title = stringResource(R.string.metro_transfers_title),
                initial = t.minutes,
                allowZero = true,
                onDismiss = close,
                onSave = { minutes ->
                    edit { metroTransferSet(it, t.aStationId, t.bStationId, minutes) }
                    close()
                },
            )
        }
        "addexit" -> MetroTextDialog(
            title = stringResource(R.string.metro_add_exit),
            label = stringResource(R.string.metro_exit_name),
            initial = "",
            onDismiss = close,
            onSave = { name ->
                edit { metroExitAdded(it, stationId, name) }
                close()
            },
        )
        "exit" -> {
            val e = scheme.exits.firstOrNull { it.id == argId } ?: return
            ActionsDialog(
                title = e.name,
                actions = listOf(
                    act(stringResource(R.string.metro_rename)) { dialog = "exitname:${e.id}" },
                    act(if (e.closure == null) stringResource(R.string.metro_close) else stringResource(R.string.metro_open)) {
                        if (e.closure == null) {
                            dialog = "close:e${e.id}"
                        } else {
                            edit { metroClosureSet(it, MetroClosable.Exit(e.id), null) }
                            close()
                        }
                    },
                    act(stringResource(R.string.delete)) {
                        edit { metroExitRemoved(it, e.id) }
                        close()
                    },
                ),
                onDismiss = close,
            )
        }
        "exitname" -> {
            val e = scheme.exits.firstOrNull { it.id == argId } ?: return
            MetroTextDialog(
                title = stringResource(R.string.metro_rename),
                label = stringResource(R.string.metro_exit_name),
                initial = e.name,
                onDismiss = close,
                onSave = { name ->
                    edit { metroExitRenamed(it, e.id, name) }
                    close()
                },
            )
        }
        "addhint", "edithint" -> {
            val old = if (d.startsWith("edithint")) scheme.hints.firstOrNull { it.id == argId } else null
            val sides = metroSidesOf(line)[stationId].orEmpty().values.distinct().map { it to names[it].orEmpty() }
            val targets = scheme.transfers
                .filter { it.aStationId == stationId || it.bStationId == stationId }
                .map { t ->
                    val other = if (t.aStationId == stationId) t.bStationId else t.aStationId
                    MetroHintChoice(
                        MetroHintTarget.Transfer(other),
                        stringResource(R.string.metro_hint_for_transfer_to, names[other].orEmpty()),
                    )
                } + scheme.exits.filter { it.stationId == stationId }.map { e ->
                MetroHintChoice(MetroHintTarget.Exit(e.id), stringResource(R.string.metro_hint_for_exit_name, e.name))
            }
            MetroHintDialog(
                sides = sides,
                targets = targets,
                carCount = line.carCount,
                doorsPerCar = line.doorsPerCar,
                initialFrom = old?.fromStationId,
                initialTarget = old?.let { h -> targets.indexOfFirst { it.target == h.target }.takeIf { it >= 0 } },
                initialCar = old?.car,
                initialDoor = old?.door,
                onDismiss = close,
                onSave = { from, target, car, door ->
                    edit { metroHintSet(it, old?.id, stationId, from, car, door, target) }
                    close()
                },
            )
        }
        "hint" -> {
            val h = scheme.hints.firstOrNull { it.id == argId } ?: return
            ActionsDialog(
                title = stringResource(R.string.metro_hint_from, names[h.fromStationId].orEmpty()),
                actions = listOf(
                    act(stringResource(R.string.metro_edit_hint)) { dialog = "edithint:${h.id}" },
                    act(stringResource(R.string.delete)) {
                        edit { metroHintRemoved(it, h.id) }
                        close()
                    },
                ),
                onDismiss = close,
            )
        }
        "delete" -> ConfirmDialog(
            titleRes = R.string.metro_delete_station,
            textRes = R.string.metro_delete_station_text,
            confirmLabelRes = R.string.delete,
            onConfirm = {
                close()
                edit { metroStationRemoved(it, stationId) }
                onBack()
            },
            onDismiss = close,
        )
    }
}

private fun act(label: String, run: () -> Unit): Pair<String, () -> Unit> = label to run

/** "s12", "t5", "e7" — a station, transfer or exit, as a dialog's name carries it. */
private fun closableOf(key: String): MetroClosable? {
    val id = key.drop(1).toLongOrNull() ?: return null
    return when (key.firstOrNull()) {
        's' -> MetroClosable.Station(id)
        't' -> MetroClosable.Transfer(id)
        'e' -> MetroClosable.Exit(id)
        else -> null
    }
}
