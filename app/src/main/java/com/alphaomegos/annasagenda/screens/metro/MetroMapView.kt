package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.magnifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.alphaomegos.annasagenda.support.metroStationsInside
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphaomegos.annasagenda.model.MetroLabelsMode
import com.alphaomegos.annasagenda.model.MetroScheme
import com.alphaomegos.annasagenda.support.METRO_LAYOUT_STEP
import com.alphaomegos.annasagenda.support.MetroPoint
import com.alphaomegos.annasagenda.support.MetroRoute
import com.alphaomegos.annasagenda.support.MetroRouteStep
import com.alphaomegos.annasagenda.support.metroSidesOf
import com.alphaomegos.annasagenda.support.metroSnapped
import com.alphaomegos.annasagenda.support.metroStationAt
import com.alphaomegos.annasagenda.support.metroStationsMovingWith
import com.alphaomegos.annasagenda.support.METRO_CASING
import com.alphaomegos.annasagenda.support.METRO_CORNER_RADIUS
import com.alphaomegos.annasagenda.support.metroRoundedCorners
import com.alphaomegos.annasagenda.support.METRO_LABEL_HIDDEN
import com.alphaomegos.annasagenda.support.MetroViewport
import com.alphaomegos.annasagenda.support.metroEditablePart
import com.alphaomegos.annasagenda.support.metroFittedViewport
import com.alphaomegos.annasagenda.support.metroPictureFacts
import com.alphaomegos.annasagenda.support.metroLabelSides
import com.alphaomegos.annasagenda.support.metroTickAngles
import com.alphaomegos.annasagenda.support.metroTerminusOutwards
import com.alphaomegos.annasagenda.support.metroLabelTopLeft
import com.alphaomegos.annasagenda.support.METRO_STATION_RADIUS
import com.alphaomegos.annasagenda.support.MetroBendHit
import com.alphaomegos.annasagenda.support.MetroTrack
import com.alphaomegos.annasagenda.support.metroAloneDropPoint
import com.alphaomegos.annasagenda.support.metroBendPointAt
import com.alphaomegos.annasagenda.support.metroBendPoints
import com.alphaomegos.annasagenda.support.metroBendNeighbours
import com.alphaomegos.annasagenda.support.metroLabelledStations
import com.alphaomegos.annasagenda.support.metroTrackAt
import com.alphaomegos.annasagenda.support.metroAngleAnchors
import com.alphaomegos.annasagenda.support.metroOctilinearSnap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.floor
import kotlin.math.abs
import com.alphaomegos.annasagenda.support.metroGuided
import com.alphaomegos.annasagenda.support.metroEraColor
import kotlin.math.cos
import kotlin.math.sin
import com.alphaomegos.annasagenda.support.metroInterchangeStations
import com.alphaomegos.annasagenda.support.metroTermini
import com.alphaomegos.annasagenda.support.metroLabelFacesOutward
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance

/**
 * The picture of the metro map (05.10): lines in their colours, stations as
 * rings, transfers as grey links, names once zoomed in enough to read them.
 *
 * [layout] says where each station is (support/MetroLayout.kt). With a
 * [route], everything off it fades and the route stands out; [marked] are
 * the stations chosen as "from" and "to". A tap on a station is
 * [onTapStation]; with [onMoveStation] (the editor) a station is dragged by
 * a finger that starts on it, and let go where it should be. Two fingers
 * zoom, one finger off a station pans; the view survives turning the phone.
 *
 * With [gridStep] (06.10) the grid is drawn and a dragged station lands on
 * its nearest node; with the scheme's transfers kept together, the whole
 * transfer moves with the station dragged.
 *
 * 08.10: with [moveAlone] a dragged station moves by itself — glued to its
 * interchange when the scheme glues rings; with [bendMode] a finger bends
 * track instead of moving stations: a tap on a track gives it an elbow (and
 * the next one, and straight again), a finger along a track puts a point
 * there and drags it, a tap on a point takes it away — each said through
 * [onBend]. Track is drawn through its bends; with one name per interchange
 * only the names [metroLabelledStations] picks are written. With the
 * scheme's casing each line is edged in the background colour; with its
 * capsules the links between interchange stations are drawn as passages.
 * Each name goes on its station's side (support/MetroLabels.kt); with
 * [namingMode] every name shows, the hidden ones faintly, for the editor's
 * names tool. A drag of a station in [group] moves the whole group, with
 * the track bent between its stations (the editor's selection). A new
 * [fitKey] fits the whole picture to the view again. With [capture] the
 * picture is recorded into that layer too, to be saved as an image.
 *
 * Layers (14.10): [hiddenLines] are not drawn — or drawn faintly, with
 * [ghostHidden] — and nothing of them can be touched; [lockedLines] are
 * drawn but nothing of them moves. Glue and guides look only at what is in
 * sight, and "fit" fits it.
 *
 * With [onMarquee] (14.10) one finger off a station draws a box, and the
 * stations in reach inside it are said when it is let go; two fingers pan
 * and zoom as ever. [onTapEmpty] is a tap on no station.
 *
 * [loupe] and [haptics] (15.10): the loupe over the finger while dragging,
 * and the tick when a dragged thing clicks into place — both the user's to
 * turn off.
 */
internal sealed interface MetroBendAction {
    data class Cycle(val track: MetroTrack) : MetroBendAction
    data class Insert(val hit: MetroBendHit, val x: Float, val y: Float) : MetroBendAction
    data class Move(val hit: MetroBendHit, val x: Float, val y: Float) : MetroBendAction
    data class Remove(val hit: MetroBendHit) : MetroBendAction
}

/** See [MetroMapView]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MetroMapView(
    scheme: MetroScheme,
    layout: Map<Long, MetroPoint>,
    modifier: Modifier = Modifier,
    route: MetroRoute? = null,
    marked: Set<Long> = emptySet(),
    onTapStation: (Long) -> Unit = {},
    onMoveStation: ((Long, Float, Float) -> Unit)? = null,
    gridStep: Float? = null,
    moveAlone: Boolean = false,
    bendMode: Boolean = false,
    onBend: (MetroBendAction) -> Unit = {},
    namingMode: Boolean = false,
    group: Set<Long> = emptySet(),
    fitKey: Int = 0,
    capture: GraphicsLayer? = null,
    hiddenLines: Set<Long> = emptySet(),
    lockedLines: Set<Long> = emptySet(),
    ghostHidden: Boolean = false,
    onMarquee: ((Set<Long>) -> Unit)? = null,
    onTapEmpty: (() -> Unit)? = null,
    loupe: Boolean = true,
    haptics: Boolean = true,
) {
    // Where the picture is looked at (14.10), in its own units: pixels to a
    // unit and the point at the middle; a scale of -1 is "fitted", as it opens.
    // Kept so, dropping a station past the edge of the rest no longer
    // rescales and shifts the whole picture.
    var viewK by rememberSaveable { mutableStateOf(-1f) }
    var viewCx by rememberSaveable { mutableStateOf(0f) }
    var viewCy by rememberSaveable { mutableStateOf(0f) }
    // "Fit to the screen" (08.10): each new [fitKey] brings the whole picture
    // back into view. The one last acted on is kept, so turning the phone
    // does not undo the zoom.
    var fitDone by rememberSaveable { mutableStateOf(fitKey) }
    LaunchedEffect(fitKey) {
        if (fitKey != fitDone) {
            viewK = -1f
            fitDone = fitKey
        }
    }
    // A drag in flight: the station and where on the screen it is now.
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    // A bend point being dragged: which, and whether it is a new one put in.
    var dragBend by remember { mutableStateOf<Pair<MetroBendHit, Boolean>?>(null) }
    // A box being drawn round stations (14.10): its two corners on the screen.
    var marquee by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }

    val currentTap by rememberUpdatedState(onTapStation)
    val currentMove by rememberUpdatedState(onMoveStation)
    val currentGrid by rememberUpdatedState(gridStep)
    val currentScheme by rememberUpdatedState(scheme)
    val currentBendMode by rememberUpdatedState(bendMode)
    val currentBend by rememberUpdatedState(onBend)
    val currentMoveAlone by rememberUpdatedState(moveAlone)
    val currentGroup by rememberUpdatedState(group)
    val currentMarquee by rememberUpdatedState(onMarquee)
    // A tick under the finger each time a dragged thing clicks into a place (14.10).
    val feedback = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(feedback)
    val currentTicks by rememberUpdatedState(haptics)
    val currentTapEmpty by rememberUpdatedState(onTapEmpty)
    // Layers (14.10): what is out of sight, what may not move, and the part a finger may bend.
    val hiddenStations = remember(scheme.stations, hiddenLines) {
        if (hiddenLines.isEmpty()) emptySet() else scheme.stations.filter { it.lineId in hiddenLines }.map { it.id }.toSet()
    }
    val fixedStations = remember(scheme.stations, hiddenLines, lockedLines) {
        val fixedLines = hiddenLines + lockedLines
        if (fixedLines.isEmpty()) emptySet() else scheme.stations.filter { it.lineId in fixedLines }.map { it.id }.toSet()
    }
    val reachable = remember(layout, fixedStations) { if (fixedStations.isEmpty()) layout else layout.filterKeys { it !in fixedStations } }
    val inSight = remember(layout, hiddenStations) { if (hiddenStations.isEmpty()) layout else layout.filterKeys { it !in hiddenStations } }
    val editable = remember(scheme, hiddenLines, lockedLines) {
        metroEditablePart(scheme.copy(hiddenLines = hiddenLines.toList(), lockedLines = lockedLines.toList()))
    }
    val currentEditable by rememberUpdatedState(editable)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 11.sp * scheme.labelScale, color = labelColor)
    // The rings' fill and the saved picture's ground: white on Windows' page (15.10).
    val ringFill = metroPaper()
    val markColor = MaterialTheme.colorScheme.primary
    val transferColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    // Measured again only when a name or the text changes (14.10), not on every move.
    val names = remember(scheme.stations) { scheme.stations.map { it.id to it.name } }
    val labels = remember(names, measurer, labelColor, scheme.labelScale) {
        names.associate { (id, name) -> id to measurer.measure(name, labelStyle) }
    }
    // Each line's colour as drawn: its own, or the style's era's (15.10).
    val era = LocalMetroEraColors.current
    val style = LocalMetroStyle.current
    val lineColors = remember(scheme.lines, era, style) {
        scheme.lines.associate { it.id to Color(if (era) metroEraColor(style, it.color) else it.color) }
    }
    val lineOf = remember(scheme.stations) { scheme.stations.associate { it.id to it.lineId } }
    val labelled = remember(scheme, layout) { metroLabelledStations(scheme, layout) }
    // Each name's side (08.10): set by hand, or chosen away from its track; hidden ones apart.
    // All in one pass (14.10): asked station by station, each went through the whole scheme.
    val facts = remember(scheme, layout) { metroPictureFacts(scheme, layout) }
    val labelSides = remember(facts) { metroLabelSides(scheme, layout, facts) }
    // Ticks (08.10): which stations stay rings, which are ends, and which way each tick points.
    val interchanges = remember(scheme.transfers) { metroInterchangeStations(scheme) }
    val termini = remember(scheme.lines) { metroTermini(scheme) }
    val tickAngles = remember(facts, labelSides, scheme.tickStations) {
        if (!scheme.tickStations) emptyMap() else metroTickAngles(scheme, labelSides, facts)
    }
    // Line numbers past the ends of lines (08.10): which way out at each end, and the numbers measured.
    val outwards = remember(facts, scheme.lineBadges) {
        if (!scheme.lineBadges) emptyMap() else metroTerminusOutwards(facts)
    }
    val badgeStyle = TextStyle(fontSize = 10.sp * scheme.labelScale, color = Color.White, fontWeight = FontWeight.Bold)
    val badges = remember(scheme.lines, measurer, scheme.labelScale) {
        scheme.lines.associate { it.id to measurer.measure(it.label, badgeStyle) }
    }
    val hiddenLabels = remember(scheme.stations) { scheme.stations.filter { it.labelSide == METRO_LABEL_HIDDEN }.map { it.id }.toSet() }
    val edges = remember(scheme.lines) {
        scheme.lines.flatMap { line ->
            metroSidesOf(line).flatMap { (s, sides) -> sides.values.filter { s < it }.map { Triple(line.id, s, it) } }
        }
    }
    val onRoute = remember(route) {
        val pairs = HashSet<Pair<Long, Long>>()
        val stations = HashSet<Long>()
        route?.steps?.forEach { step ->
            when (step) {
                is MetroRouteStep.Ride -> {
                    stations += step.stationIds
                    step.stationIds.zipWithNext { a, b -> pairs += minOf(a, b) to maxOf(a, b) }
                }
                is MetroRouteStep.Change -> {
                    stations += step.fromStationId; stations += step.toStationId
                    pairs += minOf(step.fromStationId, step.toStationId) to maxOf(step.fromStationId, step.toStationId)
                }
            }
        }
        pairs to stations
    }

    // Clipped: a Canvas draws past its own edges, and the picture zoomed or
    // panned went over the fields below it (06.10).
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (w <= 0f || h <= 0f || layout.isEmpty()) return@BoxWithConstraints
        val fitted = metroFittedViewport(inSight.values.ifEmpty { layout.values }, w, h)
        val view = if (viewK > 0f) MetroViewport(viewK, viewCx, viewCy) else fitted
        val k = view.k
        val cx = view.cx
        val cy = view.cy
        fun toScreen(p: MetroPoint) = Offset((p.x - cx) * k + w / 2, (p.y - cy) * k + h / 2)
        val hitPx = with(density) { 22.dp.toPx() }
        // How near a direction of 45° a dropped station is pulled onto it (08.10).
        val angleTolPx = with(density) { 18.dp.toPx() }

        // Keyed on what does not change while a finger is down: zooming must
        // not restart the very gesture that zooms. The scale is read afresh.
        val gestures = Modifier.pointerInput(layout, w, h, onMoveStation != null, fixedStations, hiddenStations) {
            fun viewNow(): MetroViewport = if (viewK > 0f) MetroViewport(viewK, viewCx, viewCy) else fitted
            fun setView(v: MetroViewport) { viewK = v.k; viewCx = v.cx; viewCy = v.cy }
            fun toMap(o: Offset): MetroPoint {
                val v = viewNow()
                return MetroPoint(v.toMapX(o.x, w), v.toMapY(o.y, h))
            }
            fun toScreenNow(p: MetroPoint): Offset {
                val v = viewNow()
                return Offset(v.toScreenX(p.x, w), v.toScreenY(p.y, h))
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = toMap(down.position)
                val hitRadius = hitPx / viewNow().k
                val bending = currentBendMode
                val bendPoint = if (bending) metroBendPointAt(currentEditable, start.x, start.y, hitRadius) else null
                val onTrack = if (bending && bendPoint == null) metroTrackAt(currentEditable, layout, start.x, start.y, hitRadius * 0.7f) else null
                val grabbed = if (currentMove != null && !bending) metroStationAt(reachable, start.x, start.y, hitRadius) else null
                var travelled = 0f
                var fingers = 1
                // A second finger during a drag calls the drag off (14.10): the picture
                // zooms instead, and the station stays where it was — before, it was
                // dropped where the first finger had been, on a picture since moved.
                var cancelled = false
                // The station keeps where it sits under the finger, rather than jumping to it.
                val grabOffset = when {
                    grabbed != null -> toScreenNow(layout.getValue(grabbed)) - down.position
                    bendPoint != null -> toScreenNow(metroBendPoints(currentScheme, bendPoint.track)[bendPoint.index]) - down.position
                    else -> Offset.Zero
                }
                if (grabbed != null) {
                    dragId = grabbed
                    dragAt = down.position + grabOffset
                }
                val grabbedBend = when {
                    bendPoint != null -> bendPoint to false
                    onTrack != null -> onTrack to true
                    else -> null
                }
                if (grabbedBend != null) {
                    dragBend = grabbedBend
                    dragAt = down.position + grabOffset
                }
                // Where the thing dragged would land now (14.10), to feel it click into a place.
                val movingAtGrab = grabbed?.let { g ->
                    when {
                        g in currentGroup -> currentGroup
                        currentMoveAlone -> setOf(g)
                        else -> metroStationsMovingWith(currentScheme, g)
                    } - fixedStations
                }
                fun landing(raw: MetroPoint): MetroPoint {
                    if (grabbed == null || movingAtGrab == null) {
                        val bend = grabbedBend ?: return raw
                        return metroBendLanding(currentScheme, layout, bend.first, bend.second, raw, currentGrid, angleTolPx / viewNow().k)
                    }
                    val snapped = metroDropSnap(currentScheme, layout, grabbed, movingAtGrab, raw, currentGrid, angleTolPx / viewNow().k, hiddenStations)
                    return if (currentMoveAlone && grabbed !in currentGroup) metroAloneDropPoint(currentScheme, inSight, grabbed, snapped) else snapped
                }
                var clickedInto: MetroPoint? = null
                // Off every station with the box on: one finger draws it.
                val boxing = currentMarquee != null && grabbed == null && grabbedBend == null && !bending
                if (boxing) marquee = down.position to down.position
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    fingers = maxOf(fingers, pressed.size)
                    if ((grabbed != null || grabbedBend != null || boxing) && !cancelled && pressed.size > 1) {
                        cancelled = true
                        dragId = null
                        dragBend = null
                        marquee = null
                    }
                    if (boxing && !cancelled && pressed.size == 1) {
                        val change = pressed[0]
                        travelled += change.positionChange().getDistance()
                        marquee = down.position to change.position
                        change.consume()
                    } else if ((grabbed != null || grabbedBend != null) && !cancelled && pressed.size == 1) {
                        val change = pressed[0]
                        travelled += change.positionChange().getDistance()
                        dragAt = change.position + grabOffset
                        change.consume()
                        val raw = toMap(dragAt)
                        val at = landing(raw)
                        val snapped = at != raw
                        if (currentTicks && snapped && at != clickedInto && travelled > viewConfiguration.touchSlop) {
                            currentHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        clickedInto = if (snapped) at else null
                    } else {
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        travelled += pan.getDistance()
                        var v = viewNow()
                        if (zoomChange != 1f) {
                            // The point under the fingers stays under them; from half the fitted scale to twelve times it.
                            val c = event.calculateCentroid()
                            v = v.zoomed(zoomChange, c.x, c.y, w, h, fitted.k * 0.5f, fitted.k * 12f)
                        }
                        if (zoomChange != 1f || pan != Offset.Zero) setView(v.panned(pan.x, pan.y))
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })

                val slop = viewConfiguration.touchSlop
                val box = marquee
                marquee = null
                if (cancelled) {
                    // Nothing dropped, nothing tapped.
                } else if (boxing && box != null && travelled > slop) {
                    currentMarquee?.invoke(metroStationsInside(reachable, toMap(box.first), toMap(box.second)))
                } else if (grabbedBend != null) {
                    val (hit, insert) = grabbedBend
                    if (travelled > slop) {
                        val at = metroBendLanding(currentScheme, layout, hit, insert, toMap(dragAt), currentGrid, angleTolPx / viewNow().k)
                        currentBend(if (insert) MetroBendAction.Insert(hit, at.x, at.y) else MetroBendAction.Move(hit, at.x, at.y))
                    } else if (fingers == 1) {
                        currentBend(if (insert) MetroBendAction.Cycle(hit.track) else MetroBendAction.Remove(hit))
                    }
                    dragBend = null
                } else if (grabbed != null && travelled > slop) {
                    val s = currentScheme
                    val moving = when {
                        grabbed in currentGroup -> currentGroup
                        currentMoveAlone -> setOf(grabbed)
                        else -> metroStationsMovingWith(s, grabbed)
                    } - fixedStations
                    val at = metroDropSnap(s, layout, grabbed, moving, toMap(dragAt), currentGrid, angleTolPx / viewNow().k, hiddenStations)
                    currentMove?.invoke(grabbed, at.x, at.y)
                } else if (travelled <= slop && fingers == 1) {
                    val tapped = metroStationAt(reachable, start.x, start.y, hitRadius)
                    if (tapped != null) currentTap(tapped) else currentTapEmpty?.invoke()
                }
                dragId = null
            }
        }

        // With [capture] (08.10) the picture is also recorded, on its background, for saving as a file.
        val recorded = if (capture == null) Modifier else Modifier.drawWithContent {
            capture.record {
                drawRect(ringFill)
                this@drawWithContent.drawContent()
            }
            drawLayer(capture)
        }
        // A loupe while dragging (14.10), as over text: the finger hides what it moves,
        // so what is under it shows, twice as big, above it. From Android 9 on; before, nothing.
        val loupeLift = with(density) { 96.dp.toPx() }
        val magnified = if (!loupe) Modifier else Modifier.magnifier(
            sourceCenter = { if (dragId != null || dragBend != null) dragAt else Offset.Unspecified },
            magnifierCenter = { if (dragId != null || dragBend != null) dragAt - Offset(0f, loupeLift) else Offset.Unspecified },
            zoom = 2f,
            size = DpSize(112.dp, 112.dp),
            cornerRadius = 56.dp,
        )
        Canvas(modifier = Modifier.fillMaxSize().then(recorded).then(gestures).then(magnified)) {
            // Glued rings touch in the picture's units, so then they scale with it, unclamped.
            val glued = scheme.glue > 0
            // The look's sizes (08.10) on top.
            val lineWidth = scheme.lineScale *
                (if (glued) (0.16f * k).coerceAtMost(10.dp.toPx()) else (0.16f * k).coerceIn(3.dp.toPx(), 10.dp.toPx()))
            val radius = scheme.stationScale *
                (if (glued) METRO_STATION_RADIUS * k else (METRO_STATION_RADIUS * k).coerceIn(4.dp.toPx(), 9.dp.toPx()))
            val (routePairs, routeStations) = onRoute
            val fade = route != null
            // A drag in flight moves its station (snapped, with a grid) and,
            // with transfers kept together, the rest of its transfer as far.
            val dragged = dragId?.let { layout[it] }
            val moving = dragId?.takeIf { dragged != null }?.let {
                when {
                    it in group -> group
                    moveAlone -> setOf(it)
                    else -> metroStationsMovingWith(scheme, it)
                } - fixedStations
            } ?: emptySet()
            val inGroup = dragId != null && dragId in group
            fun dragPoint(): MetroPoint {
                val raw = MetroPoint((dragAt.x - w / 2) / k + cx, (dragAt.y - h / 2) / k + cy)
                val bend = dragBend ?: return gridStep?.let { metroSnapped(raw, it) } ?: raw
                return metroBendLanding(scheme, layout, bend.first, bend.second, raw, gridStep, angleTolPx / k)
            }
            val shift = dragged?.let { from ->
                val raw = MetroPoint((dragAt.x - w / 2) / k + cx, (dragAt.y - h / 2) / k + cy)
                val snapped = metroDropSnap(scheme, layout, dragId!!, moving, raw, gridStep, angleTolPx / k, hiddenStations)
                val to = if (moveAlone && !inGroup) metroAloneDropPoint(scheme, inSight, dragId!!, snapped) else snapped
                MetroPoint(to.x - from.x, to.y - from.y)
            }
            fun at(id: Long): Offset? {
                val p = layout[id] ?: return null
                if (shift != null && id in moving) return toScreen(MetroPoint(p.x + shift.x, p.y + shift.y))
                return toScreen(p)
            }

            if (gridStep != null && gridStep > 0f) {
                // Never denser than a line every 8 dp: zoomed out, every other one goes.
                var step = gridStep
                while (step * k < 8.dp.toPx()) step *= 2
                val thin = 1.dp.toPx()
                val left = (-w / 2) / k + cx
                val right = (w / 2) / k + cx
                val top = (-h / 2) / k + cy
                val bottom = (h / 2) / k + cy
                var gx = floor(left / step) * step
                while (gx <= right) {
                    val sx = (gx - cx) * k + w / 2
                    drawLine(gridColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = thin)
                    gx += step
                }
                var gy = floor(top / step) * step
                while (gy <= bottom) {
                    val sy = (gy - cy) * k + h / 2
                    drawLine(gridColor, Offset(0f, sy), Offset(w, sy), strokeWidth = thin)
                    gy += step
                }
            }

            val capsules = scheme.showTransferLinks && scheme.transferCapsules
            fun drawLinks() = scheme.transfers.forEach { t ->
                // A link to a hidden line goes with it, or is drawn faintly with it.
                val ghost = t.aStationId in hiddenStations || t.bStationId in hiddenStations
                if (ghost && !ghostHidden) return@forEach
                val a = at(t.aStationId) ?: return@forEach
                val c = at(t.bStationId) ?: return@forEach
                val lit = !fade || (minOf(t.aStationId, t.bStationId) to maxOf(t.aStationId, t.bStationId)) in routePairs
                val alpha = if (ghost) GHOST_ALPHA else if (lit) 1f else 0.25f
                if (capsules) {
                    // A passage as on the Moscow map (08.10): a band as wide as the
                    // rings, edged dark, over the track; the rings sit on its ends.
                    val outer = radius * 2f
                    drawLine(labelColor.copy(alpha = alpha), a, c, strokeWidth = outer, cap = StrokeCap.Round)
                    drawLine(ringFill.copy(alpha = alpha), a, c, strokeWidth = outer - radius * 0.6f, cap = StrokeCap.Round)
                } else {
                    drawLine(transferColor.copy(alpha = alpha), a, c, strokeWidth = lineWidth * 0.8f, cap = StrokeCap.Round)
                }
            }
            if (scheme.showTransferLinks && !capsules) drawLinks()
            val bendDrag = dragBend
            // Each track as drawn: its ends, its bends (the one being dragged where the finger is).
            fun trackPoints(s: Long, t: Long): List<Offset>? {
                val a = at(s) ?: return null
                val c = at(t) ?: return null
                val track = MetroTrack(s, t)
                val bends = metroBendPoints(scheme, track).toMutableList()
                if (bendDrag != null && bendDrag.first.track == track) {
                    val p = dragPoint()
                    val i = bendDrag.first.index
                    if (bendDrag.second) bends.add(i.coerceIn(0, bends.size), p) else if (i in bends.indices) bends[i] = p
                }
                // Track bent between two stations moving moves with them (08.10).
                val carried = shift != null && s in moving && t in moving
                return listOf(a) + bends.map { if (carried) toScreen(MetroPoint(it.x + shift!!.x, it.y + shift.y)) else toScreen(it) } + c
            }
            fun drawTrack(pts: List<Offset>, paint: Color, width: Float) {
                if (pts.size == 2) {
                    drawLine(paint, pts[0], pts[1], strokeWidth = width, cap = StrokeCap.Round)
                } else {
                    // Track drawn through its bends (08.10), the corners rounded if so chosen.
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        if (scheme.roundCorners) {
                            metroRoundedCorners(pts.map { MetroPoint(it.x, it.y) }, METRO_CORNER_RADIUS * k).forEach { c ->
                                lineTo(c.from.x, c.from.y)
                                // The quadratic curve round the corner, as a cubic: 2/3 of the way to it from each end.
                                cubicTo(
                                    c.from.x + (c.corner.x - c.from.x) * 2 / 3, c.from.y + (c.corner.y - c.from.y) * 2 / 3,
                                    c.to.x + (c.corner.x - c.to.x) * 2 / 3, c.to.y + (c.corner.y - c.to.y) * 2 / 3,
                                    c.to.x, c.to.y,
                                )
                            }
                            lineTo(pts.last().x, pts.last().y)
                        } else {
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                    }
                    drawPath(path, paint, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            // Line by line (08.10): with casing, a line's edging first and then
            // its colour, so a line drawn later crosses an earlier one with a
            // gap of the background on each side, as on a printed map.
            edges.groupBy { it.first }.forEach { (lineId, ofLine) ->
                // A hidden line (14.10): not drawn, or faintly with its ghosts shown.
                val ghost = lineId in hiddenLines
                if (ghost && !ghostHidden) return@forEach
                val color = lineColors[lineId] ?: Color(0xFF888888)
                val drawn = ofLine.mapNotNull { (_, s, t) ->
                    val pts = trackPoints(s, t) ?: return@mapNotNull null
                    val lit = !fade || (s to t) in routePairs
                    Triple(pts, lit, if (fade && lit) lineWidth * 1.5f else lineWidth)
                }
                fun alphaOf(lit: Boolean) = if (ghost) GHOST_ALPHA else if (lit) 1f else 0.25f
                if (scheme.lineCasing && !ghost) drawn.forEach { (pts, lit, width) ->
                    drawTrack(pts, ringFill.copy(alpha = alphaOf(lit)), width * (1f + 2 * METRO_CASING))
                }
                drawn.forEach { (pts, lit, width) -> drawTrack(pts, color.copy(alpha = alphaOf(lit)), width) }
                if (bendMode && !ghost && lineId !in lockedLines) drawn.forEach { (pts, _, _) ->
                    pts.subList(1, pts.size - 1).forEach { drawCircle(markColor, radius = 4.dp.toPx(), center = it) }
                }
            }
            // Guides (08.10): where the station dragged stands level with, or under, one
            // standing still, a line across the picture shows it.
            val dropAt = shift?.let { s -> dragged?.let { MetroPoint(it.x + s.x, it.y + s.y) } }
            if (scheme.alignGuides && dropAt != null) {
                val still = inSight.filterKeys { it !in moving }.values
                val thin = 1.dp.toPx()
                if (still.any { abs(it.x - dropAt.x) < 1e-4f }) {
                    val sx = toScreen(dropAt).x
                    drawLine(markColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = thin)
                }
                if (still.any { abs(it.y - dropAt.y) < 1e-4f }) {
                    val sy = toScreen(dropAt).y
                    drawLine(markColor, Offset(0f, sy), Offset(w, sy), strokeWidth = thin)
                }
            }

            if (capsules) drawLinks()
            val showLabels = when (scheme.labelsMode) {
                MetroLabelsMode.AUTO -> k * METRO_LAYOUT_STEP > 52.dp.toPx() * scheme.labelScale
                MetroLabelsMode.ALWAYS -> true
                MetroLabelsMode.NEVER -> false
            }
            scheme.stations.forEach { st ->
                val p = at(st.id) ?: return@forEach
                // A station of a hidden line (14.10): not drawn, or faintly and nameless.
                val ghost = st.id in hiddenStations
                if (ghost && !ghostHidden) return@forEach
                val color = lineOf[st.id]?.let { lineColors[it] } ?: Color(0xFF888888)
                val lit = !fade || st.id in routeStations
                val alpha = if (ghost) GHOST_ALPHA else if (lit) 1f else 0.3f
                // Ticks (08.10): a station that is no interchange is a bar off its line, a
                // terminus a bar across its end; a closed one grey. Interchanges stay rings.
                val tick = if (scheme.tickStations && st.id !in interchanges) tickAngles[st.id] else null
                val tickLen = lineWidth * 1.1f
                if (tick != null) {
                    val u = Offset(cos(tick).toFloat(), sin(tick).toFloat()) * tickLen
                    val paint = if (st.closure != null) transferColor else color
                    val from = if (st.id in termini) p - u else p
                    drawLine(paint, from, p + u, strokeWidth = lineWidth * 0.75f, alpha = alpha)
                } else {
                    drawCircle(if (st.closure != null) transferColor else ringFill, radius = radius, center = p, alpha = alpha)
                    drawCircle(color, radius = radius, center = p, style = Stroke(radius * 0.45f), alpha = alpha)
                }
                if (st.id in marked) drawCircle(markColor, radius = radius * 1.8f, center = p, style = Stroke(2.5f.dp.toPx()))
                // The line's number in a disc past its end (08.10); a name that way starts past it.
                val out = outwards[st.id]
                val markEnd = (if (tick != null) tickLen else radius) + 3.dp.toPx()
                var badgeRoom = 0f
                val badge = lineOf[st.id]?.let { badges[it] }
                if (out != null && badge != null && !ghost) {
                    val r = maxOf(badge.size.width, badge.size.height) / 2f + 2.dp.toPx()
                    val c = p + Offset(cos(out).toFloat(), sin(out).toFloat()) * (markEnd + r)
                    drawCircle(color, radius = r, center = c, alpha = alpha)
                    // Dark figures on a light line (the yellow one), white on the rest.
                    drawText(
                        badge,
                        color = if (color.luminance() > 0.55f) Color.Black else Color.White,
                        topLeft = Offset(c.x - badge.size.width / 2f, c.y - badge.size.height / 2f),
                        alpha = alpha,
                    )
                    if (metroLabelFacesOutward(labelSides[st.id] ?: 0, out)) badgeRoom = 2 * r + 3.dp.toPx()
                }
                val written = st.id in labelled && st.id !in hiddenLabels
                if (!ghost && ((written && (showLabels || (fade && lit))) || st.id in marked || st.id == dragId || namingMode)) {
                    val label = labels[st.id] ?: return@forEach
                    // With the names tool every name shows; one not written on the picture, faintly.
                    val faint = namingMode && !written
                    val corner = metroLabelTopLeft(
                        p.x, p.y, markEnd + badgeRoom, labelSides[st.id] ?: 0,
                        label.size.width.toFloat(), label.size.height.toFloat(),
                    )
                    drawText(label, topLeft = Offset(corner.x, corner.y), alpha = if (faint) alpha * 0.35f else alpha)
                }
            }
            // The box being drawn (14.10), over everything.
            marquee?.let { (a, c) ->
                val topLeft = Offset(minOf(a.x, c.x), minOf(a.y, c.y))
                val size = Size(abs(a.x - c.x), abs(a.y - c.y))
                drawRect(markColor.copy(alpha = 0.12f), topLeft, size)
                drawRect(markColor, topLeft, size, style = Stroke(1.5f.dp.toPx()))
            }
        }
    }
}

/**
 * Where a dragged station lands before glue (08.10): with the scheme's
 * angles of 45°, on a direction square with its neighbours if one is
 * within [tolerance]; else, with guides, level with or under a station
 * standing still within [tolerance], each way on its own; else on the grid,
 * if it is on; else where dropped.
 */
private fun metroDropSnap(
    scheme: MetroScheme,
    layout: Map<Long, MetroPoint>,
    stationId: Long,
    moving: Set<Long>,
    raw: MetroPoint,
    grid: Float?,
    tolerance: Float,
    unseen: Set<Long> = emptySet(),
): MetroPoint {
    if (scheme.snapAngles) {
        val squared = metroOctilinearSnap(raw, metroAngleAnchors(scheme, layout, stationId, moving), tolerance)
        if (squared !== raw) return squared
    }
    val gridded = grid?.let { metroSnapped(raw, it) } ?: raw
    if (scheme.alignGuides) {
        // Level with, or under, a station standing still (08.10); the other way, the grid.
        val g = metroGuided(raw, layout.filterKeys { it !in moving && it !in unseen }.values, tolerance)
        return MetroPoint(g.x ?: gridded.x, g.y ?: gridded.y)
    }
    return gridded
}

/** How strongly a hidden line shows when its ghosts are shown (14.10). */
private const val GHOST_ALPHA = 0.15f

/**
 * Where a dragged bend point lands (14.10): with the scheme's angles of 45°,
 * square with the points either side of it along its track if near enough;
 * else on the grid, if it is on; else where dropped.
 */
private fun metroBendLanding(
    scheme: MetroScheme,
    layout: Map<Long, MetroPoint>,
    hit: MetroBendHit,
    inserting: Boolean,
    raw: MetroPoint,
    grid: Float?,
    tolerance: Float,
): MetroPoint {
    if (scheme.snapAngles) {
        val squared = metroOctilinearSnap(raw, metroBendNeighbours(scheme, layout, hit, inserting), tolerance)
        if (squared !== raw) return squared
    }
    return grid?.let { metroSnapped(raw, it) } ?: raw
}
