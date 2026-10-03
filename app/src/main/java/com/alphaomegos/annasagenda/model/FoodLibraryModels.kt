package com.alphaomegos.annasagenda

/**
 * The shelves of the food library.
 *
 * Stored **by name**, in the library file and in the saved state alike:
 * renaming a constant is a change to stored data. The order here is the
 * order the shelves are shown in.
 */
enum class FoodCategory {
    MEAT,
    FISH,
    DAIRY,
    VEGETABLES,
    GRAIN,
    BREAD,
    DRINKS,
    ALCOHOL,
    DESSERT,
    FASTFOOD,

    /** Everything that is not sorted yet (03.10); sorted later from the backup. */
    OTHER,
}

/**
 * One food on a shelf, as the picker shows it.
 *
 * [key] tells where it came from: "std:<id>" for the library that ships with
 * the app, "user:<id>" for one the user added. [amount] and [unit] are the
 * portion [kcal] is for, in the shape parseFoodTitle reads them, so that a
 * food picked here is priced and re-priced exactly like a suggestion from
 * the log.
 */
data class FoodLibraryItem(
    val key: String,
    val category: FoodCategory,
    val name: String,
    val amount: Int?,
    val unit: String?,
    val kcal: Int,
)

/**
 * A food the user put on a shelf themselves.
 *
 * Kept in the saved state, and so in every backup, **on purpose**: these are
 * meant to be lifted out of a backup later and shipped as part of the
 * standard library (agreed 02.10). That is why the fields are the same as a
 * row of the library file, and why [name] is written once, in whatever
 * language it was typed in — the translation happens when it is shipped.
 *
 * [id] comes from the one id counter, like everything else the user makes.
 */
data class FoodLibraryUserItem(
    val id: Long,
    val category: FoodCategory,
    val name: String,
    val amount: Int?,
    val unit: String?,
    val kcal: Int,
)
