package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Which metro scheme is shown, and the list of the user's schemes (04.10). */
class MetroSelectionTest {

    private val moscow = MetroScheme(id = 1, city = "Москва")
    private val belgrade = MetroScheme(id = 2, city = "Белград")
    private val library = mapOf("spb" to MetroScheme(id = 1, city = "Петербург"), "moscow" to MetroScheme(id = 1, city = "Москва (библ.)"))

    @Test
    fun selectionText_goesBothWays() {
        listOf(MetroSelection.User(12), MetroSelection.Library("moscow")).forEach {
            assertEquals(it, metroSelectionOf(metroSelectionText(it)))
        }
        assertEquals("user:12", metroSelectionText(MetroSelection.User(12)))
    }

    @Test
    fun selectionText_thisBuildCannotRead_isNothingChosen() {
        listOf(null, "", "user:", "user:x", "library:", "line:3", "12").forEach {
            assertNull(it, metroSelectionOf(it))
        }
    }

    @Test
    fun whatWasChosen_isShown() {
        assertEquals(belgrade, metroSchemeToShow(listOf(moscow, belgrade), "user:2", library))
        assertEquals("Петербург", metroSchemeToShow(listOf(moscow), "library:spb", library)!!.city)
    }

    @Test
    fun aChoiceThatIsGone_fallsBackToTheUsersFirst_thenTheLibrarysFirstByName() {
        assertEquals(moscow, metroSchemeToShow(listOf(moscow, belgrade), "user:9", library))
        assertEquals(moscow, metroSchemeToShow(listOf(moscow), "library:paris", library))
        assertEquals("Москва (библ.)", metroSchemeToShow(emptyList(), null, library)!!.city)
        assertNull(metroSchemeToShow(emptyList(), null, emptyMap()))
    }

    @Test
    fun aNewScheme_getsAnIdPastTheOthers() {
        assertEquals(1L, metroNewSchemeId(emptyList()))
        assertEquals(3L, metroNewSchemeId(listOf(belgrade, moscow)))
    }

    @Test
    fun saving_replacesByIdOrAdds() {
        val renamed = moscow.copy(city = "Мск")
        assertEquals(listOf(renamed, belgrade), metroSchemesAfterSaving(listOf(moscow, belgrade), renamed))
        assertEquals(listOf(moscow, belgrade), metroSchemesAfterSaving(listOf(moscow), belgrade))
        val same = listOf(moscow)
        assertSame(same, metroSchemesAfterSaving(same, moscow))
    }

    @Test
    fun removingTheChosenScheme_clearsTheChoice_andOnlyThen() {
        assertNull(metroSelectionAfterRemoving("user:1", 1))
        assertEquals("user:2", metroSelectionAfterRemoving("user:2", 1))
        assertEquals("library:moscow", metroSelectionAfterRemoving("library:moscow", 1))
    }
}
