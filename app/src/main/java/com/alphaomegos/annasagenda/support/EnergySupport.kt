package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.ProfileSex

/*
 * The body's own energy use (28.10), for the new weight forecast: the
 * Mifflin–St Jeor equation, the one dietitians reach for first.
 */

/** Years of birth the profile takes. */
val PROFILE_BIRTH_YEARS = 1900..2100

/** Age in [year] for someone born in [birthYear] (the year's birthday reached or not: near enough here). */
fun ageIn(year: Int, birthYear: Int): Int = (year - birthYear).coerceAtLeast(0)

/**
 * Resting energy in kcal a day (Mifflin–St Jeor): 10 × kg + 6.25 × cm − 5 ×
 * years, plus 5 for men, minus 161 for women.
 */
fun restingKcal(sex: ProfileSex, weightKg: Double, heightCm: Int, ageYears: Int): Double =
    10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + if (sex == ProfileSex.MALE) 5.0 else -161.0

/**
 * The day's energy before walking and running are counted: rest times 1.2,
 * «sedentary». Low on purpose — the forecast adds steps and runs itself, and a
 * higher factor would count them twice.
 */
const val SEDENTARY_FACTOR = 1.2

fun baselineKcal(sex: ProfileSex, weightKg: Double, heightCm: Int, ageYears: Int): Double =
    restingKcal(sex, weightKg, heightCm, ageYears) * SEDENTARY_FACTOR
