package com.alphaomegos.annasagenda

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The twenty-ninth of July, and the three hundred and sixty-four days that
 * are not it.
 *
 * A date check that is wrong by one is wrong for a year before anybody finds
 * out, and nobody is going to sit on the main menu on the 28th to check. So
 * the neighbours are asked explicitly, as is the month a zero-based month
 * number would land on.
 */
class AnnaDayTest {

    @Test
    fun theTwentyNinthOfJulyIsTheDay() {
        assertTrue(isAnnaDay(LocalDate.of(2026, 7, 29)))
    }

    @Test
    fun itComesRoundEveryYear() {
        listOf(2024, 2025, 2026, 2027, 2028, 2100).forEach { year ->
            assertTrue("$year", isAnnaDay(LocalDate.of(year, 7, 29)))
        }
    }

    /**
     * A leap year moves February, not July, but the arithmetic that would get
     * this wrong is day-of-year arithmetic — so both kinds of year are asked.
     */
    @Test
    fun aLeapYearDoesNotMoveIt() {
        assertTrue(isAnnaDay(LocalDate.of(2028, 7, 29)))
        assertFalse(isAnnaDay(LocalDate.of(2028, 7, 28)))
        assertFalse(isAnnaDay(LocalDate.of(2028, 7, 30)))
    }

    @Test
    fun theDaysEitherSideAreNot() {
        assertFalse(isAnnaDay(LocalDate.of(2026, 7, 28)))
        assertFalse(isAnnaDay(LocalDate.of(2026, 7, 30)))
    }

    /**
     * The twenty-ninth of every other month, including the two that a
     * zero-based or one-off month number would reach instead of July.
     *
     * A leap year, because otherwise the 29th of February does not exist and
     * the loop throws before it gets to August — which is the fixture failing
     * the rule it was written to check, not the rule failing.
     */
    @Test
    fun theTwentyNinthOfAnyOtherMonthIsNot() {
        (1..12).filter { it != 7 }.forEach { month ->
            assertFalse("month $month", isAnnaDay(LocalDate.of(2028, month, 29)))
        }
    }

    @Test
    fun noOtherDayOfJulyIsIt() {
        (1..31).filter { it != 29 }.forEach { day ->
            assertFalse("July $day", isAnnaDay(LocalDate.of(2026, 7, day)))
        }
    }
}
