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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Share
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.alphaomegos.annasagenda.model.MetroRecentRoute
import com.alphaomegos.annasagenda.model.MetroPrefs
import com.alphaomegos.annasagenda.support.formatTaskTime
import com.alphaomegos.annasagenda.support.metroArrival
import java.time.LocalTime
import com.alphaomegos.annasagenda.support.METRO_RECENT_SETTLE_MILLIS
import com.alphaomegos.annasagenda.support.metroRecentKey
import com.alphaomegos.annasagenda.support.metroRecentOf
import com.alphaomegos.annasagenda.support.metroRouteEnds
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import com.alphaomegos.annasagenda.support.MetroStationFacts
import com.alphaomegos.annasagenda.support.metroStationFacts
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.ui.text.style.TextOverflow
import com.alphaomegos.annasagenda.support.metroRouteStations
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.produceState
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
import com.alphaomegos.annasagenda.support.MetroPoint
import com.alphaomegos.annasagenda.support.MetroRoute
import com.alphaomegos.annasagenda.support.MetroRouteStep
import com.alphaomegos.annasagenda.support.formatShortDate
import com.alphaomegos.annasagenda.support.metroClosedStations
import com.alphaomegos.annasagenda.support.metroClosureSet
import com.alphaomegos.annasagenda.support.metroClosuresDue
import com.alphaomegos.annasagenda.support.metroLayout
import com.alphaomegos.annasagenda.support.metroRoute
import com.alphaomegos.annasagenda.support.metroShown
import com.alphaomegos.annasagenda.support.metroStationSuggestions
import com.alphaomegos.annasagenda.support.MetroNameChoice
import com.alphaomegos.annasagenda.support.metroStationsForField
import com.alphaomegos.annasagenda.util.appLocale
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    // The very station picked from the list or on the picture (06.10): with
    // "Печатники" on three lines, which one is meant changes the route.
    var fromPick by rememberSaveable { mutableStateOf<Long?>(null) }
    var toPick by rememberSaveable { mutableStateOf<Long?>(null) }
    // Which field the list of names is open under: "from", "to", or none.
    var typingIn by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var putOff by rememberSaveable { mutableStateOf(listOf<String>()) }
    var movingDateOf by rememberSaveable { mutableStateOf<String?>(null) }
    var showMap by rememberSaveable { mutableStateOf(true) }
    // The section's style (15.10): the chooser open.
    var styling by rememberSaveable { mutableStateOf(false) }
    // How the picture is worked (15.10): its settings open.
    var mapPrefsShown by rememberSaveable { mutableStateOf(false) }
    // The map over the whole screen (15.10), the fields folded into one line under it.
    var bigMap by rememberSaveable { mutableStateOf(false) }
    // The lines lit up from the legend (15.10).
    var litLines by rememberSaveable { mutableStateOf(listOf<Long>()) }
    // The station whose card is open over the map (15.10).
    var cardFor by rememberSaveable { mutableStateOf<Long?>(null) }
    // The "to" field, for the keyboard's "next" in "from" (16.10).
    val toFocus = remember { FocusRequester() }

    val today = remember { LocalDate.now() }

    // The picture (05.10): laid out off the main thread, again after every edit.
    val layout by produceState<Map<Long, MetroPoint>?>(null, scheme) {
        value = scheme?.let { s -> withContext(Dispatchers.Default) { metroLayout(s) } }
    }
    val fromIds = remember(scheme, fromText, fromPick) { scheme?.let { metroStationsForField(it, fromText, fromPick) }.orEmpty() }
    val toIds = remember(scheme, toText, toPick) { scheme?.let { metroStationsForField(it, toText, toPick) }.orEmpty() }
    val shownRoute = remember(scheme, fromIds, toIds) {
        if (scheme == null || fromIds.isEmpty() || toIds.isEmpty()) null else metroRoute(scheme, fromIds, toIds)
    }
    // A route that stays found is written down (15.10): a name half typed is not looked up yet.
    val recentKey = shown?.let { metroRecentKey(it) }
    val routeEnds = remember(shownRoute) { shownRoute?.let { metroRouteEnds(it) } }
    LaunchedEffect(routeEnds, recentKey) {
        if (routeEnds != null && recentKey != null) {
            delay(METRO_RECENT_SETTLE_MILLIS)
            vm.rememberMetroRoute(recentKey, routeEnds.first, routeEnds.second)
        }
    }
    // What the picture frames when a route is found (15.10), if the settings say so.
    val framed = remember(shownRoute, state.metroPrefs.frameRoute) {
        if (!state.metroPrefs.frameRoute) emptySet() else shownRoute?.let { metroRouteStations(it) }.orEmpty()
    }

    // "From" and "to" the other way round.
    val swap = {
        val was = fromText
        fromText = toText
        toText = was
        val wasPick = fromPick
        fromPick = toPick
        toPick = wasPick
        typingIn = null
    }
    val edit = {
        if (scheme != null) {
            val id = if (libraryKey == null) scheme.id else vm.copyMetroLibraryScheme(libraryKey)
            if (id != null) onEditScheme(id)
        }
    }

    Scaffold(
        // Norton Commander's keys along the bottom, in DOS (15.10).
        bottomBar = {
            MetroKeyBar(
                listOf(
                    MetroKey(1, stringResource(R.string.metro_key_map)) { showMap = !showMap },
                    MetroKey(2, stringResource(R.string.metro_key_swap), swap),
                    MetroKey(3, stringResource(R.string.metro_key_style)) { styling = true },
                    MetroKey(4, stringResource(R.string.metro_key_edit), edit),
                    MetroKey(9, stringResource(R.string.metro_key_setup)) { mapPrefsShown = true },
                    MetroKey(10, stringResource(R.string.metro_key_quit), onBack),
                ),
            )
        },
        topBar = {
            // In the section's style (15.10).
            MetroTopBar(
                title = scheme?.city ?: stringResource(R.string.menu_metro),
                onBack = onBack,
                centered = true,
                actions = {
                    IconButton(onClick = { styling = true }) {
                        Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.metro_style))
                    }
                    IconButton(onClick = { mapPrefsShown = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.metro_map_prefs))
                    }
                    if (scheme != null && scheme.stations.isNotEmpty()) {
                        IconButton(onClick = { showMap = !showMap }) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = stringResource(if (showMap) R.string.metro_hide_map else R.string.metro_show_map),
                            )
                        }
                    }
                    if (scheme != null) {
                        IconButton(onClick = edit) {
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
                .imePadding(),
        ) {
        val pictured = layout
        val mapShown = scheme != null && showMap && pictured != null && scheme.stations.isNotEmpty()
        if (scheme != null && mapShown && pictured != null) {
            // Outside the scrolling part: a finger on the picture pans the picture.
            // Big (15.10), it takes all the room the fields leave.
            Box(
                modifier = (if (bigMap) Modifier.weight(1f) else Modifier.height(300.dp))
                    .fillMaxWidth()
                    .metroMapField(),
            ) {
            MetroMapView(
                scheme = scheme,
                layout = pictured,
                // On the style's field (15.10): Windows' white page, the others' frame.
                route = shownRoute?.takeIf { it.steps.isNotEmpty() },
                marked = fromIds + toIds,
                onTapStation = { id ->
                    // With cards on (15.10), a tap opens the station's card instead.
                    if (state.metroPrefs.stationCard) {
                        cardFor = id
                        return@MetroMapView
                    }
                    val name = scheme.stations.firstOrNull { it.id == id }?.name ?: return@MetroMapView
                    // The first tap is "from", the second "to"; a third starts again.
                    if (fromText.isBlank() || toText.isNotBlank()) {
                        fromText = name
                        fromPick = id
                        toText = ""
                        toPick = null
                    } else {
                        toText = name
                        toPick = id
                    }
                    typingIn = null
                },
                modifier = Modifier.fillMaxSize(),
                zoomButtons = state.metroPrefs.zoomButtons,
                doubleTapZoom = state.metroPrefs.doubleTapZoom,
                focus = framed,
                highlightLines = litLines.toSet(),
                minimap = state.metroPrefs.minimap,
                onTapEmpty = { cardFor = null },
            )
            val facts = cardFor?.let { metroStationFacts(scheme, it) }
            if (facts != null) {
                val lineId = facts.station.lineId
                MetroStationCard(
                    facts = facts,
                    lineLit = lineId in litLines,
                    onFrom = {
                        fromText = facts.station.name
                        fromPick = facts.station.id
                        typingIn = null
                        cardFor = null
                    },
                    onTo = {
                        toText = facts.station.name
                        toPick = facts.station.id
                        typingIn = null
                        cardFor = null
                    },
                    onLine = { litLines = if (lineId in litLines) litLines - lineId else litLines + lineId },
                    onWalk = { cardFor = it },
                    onClose = { cardFor = null },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp, end = if (state.metroPrefs.zoomButtons) 64.dp else 8.dp),
                )
            }
            MetroMapButton(
                icon = if (bigMap) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                label = stringResource(if (bigMap) R.string.metro_map_smaller else R.string.metro_map_bigger),
                onClick = { bigMap = !bigMap },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            }
        }
        if (scheme != null && mapShown && state.metroPrefs.legend && scheme.lines.size > 1) {
            MetroLegend(scheme.lines, litLines.toSet()) { id ->
                litLines = if (id in litLines) litLines - id else litLines + id
            }
        }
        if (mapShown && bigMap) {
            // The fields folded (15.10): where from, where to, and the route in a word.
            BigMapSummary(
                fromText = fromText,
                toText = toText,
                route = shownRoute,
                onSwap = swap,
                onUnfold = { bigMap = false },
            )
        } else Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                            fromPick = null
                            typingIn = "from"
                        },
                        onClear = {
                            fromText = ""
                            fromPick = null
                            typingIn = null
                        },
                        // The keyboard's key goes on to "to", the list of names folded first.
                        onNext = {
                            typingIn = null
                            runCatching { toFocus.requestFocus() }
                        },
                    )
                    if (typingIn == "from") {
                        Suggestions(scheme, fromText) {
                            fromText = it.name
                            fromPick = it.stationId
                            typingIn = null
                        }
                    }
                    StationField(
                        label = stringResource(R.string.metro_to),
                        value = toText,
                        onValueChange = {
                            toText = it
                            toPick = null
                            typingIn = "to"
                        },
                        onClear = {
                            toText = ""
                            toPick = null
                            typingIn = null
                        },
                        last = true,
                        requester = toFocus,
                    )
                    if (typingIn == "to") {
                        Suggestions(scheme, toText) {
                            toText = it.name
                            toPick = it.stationId
                            typingIn = null
                        }
                    }
                }
                IconButton(onClick = swap) {
                    Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.metro_swap))
                }
            }

            // The routes looked up lately (15.10), while both fields are empty.
            if (fromText.isBlank() && toText.isBlank() && state.metroPrefs.recentRoutes && recentKey != null) {
                RecentRoutes(
                    scheme = scheme,
                    routes = metroRecentOf(state.metroRecent, recentKey),
                    onPick = { r ->
                        fromText = scheme.stations.firstOrNull { it.id == r.fromStationId }?.name.orEmpty()
                        fromPick = r.fromStationId
                        toText = scheme.stations.firstOrNull { it.id == r.toStationId }?.name.orEmpty()
                        toPick = r.toStationId
                        typingIn = null
                    },
                    onPin = { r, on -> vm.pinMetroRoute(recentKey, r.fromStationId, r.toStationId, on) },
                    onClear = { vm.clearMetroRecent(recentKey) },
                )
            }

            if (fromText.isNotBlank() && toText.isNotBlank()) {
                HorizontalDivider()
                RouteOrWhyNot(scheme, fromText, toText, fromIds, toIds, state.metroPrefs)
            }
        }
        }
    }

    if (styling) {
        MetroStyleDialog(
            current = state.metroPrefs.style,
            details = state.metroPrefs.styleDetails,
            onPick = { style -> vm.setMetroPrefs { it.copy(style = style) } },
            onDetails = { on -> vm.setMetroPrefs { it.copy(styleDetails = on) } },
            eraColors = state.metroPrefs.eraColors,
            onEraColors = { on -> vm.setMetroPrefs { it.copy(eraColors = on) } },
            onDismiss = { styling = false },
        )
    }

    if (mapPrefsShown) {
        MetroDialog(
            onDismissRequest = { mapPrefsShown = false },
            title = { Text(stringResource(R.string.metro_map_prefs)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MetroMapPrefsTicks(state.metroPrefs) { edit -> vm.setMetroPrefs(edit) }
                    MetroViewerPrefsTicks(state.metroPrefs) { edit -> vm.setMetroPrefs(edit) }
                }
            },
            confirmButton = { MetroTextButton(onClick = { mapPrefsShown = false }) { Text(stringResource(R.string.ok)) } },
        )
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

/**
 * A "from" or "to" field (16.10): a cross to empty it once something is
 * typed; the keyboard's key goes on to the next field, or, in the [last],
 * puts the keyboard away.
 */
@Composable
private fun StationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit = { onValueChange("") },
    last: Boolean = false,
    onNext: () -> Unit = {},
    requester: FocusRequester? = null,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        colors = metroFieldColors(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = if (value.isEmpty()) null else {
            {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.metro_clear_field, label))
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
            onDone = { focus.clearFocus() },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester == null) Modifier else Modifier.focusRequester(requester)),
    )
}

/** The stations the typed text could stand for, each with its line. */
@Composable
private fun Suggestions(scheme: MetroScheme, typed: String, onPick: (MetroNameChoice) -> Unit) {
    val choices = remember(scheme, typed) { metroStationSuggestions(scheme, typed) }
    val lines = remember(scheme) { scheme.lines.associateBy { it.id } }
    choices.forEach { choice ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(choice) }
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
    val background = metroLineColor(line.color)
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

/**
 * The routes looked up lately on this scheme (15.10): the pinned first, then
 * the newest; a tap fills both fields, the star pins or lets go. A route
 * whose station has since gone is left out. Clearing keeps the pinned.
 */
@Composable
private fun RecentRoutes(
    scheme: MetroScheme,
    routes: List<MetroRecentRoute>,
    onPick: (MetroRecentRoute) -> Unit,
    onPin: (MetroRecentRoute, Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val stations = remember(scheme) { scheme.stations.associateBy { it.id } }
    val lines = remember(scheme) { scheme.lines.associateBy { it.id } }
    val listed = routes.filter { it.fromStationId in stations && it.toStationId in stations }
    if (listed.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.metro_recent),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (listed.any { !it.pinned }) {
            MetroTextButton(onClick = onClear) { Text(stringResource(R.string.metro_recent_clear)) }
        }
    }
    listed.forEach { r ->
        val a = stations.getValue(r.fromStationId)
        val b = stations.getValue(r.toStationId)
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onPick(r) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lines[a.lineId]?.let { LineDot(it, size = 18) }
            Text(
                a.name + " → " + b.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            lines[b.lineId]?.let { LineDot(it, size = 18) }
            IconButton(onClick = { onPin(r, !r.pinned) }) {
                Icon(
                    if (r.pinned) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(if (r.pinned) R.string.metro_recent_unpin else R.string.metro_recent_pin),
                )
            }
        }
    }
}

/**
 * A station's card over the map (15.10): its name and line, the lines a
 * walk away — each a tap to that station's card, a closed walk faint — and
 * what to do with it: from here, to here, its line lit up or put out.
 */
@Composable
private fun MetroStationCard(
    facts: MetroStationFacts,
    lineLit: Boolean,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onLine: () -> Unit,
    onWalk: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalMetroStyle.current
    val details = LocalMetroStyleDetails.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .metroFrame(style, details)
                .padding(metroFrameInset(style, details))
                .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                facts.line?.let { LineDot(it, size = 20) }
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        facts.station.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    facts.line?.let {
                        Text(
                            it.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.metro_card_close))
                }
            }
            facts.station.closure?.let { closure ->
                val day = closure.expectedOpening
                Text(
                    if (day == null) stringResource(R.string.metro_station_closed, facts.station.name)
                    else stringResource(R.string.metro_station_closed_until, facts.station.name, formatShortDate(day, appLocale())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (facts.walks.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.metro_card_walks), style = MaterialTheme.typography.bodySmall)
                    facts.walks.forEach { walk ->
                        val line = walk.line ?: return@forEach
                        Box(
                            modifier = Modifier
                                .alpha(if (walk.closed) 0.4f else 1f)
                                .clickable(onClickLabel = walk.station.name) { onWalk(walk.station.id) },
                        ) { LineDot(line, size = 18) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MetroTextButton(onClick = onFrom) { Text(stringResource(R.string.metro_card_from)) }
                MetroTextButton(onClick = onTo) { Text(stringResource(R.string.metro_card_to)) }
                MetroTextButton(onClick = onLine) {
                    Text(stringResource(if (lineLit) R.string.metro_card_line_off else R.string.metro_card_line_on))
                }
            }
        }
    }
}

/**
 * The scheme's lines in a row under the map (15.10): each its number and
 * name; a tap lights that line up on the picture — its names written, the
 * rest faded — and another puts it out. Several may be lit at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetroLegend(lines: List<MetroLine>, lit: Set<Long>, onToggle: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        lines.forEach { line ->
            FilterChip(
                selected = line.id in lit,
                onClick = { onToggle(line.id) },
                label = { Text(line.name.ifBlank { line.label }, maxLines = 1) },
                leadingIcon = { LineDot(line, size = 18) },
            )
        }
    }
}

/**
 * Under the big map (15.10): from and to in one line, the route's minutes
 * and changes, and a tap to have the fields back.
 */
@Composable
private fun BigMapSummary(
    fromText: String,
    toText: String,
    route: MetroRoute?,
    onSwap: () -> Unit,
    onUnfold: () -> Unit,
) {
    val unfold = stringResource(R.string.metro_map_smaller)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = unfold) { onUnfold() }
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val ends = listOf(fromText.trim(), toText.trim()).filter { it.isNotEmpty() }
            Text(
                if (ends.isEmpty()) stringResource(R.string.metro_tap_two_stations) else ends.joinToString(" → "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (route != null && route.steps.isNotEmpty()) {
                val transfers = if (route.transfers == 0) stringResource(R.string.metro_no_transfers)
                else pluralStringResource(R.plurals.metro_transfers, route.transfers, route.transfers)
                Text(
                    stringResource(R.string.metro_minutes, route.minutes) + " · " + transfers,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        IconButton(onClick = onSwap) {
            Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.metro_swap))
        }
        IconButton(onClick = onUnfold) {
            Icon(Icons.Default.ExpandLess, contentDescription = unfold)
        }
    }
}

/** The route, or the reason there is none. */
@Composable
private fun RouteOrWhyNot(
    scheme: MetroScheme,
    fromText: String,
    toText: String,
    fromIds: Set<Long>,
    toIds: Set<Long>,
    prefs: MetroPrefs,
) {
    val locale = appLocale()

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
        else -> RouteSteps(scheme, route, timeline = prefs.routeTimeline, arrival = prefs.arrivalTime)
    }
}

/**
 * The route as plain text to share (15.10): the scheme's city and the two
 * ends, [summary] — minutes and changes — and each step on a line of its
 * own, as the screen says it.
 */
@Composable
private fun routeShareText(scheme: MetroScheme, route: MetroRoute, summary: String): String {
    val stations = remember(scheme) { scheme.stations.associateBy { it.id } }
    val lines = remember(scheme) { scheme.lines.associateBy { it.id } }
    fun nameOf(id: Long) = stations[id]?.name.orEmpty()
    val ends = metroRouteEnds(route)
    val head = buildString {
        append(scheme.city)
        if (ends != null) append(": ").append(nameOf(ends.first)).append(" → ").append(nameOf(ends.second))
    }
    val steps = route.steps.map { step ->
        when (step) {
            is MetroRouteStep.Ride -> {
                val line = lines[step.lineId]
                val name = listOfNotNull(line?.label, line?.name?.takeIf { it.isNotBlank() }).joinToString(" ")
                "• " + name + ": " + nameOf(step.stationIds.first()) + " → " + nameOf(step.stationIds.last()) +
                    " · " + stringResource(R.string.metro_minutes, step.minutes)
            }
            is MetroRouteStep.Change -> {
                val words = if (step.fromStationId == step.toStationId) {
                    stringResource(R.string.metro_change_fork, nameOf(step.toStationId))
                } else {
                    stringResource(R.string.metro_change_to, nameOf(step.toStationId))
                }
                "• " + words + " · " + stringResource(R.string.metro_minutes, step.minutes)
            }
        }
    }
    return (listOf(head, summary) + steps).joinToString("\n")
}

/**
 * The route, step by step. With [timeline] (15.10) each ride lies on a bar
 * of its line's colour and each walk on a thin grey one, as on the metro's
 * own apps; with [arrival], the time one would get there leaving now, kept
 * up to the minute.
 */
@Composable
private fun RouteSteps(scheme: MetroScheme, route: MetroRoute, timeline: Boolean, arrival: Boolean) {
    val stations = remember(scheme) { scheme.stations.associateBy { it.id } }
    val lines = remember(scheme) { scheme.lines.associateBy { it.id } }
    fun nameOf(id: Long) = stations[id]?.name.orEmpty()

    val transfers = if (route.transfers == 0) stringResource(R.string.metro_no_transfers)
    else pluralStringResource(R.plurals.metro_transfers, route.transfers, route.transfers)
    // The clock read again every half minute, so the arrival does not stand still.
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalTime.now()
        }
    }
    val arrives = if (arrival) " · " + stringResource(R.string.metro_arrive_at, formatTaskTime(metroArrival(now, route.minutes))) else ""
    val summary = stringResource(R.string.metro_minutes, route.minutes) + " · " + transfers
    // Shared as plain text (15.10): to send someone the way, or keep it in a note.
    val context = LocalContext.current
    val shareText = routeShareText(scheme, route, summary)
    val shareTitle = stringResource(R.string.metro_share_route)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            summary + arrives,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            runCatching { context.startActivity(Intent.createChooser(send, shareTitle)) }
        }) {
            Icon(Icons.Default.Share, contentDescription = shareTitle)
        }
    }

    route.steps.forEach { step ->
        when (step) {
            is MetroRouteStep.Ride -> {
                val line = lines[step.lineId] ?: return@forEach
                if (timeline) {
                    // A bar of the line's colour down the whole ride (15.10).
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .padding(start = 9.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(metroLineColor(line.color), RoundedCornerShape(3.dp)),
                        )
                        RideStep(scheme, line, step, ::nameOf)
                    }
                } else {
                    RideStep(scheme, line, step, ::nameOf)
                }
            }
            is MetroRouteStep.Change -> {
                val fork = step.fromStationId == step.toStationId
                val text = if (fork) {
                    stringResource(R.string.metro_change_fork, nameOf(step.toStationId))
                } else {
                    stringResource(R.string.metro_change_to, nameOf(step.toStationId))
                }
                val toLine = stations[step.toStationId]?.lineId?.let { lines[it] }
                // A walk on a thin grey bar (15.10), between the rides' coloured ones.
                Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    if (timeline) {
                        Box(
                            modifier = Modifier
                                .padding(start = 11.dp, end = 14.dp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outline),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(vertical = if (timeline) 6.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
        // A tap unfolds the stations the train calls at on the way (06.10).
        var open by rememberSaveable(ride.stationIds) { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.metro_ride_stations, count, count, nameOf(ride.stationIds.last())) +
                    " · " + stringResource(R.string.metro_minutes, ride.minutes),
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (open) R.string.metro_hide_stations else R.string.metro_show_stations),
            )
        }
        if (open) RideStations(line, ride, nameOf)
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

/**
 * The stations of one ride, first to last, on a thin line of its colour: the
 * ends bold, a closed one the train runs through grey.
 */
@Composable
private fun RideStations(line: MetroLine, ride: MetroRouteStep.Ride, nameOf: (Long) -> String) {
    val color = metroLineColor(line.color)
    val passed = ride.passedClosedStationIds.toSet()
    Column(modifier = Modifier.padding(start = 8.dp)) {
        ride.stationIds.forEachIndexed { i, id ->
            val end = i == 0 || i == ride.stationIds.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(if (end) 12.dp else 8.dp)
                        .background(if (id in passed) MaterialTheme.colorScheme.outline else color, CircleShape),
                )
                Text(
                    nameOf(id),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (end) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (id in passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
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
    MetroDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_create_scheme)) },
        text = {
            OutlinedTextField(
                colors = metroFieldColors(),
                value = city,
                onValueChange = { city = it },
                label = { Text(stringResource(R.string.metro_city)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            MetroTextButton(onClick = { onCreate(city) }, enabled = city.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            MetroTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
    MetroDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.metro_reopen_title)) },
        text = { Text(text) },
        confirmButton = {
            MetroTextButton(onClick = onOpened) { Text(stringResource(R.string.metro_reopen_yes)) }
        },
        dismissButton = {
            Row {
                MetroTextButton(onClick = onLater) { Text(stringResource(R.string.metro_reopen_later)) }
                MetroTextButton(onClick = onMoveDate) { Text(stringResource(R.string.metro_reopen_move)) }
            }
        },
    )
}
