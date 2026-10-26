package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.monthCellHeightPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The month's rows fit the screen to the pixel (0311: the folded Fold 3 lost the last week's lower edge). */
class MonthGridTest {
    @Test
    fun rowsAndGapsNeverComeToMoreThanTheScreen() {
        for (available in 300..1400 step 7) for (rows in 4..6) for (gap in listOf(15, 16, 18)) {
            val cell = monthCellHeightPx(available, gap, rows)
            assertTrue("$available/$rows/$gap", cell * rows + gap * (rows - 1) <= available)
            assertTrue("no more than a pixel a row lost", available - (cell * rows + gap * (rows - 1)) < rows)
        }
        assertEquals(0, monthCellHeightPx(10, 16, 6))
    }
}
