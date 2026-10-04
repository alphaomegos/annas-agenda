package com.alphaomegos.annasagenda.model

import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * What the calendar may mark a day with, each the user's to switch on or off.
 *
 * Written into the saved state **by name** and read back by name, like every
 * enum here: renaming a constant is a change to stored data.
 */
enum class CalendarBadge {
    /** A measurement was taken that day — the circle the calendar always drew. */
    ANTHROPOMETRY,

    /** Something was written down as eaten. */
    FOOD,

    /** A task on that day is still not done. */
    DEBTS,
}

/**
 * One dish of the diet for one day of the week.
 *
 * [title] is written the way the food log writes it, portion included
 * ("Творог 5 %, 200 г"), so the same parser reads both and a ticked dish lands
 * in the log looking like anything else eaten that day.
 *
 * **The plan is not history.** Ticking a dish writes an ordinary [FoodEntry]
 * that carries this [id] as its dietItemId; the plan itself is never copied
 * into the log. Changing the diet therefore changes no past day, and a dish
 * nobody ticked simply never happened — there is nothing to delete at
 * midnight, so nothing can fail to be deleted.
 */
data class DietItem(
    val id: Long,
    val title: String,
    val kcal: Int,
)
