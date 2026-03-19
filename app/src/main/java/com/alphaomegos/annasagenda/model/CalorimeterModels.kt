package com.alphaomegos.annasagenda

import java.time.LocalDate

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
)