package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Weight from a Samsung Health export (0332): made-up rows laid out as the steps export is. */
class SamsungWeightImportTest {
    private val text = listOf(
        "\uFEFFcom.samsung.health.weight,6305000,8",
        "vfa_level,com.samsung.health.weight.start_time,body_fat,com.samsung.health.weight.time_offset,weight,height",
        ",2026-10-01 06:10:00.000,,UTC+0300,76.4,180,",
        ",2026-10-01 18:30:00.000,,UTC+0300,76.9,180,",
        // 22:30 UTC is 01:30 the next day at UTC+3.
        ",2026-10-02 22:30:00.000,,UTC+0300,75.8,180,",
        ",garbage,,UTC+0300,,180,",
        "",
    ).joinToString("\r\n")

    @Test
    fun eachDaysLastWeighingByLocalTime() {
        val read = parseSamsungWeight(text)
        assertEquals(mapOf(LocalDate.of(2026, 10, 1) to 76.9, LocalDate.of(2026, 10, 3) to 75.8), read)
        assertEquals(emptyMap<LocalDate, Double>(), parseSamsungWeight("date,steps\n2026-10-01,5\n"))
    }

    @Test
    fun theUserChoosesOnDaysBothWeighed() {
        val d1 = LocalDate.of(2026, 10, 1)
        val d3 = LocalDate.of(2026, 10, 3)
        val app = listOf(AnthropometryEntry(date = d1, weightKg = 77.0, waistCm = 80.0))
        val read = mapOf(d1 to 76.9, d3 to 75.8)
        assertEquals(1, weightImportConflicts(app, read))
        val keep = anthropometryAfterWeightImport(app, read, preferImported = false)
        assertEquals(77.0, keep.entries.single { it.date == d1 }.weightKg!!, 0.0)
        assertEquals(listOf(1, 0, 1), listOf(keep.added, keep.replaced, keep.kept))
        val take = anthropometryAfterWeightImport(app, read, preferImported = true)
        assertEquals(76.9, take.entries.single { it.date == d1 }.weightKg!!, 0.0)
        // The day's other measurements stay.
        assertEquals(80.0, take.entries.single { it.date == d1 }.waistCm!!, 0.0)
        assertEquals(listOf(1, 1, 0), listOf(take.added, take.replaced, take.kept))
    }
}
