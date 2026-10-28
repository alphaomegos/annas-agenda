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

    /** Bars unless a line was chosen (0302); a style this version does not know is bars. */
    @Test
    fun theChartIsBarsUnlessALineWasChosen() {
        assertEquals(StepsChartStyle.BARS, AppState().stepsChartStyle)
        assertEquals(StepsChartStyle.LINE, roundTrip(AppState(stepsChartStyle = StepsChartStyle.LINE)).stepsChartStyle)
        val json = """{"v":$CURRENT_SCHEMA_VERSION,"stepsChartStyle":"PIE"}"""
        assertEquals(StepsChartStyle.BARS, appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain().stepsChartStyle)
    }

    /** The widget's options (0310): yesterday, the record and 10 000 unless chosen otherwise; a goal out of range is brought in. */
    @Test
    fun theWidgetsOptions() {
        val fresh = AppState()
        assertEquals(StepsWidgetLine.YESTERDAY, fresh.stepsWidgetLine)
        assertEquals(true, fresh.stepsWidgetRecord)
        assertEquals(10_000, fresh.stepsGoal)
        val back = roundTrip(AppState(stepsWidgetLine = StepsWidgetLine.GOAL_BAR, stepsWidgetRecord = false, stepsGoal = 12_000))
        assertEquals(StepsWidgetLine.GOAL_BAR, back.stepsWidgetLine)
        assertEquals(false, back.stepsWidgetRecord)
        assertEquals(12_000, back.stepsGoal)
        val json = """{"v":$CURRENT_SCHEMA_VERSION,"stepsWidgetLine":"RING","stepsGoal":5}"""
        val odd = appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain()
        assertEquals(StepsWidgetLine.YESTERDAY, odd.stepsWidgetLine)
        assertEquals(1_000, odd.stepsGoal)
    }

    /** The profile (0327): kept as set; a year of birth out of range, or a sex this version does not know, left unset. */
    @Test
    fun theProfileComesBackAsKept() {
        val back = roundTrip(AppState(profileSex = ProfileSex.FEMALE, stepsHeightCm = 168, profileBirthYear = 1990))
        assertEquals(ProfileSex.FEMALE, back.profileSex)
        assertEquals(168, back.stepsHeightCm)
        assertEquals(1990, back.profileBirthYear)
        val json = """{"v":$CURRENT_SCHEMA_VERSION,"profileSex":"OTHER","profileBirthYear":1}"""
        val odd = appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain()
        assertEquals(null, odd.profileSex)
        assertEquals(null, odd.profileBirthYear)
    }
}
