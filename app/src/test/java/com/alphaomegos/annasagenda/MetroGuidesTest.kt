package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.alphaomegos.annasagenda.support.*

/** Guides while dragging (08.10). */
class MetroGuidesTest {

    private val others = listOf(MetroPoint(0f, 0f), MetroPoint(3f, 2f))

    @Test
    fun nearlyLevel_landsLevel_andNearlyUnder_landsUnder() {
        val g = metroGuided(MetroPoint(2.95f, 0.07f), others, 0.1f)
        assertEquals(MetroPoint(3f, 0f), g.point)
        assertEquals(3f, g.x)
        assertEquals(0f, g.y)
    }

    @Test
    fun farFromEveryone_staysWhereDropped() {
        val g = metroGuided(MetroPoint(1.5f, 1f), others, 0.1f)
        assertEquals(MetroPoint(1.5f, 1f), g.point)
        assertNull(g.x); assertNull(g.y)
    }
}
