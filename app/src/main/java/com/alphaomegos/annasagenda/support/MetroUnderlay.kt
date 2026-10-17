package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroTileSource
import com.alphaomegos.annasagenda.model.MetroUnderlay
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

/*
 * A city map under the editor's picture (16.10, Eduard): free tiles in Web
 * Mercator laid under the scheme so that it shows roughly where its stations
 * are. The scheme is a picture, not a map, so the map is laid by hand — moved,
 * scaled and turned under it like tracing paper — and kept with the scheme.
 *
 * "Mercator metres" here are the Web Mercator plane measured from the
 * world's top left corner, y going down — as the tiles count — the whole
 * world [METRO_MERCATOR_WORLD] across.
 */

/** The world's width on the Web Mercator plane, in its metres. */
const val METRO_MERCATOR_WORLD = 40075016.68557849

/** The tiles' side, in pixels. */
const val METRO_TILE_SIZE = 256

/** How faint the map may be made, at most (16.10). */
const val METRO_UNDERLAY_MIN_OPACITY = 0.15f

/** A step of a fresh map's scale (16.10): about as far as two stations of a city's metro stand. */
const val METRO_UNDERLAY_DEFAULT_METERS = 1500f

/** How many tiles a view may ask for at once; past that it takes coarser ones (metroTilesForView). */
const val METRO_UNDERLAY_MAX_TILES = 64

/** How many zooms coarser than the sharpest a view may take to keep within [METRO_UNDERLAY_MAX_TILES]. */
const val METRO_UNDERLAY_COARSER_STEPS = 4

fun metroMercatorX(lon: Double): Double = (lon + 180.0) / 360.0 * METRO_MERCATOR_WORLD

fun metroMercatorY(lat: Double): Double {
    val r = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * METRO_MERCATOR_WORLD
}

fun metroLonOf(mx: Double): Double = mx / METRO_MERCATOR_WORLD * 360.0 - 180.0

fun metroLatOf(my: Double): Double = atan(sinh(PI * (1.0 - 2.0 * my / METRO_MERCATOR_WORLD))) * 180.0 / PI

/** Mercator metres to a step of the picture: the ground's metres, stretched as Mercator stretches them there. */
fun metroUnderlayStretch(u: MetroUnderlay): Double = u.metersPerUnit / cos(u.lat * PI / 180.0)

/** Where the picture's point ([px], [py]) lies on the Mercator plane. */
fun metroUnderlayMercator(u: MetroUnderlay, px: Float, py: Float): Pair<Double, Double> {
    val s = metroUnderlayStretch(u)
    val dx = (px - u.x) * s
    val dy = (py - u.y) * s
    val t = u.rotationDeg * PI / 180.0
    val rx = dx * cos(t) - dy * sin(t)
    val ry = dx * sin(t) + dy * cos(t)
    return (metroMercatorX(u.lon) + rx) to (metroMercatorY(u.lat) + ry)
}

/** The picture's point lying over the Mercator plane's ([mx], [my]). */
fun metroUnderlayPicture(u: MetroUnderlay, mx: Double, my: Double): Pair<Float, Float> {
    val s = metroUnderlayStretch(u)
    val dx = mx - metroMercatorX(u.lon)
    val dy = my - metroMercatorY(u.lat)
    val t = -u.rotationDeg * PI / 180.0
    val rx = dx * cos(t) - dy * sin(t)
    val ry = dx * sin(t) + dy * cos(t)
    return (u.x + rx / s).toFloat() to (u.y + ry / s).toFloat()
}

/**
 * The map moved under the picture by a hand (16.10): carried [dx], [dy] steps
 * of the picture, made [zoom] times as large and turned [turnDeg] clockwise,
 * both about the picture's point ([cx], [cy]) — the fingers' middle — which
 * keeps what lay under it.
 */
fun metroUnderlayMoved(
    u: MetroUnderlay,
    dx: Float,
    dy: Float,
    zoom: Float,
    turnDeg: Float,
    cx: Float,
    cy: Float,
): MetroUnderlay {
    // The pinned point travels with the map: carried, then scaled and turned about the middle.
    val z = zoom.takeIf { it.isFinite() && it > 0f } ?: 1f
    val ax = u.x + dx - cx
    val ay = u.y + dy - cy
    val t = turnDeg * PI / 180.0
    val rx = (ax * cos(t) - ay * sin(t)) * z
    val ry = (ax * sin(t) + ay * cos(t)) * z
    return u.copy(
        x = (cx + rx).toFloat(),
        y = (cy + ry).toFloat(),
        metersPerUnit = (u.metersPerUnit / z).coerceIn(1f, 1_000_000f),
        rotationDeg = metroNormalDegrees(u.rotationDeg - turnDeg),
    )
}

/** An angle brought within -180..180. */
fun metroNormalDegrees(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/**
 * The zoom of the tiles to draw (16.10) when [screenPerMercator] pixels of
 * the screen fall to a Mercator metre: the one whose own pixels come
 * nearest the screen's, a little sharper rather than blurrier, within what
 * [source] has.
 */
fun metroTileZoom(screenPerMercator: Double, source: MetroTileSource): Int {
    if (!(screenPerMercator > 0.0)) return 0
    val z = kotlin.math.log2(screenPerMercator * METRO_MERCATOR_WORLD / METRO_TILE_SIZE)
    return kotlin.math.ceil(z - 0.3).toInt().coerceIn(0, metroTileMaxZoom(source))
}

fun metroTileMaxZoom(source: MetroTileSource): Int = when (source) {
    MetroTileSource.OSM -> 19
    MetroTileSource.CARTO_LIGHT, MetroTileSource.CARTO_DARK -> 20
}

/** Where tile [x], [y] at zoom [z] of [source] is fetched from. */
fun metroTileUrl(source: MetroTileSource, z: Int, x: Int, y: Int): String = when (source) {
    MetroTileSource.OSM -> "https://tile.openstreetmap.org/$z/$x/$y.png"
    MetroTileSource.CARTO_LIGHT -> "https://${"abcd"[(x + y).mod(4)]}.basemaps.cartocdn.com/light_all/$z/$x/$y.png"
    MetroTileSource.CARTO_DARK -> "https://${"abcd"[(x + y).mod(4)]}.basemaps.cartocdn.com/dark_all/$z/$x/$y.png"
}

/** The words each source asks to be named by, under its map. */
fun metroTileCredit(source: MetroTileSource): String = when (source) {
    MetroTileSource.OSM -> "© OpenStreetMap contributors"
    MetroTileSource.CARTO_LIGHT, MetroTileSource.CARTO_DARK -> "© OpenStreetMap contributors © CARTO"
}

/** A tile: its zoom and place; [at] is its column before wrapping round the world, where it is drawn. */
data class MetroTile(val z: Int, val x: Int, val y: Int, val at: Int = x)

/**
 * The tiles at zoom [z] that the Mercator box from ([minX], [minY]) to
 * ([maxX], [maxY]) touches (16.10), nearest the box's middle first; none
 * when there would be more than [limit]. Across the date line they wrap.
 */
fun metroTilesCovering(z: Int, minX: Double, minY: Double, maxX: Double, maxY: Double, limit: Int = METRO_UNDERLAY_MAX_TILES): List<MetroTile> {
    val n = 1 shl z
    val tile = METRO_MERCATOR_WORLD / n
    val x0 = kotlin.math.floor(minX / tile).toInt()
    val x1 = kotlin.math.floor(maxX / tile).toInt()
    val y0 = kotlin.math.floor(minY / tile).toInt().coerceAtLeast(0)
    val y1 = kotlin.math.floor(maxY / tile).toInt().coerceAtMost(n - 1)
    if (x1 < x0 || y1 < y0) return emptyList()
    val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
    if (count > limit) return emptyList()
    val midX = (x0 + x1) / 2.0
    val midY = (y0 + y1) / 2.0
    return (y0..y1).flatMap { ty -> (x0..x1).map { tx -> tx to ty } }
        .sortedBy { (tx, ty) -> (tx - midX) * (tx - midX) + (ty - midY) * (ty - midY) }
        .map { (tx, ty) -> MetroTile(z, tx.mod(n), ty, tx) }
}

/**
 * The tiles to draw for the Mercator box (17.10): those of the sharpest zoom
 * [z] that keeps within [limit], going up to [METRO_UNDERLAY_COARSER_STEPS]
 * zooms coarser, each a quarter as many — and the zoom they are of.
 *
 * At the sharpest zoom a tall screen — the Fold's open one, a turned map —
 * could need well over the limit, and then nothing was drawn at all: the
 * white Eduard saw at some scales (17.10). A map a little blurred is a map.
 */
fun metroTilesForView(
    z: Int,
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
    limit: Int = METRO_UNDERLAY_MAX_TILES,
): Pair<Int, List<MetroTile>> {
    var zoom = z
    while (true) {
        val tiles = metroTilesCovering(zoom, minX, minY, maxX, maxY, limit)
        if (tiles.isNotEmpty() || zoom == 0 || zoom <= z - METRO_UNDERLAY_COARSER_STEPS) return zoom to tiles
        zoom--
    }
}

/**
 * Where a tile's top left falls on the screen (17.10), counted in doubles:
 * [pinX], [pinY] the pinned place on the screen, [lx], [ly] the tile's
 * corner from it in tile pixels, made [zoomBy] times as large and turned
 * [turnDeg] as the drawing turns (clockwise, y down).
 *
 * Each tile is drawn from its own corner, so that in its place a part of a
 * coarser one can stand while it comes (metroTileStandIn).
 */
fun metroTileScreenCorner(pinX: Double, pinY: Double, lx: Double, ly: Double, zoomBy: Double, turnDeg: Double): Pair<Double, Double> {
    val t = turnDeg * PI / 180.0
    val x = lx * zoomBy
    val y = ly * zoomBy
    return (pinX + x * cos(t) - y * sin(t)) to (pinY + x * sin(t) + y * cos(t))
}

/** How many zooms coarser a tile kept in memory may be to stand in for one still coming (17.10). */
const val METRO_UNDERLAY_STAND_IN_STEPS = 4

/** A part of a coarser [tile]: of its [across] × [across] squares, the one in column [col], row [row]. */
data class MetroTilePart(val tile: MetroTile, val col: Int, val row: Int, val across: Int)

/**
 * Where tile [t] lies in the one [up] zooms coarser (17.10): that tile, and
 * the square of it [t] covers — what is drawn, enlarged, in [t]'s place
 * while [t] itself is still on its way, rather than white.
 */
fun metroTileStandIn(t: MetroTile, up: Int): MetroTilePart {
    require(up in 1..t.z) { "up $up for zoom ${t.z}" }
    val n = 1 shl up
    return MetroTilePart(MetroTile(t.z - up, t.x shr up, t.y shr up), t.x and (n - 1), t.y and (n - 1), n)
}

/**
 * The middle of a city known without asking anyone (16.10), by the scheme's
 * name — "Москва Тест" is Moscow — or null for one this build does not know.
 */
fun metroCityCentre(city: String): Pair<Double, Double>? {
    val key = city.trim().lowercase().replace('ё', 'е')
    return METRO_CITY_CENTRES.entries.firstOrNull { (names, _) -> names.any { key == it || key.startsWith("$it ") } }?.value
}

private val METRO_CITY_CENTRES: Map<List<String>, Pair<Double, Double>> = mapOf(
    listOf("москва", "moscow", "moskva") to (55.7558 to 37.6176),
    listOf("санкт-петербург", "петербург", "saint petersburg", "st petersburg", "sankt peterburg") to (59.9343 to 30.3351),
    listOf("стамбул", "istanbul") to (41.0082 to 28.9784),
    listOf("новосибирск", "novosibirsk") to (55.0302 to 82.9204),
    listOf("екатеринбург", "yekaterinburg") to (56.8389 to 60.6057),
    listOf("казань", "kazan") to (55.7963 to 49.1088),
    listOf("нижний новгород", "nizhny novgorod") to (56.3269 to 44.0059),
    listOf("самара", "samara") to (53.1959 to 50.1002),
    listOf("минск", "minsk") to (53.9006 to 27.5590),
    listOf("белград", "beograd", "belgrade") to (44.8125 to 20.4612),
    listOf("ташкент", "tashkent") to (41.2995 to 69.2401),
    listOf("баку", "baku") to (40.4093 to 49.8671),
    listOf("тбилиси", "tbilisi") to (41.7151 to 44.8271),
    listOf("ереван", "yerevan") to (40.1792 to 44.4991),
    listOf("алматы", "almaty") to (43.2220 to 76.8512),
    listOf("париж", "paris") to (48.8566 to 2.3522),
    listOf("лондон", "london") to (51.5074 to -0.1278),
    listOf("берлин", "berlin") to (52.5200 to 13.4050),
    listOf("вена", "wien", "vienna") to (48.2082 to 16.3738),
    listOf("прага", "praha", "prague") to (50.0755 to 14.4378),
)

/**
 * "55.75, 37.62" read as a place (16.10): a latitude and a longitude by a
 * comma, a semicolon or spaces; null for anything else.
 */
fun metroParseLatLon(text: String): Pair<Double, Double>? {
    val parts = text.trim().split(Regex("[,;\\s]+")).filter { it.isNotEmpty() }
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    return if (lat in -85.0..85.0 && lon in -180.0..180.0) lat to lon else null
}
