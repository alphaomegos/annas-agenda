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

    /* ---------------- how many tiles fit ---------------- */

    /**
     * A phone upright keeps the list. A grid there would be two postage
     * stamps side by side, and the list is already the right shape.
     */
    @Test
    fun aPhoneHeldUprightKeepsTheList() {
        listOf(320, 360, 411, 480, 599).forEach { width ->
            assertEquals("$width dp", 1, mainMenuColumns(width))
        }
    }

    @Test
    fun aWideScreenGetsTwoColumns() {
        listOf(600, 674, 800, 899).forEach { width ->
            assertEquals("$width dp", 2, mainMenuColumns(width))
        }
    }

    @Test
    fun aVeryWideScreenGetsThree() {
        listOf(900, 1000, 1280).forEach { width ->
            assertEquals("$width dp", 3, mainMenuColumns(width))
        }
    }

    /**
     * Capped. A fourth column on a tablet makes each tile smaller than the
     * icon it holds, and large icons are the whole point of the grid.
     */
    @Test
    fun theGridNeverGrowsPastThreeColumns() {
        assertEquals(3, mainMenuColumns(4000))
        assertEquals(3, mainMenuColumns(Int.MAX_VALUE))
    }

    /**
     * A width of zero happens for one frame before anything is measured.
     * Answering with zero columns there would divide by it.
     */
    @Test
    fun anUnmeasuredScreenStillGetsOneColumn() {
        assertEquals(1, mainMenuColumns(0))
        assertEquals(1, mainMenuColumns(-100))
    }
}
