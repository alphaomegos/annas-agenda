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

    /* ---------- the release key ---------- */

    /**
     * No password, no key path, no alias written into a file that is in git.
     *
     * This is the accident worth a test rather than a convention: the build
     * file is where a signing block goes, the values are right there in the
     * dialog that generates one, and pasting them in is the thing that works
     * immediately and cannot be taken back once it is pushed. The repository
     * is public.
     *
     * Everything is read from local.properties or the environment, both of
     * which stay off git, so a literal here can only be a mistake.
     */
    @Test
    fun theSigningDetailsAreNeverWrittenIntoTheBuildFile() {
        val build = buildFile().readText()

        val assignments = Regex(
            """(storePassword|keyPassword|keyAlias|storeFile)\s*=\s*"[^"]*""""
        ).findAll(build).map { it.value }.toList()

        assertTrue(
            "a signing value is written into app/build.gradle.kts, which is in " +
                "a public repository: $assignments",
            assignments.isEmpty(),
        )
    }

    /**
     * With no key configured the release build has to come out unsigned.
     *
     * The tempting alternative is to fall back to the debug key so that
     * something installable always comes out. That something is refused by
     * Android on top of the real app, and the refusal arrives on the phone
     * rather than here.
     */
    @Test
    fun theReleaseBuildSignsWithTheReleaseKeyOrWithNothing() {
        val build = buildFile().readText()

        assertTrue(
            "the release build type does not choose a signing config",
            build.contains("""signingConfig = signingConfigs.findByName("release")"""),
        )
        assertTrue(
            "the release build falls back to the debug key",
            !build.contains("""signingConfigs.getByName("debug")"""),
        )
    }

    private fun buildFile(): File =
        listOf("build.gradle.kts", "app/build.gradle.kts", "../app/build.gradle.kts")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?: error("Cannot find app/build.gradle.kts from ${File("").absolutePath}")
}