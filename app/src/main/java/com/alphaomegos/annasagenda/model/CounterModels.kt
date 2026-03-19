package com.alphaomegos.annasagenda

import java.time.LocalDate

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