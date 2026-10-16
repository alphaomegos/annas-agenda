package com.alphaomegos.annasagenda.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** The most of a JSON answer about backups read: a list of four is a few kilobytes. */
private const val SIDECAR_BACKUP_MAX_REPLY_BYTES = 1024 * 1024

/** What the server answered: its status, its body, and how long to wait if it said. */
internal data class SidecarBackupReply(
    val status: Int,
    val body: String,
    val retryAfter: String? = null,
)

/** A download's end: the status, the sha256 the server keeps, and — for an error — its body. */
internal data class SidecarBackupDownloadReply(
    val status: Int,
    val sha256: String? = null,
    val body: String = "",
)

/**
 * The requests about backups (16.10), with the phone's token as a bearer.
 * Each throws [IOException] when the server cannot be reached. Blocking.
 */
internal interface SidecarBackupHttp {
    /** A request with a JSON body, or none. */
    fun json(method: String, url: String, bearer: String?, json: String?): SidecarBackupReply

    /** A part: [length] bytes of [bytes] from [offset], sent with their length and never in chunks. */
    fun putBytes(url: String, bearer: String, bytes: ByteArray, offset: Int, length: Int): SidecarBackupReply

    /**
     * The file at [url], from byte [from] on (a Range when above zero), into
     * the stream [open] gives — told whether what comes is the rest (a 206)
     * or the whole, from the start (a 200) — and closed after. [onBytes] is
     * told what has come so far. Nothing is opened for an error.
     */
    fun download(url: String, bearer: String, from: Long, open: (partial: Boolean) -> OutputStream, onBytes: (Long) -> Unit): SidecarBackupDownloadReply
}

/**
 * The requests as Android makes them (16.10): no redirects — the token goes
 * only to the server it was given by — no cache.
 */
internal object SidecarBackupUrlConnection : SidecarBackupHttp {

    private fun open(method: String, url: String, bearer: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", SIDECAR_HTTP_AGENT)
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }

    private fun reply(c: HttpURLConnection): SidecarBackupReply {
        val status = c.responseCode
        val stream = if (status >= 400) c.errorStream else c.inputStream
        val body = stream?.use { readAtMost(it) } ?: ""
        return SidecarBackupReply(status, body, c.getHeaderField("Retry-After"))
    }

    override fun json(method: String, url: String, bearer: String?, json: String?): SidecarBackupReply {
        val c = open(method, url, bearer)
        try {
            c.setRequestProperty("Accept", "application/json")
            if (json != null) {
                val bytes = json.toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            return reply(c)
        } finally {
            c.disconnect()
        }
    }

    override fun putBytes(url: String, bearer: String, bytes: ByteArray, offset: Int, length: Int): SidecarBackupReply {
        val c = open("PUT", url, bearer)
        try {
            c.setRequestProperty("Accept", "application/json")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/octet-stream")
            // The length is told before the body, and the body is never sent in chunks: the server
            // refuses a part without a Content-Length (411) or with another length than its own (400).
            c.setFixedLengthStreamingMode(length)
            c.outputStream.use { it.write(bytes, offset, length) }
            return reply(c)
        } finally {
            c.disconnect()
        }
    }

    override fun download(url: String, bearer: String, from: Long, open: (partial: Boolean) -> OutputStream, onBytes: (Long) -> Unit): SidecarBackupDownloadReply {
        val c = open("GET", url, bearer)
        try {
            if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
            val status = c.responseCode
            if (status != 200 && status != 206) {
                val body = c.errorStream?.use { readAtMost(it) } ?: ""
                return SidecarBackupDownloadReply(status, body = body)
            }
            val sha = c.getHeaderField("X-Backup-Sha256")
            c.inputStream.use { input ->
                open(status == 206).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        total += n
                        onBytes(total)
                    }
                }
            }
            return SidecarBackupDownloadReply(status, sha)
        } finally {
            c.disconnect()
        }
    }

    private fun readAtMost(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > SIDECAR_BACKUP_MAX_REPLY_BYTES) throw IOException("The answer is too long.")
            out.write(buffer, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
