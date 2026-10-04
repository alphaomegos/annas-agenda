package com.alphaomegos.annasagenda.model

import java.time.LocalDate
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/** Daily goal changes: new value applies from [date] and all future days. */
data class CalorieGoalChange(
    val date: LocalDate,
    val kcal: Int,
)

data class FoodEntry(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val kcal: Int,
    // The diet dish this was ticked off from, or null for anything typed in.
    // Kept after the dish is removed from the diet: the entry is what was
    // eaten, and a past day does not change because the plan did.
    val dietItemId: Long? = null,
)