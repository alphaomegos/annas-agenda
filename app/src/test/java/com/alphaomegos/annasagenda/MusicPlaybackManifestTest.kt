package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What playing in the background needs of the build (16.10), read from the
 * files: none of it fails a build or a test when missing — it fails on a
 * phone, when the music stops with the screen or the app dies on play.
 */
class MusicPlaybackManifestTest {

    private val manifest: String by lazy { File(mainResRoot().parentFile, "AndroidManifest.xml").readText() }

    private fun versions(): String =
        listOf("gradle/libs.versions.toml", "../gradle/libs.versions.toml", "../../gradle/libs.versions.toml")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("Cannot find gradle/libs.versions.toml from ${File("").absolutePath}")

    private fun version(name: String): String =
        Regex("""(?m)^$name\s*=\s*"([^"]+)"""").find(versions())?.groupValues?.get(1)
            ?: error("libs.versions.toml names no $name")

    /** Android 14 refuses a foreground service whose kind is not declared, and its permission with it. */
    @Test
    fun theServiceIsAMediaPlaybackOneWithItsPermissions() {
        val service = Regex("""<service[^>]*android:name="\.MusicPlaybackService"[^>]*>""").find(manifest)?.value

        assertTrue("the manifest declares no MusicPlaybackService", service != null)
        assertTrue("the service is not of the mediaPlayback kind: $service", service!!.contains("""android:foregroundServiceType="mediaPlayback""""))
        assertTrue("the shade, headsets and cars cannot reach an unexported session", service.contains("""android:exported="true""""))
        assertTrue(manifest.contains("""<action android:name="androidx.media3.session.MediaSessionService" />"""))
        listOf("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK", "WAKE_LOCK", "INTERNET").forEach { permission ->
            assertTrue("the manifest does not ask for $permission", manifest.contains("""android.permission.$permission""""))
        }
    }

    /** The manifest names it by the root package, as the other components (0160): it has to be there. */
    @Test
    fun theServiceIsWhereTheManifestLooks() {
        val file = mainSourceFiles().single { it.name == "MusicPlaybackService.kt" }
        val text = file.readText()

        assertTrue(text.contains("package com.alphaomegos.annasagenda\n"))
        assertTrue(text.contains("class MusicPlaybackService : MediaSessionService()"))
    }

    /** Media3 from 1.11 is built with Kotlin 2.2; Kotlin 2.0 cannot read it. Raise both, or neither. */
    @Test
    fun theMediaLibraryIsOneThisKotlinReads() {
        val kotlin = version("kotlin")
        val (major, minor) = version("media3").split('.').map { it.toInt() }

        if (kotlin.startsWith("2.0.") || kotlin.startsWith("1.")) {
            assertTrue("media3 ${version("media3")} needs a newer Kotlin than $kotlin", major == 1 && minor <= 10)
        }
    }
}
