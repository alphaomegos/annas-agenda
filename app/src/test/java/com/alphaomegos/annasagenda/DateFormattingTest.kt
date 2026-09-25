package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Dates read in the app's language, not the machine's.
 *
 * The assertions are about behaviour rather than exact wording on purpose.
 * The words themselves come from the platform's locale data, and Android's is
 * not the same vintage as this JVM's — pinning "2 мар. 2026 г." here would
 * fail on a phone for a reason that has nothing to do with this app.
 */
class DateFormattingTest {

    private val day = LocalDate.of(2026, 3, 2)
    private val russian = Locale.forLanguageTag("ru-RU")

    @Test
    fun theLanguageAsksForChangesTheAnswer() {
        assertNotEquals(
            formatMediumDate(day, Locale.US),
            formatMediumDate(day, russian),
        )
    }

    /**
     * The point of the whole patch. Before it these dates were written with
     * `LocalDate.toString()`, which is ISO and the same everywhere — a phone
     * in Russian showed "2026-03-02" in a dialog while every other screen
     * showed the month by name.
     */
    @Test
    fun neitherLengthIsTheIsoForm() {
        assertNotEquals("2026-03-02", formatMediumDate(day, Locale.US))
        assertNotEquals("2026-03-02", formatShortDate(day, Locale.US))
        assertNotEquals("2026-03-02", formatMediumDate(day, russian))
        assertNotEquals("2026-03-02", formatShortDate(day, russian))
    }

    /**
     * Whatever the words are, the day and the year have to be in them, and
     * the short form has to be shorter — it goes under a chart, where there
     * is room for about ten characters.
     */
    @Test
    fun bothFormsSayWhichDayAndWhichYear() {
        listOf(Locale.US, russian, Locale.GERMANY).forEach { locale ->
            val medium = formatMediumDate(day, locale)
            val short = formatShortDate(day, locale)

            assertTrue("$locale medium: $medium", medium.contains("2"))
            assertTrue("$locale medium: $medium", medium.contains("2026"))
            assertTrue("$locale short: $short", short.contains("2"))
            assertTrue("$locale short: $short", short.length <= medium.length)
        }
    }

    /**
     * Said out loud, the way 0092 learned to: change what the machine is set
     * to and ask for the same answer.
     */
    @Test
    fun theAnswerDoesNotFollowWhateverLanguageTheMachineSpeaks() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val asked = formatMediumDate(day, russian)

            Locale.setDefault(Locale.JAPAN)
            assertEquals(asked, formatMediumDate(day, russian))
        } finally {
            Locale.setDefault(original)
        }
    }
}
