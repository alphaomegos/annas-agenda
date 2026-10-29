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
