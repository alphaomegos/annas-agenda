package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.STANDARD_SPORT_KINDS
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Other» in «Sport» (0385, Eduard 05.11), read from the sources: the tab is
 * offered and shows the workouts, every standard kind has a name in every
 * language, and the kilocalories reach the calorimeter and the forecast.
 */
class SportTabTest {
    @Test
    fun theTabIsOfferedAndShowsTheWorkouts() {
        val bar = source("screens/RunningModeBar.kt")
        assertTrue(bar.hasCode("onClick = { onSelect(RunningMode.OTHER) },") && bar.hasCode("R.string.running_mode_other"))
        val screen = source("screens/RunningPlanScreen.kt")
        assertTrue(screen.hasCode("SportWorkoutsContent(") && screen.hasCode("onSave = vm::saveSportWorkout,"))
        assertTrue("«+» adds a workout on the Other tab", screen.hasCode("IconButton(onClick = { sportAddRequest += 1 })"))
    }

    @Test
    fun everyStandardKindHasAName() {
        val content = source("screens/SportWorkoutsContent.kt")
        STANDARD_SPORT_KINDS.forEach { key ->
            assertTrue("$key has no name in the screen", content.hasCode("\"$key\" to R.string.sport_kind_$key,"))
            listOf("values", "values-ru", "values-b+sr+Latn").forEach { dir ->
                assertTrue("$key has no name in $dir", !string(dir, "sport_kind_$key").isNullOrBlank())
            }
        }
    }

    /** Eduard: the workouts' kilocalories count in the calorimeter for losing weight. */
    @Test
    fun theKilocaloriesReachTheCalorimeterAndTheForecast() {
        assertTrue(source("screens/CalorimeterScreen.kt").hasCode("sport = state.sportWorkouts,"))
        assertTrue(source("screens/CalorimeterNewDay.kt").hasCode("if (energy.sportKcal > 0) {"))
        assertTrue(source("screens/AnthropometryScreen.kt").hasCode("sport = state.sportWorkouts,"))
        assertTrue(source("screens/AnthropometryParts.kt").hasCode("R.string.forecast_breakdown_sport"))
    }

    private fun source(name: String): String =
        File(mainSourceRoot(), "com/alphaomegos/annasagenda/$name").readText()

    private fun string(dir: String, name: String): String? =
        File(mainResRoot(), dir).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!
            .firstNotNullOfOrNull { file ->
                Regex("<string name=\"" + name + "\">([^<]*)</string>").find(file.readText())?.groupValues?.get(1)
            }
}
