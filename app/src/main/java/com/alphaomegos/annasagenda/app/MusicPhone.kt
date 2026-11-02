package com.alphaomegos.annasagenda.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.alphaomegos.annasagenda.support.PhoneTrack
import com.alphaomegos.annasagenda.support.phoneArtist
import com.alphaomegos.annasagenda.support.phoneFolderOf
import com.alphaomegos.annasagenda.support.phoneTitleOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the phone's media index gave, and whether the app may read it. */
internal data class MusicPhoneScan(val permitted: Boolean = false, val scanned: Boolean = false, val tracks: List<PhoneTrack> = emptyList())

/**
 * The phone's own music (02.11, Eduard): read from the system's media index — the
 * titles, artists and lengths it has from the files' tags — with the permission
 * «Music and audio» (READ_MEDIA_AUDIO; storage before Android 13). The index tells
 * of new and removed files, and the list follows within a second: a track copied
 * into a chosen folder shows up by itself. One for the process.
 */
internal class MusicPhone(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _scan = MutableStateFlow(MusicPhoneScan())
    val scan: StateFlow<MusicPhoneScan> = _scan.asStateFlow()
    private var watching = false
    private var pending: Job? = null

    private val collection: Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // A copy brings several changes in a row: one look after they settle.
            pending?.cancel()
            pending = scope.launch {
                delay(800)
                rescan()
            }
        }
    }

    fun permitted(): Boolean = context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** Reads the index again (on entering, after the permission, on «refresh», on a change). */
    suspend fun rescan() {
        if (!permitted()) {
            _scan.value = MusicPhoneScan(permitted = false, scanned = true)
            return
        }
        if (!watching) {
            runCatching { context.contentResolver.registerContentObserver(collection, true, observer) }
            watching = true
        }
        val tracks = withContext(Dispatchers.IO) { runCatching { query() }.getOrDefault(emptyList()) }
        _scan.value = MusicPhoneScan(permitted = true, scanned = true, tracks = tracks)
    }

    @Suppress("DEPRECATION")
    private fun query(): List<PhoneTrack> {
        val modern = Build.VERSION.SDK_INT >= 29
        val where = if (modern) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            where,
        )
        val root = Environment.getExternalStorageDirectory().absolutePath
        val out = ArrayList<PhoneTrack>()
        context.contentResolver.query(collection, columns, null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val place = c.getColumnIndexOrThrow(where)
            while (c.moveToNext()) {
                val mediaId = c.getLong(id)
                val fileName = c.getString(name) ?: continue
                val folder = c.getString(place)?.let { if (modern) it.trimStart('/').let { p -> if (p.endsWith("/")) p else "$p/" } else phoneFolderOf(it, root) } ?: continue
                out += PhoneTrack(
                    mediaId = mediaId,
                    uri = ContentUris.withAppendedId(collection, mediaId).toString(),
                    title = phoneTitleOf(c.getString(title), fileName),
                    artist = phoneArtist(c.getString(artist)),
                    durationMs = c.getLong(duration),
                    sizeBytes = c.getLong(size),
                    folder = folder,
                    fileName = fileName,
                    mimeType = c.getString(mime),
                    addedAt = c.getLong(added),
                )
            }
        }
        return out
    }

    companion object {
        /** What to ask for: «Music and audio» from Android 13, the storage before it. */
        fun permission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

/** The phone's music and its choices, one of each for the process. */
internal object MusicPhones {
    @Volatile
    private var phone: MusicPhone? = null

    @Volatile
    private var prefs: MusicPhonePrefs? = null

    fun of(context: Context): MusicPhone = phone ?: synchronized(this) {
        phone ?: MusicPhone(context.applicationContext).also { phone = it }
    }

    fun prefsOf(context: Context): MusicPhonePrefs = prefs ?: synchronized(this) {
        prefs ?: MusicPhonePrefs(File(File(context.applicationContext.noBackupFilesDir, "music"), "phone.json")).also { prefs = it }
    }

    @Volatile
    private var proposals: MusicProposals? = null

    /** The phone's tracks proposed to the sidecar, waiting or sent (02.11). */
    fun proposalsOf(context: Context): MusicProposals = proposals ?: synchronized(this) {
        proposals ?: MusicProposals(File(File(context.applicationContext.noBackupFilesDir, "music"), "proposals.json")).also { proposals = it }
    }
}
