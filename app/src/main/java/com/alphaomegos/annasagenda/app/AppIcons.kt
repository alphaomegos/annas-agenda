package com.alphaomegos.annasagenda

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BreakfastDining
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.KebabDining
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every picture the calendar marks and the food library use, in one place.
 *
 * Material icons for now; Eduard will draw his own later (02.10). Swapping
 * one is one line here — no screen names an icon of these itself, and a
 * guard test keeps it that way.
 */
internal object AppIcons {

    fun calendarBadge(badge: CalendarBadge): ImageVector = when (badge) {
        CalendarBadge.ANTHROPOMETRY -> Icons.Filled.Straighten
        CalendarBadge.FOOD -> Icons.Filled.Restaurant
        CalendarBadge.DEBTS -> Icons.Filled.ErrorOutline
    }

    fun foodCategory(category: FoodCategory): ImageVector = when (category) {
        FoodCategory.MEAT -> Icons.Filled.KebabDining
        FoodCategory.FISH -> Icons.Filled.SetMeal
        FoodCategory.DAIRY -> Icons.Filled.Icecream
        FoodCategory.VEGETABLES -> Icons.Filled.Eco
        FoodCategory.GRAIN -> Icons.Filled.RiceBowl
        FoodCategory.BREAD -> Icons.Filled.BreakfastDining
        FoodCategory.DRINKS -> Icons.Filled.LocalDrink
        FoodCategory.ALCOHOL -> Icons.Filled.WineBar
        FoodCategory.DESSERT -> Icons.Filled.Cake
        FoodCategory.FASTFOOD -> Icons.Filled.Fastfood
        FoodCategory.OTHER -> Icons.Filled.Category
    }
}
