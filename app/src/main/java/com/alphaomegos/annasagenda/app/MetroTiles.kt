package com.alphaomegos.annasagenda.app

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.alphaomegos.annasagenda.model.MetroTileSource
import com.alphaomegos.annasagenda.support.MetroTile
import com.alphaomegos.annasagenda.support.metroTileUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/*
 * The city map's network side (16.10): the only place the app goes online.
 * Tiles are fetched as they come into sight, two at a time, under the app's
 * own name, and kept — in memory while the editor is open, and in the app's
 * cache for a month, as OpenStreetMap asks of those who use its tiles.
 */

/**
 * How the app names itself to the map's servers: its own name and version,
 * and where to learn of it — as OpenStreetMap's tile policy asks (17.10).
 */
private const val METRO_HTTP_AGENT = "AnnasAgenda/22.0 (Android; +https://github.com/alphaomegos/annas-agenda)"

/** Tiles kept in memory: a screen and a half of them, twice over. */
private const val MEMORY_TILES = 160

/** How long a tile on disk is good for before it is fetched afresh. */
private const val KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000

/** How long a tile that would not come is left alone before it is asked for again. */
private const val RETRY_MILLIS = 60_000L

/**
 * The tiles of the map under the editor's picture (16.10). [tile] is asked
 * while drawing: it hands back what is here and sends for what is not, and
 * the drawing — which reads [arrived] through it — is redone as tiles come.
 * Everything but the fetching itself runs on the main thread.
 *
 * Each drawing begins with [newView] (17.10): a tile sent for that the
 * latest view no longer shows is given up before it is fetched, so that
 * after a quick zoom through several scales the tiles now in sight are not
 * kept waiting behind those of the scales passed.
 */
internal class MetroTileLoader(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.applicationContext.cacheDir, "metro_tiles")
    private val memory = LruCache<String, ImageBitmap>(MEMORY_TILES)
    private val pending = HashSet<String>()
    private val failedAt = HashMap<String, Long>()
    private val gate = Semaphore(2)
    private val view = AtomicInteger(0)
    private val askedIn = ConcurrentHashMap<String, Int>()

    /** Bumped each time a tile comes, or will not. */
    var arrived by mutableIntStateOf(0)
        private set

    /** A drawing begins: the tiles it asks for are those wanted now. */
    fun newView() {
        view.incrementAndGet()
    }

    /** The tile if it is in memory, without sending for it — to stand in for a finer one still coming. */
    fun kept(source: MetroTileSource, t: MetroTile): ImageBitmap? = memory.get(keyOf(source, t))

    private fun keyOf(source: MetroTileSource, t: MetroTile) = "${source.name}/${t.z}/${t.x}/${t.y}"

    fun tile(source: MetroTileSource, t: MetroTile): ImageBitmap? {
        // Read, so that whoever draws draws again when a tile comes.
        @Suppress("UNUSED_VARIABLE") val seen = arrived
        val key = keyOf(source, t)
        memory.get(key)?.let { return it }
        val failed = failedAt[key]
        if (failed != null && SystemClock.elapsedRealtime() - failed < RETRY_MILLIS) return null
        askedIn[key] = view.get()
        if (key in pending) return null
        pending += key
        scope.launch {
            var stale = false
            val image = withContext(Dispatchers.IO) {
                gate.withPermit {
                    // Out of sight by now: left for when it is in sight again.
                    stale = askedIn[key] != view.get()
                    if (stale) null else runCatching { load(source, t) }.getOrNull()
                }
            }
            pending -= key
            // Given up just as a drawing asked for it again: drawn again, and so sent for again.
            val again = askedIn.remove(key) == view.get()
            if (stale) {
                if (again) arrived++
                return@launch
            }
            if (image != null) {
                memory.put(key, image)
                failedAt.remove(key)
            } else {
                failedAt[key] = SystemClock.elapsedRealtime()
            }
            arrived++
        }
        return null
    }

    private fun load(source: MetroTileSource, t: MetroTile): ImageBitmap? {
        val file = File(dir, "${source.name}/${t.z}/${t.x}/${t.y}.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < KEEP_MILLIS
        if (fresh) BitmapFactory.decodeFile(file.path)?.let { return it.asImageBitmap() }
        val bytes = metroHttpGet(metroTileUrl(source, t.z, t.x, t.y))
        if (bytes == null) {
            // Offline: an old tile is better than none.
            return if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
        return bitmap.asImageBitmap()
    }
}

/** A GET under the app's name; the body, or null for anything but a 200. */
internal fun metroHttpGet(url: String): ByteArray? {
    val c = URL(url).openConnection() as HttpURLConnection
    return try {
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", METRO_HTTP_AGENT)
        if (c.responseCode != HttpURLConnection.HTTP_OK) null else c.inputStream.use { it.readBytes() }
    } catch (e: Exception) {
        null
    } finally {
        c.disconnect()
    }
}

/**
 * A place by its name (16.10), asked of OpenStreetMap's Nominatim — once,
 * when the user asks: its latitude and longitude, or null when it is not
 * found or the phone is offline.
 */
internal suspend fun metroGeocode(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    runCatching {
        val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(query.trim(), "UTF-8")
        val bytes = metroHttpGet(url) ?: return@runCatching null
        val found = JSONArray(String(bytes, Charsets.UTF_8))
        if (found.length() == 0) {
            null
        } else {
            val first = found.getJSONObject(0)
            first.getString("lat").toDouble() to first.getString("lon").toDouble()
        }
    }.getOrNull()
}
