package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.model.FoodEntry
import com.alphaomegos.annasagenda.model.ProfileSex
import com.alphaomegos.annasagenda.model.SportWorkout
import com.alphaomegos.annasagenda.support.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Other» in «Sport» (0384, Eduard 05.11): the kinds, the list's order and filters, the kilocalories counted. */
class SportSupportTest {
    private val day = LocalDate.of(2026, 11, 5)
    private val labels = mapOf("snowboard" to "Сноуборд", "zumba" to "Зумба", "strength" to "Силовая", "yoga" to "Йога")

    private fun w(id: Long, kind: String, daysAgo: Long = 0, minutes: Int? = null, kcal: Int? = null) =
        SportWorkout(id = id, date = day.minusDays(daysAgo), kind = kind, durationMinutes = minutes, kcal = kcal)

    @Test
    fun aStandardKindIsKnownByItsNameInAnyCase() {
        assertEquals("std:zumba", sportKindFromInput("  зУмба ", labels))
        assertEquals("Зумба", sportKindLabel("std:zumba", labels))
    }

    @Test
    fun ownWordsAreKeptAndMatchTheirEarlierSpelling() {
        assertEquals("Скалолазание", sportKindFromInput("скалолазание", labels, used = listOf("Скалолазание")))
        assertEquals("Падел теннис", sportKindFromInput(" Падел   теннис ", labels))
        assertNull(sportKindFromInput("   ", labels))
        // A key this build does not know, a newer build's, shows as the key.
        assertEquals("kitesurf", sportKindLabel("std:kitesurf", labels))
    }

    @Test
    fun theMostUsedKindsComeFirst() {
        val log = listOf(w(1, "std:yoga"), w(2, "Скалолазание"), w(3, "std:yoga"), w(4, "Скалолазание"), w(5, "std:yoga"), w(6, "std:zumba"))
        val offered = sportKindSuggestions(log, labels)
        assertEquals(listOf("std:yoga", "Скалолазание", "std:zumba", "std:snowboard"), offered.take(4))
        assertEquals(STANDARD_SPORT_KINDS.size + 1, offered.size)
    }

    @Test
    fun theListNarrowsToWhatIsTyped() {
        val log = listOf(w(1, "Силовая на улице"), w(2, "std:strength"))
        // Both contain «сил», any case; as often used, the standard one first.
        assertEquals(listOf("std:strength", "Силовая на улице"), sportKindSuggestions(log, labels, query = "СИЛ"))
    }

    @Test
    fun aWorkoutNeedsAKindAndANumber() {
        assertNull(sportWorkoutFromInput(1, day, null, "1:00", "300"))
        assertNull(sportWorkoutFromInput(1, day, "std:yoga", "", ""))
        assertEquals(w(1, "std:yoga", minutes = 90), sportWorkoutFromInput(1, day, "std:yoga", "1:30", ""))
        assertEquals(w(1, "std:yoga", kcal = 1200), sportWorkoutFromInput(1, day, "std:yoga", "", "1 200"))
    }

    @Test
    fun kilocaloriesAreReadAsTyped() {
        assertEquals(1200, parseSportKcal("1,200"))
        assertEquals(1200, parseSportKcal("1\u00A0200"))
        assertEquals(351, parseSportKcal("350,5"))
        assertNull(parseSportKcal("0"))
        assertNull(parseSportKcal("-40"))
        assertNull(parseSportKcal("NaN"))
        assertNull(parseSportKcal("50000"))
        assertEquals("1:05", sportDurationInput(65))
        assertEquals("45", sportDurationInput(45))
        assertEquals("", sportDurationInput(null))
    }

    @Test
    fun savingReplacesTheSameWorkoutAndKeepsTheDateOrder() {
        val a = w(1, "std:yoga", daysAgo = 2, kcal = 100)
        val b = w(2, "std:zumba", daysAgo = 1, kcal = 300)
        val list = sportWorkoutsAfterSaving(sportWorkoutsAfterSaving(emptyList(), b), a)
        assertEquals(listOf(a, b), list)
        val edited = a.copy(date = day, kcal = 150)
        assertEquals(listOf(b, edited), sportWorkoutsAfterSaving(list, edited))
        assertEquals(listOf(b), sportWorkoutsAfterDeleting(list, 1))
        assertNull(sportWorkoutsAfterDeleting(list, 9))
    }

    @Test
    fun theListIsFilteredByKindAndPeriod() {
        val log = listOf(w(1, "std:yoga", daysAgo = 40, kcal = 100), w(2, "std:yoga", daysAgo = 3, kcal = 200), w(3, "std:zumba", daysAgo = 1, kcal = 300))
        val (from, to) = sportPeriodWindow(SportPeriod.DAYS_30, day)
        assertEquals(listOf(2L), sportWorkoutsShown(log, labels, kind = "std:yoga", from = from, to = to).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), sportWorkoutsShown(log, labels).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), sportWorkoutsShown(log, labels, descending = false).map { it.id })
        assertEquals(day.minusDays(6) to null, sportPeriodWindow(SportPeriod.DAYS_7, day))
        assertEquals(day to day, sportPeriodWindow(SportPeriod.CUSTOM, day, day, day))
    }

    @Test
    fun aSortByANumberPutsTheUnknownLastEitherWay() {
        val log = listOf(w(1, "std:yoga", minutes = 60, kcal = 200), w(2, "std:zumba", kcal = 500), w(3, "std:strength", minutes = 45))
        assertEquals(listOf(2L, 1L, 3L), sportWorkoutsShown(log, labels, sort = SportSort.KCAL).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), sportWorkoutsShown(log, labels, sort = SportSort.KCAL, descending = false).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), sportWorkoutsShown(log, labels, sort = SportSort.DURATION).map { it.id })
        // By name, А to Я: Зумба, Йога, Силовая.
        assertEquals(listOf(2L, 1L, 3L), sportWorkoutsShown(log, labels, sort = SportSort.KIND).map { it.id })
    }

    @Test
    fun theTotalsAndTheDaysKilocalories() {
        val log = listOf(w(1, "std:yoga", minutes = 60, kcal = 200), w(2, "std:zumba", kcal = 500), w(3, "std:strength", daysAgo = 1, minutes = 45))
        assertEquals(SportTotals(workouts = 3, minutes = 105, kcal = 700), sportTotals(log))
        assertEquals(700, sportKcalOn(log, day))
        assertEquals(0, sportKcalOn(log, day.minusDays(1)))
    }

    /** Eduard: the workouts' kilocalories count for losing weight — in the day's spending and in the forecast. */
    @Test
    fun theDaysSpendingAndTheForecastCountTheWorkouts() {
        val weights = listOf(AnthropometryEntry(date = day.minusDays(10), weightKg = 60.0))
        val sport = listOf(w(1, "std:zumba", kcal = 400), w(2, "std:yoga", daysAgo = 1, kcal = 250))
        val without = dayEnergy(day, ProfileSex.FEMALE, 168, 1990, weights, emptyList(), emptyList())!!
        val with = dayEnergy(day, ProfileSex.FEMALE, 168, 1990, weights, emptyList(), emptyList(), sport = sport)!!
        assertEquals(400, with.sportKcal)
        assertEquals(without.totalKcal + 400, with.totalKcal)

        // In the forecast only on the days with food written down, like the steps and the runs.
        val food = listOf(FoodEntry(id = 1, date = day, title = "обед", kcal = 1500))
        fun forecast(s: List<SportWorkout>) = newWeightForecast(
            from = day.minusDays(1), to = day, today = day,
            sex = ProfileSex.FEMALE, heightCm = 168, birthYear = 1990,
            anthropometry = weights, foodLog = food, stepDays = emptyList(), workouts = emptyList(), sport = s,
        )!!
        assertEquals(400.0, forecast(sport).sportKcal, 0.001)
        assertEquals(forecast(emptyList()).balanceKcal + 400.0, forecast(sport).balanceKcal, 0.001)
    }
}
