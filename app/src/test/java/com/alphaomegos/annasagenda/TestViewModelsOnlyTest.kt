package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM tests make their AppViewModel through TestViewModels and nowhere else.
 *
 * One built with its bare constructor is never cleared, and its debounced
 * loops go on firing into the next test's Dispatchers.setMain (0270, 17.10).
 * The failure lands on whichever test runs next, so it never points back at
 * the test that caused it — which is why this is checked here and not left to
 * be noticed.
 *
 * Instrumented tests (src/androidTest) are not included: they run on a device,
 * where Main is the real main looper and nobody swaps it.
 */
class TestViewModelsOnlyTest {

    private val bareConstructor = Regex("""\bAppViewModel\(""")

    @Test
    fun noJvmTestBuildsAnAppViewModelItself() {
        val roots = listOf("src/sharedTest/java", "src/test/java", "app/src/sharedTest/java", "app/src/test/java")
            .map(::File).filter { it.isDirectory }
        assertTrue("no test sources found from ${File("").absolutePath}", roots.size >= 2)

        val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList() }
        assertTrue("TestViewModels.kt is gone", files.any { it.name == "TestViewModels.kt" })

        val strays = files
            .filter { it.name != "TestViewModels.kt" && it.name != "TestViewModelsOnlyTest.kt" }
            .filter { bareConstructor.containsMatchIn(it.readText()) }
            .map { it.name }
        assertTrue("these build AppViewModel themselves; use TestViewModels: $strays", strays.isEmpty())
    }
}
