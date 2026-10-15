package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** The routes looked up lately (15.10). */
class MetroRecentTest {

    private val moscow = "library:moscow"
    private val mine = "user:3"

    private fun remember(list: List<MetroRecentRoute>, from: Long, to: Long, at: Long, key: String = moscow) =
        metroRecentRemembered(list, key, from, to, at)

    @Test
    fun aRouteLookedUpAgainMovesToTheFrontOnceAndStaysPinned() {
        var list = remember(emptyList(), 1, 2, 10)
        list = remember(list, 3, 4, 20)
        list = metroRecentPinned(list, moscow, 1, 2, pinned = true)
        list = remember(list, 1, 2, 30)

        assertEquals(listOf(1L to 2L, 3L to 4L), list.map { it.fromStationId to it.toStationId })
        assertTrue(list.first().pinned)
        assertEquals(30L, list.first().atEpochMillis)
    }

    @Test
    fun theOtherWayRoundIsARouteOfItsOwn() {
        val list = remember(remember(emptyList(), 1, 2, 10), 2, 1, 20)

        assertEquals(2, list.size)
    }

    @Test
    fun onlyTheNewestUnpinnedAreKeptAndEachSchemeCountsAlone() {
        var list = remember(emptyList(), 100, 101, 1)
        list = metroRecentPinned(list, moscow, 100, 101, pinned = true)
        list = remember(list, 7, 8, 2, key = mine)
        (1..10).forEach { list = remember(list, it.toLong(), it + 50L, 10L + it) }

        val ofMoscow = metroRecentOf(list, moscow)
        assertEquals(METRO_RECENT_KEPT + 1, ofMoscow.size)
        assertEquals(100L, ofMoscow.first().fromStationId)
        assertEquals(10L, ofMoscow[1].fromStationId)
        assertEquals(1, metroRecentOf(list, mine).size)
    }

    @Test
    fun clearingKeepsThePinnedAndASchemeGoneTakesEverything() {
        var list = remember(remember(emptyList(), 1, 2, 10), 3, 4, 20)
        list = metroRecentPinned(list, moscow, 1, 2, pinned = true)
        list = remember(list, 5, 6, 30, key = mine)

        assertEquals(listOf(1L), metroRecentOf(metroRecentCleared(list, moscow), moscow).map { it.fromStationId })
        assertTrue(metroRecentOf(metroRecentWithout(list, mine), mine).isEmpty())
        assertEquals(2, metroRecentOf(metroRecentWithout(list, mine), moscow).size)
    }

    @Test
    fun aRoutesEndsAreItsFirstAndLastStation() {
        val route = MetroRoute(
            steps = listOf(
                MetroRouteStep.Ride(1, listOf(1, 2, 3), emptyList(), 6, emptyList()),
                MetroRouteStep.Change(3, 12, 5),
                MetroRouteStep.Ride(2, listOf(12, 13), emptyList(), 3, emptyList()),
            ),
            minutes = 14,
            transfers = 1,
        )

        assertEquals(1L to 13L, metroRouteEnds(route))
        assertNull(metroRouteEnds(MetroRoute(emptyList(), 0, 0)))
    }

    @Test
    fun theKeyIsTheSelectionText() {
        val scheme = MetroScheme(id = 3, city = "Тест")
        assertEquals(mine, metroRecentKey(MetroShown(scheme, null)))
        assertEquals(moscow, metroRecentKey(MetroShown(scheme, "moscow")))
    }
}
