package com.alphaomegos.annasagenda.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The app's name to oursidecar's server (16.10): no address, no account, only what is asking. */
internal const val SIDECAR_HTTP_AGENT = "AnnasAgenda/22.1 (Android)"

/** The most of an answer read: the longest list of tracks is far smaller. */
private const val SIDECAR_MAX_REPLY_BYTES = 8 * 1024 * 1024

/** What the server answered: its status, its body, the cookies it set, and how long to wait if it said. */
internal data class SidecarHttpReply(
    val status: Int,
    val body: String,
    val setCookies: List<String> = emptyList(),
    val retryAfter: String? = null,
)

/** One request to the server (16.10); throws [IOException] when it cannot be reached. Blocking. */
internal fun interface SidecarHttp {
    fun send(method: String, url: String, cookie: String?, json: String?): SidecarHttpReply
}

/**
 * The requests as Android makes them (16.10). Redirects are not followed:
 * the session goes with the request, and only to the server it was given by.
 * No cache, and no cookie jar — the one cookie that matters is read out of
 * the answer and kept sealed (SidecarConnection).
 */
internal object SidecarUrlConnection : SidecarHttp {
    override fun send(method: String, url: String, cookie: String?, json: String?): SidecarHttpReply {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.setRequestProperty("User-Agent", SIDECAR_HTTP_AGENT)
            c.setRequestProperty("Accept", "application/json")
            if (cookie != null) c.setRequestProperty("Cookie", cookie)
            if (json != null) {
                val bytes = json.toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val status = c.responseCode
            val stream = if (status >= 400) c.errorStream else c.inputStream
            val body = stream?.use { readAtMost(it) } ?: ""
            val cookies = c.headerFields.entries
                .filter { (name, _) -> name != null && name.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value.orEmpty() }
            return SidecarHttpReply(status, body, cookies, c.getHeaderField("Retry-After"))
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
            if (out.size() + n > SIDECAR_MAX_REPLY_BYTES) throw IOException("The answer is too long.")
            out.write(buffer, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
