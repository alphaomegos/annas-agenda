package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.millisUntilTomorrow
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Today turns at midnight (0398): the wait until then, a second over. */
class TodayTest {
    private val day = LocalDate.of(2026, 11, 5)

    @Test
    fun theWaitEndsJustAfterMidnight() {
        assertEquals(30_000L + 1_000L, millisUntilTomorrow(LocalDateTime.of(2026, 11, 5, 23, 59, 30), day))
        assertEquals(12 * 3_600_000L + 1_000L, millisUntilTomorrow(LocalDateTime.of(2026, 11, 5, 12, 0), day))
        // Over already — the phone slept through midnight: no wait but the second.
        assertEquals(1_000L, millisUntilTomorrow(LocalDateTime.of(2026, 11, 6, 7, 0), day))
    }
}
