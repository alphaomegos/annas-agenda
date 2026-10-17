package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

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
 *
 * A new main version starts its minor part at 0 again while the code goes on
 * up by one (16.10: 21.6 was 24, 22.0 is 25), so each main version has its
 * own offset, written down here when it begins.
 */
class VersionNumbersTest {

    /** The gap between versionCode and the minor part of versionName, by main version. */
    private val agreedOffsets = mapOf(21 to 18, 22 to 25)

    /**
     * And the built file says which of them it is.
     *
     * Two release APKs in one folder used to be told apart by the date on the
     * file, and a phone cannot be asked which build it is carrying. Naming the
     * archive after the version costs one line and answers both.
     */
    @Test
    fun theApkCarriesTheVersionInItsName() {
        val build = buildFile().readText()

        assertTrue(
            "app/build.gradle.kts no longer sets archivesName",
            build.contains("archivesName"),
        )
        assertTrue(
            "archivesName no longer takes the version from defaultConfig, " +
                "so the two can drift apart again",
            build.contains("android.defaultConfig.versionName") &&
                build.contains("android.defaultConfig.versionCode"),
        )
    }

    @Test
    fun theCodeAndTheNameStillAgree() {
        val build = buildFile().readText()

        val code = Regex("""versionCode\s*=\s*(\d+)""").find(build)
            ?.groupValues?.get(1)?.toInt()
        val name = Regex("""versionName\s*=\s*"([^"]+)"""").find(build)
            ?.groupValues?.get(1)

        requireNotNull(code) { "no versionCode in app/build.gradle.kts" }
        requireNotNull(name) { "no versionName in app/build.gradle.kts" }

        val major = name.substringBefore('.').toIntOrNull()
        val minor = name.substringAfter('.', "").toIntOrNull()
        requireNotNull(major) { "versionName is not major.minor: $name" }
        requireNotNull(minor) { "versionName is not major.minor: $name" }
        val offset = agreedOffsets[major]
        requireNotNull(offset) {
            "versionName $name begins a main version with no offset here: " +
                "add it, as the code of its .0 — one above the last release's"
        }

        assertEquals(
            "versionName $name and versionCode $code have drifted apart. " +
                "Raise both, one step each, or change the offset here on purpose.",
            offset + minor,
            code,
        )
    }

    /** Each main version starts one code above where the one before it ended, never on top of it. */
    @Test
    fun eachMainVersionStartsAboveTheOneBefore() {
        val starts = agreedOffsets.toSortedMap()
        starts.entries.zipWithNext().forEach { (before, after) ->
            assertTrue(
                "${after.key}.0 (code ${after.value}) does not come after ${before.key}.x (code ${before.value} and on)",
                after.value > before.value,
            )
        }
    }

    /**
     * The app names itself to the servers it asks — oursidecar, the map's
     * tiles — with its version, written out by hand there (16.10: both
     * still said 21.6 at 22.0). The version there is the one here.
     */
    @Test
    fun theAppNamesItsOwnVersionToServers() {
        val name = Regex("""versionName\s*=\s*"([^"]+)"""").find(buildFile().readText())!!.groupValues[1]
        val agents = mainSourceFiles()
            .flatMap { file -> Regex("""AnnasAgenda/([0-9][0-9.]*)""").findAll(file.readText()).map { file.name to it.groupValues[1] } }
            .toList()
        assertTrue("no AnnasAgenda/<version> found in the sources", agents.isNotEmpty())
        agents.forEach { (file, version) ->
            assertEquals("$file names the app as AnnasAgenda/$version", name, version)
        }
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

    /**
     * An empty password is a password.
     *
     * A key entry may have no password at all. The first version of the
     * signing block treated blank as absent for every setting alike, so such
     * a key would have read as no key configured and produced an unsigned APK
     * without a word — the failure only showing up on the phone, as a refused
     * install.
     *
     * A path and an alias are different: blank neither names a file nor names
     * a key, so blank there really is absence. The two are read through
     * differently named functions so that the distinction is visible at the
     * point it is made, and this holds the passwords to the right one.
     */
    @Test
    fun anEmptyPasswordIsNotMistakenForNoPasswordAtAll() {
        val build = buildFile().readText()

        listOf("releaseStorePassword", "releaseKeyPassword").forEach { setting ->
            val line = build.lines().firstOrNull { it.contains("""$setting"""") && it.contains("=") }

            requireNotNull(line) { "$setting is not read at all" }
            assertTrue(
                "$setting is read as though blank meant absent: $line",
                line.contains("passwordSetting("),
            )
        }

        listOf("releaseStoreFile", "releaseKeyAlias").forEach { setting ->
            val line = build.lines().firstOrNull { it.contains("""$setting"""") && it.contains("=") }

            requireNotNull(line) { "$setting is not read at all" }
            assertTrue(
                "$setting should treat blank as absent: $line",
                line.contains("requiredSetting("),
            )
        }
    }

    /**
     * CARTO's key (17.10) is read like the release key — from
     * local.properties or the environment — and never written down here: the
     * repository is public, and the key is CARTO's to the one who asked.
     */
    @Test
    fun theCartoKeyIsReadFromLocalPropertiesAndNeverWrittenDown() {
        val build = buildFile().readText()
        val line = build.lines().firstOrNull { it.contains(""""cartoKey"""") && it.contains("=") }
        requireNotNull(line) { "cartoKey is not read at all" }
        assertTrue("cartoKey is not read from the settings: $line", line.contains("requiredSetting(\"cartoKey\""))

        val written = Regex("""[?&]key=[A-Za-z0-9_-]{6,}""")
        val found = (mainSourceFiles() + buildFile()).filter { written.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("a CARTO key is written into $found", found.isEmpty())
    }

    private fun mainSourceFiles(): List<File> =
        listOf("src/main/java", "app/src/main/java", "../app/src/main/java")
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?.walkTopDown()?.filter { it.isFile && it.name.endsWith(".kt") }?.toList()
            ?: error("Cannot find src/main/java from ${File("").absolutePath}")

    private fun buildFile(): File =
        listOf("build.gradle.kts", "app/build.gradle.kts", "../app/build.gradle.kts")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?: error("Cannot find app/build.gradle.kts from ${File("").absolutePath}")
}