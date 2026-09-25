package com.alphaomegos.annasagenda

import java.time.DayOfWeek

/**
 * A half-made repeat rule, written down by name so it can be put in a Bundle.
 *
 * Names rather than ordinals on purpose. An ordinal is a position in a
 * declaration, and reordering the declaration is a normal edit that would
 * silently turn every saved Monday into a Tuesday. A name is only ever wrong
 * if somebody renames the constant, which is a rename the compiler sees.
 *
 * Both directions refuse to guess. A name this build does not know gives
 * nothing back rather than a plausible substitute: the saver's caller then
 * falls back to what the dialog would have opened with, which is the answer
 * the user last confirmed — not an answer invented from a string.
 */
fun weekDaysToSavedNames(days: Set<DayOfWeek>): List<String> =
    days.map { it.name }

fun weekDaysFromSavedNames(names: List<String>): Set<DayOfWeek> =
    names.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }.toSet()

fun repeatFreqFromSavedName(name: String): RepeatFreq? =
    RepeatFreq.entries.firstOrNull { it.name == name }
