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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
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
import com.alphaomegos.annasagenda.support.metroIsPlaced
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
import com.alphaomegos.annasagenda.support.metroSelectionLinedUp
import com.alphaomegos.annasagenda.support.metroSelectionSpaced
import com.alphaomegos.annasagenda.support.metroSelectionTurned
import com.alphaomegos.annasagenda.support.MetroTurn
import com.alphaomegos.annasagenda.support.METRO_NUDGE_STEP
import com.alphaomegos.annasagenda.support.metroStationsShifted
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
    // Saving the picture as a PNG (08.10): the view records itself into this
    // layer; a file is asked for, and the layer written there.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Only while saving: a layer is made then, not on every visit (nor in tests).
    var exporting by rememberSaveable { mutableStateOf(false) }
    val pictureLayer = if (exporting) rememberGraphicsLayer() else null
    val currentLayer by rememberUpdatedState(pictureLayer)
    val savePicture = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri == null) {
            exporting = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = runCatching {
                val layer = currentLayer ?: error("no picture recorded")
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
                }
            }.getOrDefault(false)
            exporting = false
            Toast.makeText(
                context,
                context.getString(if (saved) R.string.metro_picture_saved else R.string.metro_picture_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val scheme = state.metroSchemes.firstOrNull { it.id == schemeId }
    val histories by vm.metroHistory.collectAsStateWithLifecycle()
    val history = histories[schemeId]
    val laidOut by produceState<Map<Long, MetroPoint>?>(null, scheme) {
        value = scheme?.let { s -> withContext(Dispatchers.Default) { metroLayout(s) } }
    }
    // A picture placed all by hand is read straight off the scheme (14.10): laid out
    // in the background, a dropped station first jumped back for a frame or two.
    val placed = remember(scheme) { scheme?.takeIf { metroIsPlaced(it) }?.let { metroLayout(it) } }
    val layout = placed ?: laidOut
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // Tools of the editor (08.10), not settings: bending track, and moving an
    // interchange whole or one station of it.
    // One tool at a time (08.10): move stations, bend track, place names
    // (a tap moves a station's name round it), or select stations to move
    // together (a tap puts one in or out, a drag of one moves them all).
    var tool by rememberSaveable { mutableStateOf(TOOL_MOVE) }
    var selectedIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    // Layers (14.10): stations of hidden lines are out of sight, those of hidden and
    // locked lines out of reach — not selected, not carried along, not glued to.
    val unseen = remember(scheme) { scheme?.let { metroHiddenStations(it) }.orEmpty() }
    val fixed = remember(scheme) { scheme?.let { metroFixedStations(it) }.orEmpty() }
    val selection = selectedIds.toSet() - fixed
    val bending = tool == TOOL_BEND
    val naming = tool == TOOL_NAMES
    val selecting = tool == TOOL_SELECT
    var moveWhole by rememberSaveable { mutableStateOf(true) }
    // The look's panel open (08.10).
    var looking by rememberSaveable { mutableStateOf(false) }
    // Bumped by "Fit to the screen" (08.10).
    var fitKey by rememberSaveable { mutableStateOf(0) }
    // The layers' panel open (14.10).
    var layering by rememberSaveable { mutableStateOf(false) }

    // The editor's frame (14.10): tools and layers over the picture, the picture
    // the whole rest of the screen; the tool's tip and the selection's actions
    // float over it. What is seldom done is in the menu.
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // The tool whose tip was closed; the tip comes back with another tool.
    var tipClosedFor by rememberSaveable { mutableStateOf(-1) }
    val startExport = {
        tool = TOOL_MOVE
        fitKey++
        exporting = true
        savePicture.launch("${scheme?.city ?: "metro"}.png")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.metro_place_stations), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.metro_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // The whole picture, without the tools' marks, then where to save it.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.metro_save_picture)) },
                                leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                                onClick = { menuOpen = false; startExport() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.metro_layout_again)) },
                                onClick = { menuOpen = false; confirmReset = true },
                            )
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (scheme != null) {
                // One tool at a time (08.10), each with its picture (14.10).
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        Triple(TOOL_MOVE, R.string.metro_tool_move, Icons.Default.OpenWith),
                        Triple(TOOL_BEND, R.string.metro_bend_tracks, Icons.Default.Timeline),
                        Triple(TOOL_NAMES, R.string.metro_names_tool, Icons.Default.TextFields),
                        Triple(TOOL_SELECT, R.string.metro_tool_select, Icons.Default.SelectAll),
                    ).forEach { (t, words, picture) ->
                        FilterChip(
                            selected = tool == t,
                            onClick = { tool = t },
                            label = { Text(stringResource(words)) },
                            leadingIcon = { Icon(picture, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                    // The aids to drawing (14.10), at hand by the tools, as the snapping
                    // switches of drawing programs: no step of undo.
                    VerticalDivider(modifier = Modifier.height(32.dp).align(Alignment.CenterVertically))
                    listOf(
                        Triple(R.string.metro_map_grid, scheme.mapGrid, { s: MetroScheme -> s.copy(mapGrid = !s.mapGrid) }),
                        Triple(R.string.metro_snap_angles, scheme.snapAngles, { s: MetroScheme -> s.copy(snapAngles = !s.snapAngles) }),
                        Triple(R.string.metro_align_guides, scheme.alignGuides, { s: MetroScheme -> s.copy(alignGuides = !s.alignGuides) }),
                    ).forEach { (words, on, flip) ->
                        FilterChip(
                            selected = on,
                            onClick = { vm.setMetroView(schemeId, flip) },
                            label = { Text(stringResource(words)) },
                        )
                    }
                }
                // The layers (14.10): the panel, then a chip a line — a tap shows or hides
                // it, holding it shows that line only (and, again, every line).
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { layering = true }) {
                        Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.metro_layers))
                    }
                    scheme.lines.forEach { line ->
                        MetroLayerChip(
                            label = line.label.ifBlank { line.name },
                            color = Color(line.color),
                            shown = line.id !in scheme.hiddenLines,
                            locked = line.id in scheme.lockedLines,
                            onToggle = { vm.setMetroView(schemeId) { metroLayerShownToggled(it, line.id) } },
                            onOnly = { vm.setMetroView(schemeId) { metroLayerOnly(it, line.id) } },
                        )
                    }
                }
            }
            val l = layout
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (scheme != null && l != null) {
                    MetroMapView(
                        scheme = scheme,
                        layout = l,
                        modifier = Modifier.fillMaxSize(),
                        // A whole interchange moves when kept together and so chosen; else
                        // the station alone, glued to its interchange if rings are glued.
                        onMoveStation = if (naming) null else { id, x, y ->
                            vm.editMetroScheme(schemeId) {
                                when {
                                    selecting && id in selection -> metroSelectionPlaced(it, selection, id, x, y)
                                    it.transfersTogether && moveWhole -> metroStationPlaced(it, id, x, y, keep = fixed)
                                    else -> metroStationPlacedAlone(it, id, x, y, unseen = unseen)
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
                        capture = pictureLayer,
                        // Select (14.10): a box drawn round stations adds them; a tap on nothing lets go of all.
                        onMarquee = if (selecting) { ids -> selectedIds = (selection + ids - fixed).toLongArray() } else null,
                        onTapEmpty = if (selecting) { { selectedIds = longArrayOf() } } else null,
                        hiddenLines = scheme.hiddenLines.toSet(),
                        lockedLines = scheme.lockedLines.toSet(),
                        ghostHidden = scheme.ghostHidden,
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
                // The tool's tip (14.10), over the top of the picture until closed.
                if (scheme != null && tipClosedFor != tool) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                        tonalElevation = 2.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                stringResource(
                                    when (tool) {
                                        TOOL_BEND -> R.string.metro_bend_hint
                                        TOOL_NAMES -> R.string.metro_names_hint
                                        TOOL_SELECT -> R.string.metro_select_hint
                                        else -> R.string.metro_place_stations_hint
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            IconButton(onClick = { tipClosedFor = tool }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.metro_tip_close))
                            }
                        }
                    }
                }
                // What goes with the tool (14.10), along the bottom of the picture.
                if (scheme != null && (selecting || (tool == TOOL_MOVE && scheme.transfersTogether))) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            if (selecting) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.metro_selected_count, selection.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { selectedIds = (metroSelectionWholeLines(scheme, selection) - fixed).toLongArray() }, enabled = selection.isNotEmpty()) {
                                        Text(stringResource(R.string.metro_select_lines))
                                    }
                                    TextButton(onClick = { selectedIds = longArrayOf() }, enabled = selection.isNotEmpty()) {
                                        Text(stringResource(R.string.metro_select_none))
                                    }
                                }
                                // Lining the selection up (08.10): a row, a column, equal steps.
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                                ) {
                                    val grid = if (scheme.mapGrid) METRO_GRID_STEP else null
                                    TextButton(
                                        onClick = { vm.editMetroScheme(schemeId) { metroSelectionLinedUp(it, selection, across = true, grid = grid) } },
                                        enabled = selection.size >= 2,
                                    ) { Text(stringResource(R.string.metro_align_row)) }
                                    TextButton(
                                        onClick = { vm.editMetroScheme(schemeId) { metroSelectionLinedUp(it, selection, across = false, grid = grid) } },
                                        enabled = selection.size >= 2,
                                    ) { Text(stringResource(R.string.metro_align_column)) }
                                    TextButton(
                                        onClick = { vm.editMetroScheme(schemeId) { metroSelectionSpaced(it, selection) } },
                                        enabled = selection.size >= 3,
                                    ) { Text(stringResource(R.string.metro_align_spaced)) }
                                    // A nudge by one grid step, or a tenth of a step without the grid (08.10):
                                    // a finger is too thick for the last bit.
                                    val nudge = if (scheme.mapGrid) METRO_GRID_STEP else METRO_NUDGE_STEP
                                    listOf(
                                        Triple(-nudge, 0f, R.string.metro_nudge_left),
                                        Triple(0f, -nudge, R.string.metro_nudge_up),
                                        Triple(0f, nudge, R.string.metro_nudge_down),
                                        Triple(nudge, 0f, R.string.metro_nudge_right),
                                    ).forEach { (dx, dy, words) ->
                                        TextButton(
                                            onClick = { vm.editMetroScheme(schemeId) { metroStationsShifted(it, selection, dx, dy) } },
                                            enabled = selection.isNotEmpty(),
                                        ) { Text(stringResource(words)) }
                                    }
                                    // Turned or mirrored about its middle (08.10).
                                    listOf(
                                        MetroTurn.CLOCKWISE_45 to R.string.metro_turn_45,
                                        MetroTurn.MIRROR_ACROSS to R.string.metro_mirror_across,
                                        MetroTurn.MIRROR_UPDOWN to R.string.metro_mirror_updown,
                                    ).forEach { (turn, words) ->
                                        TextButton(
                                            onClick = { vm.editMetroScheme(schemeId) { metroSelectionTurned(it, selection, turn) } },
                                            enabled = selection.size >= 2,
                                        ) { Text(stringResource(words)) }
                                    }
                                }
                            } else {
                                MetroMapTick(stringResource(R.string.metro_move_whole), moveWhole, Modifier.padding(end = 12.dp)) { moveWhole = it }
                            }
                        }
                    }
                }
            }
        }
    }

    if (layering && scheme != null) {
        MetroLayersDialog(scheme, onLayers = { edit -> vm.setMetroView(schemeId, edit) }, onDismiss = { layering = false })
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
                MetroMapTick(stringResource(R.string.metro_line_badges), scheme.lineBadges, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(lineBadges = on) }
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

/**
 * A line as a layer (14.10): its colour and number, filled while shown. A
 * tap shows or hides it; holding it shows it alone. A lock when locked.
 */
@Composable
private fun MetroLayerChip(
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
private fun MetroLayersDialog(scheme: MetroScheme, onLayers: ((MetroScheme) -> MetroScheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
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
                    TextButton(onClick = { onLayers { metroLayersAllShown(it) } }, enabled = scheme.hiddenLines.isNotEmpty()) {
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
                        Box(Modifier.size(14.dp).background(Color(line.color), CircleShape))
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
                        TextButton(onClick = { onLayers { metroLayerOnly(it, line.id) } }) {
                            Text(stringResource(R.string.metro_layer_only))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
