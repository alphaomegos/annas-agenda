package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.support.TravelWorldMap

/** The green of a visited country, on the map and in the lists. */
internal val TravelVisitedGreen = Color(0xFF43A047)

/** A country of the user's own on the map: where, and whether it was visited. */
internal data class TravelUserDot(val countryId: String, val x: Float, val y: Float, val visited: Boolean)

/**
 * The world map (05.10): every country of the shipped outline file, the
 * visited ones green; tiny ones and the user's own as dots, the user's own
 * glowing.
 *
 * Interactive, it zooms and pans with two fingers and says what was tapped —
 * a country, or the sea — with the place on the map's grid, so a country of
 * the user's own can be put there. With [focus] (left, top, right, bottom on
 * the grid) it shows just that part, still: a country's card.
 *
 * Zoom and pan survive turning the phone.
 */
@Composable
internal fun TravelMapView(
    map: TravelWorldMap,
    visited: Set<String>,
    userDots: List<TravelUserDot>,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    focus: FloatArray? = null,
    onTap: (countryId: String?, gridX: Float, gridY: Float) -> Unit = { _, _, _ -> },
) {
    val paths = remember(map) {
        map.shapes.mapValues { (_, shape) ->
            if (shape.rings.isEmpty()) null
            else Path().apply {
                fillType = PathFillType.EvenOdd
                shape.rings.forEach { ring ->
                    moveTo(ring[0].toFloat(), ring[1].toFloat())
                    for (k in 2 until ring.size step 2) lineTo(ring[k].toFloat(), ring[k + 1].toFloat())
                    close()
                }
            }
        }
    }
    val dots = remember(map) { map.shapes.mapNotNull { (code, s) -> s.dot?.let { code to it } } }

    val sea = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val land = MaterialTheme.colorScheme.surfaceVariant
    val border = MaterialTheme.colorScheme.outline
    val highlightColor = MaterialTheme.colorScheme.primary

    var zoom by rememberSaveable { mutableStateOf(1f) }
    var panX by rememberSaveable { mutableStateOf(0f) }
    var panY by rememberSaveable { mutableStateOf(0f) }
    val currentOnTap by rememberUpdatedState(onTap)
    val density = LocalDensity.current
    val dotPx = with(density) { 3.dp.toPx() }
    val glowPx = with(density) { 11.dp.toPx() }
    val hitPx = with(density) { 14.dp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (w <= 0f || h <= 0f || map.width <= 1) return@BoxWithConstraints

        // Scale and offset from the grid to the screen.
        val (k, offX, offY) = if (focus != null) {
            val fw = (focus[2] - focus[0]).coerceAtLeast(1f)
            val fh = (focus[3] - focus[1]).coerceAtLeast(1f)
            val s = minOf(w / fw, h / fh)
            Triple(s, w / 2 - (focus[0] + fw / 2) * s, h / 2 - (focus[1] + fh / 2) * s)
        } else {
            val s = w / map.width * zoom
            val mapH = map.height * s
            val y = if (mapH <= h) (h - mapH) / 2 else panY.coerceIn(h - mapH, 0f)
            Triple(s, panX.coerceIn(w - map.width * s, 0f), y)
        }

        var gestures: Modifier = Modifier.pointerInput(map, k, offX, offY, userDots) {
            detectTapGestures { pos ->
                val gx = (pos.x - offX) / k
                val gy = (pos.y - offY) / k
                val own = userDots.minByOrNull { (it.x - gx) * (it.x - gx) + (it.y - gy) * (it.y - gy) }
                    ?.takeIf { val dx = (it.x - gx) * k; val dy = (it.y - gy) * k; dx * dx + dy * dy <= hitPx * hitPx }
                currentOnTap(own?.countryId ?: map.countryAt(gx, gy, hitPx / k), gx, gy)
            }
        }
        if (focus == null) {
            gestures = gestures.pointerInput(map, w, h) {
                detectTransformGestures { centroid, pan, change, _ ->
                    val base = w / map.width
                    val oldScale = base * zoom
                    val newZoom = (zoom * change).coerceIn(1f, 16f)
                    val newScale = base * newZoom
                    // The point under the fingers stays under them.
                    val curX = panX.coerceIn(w - map.width * oldScale, 0f)
                    val curY = panY.coerceIn(minOf(0f, h - map.height * oldScale), 0f)
                    panX = (centroid.x - (centroid.x - curX) * newScale / oldScale + pan.x)
                        .coerceIn(w - map.width * newScale, 0f)
                    panY = (centroid.y - (centroid.y - curY) * newScale / oldScale + pan.y)
                        .coerceIn(minOf(0f, h - map.height * newScale), 0f)
                    zoom = newZoom
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestures)) {
            drawRect(sea)
            translate(offX, offY) {
                scale(k, k, pivot = Offset.Zero) {
                    paths.forEach { (code, path) ->
                        if (path == null) return@forEach
                        val fill = when {
                            code in visited -> TravelVisitedGreen
                            code == highlight -> highlightColor.copy(alpha = 0.55f)
                            else -> land
                        }
                        drawPath(path, fill)
                        drawPath(path, border, style = Stroke(width = 0.8f / k))
                        if (code == highlight) drawPath(path, highlightColor, style = Stroke(width = 2.5f / k))
                    }
                }
            }
            dots.forEach { (code, at) ->
                val c = Offset(at.first * k + offX, at.second * k + offY)
                drawCircle(if (code in visited) TravelVisitedGreen else border, radius = dotPx, center = c)
                if (code == highlight) drawCircle(highlightColor, radius = dotPx * 2.5f, center = c, style = Stroke(2f))
            }
            userDots.forEach { d ->
                val c = Offset(d.x * k + offX, d.y * k + offY)
                val core = if (d.visited) TravelVisitedGreen else highlightColor
                drawCircle(
                    Brush.radialGradient(listOf(core.copy(alpha = 0.6f), Color.Transparent), center = c, radius = glowPx),
                    radius = glowPx,
                    center = c,
                )
                drawCircle(core, radius = dotPx * 1.4f, center = c)
            }
        }
    }
}
