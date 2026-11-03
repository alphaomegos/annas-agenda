package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.progressPercent
import com.alphaomegos.annasagenda.support.progressWorthTelling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A sending's progress in percent (0380): the backup's parts, a track's bytes. */
class ProgressSupportTest {

    @Test
    fun wholePercentNeverPastTheEnd() {
        assertEquals(0, progressPercent(0, 0))
        assertEquals(0, progressPercent(5, 0))
        assertEquals(45, progressPercent(45, 100))
        assertEquals(33, progressPercent(1, 3))
        assertEquals(100, progressPercent(3, 3))
        assertEquals(100, progressPercent(7, 3))
        assertEquals(0, progressPercent(-2, 3))
        assertEquals(50, progressPercent(26_214_400, 52_428_800))
    }

    @Test
    fun toldEveryFivePercentAndAtTheEnd() {
        assertFalse(progressWorthTelling(4, 0))
        assertTrue(progressWorthTelling(5, 0))
        assertFalse(progressWorthTelling(98, 95))
        assertTrue(progressWorthTelling(100, 98))
        assertFalse(progressWorthTelling(100, 100))
    }
}
