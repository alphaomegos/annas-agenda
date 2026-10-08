package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
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
import com.alphaomegos.annasagenda.support.metroLayoutBounds
import com.alphaomegos.annasagenda.support.metroSidesOf
import com.alphaomegos.annasagenda.support.metroSnapped
import com.alphaomegos.annasagenda.support.metroStationAt
import com.alphaomegos.annasagenda.support.metroStationsMovingWith
import com.alphaomegos.annasagenda.support.METRO_CASING
import com.alphaomegos.annasagenda.support.METRO_CORNER_RADIUS
import com.alphaomegos.annasagenda.support.metroRoundedCorners
import com.alphaomegos.annasagenda.support.METRO_LABEL_HIDDEN
import com.alphaomegos.annasagenda.support.METRO_LABEL_SIDES
import com.alphaomegos.annasagenda.support.metroAutoLabelSide
import com.alphaomegos.annasagenda.support.metroLabelTopLeft
import com.alphaomegos.annasagenda.support.METRO_STATION_RADIUS
import com.alphaomegos.annasagenda.support.MetroBendHit
import com.alphaomegos.annasagenda.support.MetroTrack
import com.alphaomegos.annasagenda.support.metroAloneDropPoint
import com.alphaomegos.annasagenda.support.metroBendPointAt
import com.alphaomegos.annasagenda.support.metroBendPoints
import com.alphaomegos.annasagenda.support.metroLabelledStations
import com.alphaomegos.annasagenda.support.metroTrackAt
import com.alphaomegos.annasagenda.support.metroAngleAnchors
import com.alphaomegos.annasagenda.support.metroOctilinearSnap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin
import com.alphaomegos.annasagenda.support.metroInterchangeStations
import com.alphaomegos.annasagenda.support.metroTermini
import com.alphaomegos.annasagenda.support.metroTickAngle
import com.alphaomegos.annasagenda.support.metroTerminusOutward
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
 * picture is recorded into that layer too, to be saved as an image. With
 * [focusLine] every other line fades.
 */
internal sealed interface MetroBendAction {
    data class Cycle(val track: MetroTrack) : MetroBendAction
    data class Insert(val hit: MetroBendHit, val x: Float, val y: Float) : MetroBendAction
    data class Move(val hit: MetroBendHit, val x: Float, val y: Float) : MetroBendAction
    data class Remove(val hit: MetroBendHit) : MetroBendAction
}

/** See [MetroMapView]. */
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
    focusLine: Long? = null,
) {
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var panX by rememberSaveable { mutableStateOf(0f) }
    var panY by rememberSaveable { mutableStateOf(0f) }
    // "Fit to the screen" (08.10): each new [fitKey] brings the whole picture
    // back into view. The one last acted on is kept, so turning the phone
    // does not undo the zoom.
    var fitDone by rememberSaveable { mutableStateOf(fitKey) }
    LaunchedEffect(fitKey) {
        if (fitKey != fitDone) {
            zoom = 1f; panX = 0f; panY = 0f
            fitDone = fitKey
        }
    }
    // A drag in flight: the station and where on the screen it is now.
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    // A bend point being dragged: which, and whether it is a new one put in.
    var dragBend by remember { mutableStateOf<Pair<MetroBendHit, Boolean>?>(null) }

    val currentTap by rememberUpdatedState(onTapStation)
    val currentMove by rememberUpdatedState(onMoveStation)
    val currentGrid by rememberUpdatedState(gridStep)
    val currentScheme by rememberUpdatedState(scheme)
    val currentBendMode by rememberUpdatedState(bendMode)
    val currentBend by rememberUpdatedState(onBend)
    val currentMoveAlone by rememberUpdatedState(moveAlone)
    val currentGroup by rememberUpdatedState(group)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 11.sp * scheme.labelScale, color = labelColor)
    val ringFill = MaterialTheme.colorScheme.surface
    val markColor = MaterialTheme.colorScheme.primary
    val transferColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labels = remember(scheme.stations, measurer, labelColor, scheme.labelScale) {
        scheme.stations.associate { it.id to measurer.measure(it.name, labelStyle) }
    }
    val lines = remember(scheme.lines) { scheme.lines.associateBy { it.id } }
    val lineOf = remember(scheme.stations) { scheme.stations.associate { it.id to it.lineId } }
    val labelled = remember(scheme, layout) { metroLabelledStations(scheme, layout) }
    // Each name's side (08.10): set by hand, or chosen away from its track; hidden ones apart.
    val labelSides = remember(scheme, layout) {
        scheme.stations.associate { st ->
            st.id to (st.labelSide?.takeIf { it in 0 until METRO_LABEL_SIDES } ?: metroAutoLabelSide(scheme, layout, st.id))
        }
    }
    // Ticks (08.10): which stations stay rings, which are ends, and which way each tick points.
    val interchanges = remember(scheme.transfers) { metroInterchangeStations(scheme) }
    val termini = remember(scheme.lines) { metroTermini(scheme) }
    val tickAngles = remember(scheme, layout, labelSides) {
        if (!scheme.tickStations) emptyMap()
        else scheme.stations.mapNotNull { st -> metroTickAngle(scheme, layout, st.id, labelSides[st.id] ?: 0)?.let { st.id to it } }.toMap()
    }
    // Line numbers past the ends of lines (08.10): which way out at each end, and the numbers measured.
    val outwards = remember(scheme, layout) {
        if (!scheme.lineBadges) emptyMap()
        else termini.mapNotNull { id -> metroTerminusOutward(scheme, layout, id)?.let { id to it } }.toMap()
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
        val b = metroLayoutBounds(layout.values)
        val bw = (b[2] - b[0]) + 2 * METRO_LAYOUT_STEP
        val bh = (b[3] - b[1]) + 2 * METRO_LAYOUT_STEP
        val fit = minOf(w / bw, h / bh)
        val k = fit * zoom
        val cx = (b[0] + b[2]) / 2
        val cy = (b[1] + b[3]) / 2
        fun toScreen(p: MetroPoint) = Offset((p.x - cx) * k + w / 2 + panX, (p.y - cy) * k + h / 2 + panY)
        val hitPx = with(density) { 22.dp.toPx() }
        // How near a direction of 45° a dropped station is pulled onto it (08.10).
        val angleTolPx = with(density) { 18.dp.toPx() }

        // Keyed on what does not change while a finger is down: zooming must
        // not restart the very gesture that zooms. The scale is read afresh.
        val gestures = Modifier.pointerInput(layout, fit, w, h, onMoveStation != null) {
            fun toMap(o: Offset): MetroPoint {
                val kk = fit * zoom
                return MetroPoint((o.x - w / 2 - panX) / kk + cx, (o.y - h / 2 - panY) / kk + cy)
            }
            fun toScreenNow(p: MetroPoint): Offset {
                val kk = fit * zoom
                return Offset((p.x - cx) * kk + w / 2 + panX, (p.y - cy) * kk + h / 2 + panY)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = toMap(down.position)
                val hitRadius = hitPx / (fit * zoom)
                val bending = currentBendMode
                val bendPoint = if (bending) metroBendPointAt(currentScheme, start.x, start.y, hitRadius) else null
                val onTrack = if (bending && bendPoint == null) metroTrackAt(currentScheme, layout, start.x, start.y, hitRadius * 0.7f) else null
                val grabbed = if (currentMove != null && !bending) metroStationAt(layout, start.x, start.y, hitRadius) else null
                var travelled = 0f
                var fingers = 1
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
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    fingers = maxOf(fingers, pressed.size)
                    if ((grabbed != null || grabbedBend != null) && pressed.size == 1) {
                        val change = pressed[0]
                        travelled += change.positionChange().getDistance()
                        dragAt = change.position + grabOffset
                        change.consume()
                    } else {
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        travelled += pan.getDistance()
                        if (zoomChange != 1f) {
                            val c = event.calculateCentroid()
                            val newZoom = (zoom * zoomChange).coerceIn(0.5f, 12f)
                            // The point under the fingers stays under them.
                            val f = newZoom / zoom
                            panX = (c.x - w / 2) - ((c.x - w / 2) - panX) * f
                            panY = (c.y - h / 2) - ((c.y - h / 2) - panY) * f
                            zoom = newZoom
                        }
                        panX += pan.x
                        panY += pan.y
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })

                val slop = viewConfiguration.touchSlop
                if (grabbedBend != null) {
                    val (hit, insert) = grabbedBend
                    if (travelled > slop) {
                        val at = toMap(dragAt).let { p -> currentGrid?.let { metroSnapped(p, it) } ?: p }
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
                    }
                    val at = metroDropSnap(s, layout, grabbed, moving, toMap(dragAt), currentGrid, angleTolPx / (fit * zoom))
                    currentMove?.invoke(grabbed, at.x, at.y)
                } else if (travelled <= slop && fingers == 1) {
                    metroStationAt(layout, start.x, start.y, hitRadius)?.let { currentTap(it) }
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
        Canvas(modifier = Modifier.fillMaxSize().then(recorded).then(gestures)) {
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
                }
            } ?: emptySet()
            val inGroup = dragId != null && dragId in group
            fun dragPoint(): MetroPoint {
                val raw = MetroPoint((dragAt.x - w / 2 - panX) / k + cx, (dragAt.y - h / 2 - panY) / k + cy)
                return gridStep?.let { metroSnapped(raw, it) } ?: raw
            }
            val shift = dragged?.let { from ->
                val raw = MetroPoint((dragAt.x - w / 2 - panX) / k + cx, (dragAt.y - h / 2 - panY) / k + cy)
                val snapped = metroDropSnap(scheme, layout, dragId!!, moving, raw, gridStep, angleTolPx / k)
                val to = if (moveAlone && !inGroup) metroAloneDropPoint(scheme, layout, dragId!!, snapped) else snapped
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
                val left = (-w / 2 - panX) / k + cx
                val right = (w / 2 - panX) / k + cx
                val top = (-h / 2 - panY) / k + cy
                val bottom = (h / 2 - panY) / k + cy
                var gx = floor(left / step) * step
                while (gx <= right) {
                    val sx = (gx - cx) * k + w / 2 + panX
                    drawLine(gridColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = thin)
                    gx += step
                }
                var gy = floor(top / step) * step
                while (gy <= bottom) {
                    val sy = (gy - cy) * k + h / 2 + panY
                    drawLine(gridColor, Offset(0f, sy), Offset(w, sy), strokeWidth = thin)
                    gy += step
                }
            }

            val capsules = scheme.showTransferLinks && scheme.transferCapsules
            fun drawLinks() = scheme.transfers.forEach { t ->
                val a = at(t.aStationId) ?: return@forEach
                val c = at(t.bStationId) ?: return@forEach
                val lit = (!fade || (minOf(t.aStationId, t.bStationId) to maxOf(t.aStationId, t.bStationId)) in routePairs) &&
                    (focusLine == null || lineOf[t.aStationId] == focusLine || lineOf[t.bStationId] == focusLine)
                val alpha = if (lit) 1f else 0.25f
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
                val color = Color(lines[lineId]?.color ?: 0xFF888888)
                val drawn = ofLine.mapNotNull { (_, s, t) ->
                    val pts = trackPoints(s, t) ?: return@mapNotNull null
                    val lit = (!fade || (s to t) in routePairs) && (focusLine == null || lineId == focusLine)
                    Triple(pts, lit, if (fade && lit) lineWidth * 1.5f else lineWidth)
                }
                if (scheme.lineCasing) drawn.forEach { (pts, lit, width) ->
                    drawTrack(pts, ringFill.copy(alpha = if (lit) 1f else 0.25f), width * (1f + 2 * METRO_CASING))
                }
                drawn.forEach { (pts, lit, width) -> drawTrack(pts, color.copy(alpha = if (lit) 1f else 0.25f), width) }
                if (bendMode) drawn.forEach { (pts, _, _) ->
                    pts.subList(1, pts.size - 1).forEach { drawCircle(markColor, radius = 4.dp.toPx(), center = it) }
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
                val color = Color(lines[lineOf[st.id]]?.color ?: 0xFF888888)
                // One line in focus (08.10): the rest fade, as off a route.
                val lit = (!fade || st.id in routeStations) && (focusLine == null || lineOf[st.id] == focusLine)
                val alpha = if (lit) 1f else 0.3f
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
                if (out != null && badge != null) {
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
                if ((written && (showLabels || (fade && lit))) || st.id in marked || st.id == dragId || namingMode) {
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
        }
    }
}

/**
 * Where a dragged station lands before glue (08.10): with the scheme's
 * angles of 45°, on a direction square with its neighbours if one is
 * within [tolerance]; else on the grid, if it is on; else where dropped.
 */
private fun metroDropSnap(
    scheme: MetroScheme,
    layout: Map<Long, MetroPoint>,
    stationId: Long,
    moving: Set<Long>,
    raw: MetroPoint,
    grid: Float?,
    tolerance: Float,
): MetroPoint {
    if (scheme.snapAngles) {
        val squared = metroOctilinearSnap(raw, metroAngleAnchors(scheme, layout, stationId, moving), tolerance)
        if (squared !== raw) return squared
    }
    return grid?.let { metroSnapped(raw, it) } ?: raw
}
