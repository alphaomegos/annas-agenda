package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test
import com.alphaomegos.annasagenda.model.*

/** The metro section's style (15.10). */
class MetroStyleTest {

    @Test
    fun aStoredStyle_readsBack_andOneThisBuildDoesNotKnow_isTodays() {
        MetroStyle.entries.forEach { assertEquals(it, parseMetroStyle(it.name)) }
        assertEquals(MetroStyle.MODERN, parseMetroStyle("AMIGA"))
        assertEquals(MetroStyle.MODERN, parseMetroStyle(null))
        assertEquals(MetroStyle.MODERN, MetroPrefs().style)
    }
}
