package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.support.sidecarAvatarFileName
import com.alphaomegos.annasagenda.support.sidecarAvatarNeedsSession
import com.alphaomegos.annasagenda.support.sidecarAvatarPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The most of a picture fetched: the server takes uploads of up to five megabytes. */
internal const val SIDECAR_AVATAR_MAX_BYTES = 6 * 1024 * 1024

/** A file fetched (16.10): its bytes for a 200, null for anything else or a reply too long. Blocking. */
internal fun interface SidecarFetch {
    fun bytes(url: String, cookie: String?, maxBytes: Int): ByteArray?
}

/** Fetched as Android fetches, under the app's name; no redirects, no cache. */
internal object SidecarUrlFetch : SidecarFetch {
    override fun bytes(url: String, cookie: String?, maxBytes: Int): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.setRequestProperty("User-Agent", SIDECAR_HTTP_AGENT)
            if (cookie != null) c.setRequestProperty("Cookie", cookie)
            if (c.responseCode != HttpURLConnection.HTTP_OK) return null
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) return null
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }
}

/**
 * The person's own picture from oursidecar (16.10, Eduard: "show the user's
 * avatar, it is there in the sidecar"), kept in [dir] — the no-backup folder
 * — so the main menu shows it without asking the server each time. The
 * server's drawn ones are fetched as they are, open to anybody; an uploaded
 * one with the session, and to the sidecar's server only. Only the picture
 * last fetched is kept.
 */
internal class SidecarAvatars(
    private val dir: File,
    private val connection: SidecarConnection,
    private val fetch: SidecarFetch,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    /** The picture of [avatarId]: the phone's copy, or the server's — then kept. Null when none can be had. */
    suspend fun bytes(avatarId: String): ByteArray? = withContext(io) {
        mutex.withLock { bytesNow(avatarId) }
    }

    private suspend fun bytesNow(avatarId: String): ByteArray? {
        val file = File(dir, sidecarAvatarFileName(avatarId))
        if (file.isFile) runCatching { file.readBytes() }.getOrNull()?.let { return it }
        val path = sidecarAvatarPath(avatarId) ?: return null
        val cookie = if (sidecarAvatarNeedsSession(path)) connection.cookieHeader() ?: return null else null
        val got = fetch.bytes(connection.status.value.server + path, cookie, SIDECAR_AVATAR_MAX_BYTES) ?: return null
        runCatching {
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            file.writeBytes(got)
        }
        return got
    }

    /** Nothing kept: the person signed out. */
    suspend fun forget() = withContext(io) {
        mutex.withLock { dir.listFiles()?.forEach { it.delete() } }
    }
}
