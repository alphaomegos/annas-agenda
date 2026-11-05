package com.alphaomegos.annasagenda

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The section's names (05.11, Eduard): «В бегах» is «Спорт» now — running is
 * one of its sports, with other workouts beside it — and «Между марафонами»,
 * the runs written down after, is «Бег».
 */
class SportNamesTest {
    @Test
    fun theSectionIsCalledSport() {
        assertEquals("Sport", string("values", "running_title"))
        assertEquals("Спорт", string("values-ru", "running_title"))
        assertEquals("Sport", string("values-b+sr+Latn", "running_title"))
    }

    @Test
    fun theRunsWrittenDownAreCalledRunning() {
        assertEquals("Running", string("values", "running_mode_between"))
        assertEquals("Бег", string("values-ru", "running_mode_between"))
        assertEquals("Trčanje", string("values-b+sr+Latn", "running_mode_between"))
    }

    /**
     * Gilbertese had the title in English, «Running»: dropped, so it falls back to
     * the English «Sport» rather than keep a name the section no longer has.
     */
    @Test
    fun noLanguageKeepsTheOldNames() {
        assertEquals(null, string("values-b+gil", "running_title"))
        val old = listOf("On the run", "Between marathons", "В бегах", "Между марафонами", "U trku", "Između maratona")
        dirs().forEach { dir ->
            stringFiles(dir).forEach { file ->
                val text = file.readText()
                old.forEach { name -> assertTrue("${dir.name}/${file.name} still says «$name»", !text.contains(">$name<")) }
            }
        }
    }

    private fun dirs(): List<File> =
        mainResRoot().listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.toList()

    private fun stringFiles(dir: File): List<File> =
        dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.toList()

    private fun string(dir: String, name: String): String? =
        stringFiles(File(mainResRoot(), dir)).firstNotNullOfOrNull { file ->
            Regex("<string name=\"" + name + "\">([^<]*)</string>").find(file.readText())?.groupValues?.get(1)
        }
}
