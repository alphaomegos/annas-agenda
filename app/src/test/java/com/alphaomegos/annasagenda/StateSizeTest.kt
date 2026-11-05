package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.data.logLine
import com.alphaomegos.annasagenda.data.stateSizeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stored state's size for the log (0404): the heaviest parts first, sizes only. */
class StateSizeTest {
    private val raw = """{"v":16,"tasks":[{"id":1,"title":"aaaaaaaaaa"}],"foodLog":[],"note":"Привет"}"""

    @Test
    fun theHeaviestPartsComeFirst() {
        val size = stateSizeOf(raw, top = 2)
        assertEquals(raw.toByteArray(Charsets.UTF_8).size, size.total)
        // The tasks' 31 bytes, then the note's 14 — Cyrillic is two bytes a letter.
        assertEquals(listOf("tasks" to 31, "note" to 14), size.largest)
    }

    @Test
    fun theLineSaysSizesNotContent() {
        val line = stateSizeOf(raw).logLine()
        assertTrue(line, line.startsWith("state ") && line.contains("tasks 31 B") && line.contains("note 14 B"))
        assertTrue(line, !line.contains("aaaa") && !line.contains("Привет"))
    }
}
