package com.alphaomegos.annasagenda

/**
 * Reading what the user typed into an anthropometry field.
 *
 * The distinction this type exists for: an empty field and an unreadable field
 * are not the same thing. The dialog used to collapse both into null, and
 * mergeAnthropometryEntryForDate treats null as "erase this measurement". So
 * typing "72.5 kg" into the weight field — with the unit, on a full text
 * keyboard, into a field that was pre-filled with the saved value — deleted
 * that day's weight and reported "Saved".
 *
 * Empty still means erase. That is a real thing to want, and it is what the
 * hint above the fields promises. Unreadable now means the save does not
 * happen at all.
 */
sealed interface AnthropometryFieldInput {

    /** The field was left empty: the measurement for that day is removed. */
    data object Cleared : AnthropometryFieldInput

    data class Value(val value: Double) : AnthropometryFieldInput

    /** Something was typed, and it is not a measurement. */
    data object Invalid : AnthropometryFieldInput
}

/**
 * The largest measurement accepted, in cm or kg.
 *
 * Deliberately generous: it is here to keep infinities, negatives and obvious
 * nonsense out of the chart, not to second-guess a body. A plain typo like 725
 * for 72.5 is inside it and stays possible — that one is visible on screen and
 * can be corrected, unlike a value that silently disappears.
 */
const val ANTHROPOMETRY_MAX_VALUE = 999.0

fun parseAnthropometryField(raw: String): AnthropometryFieldInput {
    val text = raw.trim()
    if (text.isEmpty()) return AnthropometryFieldInput.Cleared

    val value = text.replace(',', '.').toDoubleOrNull()
        ?: return AnthropometryFieldInput.Invalid

    // NaN and the infinities parse happily and then travel all the way into
    // the chart, where they turn every path into nothing.
    if (!value.isFinite()) return AnthropometryFieldInput.Invalid

    if (value <= 0.0 || value > ANTHROPOMETRY_MAX_VALUE) return AnthropometryFieldInput.Invalid

    return AnthropometryFieldInput.Value(value)
}

/**
 * What a whole dialog's worth of text means.
 *
 * [values] carries only the fields that are safe to write — cleared ones as
 * null, readable ones as their number. Unreadable fields are left out
 * altogether, so even a caller that ignored [invalidFieldIds] would leave the
 * stored measurement alone rather than erase it.
 */
data class AnthropometryInputParse(
    val values: Map<String, Double?>,
    val invalidFieldIds: Set<String>,
) {
    val isValid: Boolean get() = invalidFieldIds.isEmpty()
}

fun parseAnthropometryInputs(rawByFieldId: Map<String, String>): AnthropometryInputParse {
    val values = mutableMapOf<String, Double?>()
    val invalid = mutableSetOf<String>()

    rawByFieldId.forEach { (fieldId, raw) ->
        when (val parsed = parseAnthropometryField(raw)) {
            AnthropometryFieldInput.Cleared -> values[fieldId] = null
            is AnthropometryFieldInput.Value -> values[fieldId] = parsed.value
            AnthropometryFieldInput.Invalid -> invalid += fieldId
        }
    }

    return AnthropometryInputParse(values = values, invalidFieldIds = invalid)
}
