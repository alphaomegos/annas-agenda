package com.alphaomegos.annasagenda.app

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.alphaomegos.annasagenda.model.MusicSaveItem
import com.alphaomegos.annasagenda.model.SidecarTrack
import com.alphaomegos.annasagenda.support.musicCacheKey
import com.alphaomegos.annasagenda.support.musicCacheKeysStale
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The sidecar's music kept on the phone (16.10, Eduard: "you can play
 * online, you can keep every track on the device; clearing it, naturally").
 *
 * One store for the process, in the app's no-backup folder — Android's
 * backup has a quota a few albums would fill, and the music is the
 * sidecar's to fetch again — that nothing is ever pushed out of but by the
 * person: a kept track stays kept. Each copy goes under the track's id and
 * its file's etag (musicCacheKey), so a file replaced on the web is fetched
 * anew.
 *
 * What is played online is kept as well only when the person asked for it
 * ([keepWhilePlaying], the music settings); otherwise only "save" keeps.
 */
@OptIn(UnstableApi::class)
internal object MusicCache {
    @Volatile
    private var cache: SimpleCache? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Played online and kept too (MusicPrefs.cacheWhilePlaying), set by the music screen. */
    @Volatile
    var keepWhilePlaying: Boolean = false

    private val _revision = MutableStateFlow(0)

    /** Bumped whenever what is kept may have changed, for the screens to look again. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _saving = MutableStateFlow<Set<String>>(emptySet())

    /** The tracks being saved now, by id. */
    val saving: StateFlow<Set<String>> = _saving.asStateFlow()

    fun of(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.noBackupFilesDir, "music_cache"),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    fun changed() {
        _revision.update { it + 1 }
    }

    /** Whether [track]'s file is on the phone whole. */
    fun keptWhole(context: Context, track: SidecarTrack): Boolean {
        val key = musicCacheKey(track) ?: return false
        val size = track.audioSize?.takeIf { it > 0 } ?: return false
        return runCatching { of(context).isCached(key, 0, size) }.getOrDefault(false)
    }

    /** The ids of [tracks] kept whole. Reads the store: off the main thread. */
    fun keptIds(context: Context, tracks: List<SidecarTrack>): Set<String> =
        tracks.filter { keptWhole(context, it) }.map { it.id }.toSet()

    /** How much the store holds, in bytes. */
    fun bytes(context: Context): Long = runCatching { of(context).cacheSpace }.getOrDefault(0L)

    /**
     * Where the player reads from (16.10): the phone's copy when there is
     * one; otherwise [upstream] — and kept on the way only when the person
     * asked for that, decided at each file's opening.
     */
    fun playbackFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory {
        val store = of(context)
        val keeping = CacheDataSource.Factory().setCache(store).setUpstreamDataSourceFactory(upstream)
        val readingOnly = CacheDataSource.Factory()
            .setCache(store)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
        return DataSource.Factory { if (keepWhilePlaying) keeping.createDataSource() else readingOnly.createDataSource() }
    }

    /** [track] fetched from [url] through [upstream] and kept, in the background; once at a time each. */
    fun save(context: Context, track: SidecarTrack, url: String, upstream: DataSource.Factory) {
        val key = musicCacheKey(track) ?: return
        if (track.id in _saving.value) return
        _saving.update { it + track.id }
        scope.launch {
            try {
                val source = CacheDataSource.Factory().setCache(of(context)).setUpstreamDataSourceFactory(upstream).createDataSource()
                val spec = DataSpec.Builder().setUri(url).setKey(key).build()
                runCatching { CacheWriter(source, spec, null, null).cache() }
            } finally {
                _saving.update { it - track.id }
                changed()
            }
        }
    }

    /** [item] fetched and kept now, blocking — for "save all"'s worker. True when it is kept whole. */
    fun saveNow(context: Context, item: MusicSaveItem, upstream: DataSource.Factory): Boolean {
        val store = of(context)
        if (item.size > 0 && runCatching { store.isCached(item.key, 0, item.size) }.getOrDefault(false)) return true
        val source = CacheDataSource.Factory().setCache(store).setUpstreamDataSourceFactory(upstream).createDataSource()
        val spec = DataSpec.Builder().setUri(item.url).setKey(item.key).build()
        return runCatching { CacheWriter(source, spec, null, null).cache() }.isSuccess
    }

    /** [track]'s copy taken off the phone: it plays online again. */
    fun remove(context: Context, track: SidecarTrack) {
        val key = musicCacheKey(track) ?: return
        scope.launch {
            runCatching { of(context).removeResource(key) }
            changed()
        }
    }

    /** Every copy taken off the phone. */
    fun clear(context: Context) {
        scope.launch {
            runCatching {
                val store = of(context)
                store.keys.toList().forEach { store.removeResource(it) }
            }
            changed()
        }
    }

    /**
     * The copies no longer wanted (musicCacheKeysStale): of tracks gone from
     * the sidecar's [tracks] — its whole list, as the server last gave it —
     * or whose file was replaced since.
     */
    fun dropStale(context: Context, tracks: List<SidecarTrack>) {
        scope.launch {
            runCatching {
                val store = of(context)
                val stale = musicCacheKeysStale(store.keys.toSet(), tracks, emptySet())
                stale.forEach { store.removeResource(it) }
                if (stale.isNotEmpty()) changed()
            }
        }
    }
}
