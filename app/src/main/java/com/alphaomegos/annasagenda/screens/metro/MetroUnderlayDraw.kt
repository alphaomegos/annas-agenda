package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.alphaomegos.annasagenda.app.MetroTileLoader
import com.alphaomegos.annasagenda.model.MetroTileSource
import com.alphaomegos.annasagenda.model.MetroUnderlay
import com.alphaomegos.annasagenda.support.METRO_MERCATOR_WORLD
import com.alphaomegos.annasagenda.support.METRO_TILE_SIZE
import com.alphaomegos.annasagenda.support.MetroViewport
import com.alphaomegos.annasagenda.support.metroMercatorX
import com.alphaomegos.annasagenda.support.metroMercatorY
import com.alphaomegos.annasagenda.support.metroTileZoom
import com.alphaomegos.annasagenda.support.metroTilesCovering
import com.alphaomegos.annasagenda.support.metroUnderlayMercator
import com.alphaomegos.annasagenda.support.metroUnderlayStretch

/** The city map to lay under the picture (16.10): where it lies, whose tiles, how faint, and who fetches them. */
internal data class MetroUnderlayLayer(
    val underlay: MetroUnderlay,
    val source: MetroTileSource,
    val opacity: Float,
    val tiles: MetroTileLoader,
)

/**
 * The map's tiles drawn under the picture seen through [view] in a [w] × [h]
 * view (16.10): those the screen shows, at the zoom whose pixels come
 * nearest the screen's, each turned and scaled with the picture. A tile not
 * here yet is sent for and left out until it comes.
 */
internal fun DrawScope.drawMetroUnderlay(layer: MetroUnderlayLayer, view: MetroViewport, w: Float, h: Float) {
    val u = layer.underlay
    val screenPerMercator = view.k / metroUnderlayStretch(u)
    if (!screenPerMercator.isFinite() || screenPerMercator <= 0.0) return
    val z = metroTileZoom(screenPerMercator, layer.source)
    // The screen's corners on the Mercator plane: which tiles it shows.
    val corners = listOf(0f to 0f, w to 0f, 0f to h, w to h).map { (sx, sy) ->
        metroUnderlayMercator(u, view.toMapX(sx, w), view.toMapY(sy, h))
    }
    val tiles = metroTilesCovering(
        z,
        corners.minOf { it.first }, corners.minOf { it.second },
        corners.maxOf { it.first }, corners.maxOf { it.second },
    )
    if (tiles.isEmpty()) return
    // Tile pixels to a Mercator metre at this zoom, and the pinned place in them.
    val tilePx = METRO_TILE_SIZE * (1 shl z).toDouble() / METRO_MERCATOR_WORLD
    val pinX = metroMercatorX(u.lon) * tilePx
    val pinY = metroMercatorY(u.lat) * tilePx
    val zoomBy = (screenPerMercator / tilePx).toFloat()
    // The pinned place where its picture point is on the screen; the map turned and
    // scaled about it — drawn in tile pixels counted from the pin.
    translate(view.toScreenX(u.x, w), view.toScreenY(u.y, h)) {
        rotate(-u.rotationDeg, pivot = Offset.Zero) {
            scale(zoomBy, zoomBy, pivot = Offset.Zero) {
                tiles.forEach { t ->
                    val image = layer.tiles.tile(layer.source, t) ?: return@forEach
                    val left = (t.at.toDouble() * METRO_TILE_SIZE - pinX).toFloat()
                    val top = (t.y.toDouble() * METRO_TILE_SIZE - pinY).toFloat()
                    // A tile of more pixels than the usual (a sharper one) is drawn the usual size.
                    val shrink = METRO_TILE_SIZE.toFloat() / image.width
                    if (shrink == 1f) {
                        drawImage(image, topLeft = Offset(left, top), alpha = layer.opacity)
                    } else {
                        scale(shrink, shrink, pivot = Offset(left, top)) {
                            drawImage(image, topLeft = Offset(left, top), alpha = layer.opacity)
                        }
                    }
                }
            }
        }
    }
}
