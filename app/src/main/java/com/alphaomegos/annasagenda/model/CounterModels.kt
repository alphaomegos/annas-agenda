package com.alphaomegos.annasagenda.model

import java.time.LocalDate
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

sealed interface Counter {
    val id: Long
    val title: String
}

data class DateRangeCounter(
    override val id: Long,
    override val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
) : Counter

data class ManualCounter(
    override val id: Long,
    override val title: String,
    val balance: Int,
) : Counter
/**
 * A day counter (26.10, Eduard: «таймер»): it counts the days since
 * [startDate] — 1 on that day, one more at every midnight — plus whatever the
 * user corrected by hand, kept apart as [correction] and shown under it. Not
 * tied to tasks.
 */
data class DayTimerCounter(
    override val id: Long,
    override val title: String,
    val startDate: LocalDate,
    val correction: Int = 0,
) : Counter
