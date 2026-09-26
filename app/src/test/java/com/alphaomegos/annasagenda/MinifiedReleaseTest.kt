package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The decisions the release build depends on are still in force.
 *
 * This does not test R8. R8 runs on the release variant and `./gradlew test`
 * builds the debug one, so nothing here can tell whether minification is
 * correct — only whether it is still switched on, still pointed at this app's
 * rules, and whether those rules still say the two things the app cannot
 * survive losing.
 *
 * That is a narrow claim, and it is the one worth making. Every failure this
 * guards against is silent: a flag flipped back while chasing something else,
 * a rules file dropped from `proguardFiles` in a merge, a keep rule narrowed
 * by someone tidying. None of them breaks a build. The first two show up as a
 * bigger APK that still works, and the third shows up as a year of somebody's
 * data quietly decoding into defaults on the next launch.
 *
 * Reading the build file as text is crude, and it is also the only way this
 * project can ask the question at all: a unit test cannot see the release
 * variant's configuration.
 */
class MinifiedReleaseTest {

    private val buildFile: String by lazy { fileNamed("app/build.gradle.kts").readText() }
    private val keepRules: String by lazy { fileNamed("app/proguard-rules.pro").readText() }

    @Test
    fun theReleaseBuildIsStillMinified() {
        assertTrue(
            "isMinifyEnabled is not true in app/build.gradle.kts",
            Regex("""isMinifyEnabled\s*=\s*true""").containsMatchIn(buildFile),
        )
    }

    @Test
    fun theReleaseBuildStillReadsThisAppsRules() {
        assertTrue(
            "app/build.gradle.kts no longer names proguard-rules.pro",
            buildFile.contains("\"proguard-rules.pro\""),
        )
    }

    /**
     * The one that matters most, and the one whose failure is invisible.
     *
     * Every enum that reaches the stored state is written as its name and read
     * back with valueOf. R8 may rename enum constants; it usually notices
     * valueOf and stops, but "usually" is a statement about a version. If it
     * does rename them, the app writes names nothing will read, the next
     * launch decodes everything it does not recognise into defaults, and
     * nothing crashes and nothing is reported.
     */
    @Test
    fun enumNamesAreStillKept() {
        assertTrue(
            "the keep rule for enum constants is gone from proguard-rules.pro",
            Regex("""-keepclassmembers\s+enum\s+com\.alphaomegos\.annasagenda""")
                .containsMatchIn(keepRules),
        )
        assertTrue(
            "the enum keep rule no longer keeps the constants themselves",
            keepRules.contains("<fields>"),
        )
    }

    @Test
    fun theSerializerLookupIsStillKept() {
        assertTrue(
            "the kotlinx.serialization rules are gone from proguard-rules.pro",
            keepRules.contains("@kotlinx.serialization.Serializable"),
        )
    }

    /**
     * Without these a crash report is a screenshot of nothing. This app has no
     * crash reporting: what arrives is a photo of whatever the phone showed.
     */
    @Test
    fun aStackTraceIsStillWorthSending() {
        assertTrue(
            "line numbers are no longer kept",
            keepRules.contains("-keepattributes SourceFile,LineNumberTable"),
        )
    }

    private fun fileNamed(path: String): File =
        listOf(path, "../$path", path.removePrefix("app/"))
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?: error("Cannot find $path from ${File("").absolutePath}")
}
