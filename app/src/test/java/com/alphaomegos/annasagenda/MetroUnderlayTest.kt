package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** A city map under the editor's picture (16.10). */
class MetroUnderlayTest {

    private val moscow = MetroUnderlay(lat = 55.7558, lon = 37.6176, x = 2f, y = 3f, metersPerUnit = 1500f, rotationDeg = 0f)

    @Test
    fun mercatorThereAndBack() {
        assertEquals(37.6176, metroLonOf(metroMercatorX(37.6176)), 1e-9)
        assertEquals(55.7558, metroLatOf(metroMercatorY(55.7558)), 1e-9)
        // The equator is half way down, Greenwich half way across.
        assertEquals(METRO_MERCATOR_WORLD / 2, metroMercatorY(0.0), 1e-6)
        assertEquals(METRO_MERCATOR_WORLD / 2, metroMercatorX(0.0), 1e-6)
    }

    /** The pinned point is the place; a step east is metersPerUnit of ground, as Mercator stretches it. */
    @Test
    fun thePinIsThePlaceAndAStepIsItsMetres() {
        val (mx, my) = metroUnderlayMercator(moscow, 2f, 3f)
        assertEquals(metroMercatorX(37.6176), mx, 1e-6)
        assertEquals(metroMercatorY(55.7558), my, 1e-6)

        val (ex, _) = metroUnderlayMercator(moscow, 3f, 3f)
        val ground = (ex - mx) * kotlin.math.cos(55.7558 * Math.PI / 180)
        assertEquals(1500.0, ground, 1e-6)
    }

    @Test
    fun pictureAndPlaneThereAndBackEvenTurned() {
        val turned = moscow.copy(rotationDeg = 30f)
        val (mx, my) = metroUnderlayMercator(turned, 7.5f, -1.25f)
        val (px, py) = metroUnderlayPicture(turned, mx, my)
        assertEquals(7.5f, px, 1e-3f)
        assertEquals(-1.25f, py, 1e-3f)
    }

    /** A drag carries the map: what lay under a point now lies that much further. */
    @Test
    fun aDragCarriesTheMap() {
        val moved = metroUnderlayMoved(moscow, dx = 1f, dy = -2f, zoom = 1f, turnDeg = 0f, cx = 0f, cy = 0f)
        val (mx, my) = metroUnderlayMercator(moscow, 5f, 5f)
        val (px, py) = metroUnderlayPicture(moved, mx, my)
        assertEquals(6f, px, 1e-3f)
        assertEquals(3f, py, 1e-3f)
    }

    /** A pinch and a twist keep what lay under the fingers' middle there. */
    @Test
    fun aPinchAndATwistKeepTheMiddle() {
        val moved = metroUnderlayMoved(moscow, dx = 0f, dy = 0f, zoom = 2f, turnDeg = 40f, cx = 4f, cy = 1f)
        val (mx, my) = metroUnderlayMercator(moscow, 4f, 1f)
        val (px, py) = metroUnderlayPicture(moved, mx, my)
        assertEquals(4f, px, 1e-3f)
        assertEquals(1f, py, 1e-3f)
        assertEquals(750f, moved.metersPerUnit, 1e-3f)
        assertEquals(-40f, moved.rotationDeg, 1e-3f)
        // A point two steps east of the middle now lies four steps away, turned 40° clockwise.
        val (ex, ey) = metroUnderlayMercator(moscow, 6f, 1f)
        val (qx, qy) = metroUnderlayPicture(moved, ex, ey)
        val t = 40 * Math.PI / 180
        assertEquals(4f + 4f * kotlin.math.cos(t).toFloat(), qx, 1e-3f)
        assertEquals(1f + 4f * kotlin.math.sin(t).toFloat(), qy, 1e-3f)
    }

    @Test
    fun theTileZoomMatchesTheScreenWithinTheSource() {
        // Eight screen pixels to a Mercator metre is past any tile: the source's deepest.
        assertEquals(19, metroTileZoom(8.0, MetroTileSource.OSM))
        assertEquals(20, metroTileZoom(8.0, MetroTileSource.CARTO_LIGHT))
        // A tile's pixel a screen's pixel at zoom 10.
        val atTen = METRO_TILE_SIZE * 1024.0 / METRO_MERCATOR_WORLD
        assertEquals(10, metroTileZoom(atTen, MetroTileSource.OSM))
        assertEquals(0, metroTileZoom(0.0, MetroTileSource.OSM))
    }

    @Test
    fun tilesCoverTheBoxMiddleFirstAndNotTooMany() {
        val tile = METRO_MERCATOR_WORLD / 4
        val tiles = metroTilesCovering(2, tile * 0.5, tile * 1.5, tile * 2.5, tile * 1.6)
        assertEquals(setOf(MetroTile(2, 0, 1), MetroTile(2, 1, 1), MetroTile(2, 2, 1)), tiles.toSet())
        assertEquals(MetroTile(2, 1, 1), tiles.first())
        assertTrue(metroTilesCovering(10, 0.0, 0.0, METRO_MERCATOR_WORLD, METRO_MERCATOR_WORLD).isEmpty())
    }

    @Test
    fun aViewNeedingTooManyTilesTakesCoarserOnesRatherThanNone() {
        // 17.10: 12 × 12 tiles at zoom 10 is past the limit — white, before; at 9 it is 6 × 6.
        val tile = METRO_MERCATOR_WORLD / 1024
        val box = doubleArrayOf(tile * 100.0, tile * 200.0, tile * 111.5, tile * 211.5)
        assertTrue(metroTilesCovering(10, box[0], box[1], box[2], box[3]).isEmpty())
        val (z, tiles) = metroTilesForView(10, box[0], box[1], box[2], box[3])
        assertEquals(9, z)
        assertEquals(36, tiles.size)
        assertTrue(tiles.all { it.z == 9 })
        // What fits is taken at the zoom asked.
        assertEquals(10, metroTilesForView(10, box[0], box[1], tile * 102.5, tile * 202.5).first)
        // Never more than so many zooms coarser, nor past the whole world.
        val world = metroTilesForView(18, 0.0, 0.0, METRO_MERCATOR_WORLD, METRO_MERCATOR_WORLD)
        assertEquals(18 - METRO_UNDERLAY_COARSER_STEPS, world.first)
        assertTrue(world.second.isEmpty())
        assertEquals(0 to listOf(MetroTile(0, 0, 0)), metroTilesForView(1, 0.0, 0.0, METRO_MERCATOR_WORLD - 1, METRO_MERCATOR_WORLD - 1, limit = 1))
    }

    @Test
    fun aTileCornerIsCountedFromThePinScaledAndTurned() {
        val (x, y) = metroTileScreenCorner(100.0, 200.0, 256.0, -512.0, 2.0, 0.0)
        assertEquals(612.0, x, 1e-9)
        assertEquals(-824.0, y, 1e-9)
        // Turned a quarter clockwise with y down: to the right becomes downwards.
        val (tx, ty) = metroTileScreenCorner(100.0, 200.0, 256.0, 0.0, 2.0, 90.0)
        assertEquals(100.0, tx, 1e-9)
        assertEquals(712.0, ty, 1e-9)
        // Deep in, far from the pin: still exact to the pixel.
        val (fx, _) = metroTileScreenCorner(0.0, 0.0, 3.0e7 + 0.25, 0.0, 1.0, 0.0)
        assertEquals(3.0e7 + 0.25, fx, 1e-6)
    }

    @Test
    fun aCoarserTileStandsInForOneStillComing() {
        // 17.10: tile 13, 22 at zoom 5 lies in 3, 5 at zoom 3 — of its 4 × 4 squares the one in column 1, row 2.
        assertEquals(MetroTilePart(MetroTile(3, 3, 5), 1, 2, 4), metroTileStandIn(MetroTile(5, 13, 22), 2))
        assertEquals(MetroTilePart(MetroTile(0, 0, 0), 1, 0, 2), metroTileStandIn(MetroTile(1, 1, 0), 1))
        // Wrapped round the date line, it is the wrapped tile's.
        assertEquals(MetroTile(3, 0, 1), metroTileStandIn(MetroTile(4, 1, 3, at = 17), 1).tile)
    }

    @Test
    fun tileAddressesAndCredits() {
        assertEquals("https://tile.openstreetmap.org/12/2476/1280.png", metroTileUrl(MetroTileSource.OSM, 12, 2476, 1280))
        assertTrue(metroTileUrl(MetroTileSource.CARTO_LIGHT, 3, 1, 2).matches(Regex("https://[abcd]\\.basemaps\\.cartocdn\\.com/light_all/3/1/2\\.png")))
        assertTrue(metroTileCredit(MetroTileSource.CARTO_DARK).contains("OpenStreetMap"))
    }

    @Test
    fun citiesKnownByNameAndPlacesTypedIn() {
        assertEquals(55.7558 to 37.6176, metroCityCentre("Москва Тест"))
        assertEquals(41.0082 to 28.9784, metroCityCentre("Стамбул"))
        assertNull(metroCityCentre("Атлантида"))
        assertEquals(55.75 to 37.62, metroParseLatLon("55.75, 37.62"))
        assertEquals(55.75 to 37.62, metroParseLatLon("55.75 37.62"))
        assertNull(metroParseLatLon("95, 37"))
        assertNull(metroParseLatLon("Москва"))
    }
}
