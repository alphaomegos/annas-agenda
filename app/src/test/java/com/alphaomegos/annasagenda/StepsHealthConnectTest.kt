package com.alphaomegos.annasagenda

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app and Health Connect (0295, 19.10): what the manifest promises it, and
 * the two rules the reader keeps — which no test on this JVM can run, so they
 * are held here in the source.
 */
class StepsHealthConnectTest {

    private val manifest by lazy { mainResRoot().parentFile.resolve("AndroidManifest.xml").readText() }
    private val reader by lazy { mainSourceFiles().single { it.name == "HealthConnectSteps.kt" }.readText() }

    @Test
    fun theAppAsksToReadStepsAndNothingElse() {
        assertTrue(manifest.hasCode("android.permission.health.READ_STEPS"))
        val health = Regex("""android\.permission\.health\.[A-Z_]+""").findAll(manifest).map { it.value }.toSet()
        assertTrue("only steps, only read: $health", health == setOf("android.permission.health.READ_STEPS"))
    }

    @Test
    fun healthConnectCanAlwaysShowWhyTheAppReads() {
        // Android 13 and older…
        assertTrue(manifest.hasCode("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"))
        // …and Android 14 on, behind the permission only the system holds.
        assertTrue(manifest.hasCode("android.intent.action.VIEW_PERMISSION_USAGE"))
        assertTrue(manifest.hasCode("android.intent.category.HEALTH_PERMISSIONS"))
        assertTrue(manifest.hasCode("android.permission.START_VIEW_PERMISSION_USAGE"))
        assertTrue(manifest.hasCode(".StepsPrivacyActivity"))
        assertTrue("Health Connect as an app must be visible to ask about it", manifest.hasCode("com.google.android.apps.healthdata"))
    }

    /** Each step once (0377): the records read and each minute counted once; Health Connect's total only as the fallback. */
    @Test
    fun theReaderCountsEachStepOnce() {
        // 0377: Health Connect's total counted the phone and the watch of one walk twice; the
        // records are read and each minute counted once — never simply summed.
        assertTrue(reader.contains("readRecords("))
        assertTrue(reader.contains("stepsByDayOnce(chunks, zone)"))
        assertFalse("records summed by hand count twice", reader.contains("sumOf { it.count }"))
        // The daily total stays as the fallback.
        assertTrue(reader.contains("aggregateGroupByPeriod(") && reader.contains("StepsRecord.COUNT_TOTAL"))
    }

    /** The library wants Android 8 and the app runs from 7: nothing may reach it before the check. */
    @Test
    fun nothingReachesHealthConnectBelowAndroid9() {
        assertTrue(manifest.hasCode("""tools:overrideLibrary="androidx.health.connect.client""""))
        assertTrue(reader.contains("Build.VERSION.SDK_INT < Build.VERSION_CODES.P"))
        val elsewhere = mainSourceFiles()
            .filter { it.name != "HealthConnectSteps.kt" && it.readText().contains("import androidx.health.connect") }
            .map { it.name }
        assertTrue("only HealthConnectSteps.kt may touch the library: $elsewhere", elsewhere.isEmpty())
    }
}
