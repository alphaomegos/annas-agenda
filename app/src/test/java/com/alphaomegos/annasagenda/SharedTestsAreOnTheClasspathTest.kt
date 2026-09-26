package com.alphaomegos.annasagenda

import org.junit.Assert.fail
import org.junit.Test

/**
 * The tests in src/sharedTest are actually in this run.
 *
 * That folder is wired into two test source sets by hand, and a mis-wiring
 * does not fail — it removes tests. Patch 0087 used `java.srcDir`, which the
 * build accepted with a deprecation warning and then ignored for Kotlin; for
 * four patches every test written there was compiled by nothing, run by
 * nothing, and reported by nothing. The suite stayed green the whole time,
 * because a test that does not exist cannot fail.
 *
 * So the wiring gets a test of its own, and it lives in src/test — the folder
 * that is wired by the plugin rather than by us. It names the classes rather
 * than counting them: a count would have to be updated on every patch and
 * would be updated wrongly on the one that matters.
 *
 * Adding a class to src/sharedTest means adding its name here. That is the
 * whole cost, and it buys a loud failure instead of a silent absence.
 */
class SharedTestsAreOnTheClasspathTest {

    private val sharedTestClasses = listOf(
        "com.alphaomegos.annasagenda.AppStateStoreContractTest",
        "com.alphaomegos.annasagenda.AppStateStoreFailuresTest",
        "com.alphaomegos.annasagenda.components.ConfirmDialogTest",
        "com.alphaomegos.annasagenda.screens.media.MediaDetailsFormTest",
    )

    @Test
    fun everyTestWrittenInSharedTestIsCompiledIntoThisRun() {
        val missing = sharedTestClasses.filter { name ->
            runCatching { Class.forName(name) }.isFailure
        }

        if (missing.isNotEmpty()) {
            fail(
                "src/sharedTest is not on this run's classpath, so these were " +
                    "never run: $missing. Check the sourceSets block in " +
                    "app/build.gradle.kts — it must use kotlin.srcDir.",
            )
        }
    }
}
