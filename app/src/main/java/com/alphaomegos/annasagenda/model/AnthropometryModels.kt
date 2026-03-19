package com.alphaomegos.annasagenda

import java.time.LocalDate

object AnthropometryFieldIds {
    const val ARM = "arm"
    const val CHEST = "chest"
    const val UNDER_CHEST = "under_chest"
    const val WAIST = "waist"
    const val BELLY = "belly"
    const val HIPS = "hips"
    const val THIGH = "thigh"
    const val WEIGHT = "weight"
}

fun allAnthropometryFieldIds(): Set<String> = setOf(
    AnthropometryFieldIds.ARM,
    AnthropometryFieldIds.CHEST,
    AnthropometryFieldIds.UNDER_CHEST,
    AnthropometryFieldIds.WAIST,
    AnthropometryFieldIds.BELLY,
    AnthropometryFieldIds.HIPS,
    AnthropometryFieldIds.THIGH,
    AnthropometryFieldIds.WEIGHT,
)

fun defaultAnthropometryFieldIds(): Set<String> = allAnthropometryFieldIds()

data class AnthropometryEntry(
    val date: LocalDate,
    val armCm: Double? = null,
    val chestCm: Double? = null,
    val underChestCm: Double? = null,
    val waistCm: Double? = null,
    val bellyCm: Double? = null,
    val hipsCm: Double? = null,
    val thighCm: Double? = null,
    val weightKg: Double? = null,
) {
    fun hasAnyValue(): Boolean =
        armCm != null ||
                chestCm != null ||
                underChestCm != null ||
                waistCm != null ||
                bellyCm != null ||
                hipsCm != null ||
                thighCm != null ||
                weightKg != null
}