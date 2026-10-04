package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

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

    /**
     * Straight to three, with no two-column step in between: nine items are
     * three by three, and an unfolded foldable upright (674dp) is exactly the
     * screen that used to get two columns and five rows.
     */
    @Test
    fun aWideScreenGetsThreeColumns() {
        listOf(600, 674, 800, 899, 900, 1280).forEach { width ->
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

    /* ---------------- how tall a tile is ---------------- */

    /** Nine items, which is what the menu has today. */
    private val menu = 9

    /**
     * The case this exists for. Upright the unfolded foldable is narrower
     * than it is tall, so the tile is square — width is the limit — and all
     * three rows still fit.
     */
    @Test
    fun anUnfoldedFoldableUprightGetsSquareTilesThatAllFit() {
        val width = 674
        val height = 690
        val tile = mainMenuTileHeightDp(width, height, columns = 3, itemCount = menu)

        assertEquals((width - 2 * MAIN_MENU_TILE_GAP_DP) / 3, tile)
        assertTrue("3 rows of $tile dp fit in $height", 3 * tile + 2 * MAIN_MENU_TILE_GAP_DP <= height)
    }

    /**
     * On its side the same screen is wide and short. Square tiles would show
     * one and a half rows; instead they get shorter and every row fits.
     */
    @Test
    fun onItsSideTheTilesGetShorterSoEveryRowFits() {
        val width = 829
        val height = 520
        val tile = mainMenuTileHeightDp(width, height, columns = 3, itemCount = menu)

        assertTrue("$tile dp is shorter than it is wide", tile < (width - 2 * MAIN_MENU_TILE_GAP_DP) / 3)
        assertTrue("3 rows of $tile dp fit in $height", 3 * tile + 2 * MAIN_MENU_TILE_GAP_DP <= height)
    }

    /** The pictures are square: height past the width is only empty space. */
    @Test
    fun aTileIsNeverTallerThanItIsWide() {
        val tile = mainMenuTileHeightDp(widthDp = 900, heightDp = 5000, columns = 3, itemCount = menu)

        assertEquals((900 - 2 * MAIN_MENU_TILE_GAP_DP) / 3, tile)
    }

    /** When even the minimum does not fit, the grid scrolls rather than shrinks. */
    @Test
    fun aShortScreenScrollsRatherThanShrinkingPastTheMinimum() {
        assertEquals(MAIN_MENU_TILE_MIN_DP, mainMenuTileHeightDp(900, 200, columns = 3, itemCount = menu))
    }

    /** A partial last row is still a row: ten items are four rows, not three. */
    @Test
    fun aPartialLastRowCountsAsARow() {
        val height = 700
        val tile = mainMenuTileHeightDp(widthDp = 2000, heightDp = height, columns = 3, itemCount = 10)

        assertEquals((height - 3 * MAIN_MENU_TILE_GAP_DP) / 4, tile)
    }

    /** One frame before measuring, and the impossible inputs: no crash, the minimum. */
    @Test
    fun nothingMeasuredOrNothingToShowGetsTheMinimum() {
        assertEquals(MAIN_MENU_TILE_MIN_DP, mainMenuTileHeightDp(0, 0, columns = 3, itemCount = menu))
        assertEquals(MAIN_MENU_TILE_MIN_DP, mainMenuTileHeightDp(674, 690, columns = 0, itemCount = menu))
        assertEquals(MAIN_MENU_TILE_MIN_DP, mainMenuTileHeightDp(674, 690, columns = 3, itemCount = 0))
    }

    /* ---------------- where a dragged tile lands ---------------- */

    /** Three by three, 100px tiles with 10px gaps — the shape of the menu. */
    private val grid = (0 until 9).map { i ->
        MenuCell(index = i, left = (i % 3) * 110, top = (i / 3) * 110, width = 100, height = 100)
    }

    @Test
    fun aTileHeldOverANeighbourLandsThere() {
        // The middle of the centre tile.
        assertEquals(4, menuDropTarget(draggedIndex = 0, x = 160f, y = 160f, cells = grid)?.index)
    }

    @Test
    fun aTileHeldOverItsOwnPlaceGoesNowhere() {
        assertNull(menuDropTarget(draggedIndex = 4, x = 160f, y = 160f, cells = grid))
    }

    /** Over a gap nothing moves, so the tile does not flicker between two places. */
    @Test
    fun aTileHeldOverAGapGoesNowhere() {
        assertNull(menuDropTarget(draggedIndex = 0, x = 105f, y = 50f, cells = grid))
        assertNull(menuDropTarget(draggedIndex = 0, x = 50f, y = 105f, cells = grid))
    }

    /** Left and top edges are the tile's own; right and bottom belong to the next. */
    @Test
    fun anEdgeHasOneAnswer() {
        assertEquals(1, menuDropTarget(draggedIndex = 0, x = 110f, y = 0f, cells = grid)?.index)
        assertNull(menuDropTarget(draggedIndex = 0, x = 210f, y = 0f, cells = grid))
    }

    @Test
    fun aGridWithNothingLaidOutHasNowhereToLand() {
        assertNull(menuDropTarget(draggedIndex = 0, x = 50f, y = 50f, cells = emptyList()))
    }

    @Test
    fun movingForwardShiftsTheOnesInBetweenBack() {
        assertEquals(listOf("b", "c", "a", "d"), listOf("a", "b", "c", "d").withItemMoved(0, 2))
    }

    @Test
    fun movingBackShiftsTheOnesInBetweenForward() {
        assertEquals(listOf("d", "a", "b", "c"), listOf("a", "b", "c", "d").withItemMoved(3, 0))
    }

    @Test
    fun theSamePlaceOrAPlaceThatDoesNotExistChangesNothing() {
        val list = listOf("a", "b", "c")

        assertSame(list, list.withItemMoved(1, 1))
        assertSame(list, list.withItemMoved(-1, 1))
        assertSame(list, list.withItemMoved(0, 3))
    }

    /**
     * The whole gesture on the real shape: the first tile dragged onto the
     * centre one takes the centre, and the four it passed shift back by one
     * in reading order.
     */
    @Test
    fun theFirstTileDraggedToTheCentreTakesTheCentre() {
        val menu = listOf("calendar", "new_task", "someday", "recurring", "anthropometry",
            "calorimeter", "running", "counters", "reading")

        val target = menuDropTarget(draggedIndex = 0, x = 160f, y = 160f, cells = grid)!!

        assertEquals(
            listOf("new_task", "someday", "recurring", "anthropometry", "calendar",
                "calorimeter", "running", "counters", "reading"),
            menu.withItemMoved(0, target.index),
        )
    }
}
