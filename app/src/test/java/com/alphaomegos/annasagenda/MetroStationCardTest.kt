package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** A station's card on the map (15.10): its line and where a walk takes one. */
class MetroStationCardTest {

    private val red = MetroLine(id = 1, label = "1", name = "Красная", color = 0xFFE42313, trunk = listOf(1, 2))
    private val green = MetroLine(id = 2, label = "2", name = "Зелёная", color = 0xFF4FB04F, trunk = listOf(11, 12))
    private val ring = MetroLine(id = 3, label = "5", name = "Кольцевая", color = 0xFF915133, ring = true, trunk = listOf(21, 22, 23))

    private val scheme = MetroScheme(
        id = 1,
        city = "Тест",
        // The ring listed first: the walks follow the scheme's own order of lines.
        lines = listOf(ring, red, green),
        stations = listOf(
            MetroStation(1, 1, "Курская"), MetroStation(2, 1, "Бауманская"),
            MetroStation(11, 2, "Чкаловская"), MetroStation(12, 2, "Римская"),
            MetroStation(21, 3, "Курская"), MetroStation(22, 3, "Таганская"), MetroStation(23, 3, "Комсомольская"),
        ),
        transfers = listOf(
            MetroTransfer(1, 1, 11),
            MetroTransfer(2, 21, 1, closure = MetroClosure(null)),
            // The same walk written twice is one walk.
            MetroTransfer(3, 11, 1),
        ),
    )

    @Test
    fun theCardNamesTheLineAndTheWalksInTheSchemesOrderOfLines() {
        val facts = metroStationFacts(scheme, 1)!!

        assertEquals("Курская", facts.station.name)
        assertEquals("Красная", facts.line?.name)
        assertEquals(listOf(21L, 11L), facts.walks.map { it.station.id })
        assertTrue(facts.walks.first().closed)
        assertEquals("Кольцевая", facts.walks.first().line?.name)
    }

    @Test
    fun aStationWithNoWalksHasAnEmptyListAndNoStationNoCard() {
        assertTrue(metroStationFacts(scheme, 22)!!.walks.isEmpty())
        assertNull(metroStationFacts(scheme, 99))
    }
}
