package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The library that ships is read without a single problem.
 *
 * The parser skips a bad row on the phone rather than empty the shelves;
 * this is where a bad row is caught instead, before it ships. Read from the
 * source tree, like the other guards.
 */
class FoodLibraryFileTest {

    private fun libraryText(): String {
        val candidates = listOf("src/main/assets/food_library.tsv", "app/src/main/assets/food_library.tsv")
        val file = candidates.map(::File).firstOrNull { it.isFile }
            ?: error("food_library.tsv not found from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun theShippedLibraryHasNoProblems() {
        val parsed = parseFoodLibrary(libraryText())

        assertEquals(emptyList<String>(), parsed.problems)
        assertTrue("the library is empty", parsed.foods.isNotEmpty())
    }

    /** Russian and Serbian are the app's other languages; a gap there shows up as English. */
    @Test
    fun everyFoodIsNamedInEveryLanguageTheAppSpeaks() {
        val missing = parseFoodLibrary(libraryText()).foods.flatMap { food ->
            listOf("ru", "sr-Latn").filter { it !in food.names }.map { "${food.id}: $it" }
        }

        assertEquals(emptyList<String>(), missing)
    }
}
