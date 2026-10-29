package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Charts slid by hand (0348): day by day, staying where let go, never past today. */
class ChartPanTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val month = DateWindow(LocalDate.of(2026, 9, 30), today)

    @Test
    fun slidBackAnyWay_forwardNoFurtherThanToday() {
        assertEquals(DateWindow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 19)), anthropometryPanned(month, -10, today))
        assertEquals(month, anthropometryPanned(month, 5, today))
        assertEquals(0, anthropometryPanClamp(month, 5, today))
        val older = DateWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        assertEquals(59, anthropometryPanClamp(older, 100, today))
        assertEquals(30, month.days())
    }
}
