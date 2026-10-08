package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Undo and redo in the metro editor (08.10). */
class MetroHistoryTest {

    private fun s(city: String) = MetroScheme(id = 1, city = city)

    @Test
    fun stepsBackAndForward() {
        var h = MetroHistory().recorded(s("A")).recorded(s("B"))
        var cur = s("C")
        h.undone(cur)!!.let { (prev, next) -> cur = prev; h = next }
        assertEquals("B", cur.city)
        h.undone(cur)!!.let { (prev, next) -> cur = prev; h = next }
        assertEquals("A", cur.city)
        assertNull(h.undone(cur))
        h.redone(cur)!!.let { (n, next) -> cur = n; h = next }
        assertEquals("B", cur.city)
        assertTrue(h.canUndo && h.canRedo)
    }

    @Test
    fun aNewEditForgetsTheRedo() {
        val h = MetroHistory().recorded(s("A"))
        val (back, afterUndo) = h.undone(s("B"))!!
        assertTrue(afterUndo.canRedo)
        assertTrue(!afterUndo.recorded(back).canRedo)
    }

    @Test
    fun keepsAtMostTheLimit() {
        var h = MetroHistory()
        repeat(METRO_HISTORY_LIMIT + 20) { h = h.recorded(s("$it")) }
        assertEquals(METRO_HISTORY_LIMIT, h.undo.size)
        assertEquals("20", h.undo.first().city)
    }
}
