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
 */
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
) {
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var panX by rememberSaveable { mutableStateOf(0f) }
    var panY by rememberSaveable { mutableStateOf(0f) }
    // A drag in flight: the station and where on the screen it is now.
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }

    val currentTap by rememberUpdatedState(onTapStation)
    val currentMove by rememberUpdatedState(onMoveStation)
    val currentGrid by rememberUpdatedState(gridStep)
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

    BoxWithConstraints(modifier = modifier) {
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
                val grabbed = if (currentMove != null) metroStationAt(layout, start.x, start.y, hitRadius) else null
                var travelled = 0f
                var fingers = 1
                // The station keeps where it sits under the finger, rather than jumping to it.
                val grabOffset = grabbed?.let { toScreenNow(layout.getValue(it)) - down.position } ?: Offset.Zero
                if (grabbed != null) {
                    dragId = grabbed
                    dragAt = down.position + grabOffset
                }
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    fingers = maxOf(fingers, pressed.size)
                    if (grabbed != null && pressed.size == 1) {
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
                if (grabbed != null && travelled > slop) {
                    val at = toMap(dragAt).let { p -> currentGrid?.let { metroSnapped(p, it) } ?: p }
                    currentMove?.invoke(grabbed, at.x, at.y)
                } else if (travelled <= slop && fingers == 1) {
                    metroStationAt(layout, start.x, start.y, hitRadius)?.let { currentTap(it) }
                }
                dragId = null
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestures)) {
            val lineWidth = (0.16f * k).coerceIn(3.dp.toPx(), 10.dp.toPx())
            val radius = (0.13f * k).coerceIn(4.dp.toPx(), 9.dp.toPx())
            val (routePairs, routeStations) = onRoute
            val fade = route != null
            // A drag in flight moves its station (snapped, with a grid) and,
            // with transfers kept together, the rest of its transfer as far.
            val dragged = dragId?.let { layout[it] }
            val moving = dragId?.takeIf { dragged != null }?.let { metroStationsMovingWith(scheme, it) } ?: emptySet()
            val shift = dragged?.let { from ->
                val raw = MetroPoint((dragAt.x - w / 2 - panX) / k + cx, (dragAt.y - h / 2 - panY) / k + cy)
                val to = gridStep?.let { metroSnapped(raw, it) } ?: raw
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
            edges.forEach { (lineId, s, t) ->
                val a = at(s) ?: return@forEach
                val c = at(t) ?: return@forEach
                val color = Color(lines[lineId]?.color ?: 0xFF888888)
                val lit = !fade || (s to t) in routePairs
                drawLine(
                    color.copy(alpha = if (lit) 1f else 0.25f),
                    a, c,
                    strokeWidth = if (fade && lit) lineWidth * 1.5f else lineWidth,
                    cap = StrokeCap.Round,
                )
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
                if (showLabels || (fade && lit) || st.id in marked || st.id == dragId) {
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
