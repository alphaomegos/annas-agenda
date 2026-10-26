package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*
import java.time.LocalDate

/** The day counter in the store (0319): its start and its correction kept, by its kind. */
class DayTimerStoreTest {
    @Test
    fun aDayCounterComesBackAsKept() {
        val timer = DayTimerCounter(7, "Ногти", LocalDate.of(2026, 8, 17), correction = -3)
        val json = appStateStoreJson.encodeToString(AppState(counters = listOf(timer)).toDto())
        assertEquals(listOf(timer), appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain().counters)
    }
}
