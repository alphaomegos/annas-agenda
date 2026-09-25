package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two version numbers, held to the same story.
 *
 * versionCode said this was the eighteenth build while versionName said 21.3.
 * Neither is wrong on its own — one is a counter Android compares, the other
 * is what the user reads — but nothing connected them, so they drifted apart
 * and nobody noticed until somebody went looking.
 *
 * From 21.4 / 22 onwards they move together: one step each, every release.
 * The offset between them is a historical accident being frozen rather than a
 * law of anything, which is exactly why it needs a test and not a convention.
 */
class VersionNumbersTest {

    /** The gap between versionCode and the minor part of versionName. */
    private val agreedOffset = 18

    @Test
    fun theCodeAndTheNameStillAgree() {
        val build = buildFile().readText()

        val code = Regex("""versionCode\s*=\s*(\d+)""").find(build)
            ?.groupValues?.get(1)?.toInt()
        val name = Regex("""versionName\s*=\s*"([^"]+)"""").find(build)
            ?.groupValues?.get(1)

        requireNotNull(code) { "no versionCode in app/build.gradle.kts" }
        requireNotNull(name) { "no versionName in app/build.gradle.kts" }

        val minor = name.substringAfter('.', "").toIntOrNull()
        requireNotNull(minor) { "versionName is not major.minor: $name" }

        assertEquals(
            "versionName $name and versionCode $code have drifted apart. " +
                "Raise both, one step each, or change the offset here on purpose.",
            agreedOffset + minor,
            code,
        )
    }

    @Test
    fun theCodeHasNeverGoneBackwards() {
        val build = buildFile().readText()
        val code = Regex("""versionCode\s*=\s*(\d+)""").find(build)!!
            .groupValues[1].toInt()

        // 18 shipped. Android refuses an update whose code is lower than the
        // one already installed, so anything at or below it is unreleasable.
        assertTrue("versionCode $code is not above the 18 already installed", code > 18)
    }

    private fun buildFile(): File =
        listOf("build.gradle.kts", "app/build.gradle.kts", "../app/build.gradle.kts")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?: error("Cannot find app/build.gradle.kts from ${File("").absolutePath}")
}
