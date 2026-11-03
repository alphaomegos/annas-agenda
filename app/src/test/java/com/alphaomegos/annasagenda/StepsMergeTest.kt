package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.StepChunk
import com.alphaomegos.annasagenda.support.stepsByDayOnce
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Steps counted once (0377): the phone and the watch on one walk are one walk. */
class StepsMergeTest {

    private val zone: ZoneId = ZoneOffset.ofHours(3)
    private val day = LocalDate.of(2026, 11, 2)
    private fun at(h: Int, m: Int = 0): Long = LocalDateTime.of(day, java.time.LocalTime.of(h, m)).toEpochSecond(zone as ZoneOffset)
    private val shealth = "com.sec.android.app.shealth"

    @Test
    fun aWalkWithThePhoneAndTheWatchCountsOnce() {
        val chunks = listOf(
            StepChunk(shealth, "phone", at(10), at(10, 30), 3000),
            StepChunk(shealth, "watch", at(10), at(10, 30), 3300),
        )
        assertEquals(mapOf(day to 3300), stepsByDayOnce(chunks, zone))
    }

    @Test
    fun theWatchAloneAndThePhoneAloneAddUp() {
        val chunks = listOf(
            StepChunk(shealth, "watch", at(8), at(8, 30), 2000),      // the phone on the table
            StepChunk(shealth, "phone", at(18), at(18, 30), 1500),    // the watch charging
            StepChunk(shealth, "watch", at(12), at(12, 10), 800),
            StepChunk(shealth, "phone", at(12), at(12, 10), 700),
        )
        assertEquals(mapOf(day to 2000 + 1500 + 800), stepsByDayOnce(chunks, zone))
    }

    @Test
    fun twoAppsCountingTheSameDayAreTwoOpinions() {
        val chunks = listOf(
            StepChunk(shealth, "watch", at(9), at(11), 9000),
            StepChunk("android", "phone", at(9), at(11), 7000),
        )
        assertEquals(mapOf(day to 9000), stepsByDayOnce(chunks, zone))
    }

    @Test
    fun aRecordAcrossMidnightIsShared() {
        val chunks = listOf(StepChunk(shealth, "phone", at(23, 30), at(23, 30) + 3600, 600))
        assertEquals(mapOf(day to 300, day.plusDays(1) to 300), stepsByDayOnce(chunks, zone))
    }

    @Test
    fun oneSourceIsLeftAsItIs_andNothingIsNothing() {
        val chunks = listOf(
            StepChunk(shealth, "phone", at(7), at(7, 1), 90),
            StepChunk(shealth, "phone", at(7, 1), at(7, 2), 85),
            StepChunk(shealth, "phone", at(7, 5), at(7, 5), 10),      // an instant
            StepChunk(shealth, "phone", at(7, 6), at(7, 7), 0),
        )
        assertEquals(mapOf(day to 185), stepsByDayOnce(chunks, zone))
        assertEquals(emptyMap<LocalDate, Int>(), stepsByDayOnce(emptyList(), zone))
    }
}
