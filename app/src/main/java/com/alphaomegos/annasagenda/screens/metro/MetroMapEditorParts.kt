package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.clickable
import com.alphaomegos.annasagenda.support.METRO_SCALE_MIN
import com.alphaomegos.annasagenda.support.METRO_SCALE_MAX
import com.alphaomegos.annasagenda.model.MetroScheme
import com.alphaomegos.annasagenda.model.MetroPrefs
import com.alphaomegos.annasagenda.model.MetroLabelsMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.alphaomegos.annasagenda.support.metroHiddenStations
import com.alphaomegos.annasagenda.support.metroFixedStations
import com.alphaomegos.annasagenda.support.metroLayerShownToggled
import com.alphaomegos.annasagenda.support.metroLayerOnly
import com.alphaomegos.annasagenda.support.metroLayersAllShown
import com.alphaomegos.annasagenda.support.metroLayerLockToggled
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Map
import com.alphaomegos.annasagenda.app.MetroTileLoader
import com.alphaomegos.annasagenda.app.metroCartoKey
import com.alphaomegos.annasagenda.support.metroTileSourceDrawn
import com.alphaomegos.annasagenda.model.MetroUnderlay
import com.alphaomegos.annasagenda.support.metroUnderlayMoved
import com.alphaomegos.annasagenda.support.metroLayoutBounds
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
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
import com.alphaomegos.annasagenda.support.metroGridStep
import com.alphaomegos.annasagenda.support.METRO_GRID_DIVISIONS
import com.alphaomegos.annasagenda.support.MetroPoint
import com.alphaomegos.annasagenda.support.metroLayout
import com.alphaomegos.annasagenda.support.metroIsPlaced
import com.alphaomegos.annasagenda.support.metroLayoutCleared
import com.alphaomegos.annasagenda.support.metroStationPlaced
import com.alphaomegos.annasagenda.support.metroTransfersTogether
import com.alphaomegos.annasagenda.support.metroBendPointInserted
import com.alphaomegos.annasagenda.support.metroBendPointMoved
import com.alphaomegos.annasagenda.support.metroBendPointRemoved
import com.alphaomegos.annasagenda.support.metroElbowCycled
import com.alphaomegos.annasagenda.support.metroTrackBundles
import com.alphaomegos.annasagenda.support.metroBendPoints
import com.alphaomegos.annasagenda.support.MetroBendHit
import com.alphaomegos.annasagenda.support.metroStationPlacedAlone
import com.alphaomegos.annasagenda.support.metroStationLabelCycled
import com.alphaomegos.annasagenda.support.metroSelectionPlaced
import com.alphaomegos.annasagenda.support.metroSelectionToggled
import com.alphaomegos.annasagenda.support.metroSelectionWholeLines
import com.alphaomegos.annasagenda.support.metroSelectionLinedUp
import com.alphaomegos.annasagenda.support.metroSelectionSpaced
import com.alphaomegos.annasagenda.support.metroSelectionTurned
import com.alphaomegos.annasagenda.support.MetroTurn
import com.alphaomegos.annasagenda.support.MetroFigure
import com.alphaomegos.annasagenda.support.metroSelectionFigured
import com.alphaomegos.annasagenda.support.metroGlueGap
import com.alphaomegos.annasagenda.support.METRO_TOGETHER_GAP
import com.alphaomegos.annasagenda.support.METRO_NUDGE_STEP
import com.alphaomegos.annasagenda.support.metroStationsShifted
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The metro map editor's parts (29.10, split from MetroMapEditorScreen.kt, then 1 112 lines): the look dialog, the scale slider, the tools' codes, the layers. Moved unchanged; names the two files share are internal now rather than private.
 */
/**
 * How the picture looks (08.10), all in one panel: the grid, interchanges
 * kept together and glued, one name per interchange, angles of 45°, the
 * links between interchange stations; when names are written; and the
 * size of names, track and rings. A slider is written when let go, so
 * undo takes it back in one step.
 */
@Composable
internal fun MetroLookDialog(
    scheme: MetroScheme,
    prefs: MetroPrefs,
    onEdit: ((MetroScheme) -> MetroScheme) -> Unit,
    onPrefs: ((MetroPrefs) -> MetroPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    MetroDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_look)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MetroMapTick(stringResource(R.string.metro_transfers_together), scheme.transfersTogether, Modifier.fillMaxWidth()) { on ->
                    onEdit { metroTransfersTogether(it, on) }
                }
                // Dragging one station of such an interchange drags it all (16.10) — or not.
                if (scheme.transfersTogether) {
                    MetroMapTick(stringResource(R.string.metro_move_whole), prefs.moveWholeInterchange, Modifier.fillMaxWidth().padding(start = 24.dp)) { on ->
                        onPrefs { it.copy(moveWholeInterchange = on) }
                    }
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
                MetroMapTick(stringResource(R.string.metro_line_badges), scheme.lineBadges, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(lineBadges = on) }
                }
                MetroMapTick(stringResource(R.string.metro_round_corners), scheme.roundCorners, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(roundCorners = on) }
                }
                MetroMapTick(stringResource(R.string.metro_line_casing), scheme.lineCasing, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(lineCasing = on) }
                }
                // Two lines between the same interchanges run together (15.10).
                MetroMapTick(stringResource(R.string.metro_bundle_tracks), scheme.bundleTracks, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(bundleTracks = on) }
                }
                // As one line of several colours (16.10), or side by side.
                if (scheme.bundleTracks) {
                    MetroMapTick(stringResource(R.string.metro_bundle_striped), scheme.bundleStriped, Modifier.fillMaxWidth().padding(start = 24.dp)) { on ->
                        onEdit { it.copy(bundleStriped = on) }
                    }
                }
                // Names placing themselves (15.10): the user's to turn off.
                MetroMapTick(stringResource(R.string.metro_names_auto), scheme.labelsAuto, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(labelsAuto = on) }
                }
                if (scheme.labelsAuto) {
                    MetroMapTick(stringResource(R.string.metro_names_avoid_crowd), scheme.labelsAvoidCrowd, Modifier.fillMaxWidth()) { on ->
                        onEdit { it.copy(labelsAvoidCrowd = on) }
                    }
                }
                MetroMapTick(stringResource(R.string.metro_labels_once), scheme.labelsOncePerInterchange, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(labelsOncePerInterchange = on) }
                }
                // Long names in two lines (15.10).
                MetroMapTick(stringResource(R.string.metro_labels_wrap), scheme.labelsWrap, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(labelsWrap = on) }
                }
                // Off → 8 → 12 → off; interchanges kept together close up (or open out) to the new gap.
                val glueLabel = when (scheme.glue) {
                    8 -> stringResource(R.string.metro_glue, "8")
                    12 -> stringResource(R.string.metro_glue, "12")
                    else -> stringResource(R.string.metro_glue, stringResource(R.string.metro_glue_off))
                }
                MetroTextButton(onClick = {
                    val next = when (scheme.glue) { 0 -> 8; 8 -> 12; else -> 0 }
                    onEdit {
                        val g = it.copy(glue = next)
                        if (g.transfersTogether) metroTransfersTogether(g.copy(transfersTogether = false), true) else g
                    }
                }) { Text(glueLabel) }

                // The grid's step (15.10): how many nodes to a step between stations.
                Text(stringResource(R.string.metro_grid_step), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    METRO_GRID_DIVISIONS.forEach { d ->
                        FilterChip(
                            selected = scheme.gridDivisions == d,
                            onClick = { onEdit { it.copy(gridDivisions = d) } },
                            label = {
                                Text(
                                    when (d) {
                                        1 -> stringResource(R.string.metro_grid_step_whole)
                                        else -> stringResource(R.string.metro_grid_step_part, d)
                                    }
                                )
                            },
                        )
                    }
                }

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
        confirmButton = { MetroTextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
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
internal const val TOOL_MOVE = 0
internal const val TOOL_BEND = 1
internal const val TOOL_NAMES = 2
internal const val TOOL_SELECT = 3

/**
 * A line as a layer (14.10): its colour and number, filled while shown. A
 * tap shows or hides it; holding it shows it alone. A lock when locked.
 */
@Composable
internal fun MetroLayerChip(
    label: String,
    color: Color,
    shown: Boolean,
    locked: Boolean,
    onToggle: () -> Unit,
    onOnly: () -> Unit,
) {
    val toggle by rememberUpdatedState(onToggle)
    val only by rememberUpdatedState(onOnly)
    val shape = RoundedCornerShape(8.dp)
    val state = stringResource(if (shown) R.string.metro_layer_shown else R.string.metro_layer_hidden)
    val onlyWords = stringResource(R.string.metro_layer_only)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (shown) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .pointerInput(Unit) { detectTapGestures(onTap = { toggle() }, onLongPress = { only() }) }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = state
                onClick { toggle(); true }
                onLongClick(onlyWords) { only(); true }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(12.dp).background(if (shown) color else color.copy(alpha = 0.3f), CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (shown) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (locked) {
            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.metro_layer_locked), modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * The layers' panel (14.10): every line with its eye, its lock and "only
 * this", every line shown again, and hidden lines shown faintly.
 */
@Composable
internal fun MetroLayersDialog(scheme: MetroScheme, onLayers: ((MetroScheme) -> MetroScheme) -> Unit, onDismiss: () -> Unit) {
    MetroDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_layers)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.metro_layers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetroTextButton(onClick = { onLayers { metroLayersAllShown(it) } }, enabled = scheme.hiddenLines.isNotEmpty()) {
                        Text(stringResource(R.string.metro_layers_all))
                    }
                }
                MetroMapTick(stringResource(R.string.metro_layers_ghost), scheme.ghostHidden, Modifier.fillMaxWidth()) { on ->
                    onLayers { it.copy(ghostHidden = on) }
                }
                scheme.lines.forEach { line ->
                    val shown = line.id !in scheme.hiddenLines
                    val locked = line.id in scheme.lockedLines
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(metroLineColor(line.color), CircleShape))
                        Text(
                            listOf(line.label, line.name).filter { it.isNotBlank() }.joinToString(" "),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        IconButton(onClick = { onLayers { metroLayerShownToggled(it, line.id) } }) {
                            Icon(
                                if (shown) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = stringResource(if (shown) R.string.metro_layer_hide else R.string.metro_layer_show),
                            )
                        }
                        IconButton(onClick = { onLayers { metroLayerLockToggled(it, line.id) } }) {
                            Icon(
                                if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = stringResource(if (locked) R.string.metro_layer_unlock else R.string.metro_layer_lock),
                            )
                        }
                        MetroTextButton(onClick = { onLayers { metroLayerOnly(it, line.id) } }) {
                            Text(stringResource(R.string.metro_layer_only))
                        }
                    }
                }
            }
        },
        confirmButton = { MetroTextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** A map being laid by hand (16.10), kept through a turn of the phone as its six numbers. */
internal val metroUnderlaySaver = listSaver<MetroUnderlay?, Double>(
    save = { u -> if (u == null) emptyList() else listOf(u.lat, u.lon, u.x.toDouble(), u.y.toDouble(), u.metersPerUnit.toDouble(), u.rotationDeg.toDouble()) },
    restore = { v ->
        if (v.size != 6) null
        else MetroUnderlay(v[0], v[1], v[2].toFloat(), v[3].toFloat(), v[4].toFloat(), v[5].toFloat())
    },
)
