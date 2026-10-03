package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 6.4: the library file, its shelves, and the user's own foods. */
class FoodLibrarySupportTest {

    private val t = "\t"

    private fun row(vararg cols: String) = cols.joinToString(t)

    private val file = listOf(
        "# a comment",
        "",
        row("dairy.curd", "DAIRY", "100", "g", "121", "en=Cottage cheese", "ru=Творог", "sr-Latn=Sitni sir"),
        row("drinks.milk", "DRINKS", "200", "ml", "106", "en=Milk", "ru=Молоко"),
        row("dessert.cake", "DESSERT", "", "", "350", "en=Cake"),
    ).joinToString("\n")

    private val standard = parseFoodLibrary(file).foods

    @Test
    fun aWellFormedFileReadsWithoutProblems() {
        val parsed = parseFoodLibrary(file)

        assertEquals(emptyList<String>(), parsed.problems)
        assertEquals(listOf("dairy.curd", "drinks.milk", "dessert.cake"), parsed.foods.map { it.id })
        assertEquals(StandardFood("dessert.cake", FoodCategory.DESSERT, null, null, 350, mapOf("en" to "Cake")), parsed.foods[2])
    }

    /** One typo must not empty every shelf; it is named and skipped. */
    @Test
    fun aBadRowIsSkippedAndNamedTheRestStays() {
        val broken = listOf(
            row("ok.one", "DAIRY", "100", "g", "1", "en=One"),
            row("Bad Id", "DAIRY", "100", "g", "1", "en=x"),
            row("no.shelf", "SPACE", "100", "g", "1", "en=x"),
            row("bad.unit", "DAIRY", "100", "kg", "1", "en=x"),
            row("half.portion", "DAIRY", "100", "", "1", "en=x"),
            row("neg.kcal", "DAIRY", "100", "g", "-5", "en=x"),
            row("no.english", "DAIRY", "100", "g", "1", "ru=x"),
            row("bad.name", "DAIRY", "100", "g", "1", "Творог"),
            row("ok.one", "MEAT", "100", "g", "2", "en=Again"),
            row("short", "DAIRY"),
        ).joinToString("\n")

        val parsed = parseFoodLibrary(broken)

        assertEquals(listOf("ok.one"), parsed.foods.map { it.id })
        assertEquals(FoodCategory.DAIRY, parsed.foods.single().category) // the first ok.one wins
        assertEquals(9, parsed.problems.size)
        assertTrue(parsed.problems.first().startsWith("line 2:"))
    }

    @Test
    fun windowsLineEndsAreFine() {
        assertEquals(3, parseFoodLibrary(file.replace("\n", "\r\n")).foods.size)
        assertTrue(parseFoodLibrary(file.replace("\n", "\r\n")).problems.isEmpty())
    }

    @Test
    fun theNameFollowsTheLanguageAndFallsBackToEnglish() {
        val curd = standard.first()

        assertEquals("Творог", curd.nameFor("ru"))
        assertEquals("Sitni sir", curd.nameFor("sr-Latn"))
        assertEquals("Sitni sir", curd.nameFor("sr-Latn-RS"))
        assertEquals("Творог", curd.nameFor("ru-RU"))
        assertEquals("Cottage cheese", curd.nameFor("en"))
        // Gilbertese, and Serbian Cyrillic which the file does not carry.
        assertEquals("Cottage cheese", curd.nameFor("gil"))
        assertEquals("Cottage cheese", curd.nameFor("sr-Cyrl"))
    }

    /** Written the way parseFoodTitle reads it, so re-pricing works on a picked food. */
    @Test
    fun theUnitIsWrittenInTheUsersScript() {
        val milk = standard[1]

        assertEquals("мл", milk.asLibraryItem("ru").unit)
        assertEquals("ml", milk.asLibraryItem("en").unit)
        val title = foodTitleWithAmount(milk.nameFor("ru"), 200, milk.asLibraryItem("ru").unit!!)
        assertEquals(ParsedFoodTitle("Молоко", 200, "мл"), parseFoodTitle(title))
    }

    @Test
    fun aShelfHoldsBothKindsSortedByName() {
        val mine = listOf(
            FoodLibraryUserItem(id = 5, category = FoodCategory.DAIRY, name = "Айран", amount = 250, unit = "мл", kcal = 60),
            FoodLibraryUserItem(id = 6, category = FoodCategory.MEAT, name = "Котлета", amount = 100, unit = "г", kcal = 220),
        )

        val dairy = foodLibraryShelf(FoodCategory.DAIRY, standard, mine, "ru")

        assertEquals(listOf("user:5", "std:dairy.curd"), dairy.map { it.key })
        assertTrue(foodLibraryShelf(FoodCategory.FISH, standard, mine, "ru").isEmpty())
    }

    @Test
    fun searchLooksAcrossEveryShelf() {
        val mine = listOf(FoodLibraryUserItem(id = 5, category = FoodCategory.DAIRY, name = "Молочный коктейль", amount = null, unit = null, kcal = 200))

        assertEquals(listOf("Молоко", "Молочный коктейль"), foodLibrarySearch("  мол", standard, mine, "ru").map { it.name })
        assertTrue(foodLibrarySearch(" ", standard, mine, "ru").isEmpty())
    }

    @Test
    fun aFoodIsFoundAgainByItsKeyAfterTurningThePhone() {
        val mine = listOf(FoodLibraryUserItem(id = 5, category = FoodCategory.DAIRY, name = "Айран", amount = 250, unit = "мл", kcal = 60))

        assertEquals("Творог", foodLibraryItemByKey("std:dairy.curd", standard, mine, "ru")?.name)
        assertEquals("Айран", foodLibraryItemByKey("user:5", standard, mine, "ru")?.name)
        assertNull(foodLibraryItemByKey("user:6", standard, mine, "ru"))
        assertNull(foodLibraryItemByKey("dairy.curd", standard, mine, "ru"))
    }

    /** Picking a library food prices a different portion like any suggestion. */
    @Test
    fun aPickedFoodIsRepricedWhenThePortionChanges() {
        val curd = standard.first().asLibraryItem("ru")
        val picked = foodDraftAfterPickingSuggestion(curd.asSuggestion())

        assertEquals("Творог, 100 г", picked.title)
        assertEquals("242", foodDraftAfterTitleChange(picked, "Творог, 200 г").kcalText)
    }

    @Test
    fun theUsersFoodTakesItsPortionOutOfTheName() {
        val item = foodLibraryUserItemFrom(7, FoodCategory.DAIRY, "Сырок глазированный, 40 г", 160)!!

        assertEquals("Сырок глазированный", item.name)
        assertEquals(40, item.amount)
        assertEquals("г", item.unit)
        assertNull(foodLibraryUserItemFrom(8, FoodCategory.DAIRY, "  ", 1))
    }

    /** What the backup export will produce reads back as the same food. */
    @Test
    fun aUsersFoodBecomesALineTheLibraryReadsBack() {
        val item = FoodLibraryUserItem(id = 7, category = FoodCategory.DAIRY, name = "Сырок", amount = 40, unit = "г", kcal = 160)

        val back = parseFoodLibrary(foodLibraryRowFor(item, "ru"))

        assertTrue(back.problems.isEmpty())
        val food = back.foods.single()
        assertEquals("user.7", food.id)
        assertEquals("Сырок", food.nameFor("ru"))
        assertEquals("g", food.unit)
        assertEquals(40, food.amount)
        assertEquals(160, food.kcal)
    }

    /** 03.10: a shelf for what is not sorted yet, shown last, readable from the file. */
    @Test
    fun otherIsTheLastShelfAndTheFileKnowsIt() {
        assertEquals(FoodCategory.OTHER, FoodCategory.entries.last())
        val parsed = parseFoodLibrary(row("misc.thing", "OTHER", "", "", "10", "en=Thing"))
        assertTrue(parsed.problems.isEmpty())
        assertEquals(FoodCategory.OTHER, parsed.foods.single().category)
    }
}

/** Which food the add-a-meal dialog prices from, moved out of the screen (04.10). */
class FoodDraftPricingSourceTest {

    private val day = java.time.LocalDate.of(2026, 10, 7)
    private val standard = parseFoodLibrary("dairy.curd\tDAIRY\t100\tg\t121\ten=Cottage cheese\tru=Творог").foods
    private val log = listOf(FoodEntry(id = 1, date = day, title = "Творог, 200 г", kcal = 300))

    @Test
    fun aPickedShelfFoodWinsOverTheSameNameInTheLog() {
        val src = foodDraftPricingSource("std:dairy.curd", "Творог", standard, emptyList(), log, "ru")!!

        assertEquals(100, src.amount)
        assertEquals(121, src.kcal)
    }

    @Test
    fun withoutAKeyTheLogPricesItAndAGoneKeyFallsBackToTheLog() {
        assertEquals(300, foodDraftPricingSource(null, "Творог", standard, emptyList(), log, "ru")!!.kcal)
        assertEquals(300, foodDraftPricingSource("user:99", "Творог", standard, emptyList(), log, "ru")!!.kcal)
    }

    @Test
    fun nothingPickedIsNothingPriced() {
        assertNull(foodDraftPricingSource(null, null, standard, emptyList(), log, "ru"))
    }
}
