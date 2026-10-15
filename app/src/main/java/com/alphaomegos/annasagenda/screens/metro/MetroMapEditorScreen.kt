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
    // How the editor behaves (15.10): the user's to choose, in its menu.
    val prefs = state.metroPrefs
    var prefsShown by rememberSaveable { mutableStateOf(false) }
    var styling by rememberSaveable { mutableStateOf(false) }
    // The figures' panel (15.10), and how its figures are drawn.
    var figuring by rememberSaveable { mutableStateOf(false) }
    var figureTurn by rememberSaveable { mutableStateOf(0f) }
    var figureTouching by rememberSaveable { mutableStateOf(true) }
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
    var keysShown by rememberSaveable { mutableStateOf(false) }
    // Keys (14.10), for a keyboard on the Fold or at the emulator, as in drawing programs.
    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keys.requestFocus() } }
    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown || scheme == null) return false
        val ctrl = e.isCtrlPressed || e.isMetaPressed
        val nudge = (if (scheme.mapGrid) metroGridStep(scheme) else METRO_NUDGE_STEP) * (if (e.isShiftPressed) 5f else 1f)
        fun shift(dx: Float, dy: Float): Boolean {
            if (!selecting || selection.isEmpty()) return false
            vm.editMetroScheme(schemeId) { metroStationsShifted(it, selection, dx, dy) }
            return true
        }
        return when {
            ctrl && e.key == Key.Z && e.isShiftPressed -> { vm.redoMetroScheme(schemeId); true }
            ctrl && e.key == Key.Z -> { vm.undoMetroScheme(schemeId); true }
            ctrl && e.key == Key.Y -> { vm.redoMetroScheme(schemeId); true }
            ctrl && e.key == Key.A -> {
                tool = TOOL_SELECT
                selectedIds = scheme.stations.map { it.id }.filter { it !in fixed }.toLongArray()
                true
            }
            ctrl -> false
            e.key == Key.Escape -> { selectedIds = longArrayOf(); true }
            e.key == Key.DirectionLeft -> shift(-nudge, 0f)
            e.key == Key.DirectionRight -> shift(nudge, 0f)
            e.key == Key.DirectionUp -> shift(0f, -nudge)
            e.key == Key.DirectionDown -> shift(0f, nudge)
            e.key == Key.V || e.key == Key.M -> { tool = TOOL_MOVE; true }
            e.key == Key.B -> { tool = TOOL_BEND; true }
            e.key == Key.T -> { tool = TOOL_NAMES; true }
            e.key == Key.S -> { tool = TOOL_SELECT; true }
            e.key == Key.G -> { vm.setMetroView(schemeId) { it.copy(mapGrid = !it.mapGrid) }; true }
            e.key == Key.L -> { layering = true; true }
            e.key == Key.F || e.key == Key.Zero || e.key == Key.F5 -> { fitKey++; true }
            // The DOS keys (15.10), whatever the style.
            e.key == Key.F1 -> { tool = TOOL_MOVE; true }
            e.key == Key.F2 -> { tool = TOOL_BEND; true }
            e.key == Key.F3 -> { tool = TOOL_NAMES; true }
            e.key == Key.F4 -> { tool = TOOL_SELECT; true }
            e.key == Key.F6 -> { layering = true; true }
            e.key == Key.F7 -> { styling = true; true }
            e.key == Key.F8 -> { vm.undoMetroScheme(schemeId); true }
            e.key == Key.F9 -> { prefsShown = true; true }
            e.key == Key.F10 -> { onBack(); true }
            else -> false
        }
    }
    val startExport = {
        tool = TOOL_MOVE
        fitKey++
        exporting = true
        savePicture.launch("${scheme?.city ?: "metro"}.png")
    }

    Scaffold(
        // Norton Commander's keys along the bottom, in DOS (15.10); F1…F10 on a keyboard too.
        bottomBar = {
            MetroKeyBar(
                listOf(
                    MetroKey(1, stringResource(R.string.metro_tool_move)) { tool = TOOL_MOVE },
                    MetroKey(2, stringResource(R.string.metro_bend_tracks)) { tool = TOOL_BEND },
                    MetroKey(3, stringResource(R.string.metro_names_tool)) { tool = TOOL_NAMES },
                    MetroKey(4, stringResource(R.string.metro_tool_select)) { tool = TOOL_SELECT },
                    MetroKey(5, stringResource(R.string.metro_key_fit)) { fitKey++ },
                    MetroKey(6, stringResource(R.string.metro_layers)) { layering = true },
                    MetroKey(7, stringResource(R.string.metro_key_style)) { styling = true },
                    MetroKey(8, stringResource(R.string.metro_key_undo)) { vm.undoMetroScheme(schemeId) },
                    MetroKey(9, stringResource(R.string.metro_key_setup)) { prefsShown = true },
                    MetroKey(10, stringResource(R.string.metro_key_quit), onBack),
                ),
            )
        },
        topBar = {
            // In the section's style (15.10).
            MetroTopBar(
                title = stringResource(R.string.metro_place_stations),
                onBack = onBack,
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.metro_style)) },
                                onClick = { menuOpen = false; styling = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.metro_editor_prefs)) },
                                onClick = { menuOpen = false; prefsShown = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.metro_keys)) },
                                onClick = { menuOpen = false; keysShown = true },
                            )
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
                .focusRequester(keys)
                .focusable()
                .onPreviewKeyEvent { onKey(it) },
        ) {
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
                            color = metroLineColor(line.color),
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
                        modifier = Modifier.fillMaxSize().metroMapField(),
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
                        onMarquee = if (selecting && prefs.boxSelect) { ids -> selectedIds = (selection + ids - fixed).toLongArray() } else null,
                        onTapEmpty = if (selecting) { { selectedIds = longArrayOf() } } else null,
                        loupe = prefs.loupe,
                        haptics = prefs.haptics,
                        // The toolbar has its own "fit"; the corner keeps closer and farther (15.10).
                        zoomButtons = prefs.zoomButtons,
                        fitButton = false,
                        doubleTapZoom = prefs.doubleTapZoom,
                        minimap = prefs.minimap,
                        hiddenLines = scheme.hiddenLines.toSet(),
                        lockedLines = scheme.lockedLines.toSet(),
                        ghostHidden = scheme.ghostHidden,
                        gridStep = if (scheme.mapGrid) metroGridStep(scheme) else null,
                        moveAlone = !(scheme.transfersTogether && moveWhole),
                        bendMode = bending,
                        onBend = { action ->
                            vm.editMetroScheme(schemeId) { s ->
                                // Track run together (15.10): bending a follower bends its leader.
                                val led = if (s.bundleTracks) metroTrackBundles(s) else emptyMap()
                                when (action) {
                                    is MetroBendAction.Cycle -> metroElbowCycled(s, metroLayout(s), led[action.track]?.leader ?: action.track)
                                    is MetroBendAction.Insert -> {
                                        val follows = led[action.hit.track]
                                        val hit = if (follows == null) action.hit else {
                                            val n = metroBendPoints(s, follows.leader).size
                                            MetroBendHit(follows.leader, if (follows.sameWay) action.hit.index.coerceIn(0, n) else (n - action.hit.index).coerceIn(0, n))
                                        }
                                        metroBendPointInserted(s, hit, action.x, action.y)
                                    }
                                    is MetroBendAction.Move -> metroBendPointMoved(s, action.hit, action.x, action.y)
                                    is MetroBendAction.Remove -> metroBendPointRemoved(s, action.hit)
                                }
                            }
                        },
                    )
                }
                // The tool's tip (14.10), over the top of the picture until closed.
                if (scheme != null && prefs.tips && tipClosedFor != tool) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).metroFrame(LocalMetroStyle.current, LocalMetroStyleDetails.current),
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
                                        TOOL_SELECT -> if (prefs.boxSelect) R.string.metro_select_hint else R.string.metro_select_hint_no_box
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
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).metroFrame(LocalMetroStyle.current, LocalMetroStyleDetails.current),
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
                                    MetroTextButton(onClick = { selectedIds = (metroSelectionWholeLines(scheme, selection) - fixed).toLongArray() }, enabled = selection.isNotEmpty()) {
                                        Text(stringResource(R.string.metro_select_lines))
                                    }
                                    MetroTextButton(onClick = { selectedIds = longArrayOf() }, enabled = selection.isNotEmpty()) {
                                        Text(stringResource(R.string.metro_select_none))
                                    }
                                }
                                // Lining the selection up (08.10): a row, a column, equal steps.
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                                ) {
                                    val grid = if (scheme.mapGrid) metroGridStep(scheme) else null
                                    MetroTextButton(
                                        onClick = { vm.editMetroScheme(schemeId) { metroSelectionLinedUp(it, selection, across = true, grid = grid) } },
                                        enabled = selection.size >= 2,
                                    ) { Text(stringResource(R.string.metro_align_row)) }
                                    MetroTextButton(
                                        onClick = { vm.editMetroScheme(schemeId) { metroSelectionLinedUp(it, selection, across = false, grid = grid) } },
                                        enabled = selection.size >= 2,
                                    ) { Text(stringResource(R.string.metro_align_column)) }
                                    MetroTextButton(
                                        onClick = { vm.editMetroScheme(schemeId) { metroSelectionSpaced(it, selection) } },
                                        enabled = selection.size >= 3,
                                    ) { Text(stringResource(R.string.metro_align_spaced)) }
                                    // A nudge by one grid step, or a tenth of a step without the grid (08.10):
                                    // a finger is too thick for the last bit.
                                    val nudge = if (scheme.mapGrid) metroGridStep(scheme) else METRO_NUDGE_STEP
                                    listOf(
                                        Triple(-nudge, 0f, R.string.metro_nudge_left),
                                        Triple(0f, -nudge, R.string.metro_nudge_up),
                                        Triple(0f, nudge, R.string.metro_nudge_down),
                                        Triple(nudge, 0f, R.string.metro_nudge_right),
                                    ).forEach { (dx, dy, words) ->
                                        MetroTextButton(
                                            onClick = { vm.editMetroScheme(schemeId) { metroStationsShifted(it, selection, dx, dy) } },
                                            enabled = selection.isNotEmpty(),
                                        ) { Text(stringResource(words)) }
                                    }
                                    // A figure of the selection (15.10): a row, a column, a regular polygon.
                                    MetroTextButton(onClick = { figuring = true }, enabled = selection.size >= 2) {
                                        Text(stringResource(R.string.metro_figure))
                                    }
                                    // Turned or mirrored about its middle (08.10).
                                    listOf(
                                        MetroTurn.CLOCKWISE_45 to R.string.metro_turn_45,
                                        MetroTurn.MIRROR_ACROSS to R.string.metro_mirror_across,
                                        MetroTurn.MIRROR_UPDOWN to R.string.metro_mirror_updown,
                                    ).forEach { (turn, words) ->
                                        MetroTextButton(
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

    if (figuring && scheme != null) {
        AlertDialog(
            onDismissRequest = { figuring = false },
            title = { Text(stringResource(R.string.metro_figure)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.metro_figure_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // How far apart: rings touching, or a grid step.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = figureTouching, onClick = { figureTouching = true }, label = { Text(stringResource(R.string.metro_figure_touching)) })
                        FilterChip(selected = !figureTouching, onClick = { figureTouching = false }, label = { Text(stringResource(R.string.metro_figure_step)) })
                    }
                    // Turned by.
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0f, 15f, 30f, 45f, 90f).forEach { deg ->
                            FilterChip(selected = figureTurn == deg, onClick = { figureTurn = deg }, label = { Text("${deg.toInt()}°") })
                        }
                    }
                    listOf(
                        MetroFigure.ROW to R.string.metro_figure_row,
                        MetroFigure.COLUMN to R.string.metro_figure_column,
                        MetroFigure.POLYGON to R.string.metro_figure_polygon,
                        MetroFigure.AROUND_ONE to R.string.metro_figure_around,
                    ).forEach { (figure, words) ->
                        MetroTextButton(
                            onClick = {
                                vm.editMetroScheme(schemeId) {
                                    val gap = if (figureTouching) metroGlueGap(it) else METRO_TOGETHER_GAP
                                    metroSelectionFigured(it, selection, figure, gap, figureTurn)
                                }
                            },
                            enabled = selection.size >= (if (figure == MetroFigure.AROUND_ONE) 3 else 2),
                        ) { Text(stringResource(words)) }
                    }
                }
            },
            confirmButton = { MetroTextButton(onClick = { figuring = false }) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (styling) {
        MetroStyleDialog(
            current = prefs.style,
            details = prefs.styleDetails,
            onPick = { style -> vm.setMetroPrefs { it.copy(style = style) } },
            onDetails = { on -> vm.setMetroPrefs { it.copy(styleDetails = on) } },
            eraColors = prefs.eraColors,
            onEraColors = { on -> vm.setMetroPrefs { it.copy(eraColors = on) } },
            onDismiss = { styling = false },
        )
    }

    if (prefsShown) {
        AlertDialog(
            onDismissRequest = { prefsShown = false },
            title = { Text(stringResource(R.string.metro_editor_prefs)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MetroMapTick(stringResource(R.string.metro_pref_loupe), prefs.loupe, Modifier.fillMaxWidth()) { on ->
                        vm.setMetroPrefs { it.copy(loupe = on) }
                    }
                    MetroMapTick(stringResource(R.string.metro_pref_haptics), prefs.haptics, Modifier.fillMaxWidth()) { on ->
                        vm.setMetroPrefs { it.copy(haptics = on) }
                    }
                    MetroMapTick(stringResource(R.string.metro_pref_box), prefs.boxSelect, Modifier.fillMaxWidth()) { on ->
                        vm.setMetroPrefs { it.copy(boxSelect = on) }
                    }
                    MetroMapTick(stringResource(R.string.metro_pref_tips), prefs.tips, Modifier.fillMaxWidth()) { on ->
                        vm.setMetroPrefs { it.copy(tips = on) }
                        if (on) tipClosedFor = -1
                    }
                    MetroMapPrefsTicks(prefs) { edit -> vm.setMetroPrefs(edit) }
                }
            },
            confirmButton = { MetroTextButton(onClick = { prefsShown = false }) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (keysShown) {
        AlertDialog(
            onDismissRequest = { keysShown = false },
            title = { Text(stringResource(R.string.metro_keys)) },
            text = { Text(stringResource(R.string.metro_keys_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { MetroTextButton(onClick = { keysShown = false }) { Text(stringResource(R.string.ok)) } },
        )
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
                // Two lines between the same interchanges run together (15.10).
                MetroMapTick(stringResource(R.string.metro_bundle_tracks), scheme.bundleTracks, Modifier.fillMaxWidth()) { on ->
                    onEdit { it.copy(bundleTracks = on) }
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
