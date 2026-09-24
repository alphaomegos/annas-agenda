package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The main menu's saved order, which is a preference rather than a
 * description of what exists.
 *
 * It can name something the app no longer has and it can miss something the
 * app has just gained, and both have to keep working — the second one is what
 * decides whether adding a menu item reaches the users who have already
 * dragged their menu into shape, or only the ones who never touched it.
 */
class MainMenuSupportTest {

    private data class Item(val id: String)

    private val all = listOf(Item("calendar"), Item("new_task"), Item("reading"))

    @Test
    fun anEmptyOrderLeavesEverythingWhereItIs() {
        assertSame(all, itemsInMenuOrder(all, emptyList()) { it.id })
    }

    @Test
    fun itemsComeBackInTheOrderTheUserChose() {
        val ordered = itemsInMenuOrder(all, listOf("reading", "calendar", "new_task")) { it.id }

        assertEquals(listOf("reading", "calendar", "new_task"), ordered.map { it.id })
    }

    /**
     * A new menu item cannot be in an order saved before it existed. It has to
     * appear anyway, or the feature ships to nobody who has ever reordered.
     */
    @Test
    fun anItemTheOrderDoesNotMentionStillAppears() {
        val ordered = itemsInMenuOrder(all, listOf("reading")) { it.id }

        assertEquals(listOf("reading", "calendar", "new_task"), ordered.map { it.id })
    }

    @Test
    fun anOrderNamingSomethingGoneIsNotAProblem() {
        val ordered = itemsInMenuOrder(all, listOf("travel", "reading", "gone")) { it.id }

        assertEquals(listOf("reading", "calendar", "new_task"), ordered.map { it.id })
    }

    @Test
    fun anIdNamedTwiceDoesNotPutTheItemOnTheMenuTwice() {
        val ordered = itemsInMenuOrder(all, listOf("reading", "reading", "calendar")) { it.id }

        assertEquals(listOf("reading", "calendar", "new_task"), ordered.map { it.id })
    }

    @Test
    fun normalizeMainMenuOrderIds_dropsBlanksTrimsAndKeepsTheFirstOfEachId() {
        assertEquals(
            listOf("calendar", "reading"),
            normalizeMainMenuOrderIds(listOf(" calendar ", "", "   ", "reading", "calendar")),
        )
    }

    @Test
    fun normalizeMainMenuItemId_refusesAnIdThatIsNothing() {
        assertEquals("reading", normalizeMainMenuItemId("  reading "))
        assertNull(normalizeMainMenuItemId("   "))
        assertNull(normalizeMainMenuItemId(""))
    }
}
