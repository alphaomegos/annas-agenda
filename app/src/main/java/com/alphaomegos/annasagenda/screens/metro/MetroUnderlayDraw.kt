package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.alphaomegos.annasagenda.app.MetroTileLoader
import com.alphaomegos.annasagenda.model.MetroTileSource
import com.alphaomegos.annasagenda.model.MetroUnderlay
import com.alphaomegos.annasagenda.support.METRO_MERCATOR_WORLD
import com.alphaomegos.annasagenda.support.METRO_TILE_SIZE
import com.alphaomegos.annasagenda.support.METRO_UNDERLAY_STAND_IN_STEPS
import com.alphaomegos.annasagenda.support.MetroTile
import com.alphaomegos.annasagenda.support.MetroViewport
import com.alphaomegos.annasagenda.support.metroMercatorX
import com.alphaomegos.annasagenda.support.metroMercatorY
import com.alphaomegos.annasagenda.support.metroTileScreenCorner
import com.alphaomegos.annasagenda.support.metroTileStandIn
import com.alphaomegos.annasagenda.support.metroTileZoom
import com.alphaomegos.annasagenda.support.metroTilesForView
import com.alphaomegos.annasagenda.support.metroUnderlayMercator
import com.alphaomegos.annasagenda.support.metroUnderlayStretch

/** The city map to lay under the picture (16.10): where it lies, whose tiles, how faint, and who fetches them. */
internal data class MetroUnderlayLayer(
    val underlay: MetroUnderlay,
    val source: MetroTileSource,
    val opacity: Float,
    val tiles: MetroTileLoader,
)

/** What is drawn in a tile's place: an [image], or the part of it from [from] of [size]. */
private class MetroTilePicture(val image: ImageBitmap, val from: IntOffset, val size: IntSize)

/**
 * Tile [t]'s picture; while it is still coming, the part of a coarser one
 * kept in memory that covers its place (17.10) — a little blurred for a
 * moment rather than white; null when there is neither.
 */
private fun MetroUnderlayLayer.pictureOf(t: MetroTile): MetroTilePicture? {
    tiles.tile(source, t)?.let { return MetroTilePicture(it, IntOffset.Zero, IntSize(it.width, it.height)) }
    for (up in 1..minOf(METRO_UNDERLAY_STAND_IN_STEPS, t.z)) {
        val part = metroTileStandIn(t, up)
        val image = tiles.kept(source, part.tile) ?: continue
        val w = image.width / part.across
        val h = image.height / part.across
        if (w < 1 || h < 1) return null
        return MetroTilePicture(image, IntOffset(part.col * w, part.row * h), IntSize(w, h))
    }
    return null
}

/**
 * The map's tiles drawn under the picture seen through [view] in a [w] × [h]
 * view (16.10): those the screen shows, at the zoom whose pixels come
 * nearest the screen's, each turned and scaled with the picture. A tile not
 * here yet is sent for, and a coarser one kept in memory stands in for it
 * until it comes (17.10).
 */
internal fun DrawScope.drawMetroUnderlay(layer: MetroUnderlayLayer, view: MetroViewport, w: Float, h: Float) {
    layer.tiles.newView()
    val u = layer.underlay
    val screenPerMercator = view.k / metroUnderlayStretch(u)
    if (!screenPerMercator.isFinite() || screenPerMercator <= 0.0) return
    // The screen's corners on the Mercator plane: which tiles it shows.
    val corners = listOf(0f to 0f, w to 0f, 0f to h, w to h).map { (sx, sy) ->
        metroUnderlayMercator(u, view.toMapX(sx, w), view.toMapY(sy, h))
    }
    // The sharpest zoom whose tiles keep within the limit — coarser ones rather than none (17.10).
    val (z, tiles) = metroTilesForView(
        metroTileZoom(screenPerMercator, layer.source),
        corners.minOf { it.first }, corners.minOf { it.second },
        corners.maxOf { it.first }, corners.maxOf { it.second },
    )
    if (tiles.isEmpty()) return
    // Tile pixels to a Mercator metre at this zoom, and the pinned place in them.
    val tilePx = METRO_TILE_SIZE * (1 shl z).toDouble() / METRO_MERCATOR_WORLD
    val pinX = metroMercatorX(u.lon) * tilePx
    val pinY = metroMercatorY(u.lat) * tilePx
    val zoomBy = screenPerMercator / tilePx
    val pinOnScreenX = view.toScreenX(u.x, w).toDouble()
    val pinOnScreenY = view.toScreenY(u.y, h).toDouble()
    val side = IntSize(METRO_TILE_SIZE, METRO_TILE_SIZE)
    // Each tile from its own corner on the screen, turned and scaled about it (17.10).
    tiles.forEach { t ->
        val picture = layer.pictureOf(t) ?: return@forEach
        val (sx, sy) = metroTileScreenCorner(
            pinOnScreenX, pinOnScreenY,
            t.at.toDouble() * METRO_TILE_SIZE - pinX,
            t.y.toDouble() * METRO_TILE_SIZE - pinY,
            zoomBy,
            -u.rotationDeg.toDouble(),
        )
        // Whatever its pixels — a sharper tile's more, a stand-in's part fewer — it fills a tile's place.
        translate(sx.toFloat(), sy.toFloat()) {
            rotate(-u.rotationDeg, pivot = Offset.Zero) {
                scale(zoomBy.toFloat(), zoomBy.toFloat(), pivot = Offset.Zero) {
                    drawImage(
                        picture.image,
                        srcOffset = picture.from,
                        srcSize = picture.size,
                        dstSize = side,
                        alpha = layer.opacity,
                    )
                }
            }
        }
    }
}
