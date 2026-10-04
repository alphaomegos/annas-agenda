package com.alphaomegos.annasagenda.model

import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/** Which of today's tasks the daily summary lists. */
enum class SummaryToday {
    /** Everything planned for today. */
    ALL,

    /** Only what is not ticked yet. */
    UNDONE,

    /** None: the summary is about debts alone, or about nothing. */
    NONE,
}

/**
 * When the app may speak up, and about what.
 *
 * Every default is silence — an empty list of times and no reminders — so
 * that the step to schema 7 makes no phone buzz that did not buzz before.
 *
 * [summaryMinutes] are minutes since midnight, kept sorted and without
 * repeats. [reminderLeadMinutes] is how long before a timed task's time to
 * remind of it: null for never, 0 for at the time itself.
 */
data class NotificationSettings(
    val summaryMinutes: List<Int> = emptyList(),
    val summaryToday: SummaryToday = SummaryToday.UNDONE,
    val summaryDebts: Boolean = true,
    val reminderLeadMinutes: Int? = null,
)

/** The choices the settings offer for a reminder; null is "off". */
val REMINDER_LEAD_CHOICES: List<Int?> = listOf(null, 0, 5, 15, 30, 60)
