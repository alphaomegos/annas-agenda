package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.clickable
import com.alphaomegos.annasagenda.support.METRO_SCALE_MIN
import com.alphaomegos.annasagenda.support.METRO_SCALE_MAX
import com.alphaomegos.annasagenda.model.MetroScheme
import com.alphaomegos.annasagenda.model.MetroLabelsMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.components.ConfirmDialog
import com.alphaomegos.annasagenda.support.METRO_GRID_STEP
import com.alphaomegos.annasagenda.support.MetroPoint
import com.alphaomegos.annasagenda.support.metroLayout
import com.alphaomegos.annasagenda.support.metroLayoutCleared
import com.alphaomegos.annasagenda.support.metroStationPlaced
import com.alphaomegos.annasagenda.support.metroTransfersTogether
import com.alphaomegos.annasagenda.support.metroBendPointInserted
import com.alphaomegos.annasagenda.support.metroBendPointMoved
import com.alphaomegos.annasagenda.support.metroBendPointRemoved
import com.alphaomegos.annasagenda.support.metroElbowCycled
import com.alphaomegos.annasagenda.support.metroStationPlacedAlone
import com.alphaomegos.annasagenda.support.metroStationLabelCycled
import com.alphaomegos.annasagenda.support.metroSelectionPlaced
import com.alphaomegos.annasagenda.support.metroSelectionToggled
import com.alphaomegos.annasagenda.support.metroSelectionWholeLines
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Putting the picture right by hand (05.10): the map as laid out, a station
 * dragged by the finger that starts on it and let go where it belongs. The
 * first move keeps every other station where the picture showed it, so only
 * the one dragged moves. "Lay out again" forgets every place.
 *
 * Two ticks (06.10): the grid, which a dropped station snaps to, and
 * transfers kept together, their stations side by side and moving as one.
 * More (08.10): one name per interchange; rings glued edge to edge at 8 or
 * 12 points (off → 8 → 12); bending track (a tool: elbows and points);
 * moving an interchange whole or a station of it alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroMapEditorScreen(
    vm: AppViewModel,
    schemeId: Long,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = state.metroSchemes.firstOrNull { it.id == schemeId }
    val histories by vm.metroHistory.collectAsStateWithLifecycle()
    val history = histories[schemeId]
    val layout by produceState<Map<Long, MetroPoint>?>(null, scheme) {
        value = scheme?.let { s -> withContext(Dispatchers.Default) { metroLayout(s) } }
    }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // Tools of the editor (08.10), not settings: bending track, and moving an
    // interchange whole or one station of it.
    // One tool at a time (08.10): move stations, bend track, place names
    // (a tap moves a station's name round it), or select stations to move
    // together (a tap puts one in or out, a drag of one moves them all).
    var tool by rememberSaveable { mutableStateOf(TOOL_MOVE) }
    var selectedIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    val selection = selectedIds.toSet()
    val bending = tool == TOOL_BEND
    val naming = tool == TOOL_NAMES
    val selecting = tool == TOOL_SELECT
    var moveWhole by rememberSaveable { mutableStateOf(true) }
    // The look's panel open (08.10).
    var looking by rememberSaveable { mutableStateOf(false) }
    // Bumped by "Fit to the screen" (08.10).
    var fitKey by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.metro_place_stations)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                actions = {
                    // Undo and redo (08.10): every edit of the picture, back and forward.
                    IconButton(onClick = { vm.undoMetroScheme(schemeId) }, enabled = history?.canUndo == true) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.metro_undo))
                    }
                    IconButton(onClick = { vm.redoMetroScheme(schemeId) }, enabled = history?.canRedo == true) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.metro_redo))
                    }
                    IconButton(onClick = { fitKey++ }) {
                        Icon(Icons.Default.FitScreen, contentDescription = stringResource(R.string.metro_fit))
                    }
                    IconButton(onClick = { looking = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.metro_look))
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.metro_place_stations_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.metro_layout_again)) }
            }
            if (scheme != null) {
                // The tools stay at hand (08.10); how the picture looks is in "Look".
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        TOOL_MOVE to R.string.metro_tool_move,
                        TOOL_BEND to R.string.metro_bend_tracks,
                        TOOL_NAMES to R.string.metro_names_tool,
                        TOOL_SELECT to R.string.metro_tool_select,
                    ).forEach { (t, words) ->
                        FilterChip(selected = tool == t, onClick = { tool = t }, label = { Text(stringResource(words)) })
                    }
                }
                if (tool == TOOL_MOVE && scheme.transfersTogether) {
                    MetroMapTick(stringResource(R.string.metro_move_whole), moveWhole, Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { moveWhole = it }
                }
                if (selecting) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.metro_selected_count, selection.size),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { selectedIds = metroSelectionWholeLines(scheme, selection).toLongArray() }, enabled = selection.isNotEmpty()) {
                            Text(stringResource(R.string.metro_select_lines))
                        }
                        TextButton(onClick = { selectedIds = longArrayOf() }, enabled = selection.isNotEmpty()) {
                            Text(stringResource(R.string.metro_select_none))
                        }
                    }
                }
                if (bending || naming || selecting) {
                    Text(
                        stringResource(
                            when {
                                naming -> R.string.metro_names_hint
                                selecting -> R.string.metro_select_hint
                                else -> R.string.metro_bend_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
            val l = layout
            if (scheme != null && l != null) {
                MetroMapView(
                    scheme = scheme,
                    layout = l,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    // A whole interchange moves when kept together and so chosen; else
                    // the station alone, glued to its interchange if rings are glued.
                    onMoveStation = if (naming) null else { id, x, y ->
                        vm.editMetroScheme(schemeId) {
                            when {
                                selecting && id in selection -> metroSelectionPlaced(it, selection, id, x, y)
                                it.transfersTogether && moveWhole -> metroStationPlaced(it, id, x, y)
                                else -> metroStationPlacedAlone(it, id, x, y)
                            }
                        }
                    },
                    onTapStation = { id ->
                        when {
                            naming -> vm.editMetroScheme(schemeId) { metroStationLabelCycled(it, id) }
                            selecting -> { selectedIds = metroSelectionToggled(selection, id).toLongArray() }
                        }
                    },
                    namingMode = naming,
                    marked = if (selecting) selection else emptySet(),
                    group = if (selecting) selection else emptySet(),
                    fitKey = fitKey,
                    gridStep = if (scheme.mapGrid) METRO_GRID_STEP else null,
                    moveAlone = !(scheme.transfersTogether && moveWhole),
                    bendMode = bending,
                    onBend = { action ->
                        vm.editMetroScheme(schemeId) { s ->
                            when (action) {
                                is MetroBendAction.Cycle -> metroElbowCycled(s, metroLayout(s), action.track)
                                is MetroBendAction.Insert -> metroBendPointInserted(s, action.hit, action.x, action.y)
                                is MetroBendAction.Move -> metroBendPointMoved(s, action.hit, action.x, action.y)
                                is MetroBendAction.Remove -> metroBendPointRemoved(s, action.hit)
                            }
                        }
                    },
                )
            }
        }
    }

    if (looking && scheme != null) {
        MetroLookDialog(scheme, onEdit = { edit -> vm.editMetroScheme(schemeId, edit) }, onDismiss = { looking = false })
    }

    if (confirmReset) {
        ConfirmDialog(
            titleRes = R.string.metro_layout_again,
            textRes = R.string.metro_layout_again_text,
            onConfirm = {
                vm.editMetroScheme(schemeId) { metroLayoutCleared(it) }
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** A tick with its words; the words toggle it too. */
@Composable
private fun MetroMapTick(text: String, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier = modifier.clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * How the picture looks (08.10), all in one panel: the grid, interchanges
 * kept together and glued, one name per interchange, angles of 45°, the
 * links between interchange stations; when names are written; and the
 * size of names, track and rings. A slider is written when let go, so
 * undo takes it back in one step.
 */
@Composable
private fun MetroLookDialog(scheme: MetroScheme, onEdit: ((MetroScheme) -> MetroScheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_look)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MetroMapTick(stringResource(R.string.metro_map_grid), scheme.mapGrid, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(mapGrid = on) }
                }
                MetroMapTick(stringResource(R.string.metro_snap_angles), scheme.snapAngles, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(snapAngles = on) }
                }
                MetroMapTick(stringResource(R.string.metro_transfers_together), scheme.transfersTogether, Modifier.fillMaxWidth()) { on ->
                    onEdit { metroTransfersTogether(it, on) }
                }
                MetroMapTick(stringResource(R.string.metro_transfer_links), scheme.showTransferLinks, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(showTransferLinks = on) }
                }
                if (scheme.showTransferLinks) {
                    MetroMapTick(stringResource(R.string.metro_transfer_capsules), scheme.transferCapsules, Modifier.fillMaxWidth()) { on ->
                        onEdit { it.copy(transferCapsules = on) }
                    }
                }
                MetroMapTick(stringResource(R.string.metro_tick_stations), scheme.tickStations, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(tickStations = on) }
                }
                MetroMapTick(stringResource(R.string.metro_round_corners), scheme.roundCorners, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(roundCorners = on) }
                }
                MetroMapTick(stringResource(R.string.metro_line_casing), scheme.lineCasing, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(lineCasing = on) }
                }
                MetroMapTick(stringResource(R.string.metro_labels_once), scheme.labelsOncePerInterchange, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(labelsOncePerInterchange = on) }
                }
                // Off → 8 → 12 → off; interchanges kept together close up (or open out) to the new gap.
                val glueLabel = when (scheme.glue) {
                    8 -> stringResource(R.string.metro_glue, "8")
                    12 -> stringResource(R.string.metro_glue, "12")
                    else -> stringResource(R.string.metro_glue, stringResource(R.string.metro_glue_off))
                }
                TextButton(onClick = {
                    val next = when (scheme.glue) { 0 -> 8; 8 -> 12; else -> 0 }
                    onEdit {
                        val g = it.copy(glue = next)
                        if (g.transfersTogether) metroTransfersTogether(g.copy(transfersTogether = false), true) else g
                    }
                }) { Text(glueLabel) }

                Text(stringResource(R.string.metro_labels_when), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetroLabelsMode.entries.forEach { mode ->
                        FilterChip(
                            selected = scheme.labelsMode == mode,
                            onClick = { onEdit { it.copy(labelsMode = mode) } },
                            label = {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            MetroLabelsMode.AUTO -> R.string.metro_labels_auto
                                            MetroLabelsMode.ALWAYS -> R.string.metro_labels_always
                                            MetroLabelsMode.NEVER -> R.string.metro_labels_never
                                        }
                                    )
                                )
                            },
                        )
                    }
                }

                ScaleSlider(stringResource(R.string.metro_size_labels), scheme.labelScale) { v -> onEdit { it.copy(labelScale = v) } }
                ScaleSlider(stringResource(R.string.metro_size_lines), scheme.lineScale) { v -> onEdit { it.copy(lineScale = v) } }
                ScaleSlider(stringResource(R.string.metro_size_stations), scheme.stationScale) { v ->
                    // Glued rings stay touching at their new size.
                    onEdit {
                        val g = it.copy(stationScale = v)
                        if (g.glue > 0 && g.transfersTogether) metroTransfersTogether(g.copy(transfersTogether = false), true) else g
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** A size from half to two and a half times, written when the finger lets go. */
@Composable
private fun ScaleSlider(label: String, value: Float, onDone: (Float) -> Unit) {
    var v by rememberSaveable(value) { mutableStateOf(value) }
    Text(label + " · ×" + "%.1f".format(java.util.Locale.ROOT, v), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Slider(
        value = v,
        onValueChange = { v = it },
        valueRange = METRO_SCALE_MIN..METRO_SCALE_MAX,
        onValueChangeFinished = { onDone(v) },
    )
}

// The editor's tools (08.10).
private const val TOOL_MOVE = 0
private const val TOOL_BEND = 1
private const val TOOL_NAMES = 2
private const val TOOL_SELECT = 3
