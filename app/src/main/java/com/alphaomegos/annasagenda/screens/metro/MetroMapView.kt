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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.alphaomegos.annasagenda.support.METRO_STATION_RADIUS
import com.alphaomegos.annasagenda.support.MetroBendHit
import com.alphaomegos.annasagenda.support.MetroTrack
import com.alphaomegos.annasagenda.support.metroAloneDropPoint
import com.alphaomegos.annasagenda.support.metroBendPointAt
import com.alphaomegos.annasagenda.support.metroBendPoints
import com.alphaomegos.annasagenda.support.metroLabelledStations
import com.alphaomegos.annasagenda.support.metroTrackAt
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.floor

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
 * only the names [metroLabelledStations] picks are written.
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
) {
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var panX by rememberSaveable { mutableStateOf(0f) }
    var panY by rememberSaveable { mutableStateOf(0f) }
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
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val ringFill = MaterialTheme.colorScheme.surface
    val markColor = MaterialTheme.colorScheme.primary
    val transferColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labels = remember(scheme.stations, measurer, labelColor) {
        scheme.stations.associate { it.id to measurer.measure(it.name, labelStyle) }
    }
    val lines = remember(scheme.lines) { scheme.lines.associateBy { it.id } }
    val lineOf = remember(scheme.stations) { scheme.stations.associate { it.id to it.lineId } }
    val labelled = remember(scheme, layout) { metroLabelledStations(scheme, layout) }
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
                    val at = toMap(dragAt).let { p -> currentGrid?.let { metroSnapped(p, it) } ?: p }
                    currentMove?.invoke(grabbed, at.x, at.y)
                } else if (travelled <= slop && fingers == 1) {
                    metroStationAt(layout, start.x, start.y, hitRadius)?.let { currentTap(it) }
                }
                dragId = null
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestures)) {
            // Glued rings touch in the picture's units, so then they scale with it, unclamped.
            val glued = scheme.glue > 0
            val lineWidth = if (glued) (0.16f * k).coerceAtMost(10.dp.toPx()) else (0.16f * k).coerceIn(3.dp.toPx(), 10.dp.toPx())
            val radius = if (glued) METRO_STATION_RADIUS * k else (METRO_STATION_RADIUS * k).coerceIn(4.dp.toPx(), 9.dp.toPx())
            val (routePairs, routeStations) = onRoute
            val fade = route != null
            // A drag in flight moves its station (snapped, with a grid) and,
            // with transfers kept together, the rest of its transfer as far.
            val dragged = dragId?.let { layout[it] }
            val moving = dragId?.takeIf { dragged != null }?.let { if (moveAlone) setOf(it) else metroStationsMovingWith(scheme, it) } ?: emptySet()
            fun dragPoint(): MetroPoint {
                val raw = MetroPoint((dragAt.x - w / 2 - panX) / k + cx, (dragAt.y - h / 2 - panY) / k + cy)
                return gridStep?.let { metroSnapped(raw, it) } ?: raw
            }
            val shift = dragged?.let { from ->
                val snapped = dragPoint()
                val to = if (moveAlone) metroAloneDropPoint(scheme, layout, dragId!!, snapped) else snapped
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

            scheme.transfers.forEach { t ->
                val a = at(t.aStationId) ?: return@forEach
                val c = at(t.bStationId) ?: return@forEach
                val lit = !fade || (minOf(t.aStationId, t.bStationId) to maxOf(t.aStationId, t.bStationId)) in routePairs
                drawLine(transferColor.copy(alpha = if (lit) 1f else 0.25f), a, c, strokeWidth = lineWidth * 0.8f, cap = StrokeCap.Round)
            }
            val bendDrag = dragBend
            edges.forEach { (lineId, s, t) ->
                val a = at(s) ?: return@forEach
                val c = at(t) ?: return@forEach
                val color = Color(lines[lineId]?.color ?: 0xFF888888)
                val lit = !fade || (s to t) in routePairs
                val track = MetroTrack(s, t)
                val bends = metroBendPoints(scheme, track).toMutableList()
                if (bendDrag != null && bendDrag.first.track == track) {
                    val p = dragPoint()
                    val i = bendDrag.first.index
                    if (bendDrag.second) bends.add(i.coerceIn(0, bends.size), p) else if (i in bends.indices) bends[i] = p
                }
                val width = if (fade && lit) lineWidth * 1.5f else lineWidth
                val paint = color.copy(alpha = if (lit) 1f else 0.25f)
                if (bends.isEmpty()) {
                    drawLine(paint, a, c, strokeWidth = width, cap = StrokeCap.Round)
                } else {
                    // Track drawn through its bends (08.10).
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        bends.forEach { val q = toScreen(it); lineTo(q.x, q.y) }
                        lineTo(c.x, c.y)
                    }
                    drawPath(path, paint, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (bendMode) bends.forEach { drawCircle(markColor, radius = 4.dp.toPx(), center = toScreen(it)) }
                }
            }
            val showLabels = k * METRO_LAYOUT_STEP > 52.dp.toPx()
            scheme.stations.forEach { st ->
                val p = at(st.id) ?: return@forEach
                val color = Color(lines[lineOf[st.id]]?.color ?: 0xFF888888)
                val lit = !fade || st.id in routeStations
                val alpha = if (lit) 1f else 0.3f
                drawCircle(if (st.closure != null) transferColor else ringFill, radius = radius, center = p, alpha = alpha)
                drawCircle(color, radius = radius, center = p, style = Stroke(radius * 0.45f), alpha = alpha)
                if (st.id in marked) drawCircle(markColor, radius = radius * 1.8f, center = p, style = Stroke(2.5f.dp.toPx()))
                if ((st.id in labelled && (showLabels || (fade && lit))) || st.id in marked || st.id == dragId) {
                    val label = labels[st.id] ?: return@forEach
                    drawText(
                        label,
                        topLeft = Offset(p.x + radius + 3.dp.toPx(), p.y - label.size.height / 2f),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}
