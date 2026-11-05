package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coroutine code lets cancellations through (0397, the internals review):
 * no runCatching straight inside withContext, launch or async — it would
 * catch the cancellation with the failures — and the cancellable one in use.
 */
class CancellableGuardTest {
    @Test
    fun coroutineCodeLetsCancellationsThrough() {
        val sources = mainSourceFiles().filter { it.extension == "kt" }.map { it.name to it.readText() }
        val direct = Regex("""(withContext\([^)]*\)|launch(\([^)]*\))?|async(\([^)]*\))?) \{\s*runCatching \{""")
        sources.forEach { (name, text) -> assertTrue("$name catches a cancellation", !direct.containsMatchIn(text)) }
        assertTrue(sources.sumOf { (_, text) -> Regex("""runCatchingCancellable \{""").findAll(text).count() } >= 30)
    }
}
