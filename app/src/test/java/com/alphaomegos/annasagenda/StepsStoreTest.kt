package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*
import java.time.LocalDate

/** The step days in the store (0294): kept as they are, and nothing typed in is ever mistaken for a read. */
class StepsStoreTest {

    private val day = LocalDate.of(2026, 10, 19)

    private fun roundTrip(state: AppState): AppState =
        appStateStoreJson.decodeFromString<AppStateDto>(appStateStoreJson.encodeToString(state.toDto())).toDomain()

    @Test
    fun theDaysAndTheHeightComeBackAsKept() {
        val state = AppState(
            stepDays = listOf(
                StepDay(day.minusDays(1), 12_345, StepSource.HEALTH_CONNECT),
                StepDay(day, 800, StepSource.MANUAL),
            ),
            stepsHeightCm = 168,
            stepsRange = AnthropometryRange.QUARTER,
        )
        val back = roundTrip(state)
        assertEquals(state.stepDays, back.stepDays)
        assertEquals(168, back.stepsHeightCm)
        assertEquals(AnthropometryRange.QUARTER, back.stepsRange)
    }

    @Test
    fun aSourceThisVersionDoesNotKnowIsTreatedAsTypedIn() {
        val json = """{"v":$CURRENT_SCHEMA_VERSION,"stepDays":[{"dateEpochDay":${day.toEpochDay()},"steps":500,"source":"GARMIN_IMPORT"}]}"""
        val back = appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain()
        assertEquals(listOf(StepDay(day, 500, StepSource.MANUAL)), back.stepDays)
    }

    @Test
    fun anImpossibleHeightIsNotTaken() {
        assertNull(roundTrip(AppState(stepsHeightCm = 20)).stepsHeightCm)
        assertEquals(emptyList<StepDay>(), roundTrip(AppState()).stepDays)
    }

    /** The month shows steps unless turned off — on for a phone updating too, whose saved state has no such field. */
    @Test
    fun theMonthShowsStepsUnlessTurnedOff() {
        val json = """{"v":$CURRENT_SCHEMA_VERSION}"""
        assertEquals(true, appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain().calendarShowSteps)
        assertEquals(false, roundTrip(AppState(calendarShowSteps = false)).calendarShowSteps)
    }
}
