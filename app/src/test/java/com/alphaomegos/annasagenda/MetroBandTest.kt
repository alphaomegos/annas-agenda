package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Track run together as one line of several colours (16.10). */
class MetroBandTest {

    @Test
    fun aGroupIsItsLeaderFirstThenItsFollowersEachWithItsWay() {
        val leader = MetroTrack(1, 2)
        val bundles = mapOf(
            MetroTrack(21, 22) to MetroBundled(leader, sameWay = false),
            MetroTrack(11, 12) to MetroBundled(leader, sameWay = true),
        )

        val groups = metroBundleGroups(bundles)

        assertEquals(listOf(leader to true, MetroTrack(11, 12) to true, MetroTrack(21, 22) to false), groups.getValue(leader))
    }

    @Test
    fun theBandIsALittleWiderForEachLineMore() {
        assertEquals(10f, metroBandWidth(10f, 1), 1e-4f)
        assertEquals(13.5f, metroBandWidth(10f, 2), 1e-4f)
        assertEquals(17f, metroBandWidth(10f, 3), 1e-4f)
    }

    /** Stripes touch, fill the band exactly, and are centred on its middle. */
    @Test
    fun theStripesFillTheBandSideBySide() {
        assertEquals(listOf(-3f, 3f), metroStripeOffsets(2, 12f))
        assertEquals(listOf(-4f, 0f, 4f), metroStripeOffsets(3, 12f))
        assertEquals(listOf(0f), metroStripeOffsets(1, 12f))
    }
}
