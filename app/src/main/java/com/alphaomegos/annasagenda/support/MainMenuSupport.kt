package com.alphaomegos.annasagenda

fun normalizeMainMenuOrderIds(ids: Iterable<String>): List<String> =
    ids.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

fun normalizeMainMenuItemId(id: String): String? =
    id.trim().takeIf { it.isNotEmpty() }

/**
 * [items] arranged the way the user dragged them.
 *
 * [order] holds ids, and it is a preference rather than a description: it can
 * name something that no longer exists, and it can miss something new. Ids it
 * does not mention keep their original position at the end, which is what
 * makes adding a menu item to the app safe — it appears for everyone,
 * including the users who have already reordered their menu.
 *
 * Generic over the item so it can live here, away from Compose: the menu entry
 * carries drawable ids and a click handler, and none of that belongs in a rule
 * about ordering.
 */
fun <T> itemsInMenuOrder(
    items: List<T>,
    order: List<String>,
    idOf: (T) -> String,
): List<T> {
    if (order.isEmpty()) return items

    val byId = items.associateBy(idOf)

    // distinct(), because an id named twice would otherwise put the same item
    // on the menu twice.
    val ordered = order.distinct().mapNotNull { byId[it] }

    val mentioned = order.toSet()
    val rest = items.filterNot { idOf(it) in mentioned }

    return ordered + rest
}

/**
 * How many menu tiles fit across a screen this wide.
 *
 * One means the list: a phone held upright, where a tile grid would be two
 * postage stamps side by side and the list is already the right shape.
 *
 * Past Material's compact boundary (600dp) it is three, straight away. The
 * menu has nine items, and nine is three by three: an unfolded foldable
 * upright (about 674dp) gets the whole menu on one screen with each picture
 * around 175dp, where two columns would have meant five rows and scrolling
 * through two screens of it. Decided with Eduard on 02.10.2026, replacing
 * the 0121 rule of two columns below 900dp.
 *
 * Capped at three on purpose. A fourth column on a tablet makes each tile
 * smaller than the icon it holds, and the point of the grid is that the icons
 * are large.
 */
fun mainMenuColumns(widthDp: Int): Int = when {
    widthDp < 600 -> 1
    else -> 3
}

/** The gap between two tiles, across and down. */
const val MAIN_MENU_TILE_GAP_DP = 12

/**
 * The smallest a tile gets. Below this the picture is back to the size it had
 * in 0121, which is what was wrong with it — so past this point the grid
 * scrolls instead of shrinking further.
 */
const val MAIN_MENU_TILE_MIN_DP = 120

/**
 * How tall each tile is, given the space the grid has.
 *
 * **Square, unless that would push rows off the screen.** The pictures are
 * square, so a tile taller than it is wide only adds empty space above and
 * below one — width is the ceiling. Height is the other limit: on a foldable
 * turned on its side the screen is wide and short, and square tiles there
 * would show one and a half rows. So the tile is as tall as its width, or as
 * tall as lets every row fit, whichever is less — the picture then fills the
 * height and the tile is a little wider than it is tall.
 *
 * Never below [MAIN_MENU_TILE_MIN_DP]: when even that does not fit, scrolling
 * is better than stamps.
 *
 * Zero or negative sizes happen for a frame before anything is measured, and
 * zero columns or items would divide by zero; all of them answer with the
 * minimum rather than with a crash.
 */
fun mainMenuTileHeightDp(widthDp: Int, heightDp: Int, columns: Int, itemCount: Int): Int {
    if (columns < 1 || itemCount < 1) return MAIN_MENU_TILE_MIN_DP

    val rows = (itemCount + columns - 1) / columns
    val tileWidth = (widthDp - MAIN_MENU_TILE_GAP_DP * (columns - 1)) / columns
    val fittingHeight = (heightDp - MAIN_MENU_TILE_GAP_DP * (rows - 1)) / rows

    return minOf(tileWidth, fittingHeight).coerceAtLeast(MAIN_MENU_TILE_MIN_DP)
}
