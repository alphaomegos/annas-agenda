package com.alphaomegos.annasagenda.app

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a track's file to the sidecar (02.11), as the web's upload does: PUT the bytes
 * to /api/tracks/{id}/audio?filename=…, with their type and length, telling how much
 * has gone ([onSent]) for the notification. The answer's status, or an IOException.
 */
internal fun interface SidecarAudioPut {
    fun put(url: String, cookie: String, contentType: String, length: Long, body: InputStream, onSent: (Long) -> Unit): Int
}

/** The real one: a streamed request of a fixed length, nothing held in memory. */
internal object SidecarAudioPutHttp : SidecarAudioPut {
    override fun put(url: String, cookie: String, contentType: String, length: Long, body: InputStream, onSent: (Long) -> Unit): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "PUT"
            c.doOutput = true
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setRequestProperty("Cookie", cookie)
            c.setRequestProperty("Content-Type", contentType)
            c.setFixedLengthStreamingMode(length)
            c.outputStream.use { out ->
                val buffer = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    val n = body.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    sent += n
                    onSent(sent)
                }
                if (sent != length) throw IOException("sent $sent of $length")
            }
            return c.responseCode
        } finally {
            c.disconnect()
        }
    }
}
