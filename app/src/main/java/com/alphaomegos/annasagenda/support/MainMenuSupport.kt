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
 * The thresholds are Material's own compact / medium / expanded boundaries,
 * used here for what they actually describe — the point past which a
 * full-width row is mostly empty space. An unfolded foldable lands on two
 * columns upright and three on its side, which is the case this exists for:
 * the big screen used to give nothing but a longer row.
 *
 * Capped at three on purpose. A fourth column on a tablet makes each tile
 * smaller than the icon it holds, and the point of the grid is that the icons
 * are large.
 */
fun mainMenuColumns(widthDp: Int): Int = when {
    widthDp < 600 -> 1
    widthDp < 900 -> 2
    else -> 3
}
