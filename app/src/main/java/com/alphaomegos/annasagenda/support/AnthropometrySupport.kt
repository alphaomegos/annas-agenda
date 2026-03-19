package com.alphaomegos.annasagenda

import java.time.LocalDate
import kotlin.math.round

fun normalizeAnthropometryEnabledFieldIds(ids: Iterable<String>): Set<String> {
    val normalized = ids
        .asSequence()
        .map { it.trim() }
        .filter { it in allAnthropometryFieldIds() }
        .toSet()

    return if (normalized.isEmpty()) {
        defaultAnthropometryFieldIds()
    } else {
        normalized
    }
}

fun roundAnthropometryValue1(v: Double?): Double? {
    if (v == null) return null
    return round(v * 10.0) / 10.0
}

fun mergeAnthropometryEntryForDate(
    date: LocalDate,
    existing: AnthropometryEntry?,
    valuesByFieldId: Map<String, Double?>,
): AnthropometryEntry {
    fun valueOrExisting(fieldId: String, existingValue: Double?): Double? {
        return if (fieldId in valuesByFieldId) {
            roundAnthropometryValue1(valuesByFieldId[fieldId])
        } else {
            existingValue
        }
    }

    return AnthropometryEntry(
        date = date,
        armCm = valueOrExisting(AnthropometryFieldIds.ARM, existing?.armCm),
        chestCm = valueOrExisting(AnthropometryFieldIds.CHEST, existing?.chestCm),
        underChestCm = valueOrExisting(AnthropometryFieldIds.UNDER_CHEST, existing?.underChestCm),
        waistCm = valueOrExisting(AnthropometryFieldIds.WAIST, existing?.waistCm),
        bellyCm = valueOrExisting(AnthropometryFieldIds.BELLY, existing?.bellyCm),
        hipsCm = valueOrExisting(AnthropometryFieldIds.HIPS, existing?.hipsCm),
        thighCm = valueOrExisting(AnthropometryFieldIds.THIGH, existing?.thighCm),
        weightKg = valueOrExisting(AnthropometryFieldIds.WEIGHT, existing?.weightKg),
    )
}