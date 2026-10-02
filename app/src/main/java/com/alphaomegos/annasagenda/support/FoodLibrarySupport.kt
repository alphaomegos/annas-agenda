package com.alphaomegos.annasagenda

/**
 * The food library: the shelves that ship with the app (assets/
 * food_library.tsv) and the foods the user adds to them.
 *
 * The file is plain text, one food per line, so that it can be filled in a
 * text editor and reviewed as a diff; its own header describes the columns.
 * Read here by pure code, so a JVM test can hold the shipped file to it.
 */

/** One row of the library file, every language it names kept. */
data class StandardFood(
    val id: String,
    val category: FoodCategory,
    val amount: Int?,
    val unit: String?,
    val kcal: Int,
    /** Language tag ("en", "ru", "sr-Latn") to name. "en" is always there. */
    val names: Map<String, String>,
)

/** The file read: the foods that made sense, and a line for each that did not. */
data class FoodLibraryParse(
    val foods: List<StandardFood>,
    val problems: List<String>,
)

/** The units a row may give: what a portion is counted in, nothing else. */
val FOOD_LIBRARY_UNITS = setOf("g", "ml")

private val foodIdPattern = Regex("""[a-z0-9][a-z0-9.\-]*""")

/**
 * Reads the library file.
 *
 * A bad row is skipped and reported rather than failing the whole file: a
 * typo on one line must not empty every shelf. The guard test asks for the
 * shipped file to have no problems at all, so a typo is caught before it
 * ships instead of on the phone. A repeated id keeps the first row.
 */
fun parseFoodLibrary(text: String): FoodLibraryParse {
    val foods = mutableListOf<StandardFood>()
    val problems = mutableListOf<String>()
    val seen = mutableSetOf<String>()

    text.lines().forEachIndexed { index, raw ->
        val line = raw.trimEnd('\r')
        if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
        val n = index + 1
        val cols = line.split('\t')
        fun bad(why: String) { problems += "line $n: $why" }

        if (cols.size < 6) return@forEachIndexed bad("expected at least 6 columns, got ${cols.size}")
        val id = cols[0].trim()
        if (!foodIdPattern.matches(id)) return@forEachIndexed bad("id '$id' is not [a-z0-9.-]")
        if (id in seen) return@forEachIndexed bad("id '$id' is used twice")

        val category = FoodCategory.entries.firstOrNull { it.name == cols[1].trim() }
            ?: return@forEachIndexed bad("no shelf called '${cols[1].trim()}'")

        val amountText = cols[2].trim()
        val unitText = cols[3].trim()
        val amount = if (amountText.isEmpty()) null else amountText.toIntOrNull()
        if (amountText.isNotEmpty() && (amount == null || amount <= 0)) return@forEachIndexed bad("amount '$amountText'")
        val unit = unitText.ifEmpty { null }
        if (unit != null && unit !in FOOD_LIBRARY_UNITS) return@forEachIndexed bad("unit '$unitText' is not g or ml")
        if ((amount == null) != (unit == null)) return@forEachIndexed bad("an amount needs a unit and a unit an amount")

        val kcal = cols[4].trim().toIntOrNull()
        if (kcal == null || kcal < 0) return@forEachIndexed bad("kcal '${cols[4].trim()}'")

        val names = mutableMapOf<String, String>()
        for (cell in cols.drop(5)) {
            val eq = cell.indexOf('=')
            val lang = if (eq > 0) cell.substring(0, eq).trim() else ""
            val name = if (eq > 0) cell.substring(eq + 1).trim() else ""
            if (lang.isEmpty() || name.isEmpty()) return@forEachIndexed bad("name cell '$cell' is not language=name")
            names[lang] = name
        }
        if ("en" !in names) return@forEachIndexed bad("no en= name")

        seen += id
        foods += StandardFood(id, category, amount, unit, kcal, names)
    }
    return FoodLibraryParse(foods, problems)
}

/**
 * The name for [languageTag]: the whole tag ("sr-Latn"), then with its last
 * part dropped, down to the language alone ("sr"), then English. Gilbertese, which the library does not carry,
 * reads English like the rest of the app's untranslated strings.
 */
fun StandardFood.nameFor(languageTag: String): String {
    // "sr-Latn-RS" -> "sr-Latn" -> "sr": the phone may add a region the file does not.
    var tag = languageTag
    while (tag.isNotEmpty()) {
        names[tag]?.let { return it }
        tag = if ('-' in tag) tag.substringBeforeLast('-') else ""
    }
    return names.getValue("en")
}

/** g and ml as the user's language writes them, the way parseFoodTitle reads them back. */
fun foodUnitFor(unit: String, languageTag: String): String =
    if (languageTag.substringBefore('-') == "ru") {
        when (unit) { "g" -> "г"; "ml" -> "мл"; else -> unit }
    } else {
        unit
    }

fun StandardFood.asLibraryItem(languageTag: String): FoodLibraryItem = FoodLibraryItem(
    key = "std:$id",
    category = category,
    name = nameFor(languageTag),
    amount = amount,
    unit = unit?.let { foodUnitFor(it, languageTag) },
    kcal = kcal,
)

fun FoodLibraryUserItem.asLibraryItem(): FoodLibraryItem = FoodLibraryItem(
    key = "user:$id",
    category = category,
    name = name,
    amount = amount,
    unit = unit,
    kcal = kcal,
)

/** One shelf: the shipped foods and the user's, by name. */
fun foodLibraryShelf(
    category: FoodCategory,
    standard: List<StandardFood>,
    user: List<FoodLibraryUserItem>,
    languageTag: String,
): List<FoodLibraryItem> =
    (standard.filter { it.category == category }.map { it.asLibraryItem(languageTag) } +
        user.filter { it.category == category }.map { it.asLibraryItem() })
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/** Every food in the library whose name holds [typed], for the search box. */
fun foodLibrarySearch(
    typed: String,
    standard: List<StandardFood>,
    user: List<FoodLibraryUserItem>,
    languageTag: String,
): List<FoodLibraryItem> {
    val needle = typed.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return FoodCategory.entries
        .flatMap { foodLibraryShelf(it, standard, user, languageTag) }
        .filter { needle in it.name.lowercase() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/** A library food as a suggestion, so picking it is picking a suggestion. */
fun FoodLibraryItem.asSuggestion(): FoodSuggestion =
    FoodSuggestion(name = name, amount = amount, unit = unit, kcal = kcal, timesEaten = 0, lastEatenOn = null)

/** A food from [items] by its key, or null if it is gone. */
fun foodLibraryItemByKey(
    key: String,
    standard: List<StandardFood>,
    user: List<FoodLibraryUserItem>,
    languageTag: String,
): FoodLibraryItem? = when {
    key.startsWith("std:") -> standard.firstOrNull { "std:${it.id}" == key }?.asLibraryItem(languageTag)
    key.startsWith("user:") -> user.firstOrNull { "user:${it.id}" == key }?.asLibraryItem()
    else -> null
}

/**
 * The user's own food, from what they typed: the portion read out of the
 * name the way the food log reads it ("Сырок, 40 г"). Null for a blank name.
 */
fun foodLibraryUserItemFrom(id: Long, category: FoodCategory, typed: String, kcal: Int): FoodLibraryUserItem? {
    val parsed = parseFoodTitle(typed)
    if (parsed.name.isBlank()) return null
    return FoodLibraryUserItem(
        id = id,
        category = category,
        name = parsed.name,
        amount = parsed.amount,
        unit = parsed.unit,
        kcal = kcal.coerceAtLeast(0),
    )
}

/**
 * A user's food as a line of the library file, for shipping it as standard.
 *
 * Its id is "user.<id>", to be renamed by hand when it is moved into the
 * file; the name goes under [languageTag], and under en= too until somebody
 * translates it, because a row without en= is refused. Units are written back
 * the way the file writes them, g or ml.
 */
fun foodLibraryRowFor(item: FoodLibraryUserItem, languageTag: String): String {
    val unit = when (item.unit?.lowercase()) {
        null -> ""
        "г", "g" -> "g"
        "мл", "ml" -> "ml"
        else -> item.unit
    }
    val names = buildList {
        add("en=${item.name}")
        if (languageTag != "en") add("$languageTag=${item.name}")
    }
    return listOf("user.${item.id}", item.category.name, item.amount?.toString().orEmpty(), unit, item.kcal.toString())
        .plus(names)
        .joinToString("\t")
}
