package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
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
    val layout by produceState<Map<Long, MetroPoint>?>(null, scheme) {
        value = scheme?.let { s -> withContext(Dispatchers.Default) { metroLayout(s) } }
    }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // Tools of the editor (08.10), not settings: bending track, and moving an
    // interchange whole or one station of it.
    var bending by rememberSaveable { mutableStateOf(false) }
    var moveWhole by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.metro_place_stations)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
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
                // Two to a row (FlowRow breaks the tests, 0185).
                val glueLabel = when (scheme.glue) {
                    8 -> stringResource(R.string.metro_glue, "8")
                    12 -> stringResource(R.string.metro_glue, "12")
                    else -> stringResource(R.string.metro_glue, stringResource(R.string.metro_glue_off))
                }
                @Composable
                fun TickRow(content: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        content(Modifier.weight(1f))
                        second(Modifier.weight(1f))
                    }
                }
                TickRow(
                    { m ->
                        MetroMapTick(stringResource(R.string.metro_map_grid), scheme.mapGrid, m) { on ->
                            vm.editMetroScheme(schemeId) { it.copy(mapGrid = on) }
                        }
                    },
                    { m ->
                        MetroMapTick(stringResource(R.string.metro_transfers_together), scheme.transfersTogether, m) { on ->
                            vm.editMetroScheme(schemeId) { metroTransfersTogether(it, on) }
                        }
                    },
                )
                TickRow(
                    { m ->
                        MetroMapTick(stringResource(R.string.metro_labels_once), scheme.labelsOncePerInterchange, m) { on ->
                            vm.editMetroScheme(schemeId) { it.copy(labelsOncePerInterchange = on) }
                        }
                    },
                    { m ->
                        // Off → 8 → 12 → off; interchanges kept together close up to touching.
                        TextButton(
                            onClick = {
                                val next = when (scheme.glue) { 0 -> 8; 8 -> 12; else -> 0 }
                                vm.editMetroScheme(schemeId) {
                                    // Kept together, the interchanges close up (or open out) to the new gap.
                                    val g = it.copy(glue = next)
                                    if (g.transfersTogether) metroTransfersTogether(g.copy(transfersTogether = false), true) else g
                                }
                            },
                            modifier = m,
                        ) { Text(glueLabel) }
                    },
                )
                TickRow(
                    { m -> MetroMapTick(stringResource(R.string.metro_bend_tracks), bending, m) { bending = it } },
                    { m ->
                        if (scheme.transfersTogether) {
                            MetroMapTick(stringResource(R.string.metro_move_whole), moveWhole, m) { moveWhole = it }
                        } else {
                            Spacer(m)
                        }
                    },
                )
                if (bending) {
                    Text(
                        stringResource(R.string.metro_bend_hint),
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
                    onMoveStation = { id, x, y ->
                        vm.editMetroScheme(schemeId) {
                            if (it.transfersTogether && moveWhole) metroStationPlaced(it, id, x, y) else metroStationPlacedAlone(it, id, x, y)
                        }
                    },
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
