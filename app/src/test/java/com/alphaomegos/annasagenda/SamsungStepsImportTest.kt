package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A Samsung Health step_daily_trend export (0299). The rows below are made
 * up, laid out as Eduard's export of 25.10.2026 lays them out: a byte-order
 * mark and a line naming the table, the header, a row per day and source, a
 * comma at the end of each. No real export goes into the repository.
 */
class SamsungStepsImportTest {

    private val header = "binning_data,update_time,create_time,source_pkg_name,source_type,count,speed,distance,calorie,deviceuuid,pkg_name,datauuid,day_time"
    private fun row(source: String, count: Int, day: String) =
        "b.binning_data.json,2026-10-24 21:00:05.287,2026-10-24 21:00:05.287,com.sec.android.app.shealth,$source,$count,0.0,0.0,0.0,dev,com.sec.android.app.shealth,uuid,$day 00:00:00.000,"

    private val export = listOf(
        "\uFEFFcom.samsung.shealth.step_daily_trend,7010053,6",
        header,
        row("0", 16_039, "2026-10-24"),
        row("-2", 19_002, "2026-10-24"),
        row("24", 18_110, "2026-10-24"),
        row("0", 2_436, "2015-07-05"),
        row("-2", 2_436, "2015-07-05"),
        // A day with parts only: the largest, never their sum.
        row("0", 5_000, "2020-01-01"),
        row("24", 6_000, "2020-01-01"),
        "",
    ).joinToString("\r\n")

    @Test
    fun eachDayIsTheAllDevicesTotal_theAppsOwnNumber() {
        val days = parseSamsungStepDailyTrend(export)
        assertEquals(19_002, days[LocalDate.of(2026, 10, 24)])
        assertEquals(2_436, days[LocalDate.of(2015, 7, 5)])
        assertEquals(6_000, days[LocalDate.of(2020, 1, 1)])
        assertEquals(3, days.size)
    }

    @Test
    fun anOlderExportsDaysInMillisecondsAndPrefixedColumnsAreReadToo() {
        val millis = LocalDate.of(2019, 3, 2).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val text = "com.samsung.shealth.step_daily_trend.source_type,com.samsung.shealth.step_daily_trend.count,com.samsung.shealth.step_daily_trend.day_time\n-2,7777,$millis\n"
        assertEquals(mapOf(LocalDate.of(2019, 3, 2) to 7_777), parseSamsungStepDailyTrend(text))
    }

    @Test
    fun anotherFileIsNoExport() {
        assertEquals(emptyMap<LocalDate, Int>(), parseSamsungStepDailyTrend("name,weight\nAnna,60\n"))
        assertEquals(emptyMap<LocalDate, Int>(), parseSamsungStepDailyTrend(""))
    }

    @Test
    fun anImportFillsEmptyDaysRenewsItsOwnAndLeavesTheRest() {
        val d = LocalDate.of(2026, 10, 20)
        val kept = listOf(
            StepDay(d, 900, StepSource.HEALTH_CONNECT),
            StepDay(d.plusDays(1), 4_000, StepSource.MANUAL),
            StepDay(d.plusDays(2), 100, StepSource.SAMSUNG_IMPORT),
        )
        val outcome = stepDaysAfterImport(kept, mapOf(d to 1_000, d.plusDays(1) to 5_000, d.plusDays(2) to 200, d.plusDays(3) to 300, d.plusDays(4) to 0))
        assertEquals(
            listOf(
                StepDay(d, 900, StepSource.HEALTH_CONNECT),
                StepDay(d.plusDays(1), 4_000, StepSource.MANUAL),
                StepDay(d.plusDays(2), 200, StepSource.SAMSUNG_IMPORT),
                StepDay(d.plusDays(3), 300, StepSource.SAMSUNG_IMPORT),
            ),
            outcome.days,
        )
        assertEquals(4, outcome.inFile)
        assertEquals(1, outcome.added)
        assertEquals(1, outcome.updated)
        assertEquals(2, outcome.leftAlone)
        assertEquals(d, outcome.first)
        assertEquals(d.plusDays(3), outcome.last)
    }
}
