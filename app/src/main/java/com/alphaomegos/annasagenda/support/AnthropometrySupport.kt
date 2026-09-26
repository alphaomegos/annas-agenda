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

/**
 * The measurements list after the user saves one day's form.
 *
 * Three rules, and only the first was anywhere a test could read it.
 *
 * A field the form did not send keeps whatever that day already had — the
 * screen can show a subset, and saving a subset must not wipe the rest. That
 * part is [mergeAnthropometryEntryForDate]'s.
 *
 * A day left with no measurements at all is **removed**, not stored empty.
 * Clearing every field is how the user deletes a day; an empty entry kept in
 * the list would draw a point on the chart with nothing in it and keep the day
 * in the history for ever.
 *
 * The list stays in date order, because everything downstream — the chart, the
 * last-value lookup, the export — reads it as a sequence rather than sorting
 * it again.
 */
fun anthropometryAfterSavingDate(
    entries: List<AnthropometryEntry>,
    date: LocalDate,
    valuesByFieldId: Map<String, Double?>,
): List<AnthropometryEntry> {
    val entry = mergeAnthropometryEntryForDate(
        date = date,
        existing = entries.firstOrNull { it.date == date },
        valuesByFieldId = valuesByFieldId,
    )

    val withoutThatDay = entries.filterNot { it.date == date }

    return if (!entry.hasAnyValue()) withoutThatDay else (withoutThatDay + entry).sortedBy { it.date }
}
